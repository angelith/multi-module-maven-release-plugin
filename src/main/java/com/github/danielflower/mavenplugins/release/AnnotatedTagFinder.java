package com.github.danielflower.mavenplugins.release;

import org.apache.maven.plugin.MojoExecutionException;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.lib.Ref;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AnnotatedTagFinder {

    public static List<AnnotatedTag> tagsForVersion(Git git, String module, String versionWithoutBuildNumber) throws MojoExecutionException {
        ArrayList<AnnotatedTag> results = new ArrayList<AnnotatedTag>();
        List<Ref> tags;
        try {
            tags = git.tagList().call();
        } catch (GitAPIException e) {
            throw new MojoExecutionException("Error while getting a list of tags in the local repo", e);
        }
        Collections.reverse(tags);
        String tagWithoutBuildNumber = module + "-" + versionWithoutBuildNumber;
        for (Ref tag : tags) {
            if (isPotentiallySameVersionIgnoringBuildNumber(tagWithoutBuildNumber, tag.getName())) {
                try {
                    results.add(AnnotatedTag.fromRef(git.getRepository(), tag, false));
                } catch (IncorrectObjectTypeException ignored) {
                    // not actually a tag, so skip it.
                } catch (IOException e) {
                    throw new MojoExecutionException("Error while looking up tag " + tag, e);
                }
            }
        }
        return results;
    }

    public static List<AnnotatedTag> tagsForVersionNoBuildNumber(Git git, String module, String versionWithoutBuildNumber) throws MojoExecutionException {
        ArrayList<AnnotatedTag> results = new ArrayList<>();
        List<Ref> tags;
        try {
            tags = git.tagList().call();
        } catch (GitAPIException e) {
            throw new MojoExecutionException("Error while getting a list of tags in the local repo", e);
        }

        if(tags == null || tags.size() == 0){
            return  results;
        }

        Collections.reverse(tags);

        final Ref ref = tags.get(0);

        if(AnnotatedTag.stripRefPrefix(ref.getName()).startsWith(module + "-"))
        {
            try {
                results.add(AnnotatedTag.fromRef(git.getRepository(), ref, false));
            } catch (IncorrectObjectTypeException ignored) {
                // not actually a tag, so skip it.
            } catch (IOException e) {
                throw new MojoExecutionException("Error while looking up tag " + ref, e);
            }
        }
        return results;
    }

    public static boolean isPotentiallySameVersionIgnoringBuildNumber(String versionWithoutBuildNumber, String refName) {
        return buildNumberOf(versionWithoutBuildNumber, refName) != null;
    }

    public static Long buildNumberOf(String versionWithoutBuildNumber, String refName) {
        String tagName = AnnotatedTag.stripRefPrefix(refName);
        String prefix = versionWithoutBuildNumber + ".";
        if (tagName.startsWith(prefix)) {
            String end = tagName.substring(prefix.length());
            try {
                return Long.parseLong(end);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

}
