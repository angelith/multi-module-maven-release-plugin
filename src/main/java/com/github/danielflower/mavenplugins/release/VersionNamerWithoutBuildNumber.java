package com.github.danielflower.mavenplugins.release;

import org.eclipse.jgit.lib.Repository;

import java.util.Collection;

import static java.util.Arrays.asList;

public class VersionNamerWithoutBuildNumber {

    public static final String SNAPSHOT = "-SNAPSHOT";

    public VersionName name(String pomVersion) throws ValidationException {

        String nextRelease = nextReleaseVersion(pomVersion);

        VersionNameVersatile versionName = new VersionNameVersatile(nextReleaseVersion(nextRelease) + SNAPSHOT , nextRelease);

        if (!Repository.isValidRefName("refs/tags/" + versionName.releaseVersion())) {
            String summary = "Sorry, '" + versionName.releaseVersion() + "' is not a valid version.";
            throw new ValidationException(summary, asList(
                summary,
                "Version numbers are used in the Git tag, and so can only contain characters that are valid in git tags.",
                "Please see https://www.kernel.org/pub/software/scm/git/docs/git-check-ref-format.html for tag naming rules."
            ));
        }
        return versionName;
    }

    private static String nextReleaseVersion(String currentPomVersion) throws NumberFormatException {
        StringBuilder releaseVersion =  new StringBuilder();
        String[] versionSegments = null;
        if (currentPomVersion.contains(SNAPSHOT)) {
            releaseVersion.append(currentPomVersion.replace(SNAPSHOT, ""));
        } else {
            versionSegments = currentPomVersion.split("\\.");
            Integer nextMinorVersion = Integer.parseInt(versionSegments[versionSegments.length - 1]) + 1;
            if(versionSegments.length == 1){
                return releaseVersion.append(nextMinorVersion).toString();
            }
            for(short i=0;i< versionSegments.length - 1; i++){
                releaseVersion.append(versionSegments[i] + ".");
            }
            releaseVersion.append(nextMinorVersion);
        }
        return releaseVersion.toString();
    }

}
