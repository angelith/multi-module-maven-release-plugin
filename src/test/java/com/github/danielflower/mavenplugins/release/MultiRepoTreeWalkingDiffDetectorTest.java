package com.github.danielflower.mavenplugins.release;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.filter.AndTreeFilter;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.junit.Test;
import org.mockito.cglib.core.Local;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Created by id848421 on 17/02/2017.
 */
public class MultiRepoTreeWalkingDiffDetectorTest {

    private static final String moduleName = "module-b";
    private static final String moduleRemoteUrl = "git@el2604.bc:id848421/module-b.git";
    @Test
    public void hasChangedSince() throws Exception {
        Git git;
        File gitDir = new File("../" + moduleName);
        git = Git.open(gitDir);

        RevWalk walk = new RevWalk(git.getRepository());
        walk.setRetainBody(false);
        walk.markStart(walk.parseCommit(git.getRepository().resolve("HEAD")));

        while(walk.iterator().hasNext()){
            RevCommit revCommit = walk.iterator().next();
            System.out.println(revCommit);
        }

        walk.dispose();

        System.out.println("=================================================");

        walk = new RevWalk(git.getRepository());
        walk.markStart(walk.parseCommit(git.getRepository().resolve("HEAD")));

        /*List<TreeFilter> treeFilters = new ArrayList<TreeFilter>();
        treeFilters.add(TreeFilter.ANY_DIFF);

        treeFilters.add(PathFilter.create("/"));

        TreeFilter treeFilter = treeFilters.size() == 1 ? treeFilters.get(0) : AndTreeFilter.create(treeFilters);
        walk.setTreeFilter(treeFilter);*/

        while(walk.iterator().hasNext()){
            RevCommit revCommit = walk.iterator().next();
            System.out.println(revCommit);
        }

        walk.dispose();

    }

}