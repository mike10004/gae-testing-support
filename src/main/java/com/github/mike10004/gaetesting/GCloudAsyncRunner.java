/*
 * Copyright 2014 Google Inc. All Rights Reserved.
 */
package com.github.mike10004.gaetesting;

import com.google.common.base.Supplier;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import static com.google.common.base.Preconditions.checkState;

public class GCloudAsyncRunner extends GCloudDevServerBase {

    public GCloudAsyncRunner(String application_directory, String staging_directory, String javaVersion, Supplier<String> cloudSdkResolver, AppEngineSdkResolver appengineSdkResolver) {
        super(application_directory, staging_directory, javaVersion, cloudSdkResolver, appengineSdkResolver);
    }

    @Override
    public void execute() throws GCloudExecutionException, IOException {
        checkState(application_directory != null, "application_directory");
        getLog().info("");
        getLog().info("Gcloud SDK - Starting the Development Server");
        getLog().info("");

        File appDirFile = new File(application_directory);

        if(!appDirFile.exists()) {
            throw new GCloudExecutionException("The application directory does not exist : " + application_directory);
        }

        if(!appDirFile.isDirectory()) {
            throw new GCloudExecutionException("The application directory is not a directory : " + application_directory);
        }

        ArrayList<String> devAppServerCommand = getCommand(application_directory);

        startCommand(appDirFile, devAppServerCommand, WaitDirective.WAIT_SERVER_STARTED);
    }

}
