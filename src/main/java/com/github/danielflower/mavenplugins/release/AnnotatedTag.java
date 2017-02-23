package com.github.danielflower.mavenplugins.release;

import com.sun.org.apache.xpath.internal.operations.Bool;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import java.io.IOException;

public class AnnotatedTag {
    public static final String VERSION = "version";
    public static final String BUILD_NUMBER = "buildNumber";
    private final String name;
    private final JSONObject message;
    private Ref ref;

    private AnnotatedTag(Ref ref, String name, JSONObject message) {
        Guard.notBlank("tag name", name);
        Guard.notNull("tag message", message);
        this.ref = ref;
        this.name = name;
        this.message = message;
    }

    public static AnnotatedTag create(String name, String version, long buildNumber, Boolean useBuildNumber) {
        JSONObject message = new JSONObject();
        message.put(VERSION, version);
        if(useBuildNumber){
            message.put(BUILD_NUMBER, String.valueOf(buildNumber));
        }
        return new AnnotatedTag(null, name, message);
    }

    public static AnnotatedTag fromRef(Repository repository, Ref gitTag, Boolean useBuildNumber) throws IOException, IncorrectObjectTypeException {
        Guard.notNull("gitTag", gitTag);

        RevWalk walk = new RevWalk(repository);
        JSONObject message;
        try {
            ObjectId tagId = gitTag.getObjectId();
            RevTag tag = walk.parseTag(tagId);
            message = (JSONObject) JSONValue.parse(tag.getFullMessage());
        } finally {
            walk.dispose();
        }
        if (message == null) {
            message = new JSONObject();
            message.put(VERSION, "0");
            if(useBuildNumber){
                message.put(BUILD_NUMBER, "0");
            }
        }
        return new AnnotatedTag(gitTag, stripRefPrefix(gitTag.getName()), message);
    }

    static String stripRefPrefix(String refName) {
        return refName.substring("refs/tags/".length());
    }

    public String name() {
        return name;
    }

    public String version() {
        return String.valueOf(message.get(VERSION));
    }

    public long buildNumber() {
        if(message.get(BUILD_NUMBER) != null){
            return Long.parseLong(String.valueOf(message.get(BUILD_NUMBER)));
        }
        else{
            return 0L;
        }
    }

    public Ref saveAtHEAD(Git git) throws GitAPIException {
        String json = message.toJSONString();
        ref = git.tag().setName(name()).setAnnotated(true).setMessage(json).call();
        return ref;
    }

    @Override
    public String toString() {
        return "AnnotatedTag{" +
            "name='" + name + '\'' +
            ", version=" + version() +
            ", buildNumber=" + buildNumber() +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AnnotatedTag that = (AnnotatedTag) o;
        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    public Ref ref() {
        return ref;
    }
}
