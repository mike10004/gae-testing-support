/**
 * Copyright 2014 Google Inc. All Rights Reserved.
 */
package com.github.mike10004.gaetesting;

import com.google.common.base.Supplier;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

public class ExposedGCloudAppStop extends ExposedGCloudAppRun {

    public ExposedGCloudAppStop(String application_directory, String staging_directory, String javaVersion, Supplier<String> cloudSdkResolver, ExplicitSdkResolver appengineSdkResolver) {
        super(application_directory, staging_directory, javaVersion, cloudSdkResolver, appengineSdkResolver);
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        getLog().info("");
        getLog().info("Gcloud SDK - Stopping the Development Server");
        getLog().info("");

        stopDevAppServer();
    }

}
