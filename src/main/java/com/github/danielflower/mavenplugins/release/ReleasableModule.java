package com.github.danielflower.mavenplugins.release;

import org.apache.maven.project.MavenProject;

import java.util.List;

public class ReleasableModule {

    private final MavenProject project;
    private final VersionName version;
    private final String tagName;
    private final String equivalentVersion;
    private final String relativePathToModule;
    private final LocalGitRepo git;

    public ReleasableModule(MavenProject project, VersionName version, String equivalentVersion, String relativePathToModule, LocalGitRepo git) {
        this.project = project;
        this.version = version;
        this.equivalentVersion = equivalentVersion;
        this.relativePathToModule = relativePathToModule;
        this.tagName = project.getArtifactId() + "-" + version.releaseVersion();
        this.git = git;
    }

    public LocalGitRepo getGit() {
        return git;
    }

    public String getTagName() {
        return tagName;
    }

    public String getNewVersion() {
        return version.releaseVersion();
    }

    public String getArtifactId() {
        return project.getArtifactId();
    }

    public String getGroupId() {
        return project.getGroupId();
    }

    public MavenProject getProject() {
        return project;
    }

    public String getVersion() {
        return version.businessVersion();
    }

    public String getDevelopmentVersion(){
        return version.developmentVersion();
    }

    public long getBuildNumber() {
        return version.buildNumber();
    }

    public boolean isOneOf(List<String> moduleNames) {
        String modulePath = project.getBasedir().getName();
        for (String moduleName : moduleNames) {
            if (modulePath.equals(moduleName)) {
                return true;
            }
        }
        return false;
    }

    public boolean willBeReleased() {
        return equivalentVersion == null;
    }

    public String getVersionToDependOn() {
        return willBeReleased() ? version.releaseVersion() : equivalentVersion;
    }

    public String getRelativePathToModule() {
        return relativePathToModule;
    }

    public ReleasableModule createReleasableVersion() {
        return new ReleasableModule(project, version, null, relativePathToModule, git);
    }
}
