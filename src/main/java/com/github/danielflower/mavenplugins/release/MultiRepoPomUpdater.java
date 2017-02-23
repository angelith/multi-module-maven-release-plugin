package com.github.danielflower.mavenplugins.release;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.WriterFactory;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.File;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class MultiRepoPomUpdater {

    private final Log log;
    private final MultiRepoReactor multiRepoReactor;
    private boolean doCommit = false;

    public MultiRepoPomUpdater(Log log, MultiRepoReactor multiRepoReactor) {
        this.log = log;
        this.multiRepoReactor = multiRepoReactor;
    }

    public MultiRepoPomUpdater(Log log, MultiRepoReactor multiRepoReactor,boolean doCommit) {
        this.log = log;
        this.multiRepoReactor = multiRepoReactor;
        this.doCommit = doCommit;
    }

    public MultiRepoUpdateResult updateVersion(boolean prepareDevPhase) {
        List<File> changedPoms = new ArrayList<File>();
        List<String> errors = new ArrayList<String>();
        for (ReleasableModule module : multiRepoReactor.getModulesInBuildOrder()) {
            try {
                MavenProject project = module.getProject();
                if (module.willBeReleased()) {
                    log.info("Going to release " + module.getArtifactId() + " " + module.getNewVersion());
                }
                else{
                    continue;
                }

                String versionToUse = module.getNewVersion();
                if(prepareDevPhase){
                    versionToUse =  module.getDevelopmentVersion();
                    // Do not commit.
                    this.doCommit = false;
                }
                List<String> errorsForCurrentPom = alterModel(project, versionToUse);
                errors.addAll(errorsForCurrentPom);

                File pom = project.getFile().getCanonicalFile();
                changedPoms.add(pom);
                ModuleInfo moduleInfo = multiRepoReactor.getModuleInfos().stream()
                    .filter(p -> p.getMavenProject().getArtifactId().contentEquals(module.getArtifactId()))
                    .findFirst()
                    .get();
                moduleInfo.setChangedPom(pom);
                Writer fileWriter = WriterFactory.newXmlWriter(pom);

                Model originalModel = project.getOriginalModel();
                try {
                    MavenXpp3Writer pomWriter = new MavenXpp3Writer();
                    pomWriter.write(fileWriter, originalModel);
                } finally {
                    fileWriter.close();
                    if(doCommit){
                        commitChanges(module);
                    }
                }

            } catch (Exception e) {
                return new MultiRepoUpdateResult(changedPoms, errors, e);
            }
        }
        return new MultiRepoUpdateResult(changedPoms, errors, null);
    }

    private void commitChanges(ReleasableModule module) throws GitAPIException {
        // Commit everything changed.
        module.getGit().git.add().addFilepattern(".").call();
                    /* TODO IMPORTANT - take author from properties or in a different way.
                            author should be that of jenkins or the one configured to the
                            machine that will execute the release.
                     */
        module.getGit().git
            .commit()
            .setAuthor("Theodosios Angelidis", "theodossios.angelidis.ext@proximus.com")
            .setMessage("releaser plugin - version change")
            .call();
    }

    public static class MultiRepoUpdateResult {
        public final List<File> alteredPoms;
        public final List<String> dependencyErrors;
        public final Exception unexpectedException;

        public MultiRepoUpdateResult(List<File> alteredPoms, List<String> dependencyErrors, Exception unexpectedException) {
            this.alteredPoms = alteredPoms;
            this.dependencyErrors = dependencyErrors;
            this.unexpectedException = unexpectedException;
        }
        public boolean success() {
            return (dependencyErrors.size() == 0) && (unexpectedException == null);
        }
    }

    private List<String> alterModel(MavenProject project, String newVersion) {
        Model originalModel = project.getOriginalModel();
        originalModel.setVersion(newVersion);

        List<String> errors = new ArrayList<String>();

        String searchingFrom = project.getArtifactId();
        MavenProject parent = project.getParent();
        if (parent != null && isSnapshot(parent.getVersion())) {
            try {
                ReleasableModule parentBeingReleased = multiRepoReactor.find(parent.getGroupId(), parent.getArtifactId(), parent.getVersion());
                originalModel.getParent().setVersion(parentBeingReleased.getVersionToDependOn());
                log.debug(" Parent " + parentBeingReleased.getArtifactId() + " rewritten to version " + parentBeingReleased.getVersionToDependOn());
            } catch (UnresolvedSnapshotDependencyException e) {
                errors.add("The parent of " + searchingFrom + " is " + e.artifactId + " " + e.version);
            }
        }

        Properties projectProperties = project.getProperties();
        for (Dependency dependency : originalModel.getDependencies()) {
            String version = dependency.getVersion();
            if (isSnapshot(resolveVersion(version, projectProperties))) {
                try {
                    ReleasableModule dependencyBeingReleased = multiRepoReactor.find(dependency.getGroupId(), dependency.getArtifactId(), version);
                    dependency.setVersion(dependencyBeingReleased.getVersionToDependOn());
                    log.debug(" Dependency on " + dependencyBeingReleased.getArtifactId() + " rewritten to version " + dependencyBeingReleased.getVersionToDependOn());
                } catch (UnresolvedSnapshotDependencyException e) {
                    errors.add(searchingFrom + " references dependency " + e.artifactId + " " + e.version);
                }
            }else
                log.debug(" Dependency on " + dependency.getArtifactId() + " kept at version " + dependency.getVersion());
        }
        for (Plugin plugin : project.getModel().getBuild().getPlugins()) {
            String version = plugin.getVersion();
            if (isSnapshot(resolveVersion(version, projectProperties))) {
                if (!isMultiModuleReleasePlugin(plugin)) {
                    errors.add(searchingFrom + " references plugin " + plugin.getArtifactId() + " " + version);
                }
            }
        }
        return errors;
    }
    
	private String resolveVersion(String version, Properties projectProperties) {
		if (version != null && version.startsWith("${")) {
			return projectProperties.getProperty(version.replace("${", "").replace("}", ""), version);
		}
		return version;
	}

    private static boolean isMultiModuleReleasePlugin(Plugin plugin) {
        return plugin.getGroupId().equals("com.github.danielflower.mavenplugins") && plugin.getArtifactId().equals("multi-module-maven-release-plugin");
    }

    private boolean isSnapshot(String version) {
        return (version != null && version.endsWith("-SNAPSHOT"));
    }

}
