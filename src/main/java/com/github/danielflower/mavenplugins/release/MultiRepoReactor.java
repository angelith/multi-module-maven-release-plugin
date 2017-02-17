package com.github.danielflower.mavenplugins.release;

import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Created by id848421 on 15/02/2017.
 */
public class MultiRepoReactor {

    private final List<ReleasableModule> modulesInBuildOrder;
    private final List<ModuleInfo> moduleInfos;

    public MultiRepoReactor(List<ReleasableModule> modulesInBuildOrder, List<ModuleInfo> moduleInfos) {
        this.modulesInBuildOrder = modulesInBuildOrder;
        this.moduleInfos = moduleInfos;
    }

    public List<ReleasableModule> getModulesInBuildOrder() {
        return modulesInBuildOrder;
    }

    public List<ModuleInfo> getModuleInfos() { return moduleInfos; }

    public static MultiRepoReactor fromProjects(Log log, MavenProject rootProject, List<ModuleInfo> moduleInfos, Long buildNumber, List<String> modulesToForceRelease, NoChangesAction actionWhenNoChangesDetected) throws ValidationException, GitAPIException, MojoExecutionException {

        List<ReleasableModule> modules = new ArrayList<ReleasableModule>();
        VersionNamer versionNamer = new VersionNamer();
        for (ModuleInfo moduleInfo : moduleInfos) {
            DiffDetector detector = new MultiRepoTreeWalkingDiffDetector(moduleInfo.getGitRepo().git.getRepository());
            String artifactId = moduleInfo.getMavenProject().getArtifactId();
            String versionWithoutBuildNumber = moduleInfo.getMavenProject().getVersion().replace("-SNAPSHOT", "");
            List<AnnotatedTag> previousTagsForThisModule = AnnotatedTagFinder.tagsForVersion(moduleInfo.getGitRepo().git, artifactId, versionWithoutBuildNumber);


            Collection<Long> previousBuildNumbers = new ArrayList<Long>();
            if (previousTagsForThisModule != null) {
                for (AnnotatedTag previousTag : previousTagsForThisModule) {
                    previousBuildNumbers.add(previousTag.buildNumber());
                }
            }

            Collection<Long> remoteBuildNumbers = getRemoteBuildNumbers(moduleInfo.getGitRepo(), artifactId, versionWithoutBuildNumber);
            previousBuildNumbers.addAll(remoteBuildNumbers);

            VersionName newVersion = versionNamer.name(moduleInfo.getMavenProject().getVersion(), buildNumber, previousBuildNumbers);

            boolean oneOfTheDependenciesHasChanged = false;
            String changedDependency = null;
            for (ReleasableModule module : modules) {
                if (module.willBeReleased()) {
                    for (Dependency dependency : moduleInfo.getMavenProject().getModel().getDependencies()) {
                        if (dependency.getGroupId().equals(module.getGroupId()) && dependency.getArtifactId().equals(module.getArtifactId())) {
                            oneOfTheDependenciesHasChanged = true;
                            changedDependency = dependency.getArtifactId();
                            break;
                        }
                    }
                    if (moduleInfo.getMavenProject().getParent() != null
                        && (moduleInfo.getMavenProject().getParent().getGroupId().equals(module.getGroupId()) && moduleInfo.getMavenProject().getParent().getArtifactId().equals(module.getArtifactId()))) {
                        oneOfTheDependenciesHasChanged = true;
                        changedDependency = moduleInfo.getMavenProject().getParent().getArtifactId();
                        break;
                    }
                }
                if (oneOfTheDependenciesHasChanged) {
                    break;
                }
            }

            String equivalentVersion = null;

            if(modulesToForceRelease != null && modulesToForceRelease.contains(artifactId)) {
                log.info("Releasing " + artifactId + " " + newVersion.releaseVersion() + " as we was asked to forced release.");
            }else if (oneOfTheDependenciesHasChanged) {
                log.info("Releasing " + artifactId + " " + newVersion.releaseVersion() + " as " + changedDependency + " has changed.");
            } else {
                String relativePath = moduleInfo.getRelativePath();
                if(moduleInfo.getMavenProject().getArtifactId().contentEquals(rootProject.getArtifactId())){
                    relativePath = ".";
                }
                AnnotatedTag previousTagThatIsTheSameAsHEADForThisModule = hasChangedSinceLastRelease(previousTagsForThisModule, detector, moduleInfo.getMavenProject(), relativePath);
                if (previousTagThatIsTheSameAsHEADForThisModule != null) {
                    equivalentVersion = previousTagThatIsTheSameAsHEADForThisModule.version() + "." + previousTagThatIsTheSameAsHEADForThisModule.buildNumber();
                    log.info("Will use version " + equivalentVersion + " for " + artifactId + " as it has not been changed since that release.");
                } else {
                    log.info("Will use version " + newVersion.releaseVersion() + " for " + artifactId + " as it has changed since the last release.");
                }
            }
            ReleasableModule module = new ReleasableModule(moduleInfo.getMavenProject(), newVersion, equivalentVersion, moduleInfo.getRelativePath(), moduleInfo.getGitRepo());
            modules.add(module);
        }

        if (!atLeastOneBeingReleased(modules)) {
            switch (actionWhenNoChangesDetected) {
                case ReleaseNone:
                    log.warn("No changes have been detected in any modules so will not perform release");
                    return null;
                case FailBuild:
                    throw new MojoExecutionException("No module changes have been detected");
                default:
                    log.warn("No changes have been detected in any modules so will re-release them all");
                    List<ReleasableModule> newList = new ArrayList<ReleasableModule>();
                    for (ReleasableModule module : modules) {
                        newList.add(module.createReleasableVersion());
                    }
                    modules = newList;
            }
        }

        return new MultiRepoReactor(modules, moduleInfos);
    }

    private static Collection<Long> getRemoteBuildNumbers(LocalGitRepo gitRepo, String artifactId, String versionWithoutBuildNumber) throws GitAPIException {
        Collection<Ref> remoteTagRefs = gitRepo.allRemoteTags();
        Collection<Long> remoteBuildNumbers = new ArrayList<Long>();
        String tagWithoutBuildNumber = artifactId + "-" + versionWithoutBuildNumber;
        for (Ref remoteTagRef : remoteTagRefs) {
            String remoteTagName = remoteTagRef.getName();
            Long buildNumber = AnnotatedTagFinder.buildNumberOf(tagWithoutBuildNumber, remoteTagName);
            if (buildNumber != null) {
                remoteBuildNumbers.add(buildNumber);
            }
        }
        return remoteBuildNumbers;
    }

    private static boolean atLeastOneBeingReleased(List<ReleasableModule> modules) {
        for (ReleasableModule module : modules) {
            if (module.willBeReleased()) {
                return true;
            }
        }
        return false;
    }

    static AnnotatedTag hasChangedSinceLastRelease(List<AnnotatedTag> previousTagsForThisModule, DiffDetector detector, MavenProject project, String relativePathToModule) throws MojoExecutionException {
        try {
            if (previousTagsForThisModule.size() == 0) return null;
            boolean hasChanged = detector.hasChangedSince(relativePathToModule, project.getModel().getModules(), previousTagsForThisModule);
            return hasChanged ? null : tagWithHighestBuildNumber(previousTagsForThisModule);
        } catch (Exception e) {
            throw new MojoExecutionException("Error while detecting whether or not " + project.getArtifactId() + " has changed since the last release", e);
        }
    }

    private static AnnotatedTag tagWithHighestBuildNumber(List<AnnotatedTag> tags) {
        AnnotatedTag cur = null;
        for (AnnotatedTag tag : tags) {
            if (cur == null || tag.buildNumber() > cur.buildNumber()) {
                cur = tag;
            }
        }
        return cur;
    }

    public ReleasableModule findByLabel(String label) {
        for (ReleasableModule module : modulesInBuildOrder) {
            String currentLabel = module.getGroupId() + ":" + module.getArtifactId();
            if (currentLabel.equals(label)) {
                return module;
            }
        }
        return null;
    }

    public ReleasableModule find(String groupId, String artifactId, String version) throws UnresolvedSnapshotDependencyException {
        ReleasableModule value = findByLabel(groupId + ":" + artifactId);
        if (value == null) {
            throw new UnresolvedSnapshotDependencyException(groupId, artifactId, version);
        }
        return value;
    }
}
