package com.github.danielflower.mavenplugins.release;

/**
 * Created by id848421 on 21/02/2017.
 */
public class VersionNameVersatile extends VersionName {

    private Boolean useBuildNumber = true;

    public VersionNameVersatile(String developmentVersion, String version, long buildNumber) {
        super(developmentVersion, version, buildNumber);
    }

    public VersionNameVersatile(String developmentVersion, String version) {
        super(developmentVersion, version, 0);
        this.useBuildNumber =  false;
    }

    public VersionNameVersatile(String developmentVersion, String version, long buildNumber, Boolean useBuildNumber) {
        super(developmentVersion, version, buildNumber);
        this.useBuildNumber =  useBuildNumber;
    }

    public String releaseVersion() {
     if(useBuildNumber){
         return super.releaseVersion();
     }
     else {
         return super.businessVersion();
     }
    }
}
