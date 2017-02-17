package com.github.danielflower.mavenplugins.release;

import org.apache.maven.project.MavenProject;

import java.io.File;
import java.util.List;

/**
 * Created by id848421 on 15/02/2017.
 */
public class ModuleInfo {

    private LocalGitRepo gitRepo;

    private File changedPom;

    private AnnotatedTag proposedTag;

    private String relativePath;

    private MavenProject mavenProject;

    private Boolean hasReverted = false;

    public Boolean getHasReverted() {
        return hasReverted;
    }

    public void setHasReverted(Boolean hasReverted) {
        this.hasReverted = hasReverted;
    }

    public MavenProject getMavenProject() {
        return mavenProject;
    }

    public void setMavenProject(MavenProject mavenProject) {
        this.mavenProject = mavenProject;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public void setRelativePath(String relativePath) {
        this.relativePath = relativePath;
    }

    public LocalGitRepo getGitRepo() {
        return gitRepo;
    }

    public void setGitRepo(LocalGitRepo gitRepo) {
        this.gitRepo = gitRepo;
    }

    public File getChangedPom() {
        return changedPom;
    }

    public void setChangedPom(File changedPom) {
        this.changedPom = changedPom;
    }

    public AnnotatedTag getProposedTag() {
        return proposedTag;
    }

    public void setProposedTag(AnnotatedTag proposedTag) {
        this.proposedTag = proposedTag;
    }
}
