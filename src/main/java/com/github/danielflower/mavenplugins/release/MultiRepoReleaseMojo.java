package com.github.danielflower.mavenplugins.release;

import org.apache.maven.model.Scm;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.io.DefaultSettingsWriter;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.asList;

/**
 * Releases the project.
 */
@Mojo(
    name = "multirelease",
    requiresDirectInvocation = true, // this should not be bound to a phase as this plugin starts a phase itself
    inheritByDefault = true, // so you can configure this in a shared parent pom
    requiresProject = true, // this can only run against a maven project
    aggregator = true // the plugin should only run once against the aggregator pom
)
public class MultiRepoReleaseMojo extends BaseMojo {

    /**
     * <p>
     * The goals to run against the project during a release. By default this is "deploy" which
     * means the release version of your artifact will be tested and deployed.
     * </p>
     * <p>
     * You can specify more goals and maven options. For example if you want to perform
     * a clean, build a maven site, and then deploys it, use:
     * </p>
     * <pre>
     * {@code
     * <releaseGoals>
     *     <releaseGoal>clean</releaseGoal>
     *     <releaseGoal>site</releaseGoal>
     *     <releaseGoal>deploy</releaseGoal>
     * </releaseGoals>
     * }
     * </pre>
     */
    @Parameter(alias = "releaseGoals")
    private List<String> goals;

    /**
     * <p>
     *     Profiles to activate during the release.
     * </p>
     * <p>
     *     Note that if any profiles are activated during the build using the `-P` or `--activate-profiles` will also be activated during release.
     *     This gives two options for running releases: either configure it in the plugin configuration, or activate profiles from the command line.
     * </p>
     * @since 1.0.1
     */
    @Parameter(alias = "releaseProfiles")
    private List<String> releaseProfiles;

    /**
     * If true then tests will not be run during a release.
     * This is the same as adding -DskipTests=true to the release goals.
     */
    @Parameter(alias = "skipTests", defaultValue = "false", property = "skipTests")
    private boolean skipTests;
    
	/**
	 * Specifies a custom, user specific Maven settings file to be used during the release build.
     *
     * @deprecated In versions prior to 2.1, if the plugin was run with custom user settings the settings were ignored
     * during the release phase. Now that custom settings are inherited, setting this value is no longer needed.
     * Please use the '-s' command line parameter to set custom user settings.
	 */
	@Parameter(alias = "userSettings")
	private File userSettings;

	/**
	 * Specifies a custom, global Maven settings file to be used during the release build.
     *
     * @deprecated In versions prior to 2.1, if the plugin was run with custom global settings the settings were ignored
     * during the release phase. Now that custom settings are inherited, setting this value is no longer needed.
     * Please use the '-gs' command line parameter to set custom global settings.
     */
	@Parameter(alias = "globalSettings")
	private File globalSettings;
        
    /**
     * Push tags to remote repository as they are created.
     */
    @Parameter(alias = "pushTags", defaultValue="true", property="push")
    private boolean pushTags;


    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        Log log = getLog();

        if(buildNumber != null){
            throw new MojoExecutionException("Build  numbers are not supported in multirelease mode. Stopping execution.");
        }

        try {
            configureJsch(log);
            List<ModuleInfo> moduleInfos = gatherModulesInfo();
            MultiRepoReactor multiRepoReactor = MultiRepoReactor.fromProjects(log, project, moduleInfos, modulesToForceRelease, noChangesAction);
            if (multiRepoReactor == null) {
                return;
            }
            figureOutTagNamesAndThrowIfAlreadyExists(multiRepoReactor.getModulesInBuildOrder(), modulesToRelease, moduleInfos);
            updatePoms(log, multiRepoReactor, false);
            // Do this before running the maven build in case the build uploads some artifacts and then fails. If it is
            // not tagged in a half-failed build, then subsequent releases will re-use a version that is already in Nexus
            // and so fail. The downside is that failed builds result in tags being pushed.
            tagAndPushRepo(log, multiRepoReactor);
            updatePoms(log, multiRepoReactor, true);
            try {
            	final MultiRepoReleaseInvoker invoker = new MultiRepoReleaseInvoker(getLog(), project);
            	invoker.setGlobalSettings(globalSettings);
                if (userSettings != null) {
                    invoker.setUserSettings(userSettings);
                } else if (getSettings() != null) {
                    File settingsFile = File.createTempFile("tmp", ".xml");
                    settingsFile.deleteOnExit();
                    new DefaultSettingsWriter().write(settingsFile, null, getSettings());
                    invoker.setUserSettings(settingsFile);
                }
            	invoker.setGoals(goals);
            	invoker.setModulesToRelease(modulesToRelease);
            	invoker.setReleaseProfiles(releaseProfiles);
            	invoker.setSkipTests(skipTests);
                invoker.runMavenBuild(multiRepoReactor);
                revertChanges(log, moduleInfos, true);
            } finally {
                revertChanges(log, moduleInfos, false);
            }
        } catch (ValidationException e) {
            printBigErrorMessageAndThrow(log, e.getMessage(), e.getMessages());
        } catch (GitAPIException gae) {

            StringWriter sw = new StringWriter();
            gae.printStackTrace(new PrintWriter(sw));
            String exceptionAsString = sw.toString();

            printBigErrorMessageAndThrow(log, "Could not release due to a Git error",
                asList("There was an error while accessing the Git repository. The error returned from git was:",
                    gae.getMessage(), "Stack trace:", exceptionAsString));
        } catch (IOException e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            String exceptionAsString = sw.toString();

            printBigErrorMessageAndThrow(log, e.getMessage(),
                    asList("There was an error while creating temporary settings file. The error was:", e.getMessage(), "Stack trace:", exceptionAsString));
        }
    }

    private List<ModuleInfo> gatherModulesInfo() throws ValidationException {
        List<ModuleInfo> modulesInfo =  new ArrayList<>();
        final String prefix = "../";
        for(MavenProject moduleProject: projects){
            LocalGitRepo gitRepo = LocalGitRepo.fromCurrentDir(getRemoteUrlOrNullIfNoneSet(moduleProject.getOriginalModel().getScm(), moduleProject.getModel().getScm()), moduleProject.getArtifactId());
            gitRepo.errorIfNotClean();
            ModuleInfo moduleInfo =  new ModuleInfo();
            if(project.hasParent() && !project.getParent().getArtifactId().contains("master-pom")){
                moduleInfo.setRelativePath(project.getParent().getArtifactId() + "/" + moduleProject.getArtifactId());
            }
            moduleInfo.setRelativePath(prefix + moduleProject.getArtifactId());
            moduleInfo.setGitRepo(gitRepo);
            moduleInfo.setMavenProject(moduleProject);
            modulesInfo.add(moduleInfo);
        }

        return modulesInfo;
    }

    private void tagAndPushRepo(Log log, MultiRepoReactor multiRepoReactor) throws GitAPIException {
        for(ModuleInfo moduleInfo: multiRepoReactor.getModuleInfos()){
            if(moduleInfo.getProposedTag() == null) {
                continue;
            }
            log.info("About to tag the repository with " + moduleInfo.getProposedTag().name());
            if (pushTags) {
                moduleInfo.getGitRepo().tagRepoAndPush(moduleInfo.getProposedTag());
            } else {
                moduleInfo.getGitRepo().tagRepo(moduleInfo.getProposedTag());
            }
        }
    }

    static String getRemoteUrlOrNullIfNoneSet(Scm originalScm, Scm actualScm) throws ValidationException {
        if (originalScm == null) {
            // No scm was specified, so don't inherit from any parent poms as they are probably used in different git repos
            return null;
        }

        // There is an SCM specified, so the actual SCM with derived values is used in case (so that variables etc are interpolated)
        String remote = actualScm.getDeveloperConnection();
        if (remote == null) {
            remote = actualScm.getConnection();
        }
        if (remote == null) {
            return null;
        }
        return GitHelper.scmUrlToRemote(remote);
    }

    private static void revertChanges(Log log, List<ModuleInfo> moduleInfos, boolean throwIfError) throws MojoExecutionException {
        for(ModuleInfo moduleInfo: moduleInfos){
            if(moduleInfo.getChangedPom() == null){
                continue;
            }
            if (!moduleInfo.getGitRepo().revertMultiRepoChanges(log, moduleInfo)) {
                String message = "Could not revert changes - working directory is no longer clean. Please revert changes manually";
                if (throwIfError) {
                    throw new MojoExecutionException(message);
                } else {
                    log.warn(message);
                }
            }
        }
    }

    private void updatePoms(Log log, MultiRepoReactor multiRepoReactor, boolean prepareDevPhase) throws MojoExecutionException, ValidationException {
        MultiRepoPomUpdater pomUpdater = new MultiRepoPomUpdater(log, multiRepoReactor, true);
        MultiRepoPomUpdater.MultiRepoUpdateResult result = pomUpdater.updateVersion(prepareDevPhase);

        if (!result.success()) {
            log.info("Going to revert changes because there was an error.");
            List<File> singledummyList = new ArrayList<>();
            for(ModuleInfo moduleInfo: multiRepoReactor.getModuleInfos()){
                singledummyList.add(moduleInfo.getChangedPom());
                moduleInfo.getGitRepo().revertChanges(log, singledummyList);
                singledummyList.clear();
            }
            if (result.unexpectedException != null) {
                throw new ValidationException("Unexpected exception while setting the release versions in the pom", result.unexpectedException);
            } else {
                String summary = "Cannot release with references to snapshot dependencies";
                List<String> messages = new ArrayList<String>();
                messages.add(summary);
                messages.add("The following dependency errors were found:");
                for (String dependencyError : result.dependencyErrors) {
                    messages.add(" * " + dependencyError);
                }
                throw new ValidationException(summary, messages);
            }
        }
    }

    private void figureOutTagNamesAndThrowIfAlreadyExists(List<ReleasableModule> modules, List<String> modulesToRelease, List<ModuleInfo> moduleInfos) throws GitAPIException, ValidationException {
        for (final ReleasableModule module : modules) {
            if (!module.willBeReleased()) {
                continue;
            }
            if (modulesToRelease == null || modulesToRelease.size() == 0 || module.isOneOf(modulesToRelease)) {
                String tag = module.getTagName();
                if (module.getGit().hasLocalTag(tag)) {
                    String summary = "There is already a tag named " + tag + " in this repository.";
                    throw new ValidationException(summary, asList(
                        summary,
                        "It is likely that this version has been released before.",
                        "Please try incrementing the build number and trying again."
                    ));
                }

                AnnotatedTag annotatedTag = AnnotatedTag.create(tag, module.getVersion(), module.getBuildNumber(), false);
                for(ModuleInfo moduleInfo: moduleInfos){
                    if(moduleInfo.getMavenProject().getArtifactId().contentEquals(module.getArtifactId())){
                        moduleInfo.setProposedTag(annotatedTag);
                        break;
                    }
                }
            }
        }

        for(ModuleInfo moduleInfo: moduleInfos){
            if(moduleInfo.getProposedTag() == null){
                continue;
            }
            else{
                checkTagReleaseRules(moduleInfo.getGitRepo().remoteTagsFrom(moduleInfo.getProposedTag()));
            }
        }
    }

    private static void checkTagReleaseRules(List<String> matchingRemoteTags) throws ValidationException {
        if (matchingRemoteTags.size() > 0) {
            String summary = "Cannot release because there is already a tag with the same build number on the remote Git repo.";
            List<String> messages = new ArrayList<String>();
            messages.add(summary);
            for (String matchingRemoteTag : matchingRemoteTags) {
                messages.add(" * There is already a tag named " + matchingRemoteTag + " in the remote repo.");
            }
            messages.add("Please try releasing again with a new build number.");
            throw new ValidationException(summary, messages);
        }
    }

}
