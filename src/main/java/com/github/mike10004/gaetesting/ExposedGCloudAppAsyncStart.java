/*
 * (c) 2016 Novetta
 *
 * Created by mike
 */
package com.github.mike10004.gaetesting;

import com.google.common.base.Supplier;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

import java.io.File;
import java.util.ArrayList;

import static com.google.common.base.Preconditions.checkState;

public class ExposedGCloudAppAsyncStart extends ExposedGCloudAppRun {

    public ExposedGCloudAppAsyncStart(String application_directory, String staging_directory, String javaVersion, Supplier<String> cloudSdkResolver, ExplicitSdkResolver appengineSdkResolver) {
        super(application_directory, staging_directory, javaVersion, cloudSdkResolver, appengineSdkResolver);
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        checkState(application_directory != null, "application_directory");
        getLog().info("");
        getLog().info("Gcloud SDK - Starting the Development Server");
        getLog().info("");

        File appDirFile = new File(application_directory);

        if(!appDirFile.exists()) {
            throw new MojoExecutionException("The application directory does not exist : " + application_directory);
        }

        if(!appDirFile.isDirectory()) {
            throw new MojoExecutionException("The application directory is not a directory : " + application_directory);
        }

        ArrayList<String> devAppServerCommand = getCommand(application_directory);

        startCommand(appDirFile, devAppServerCommand, WaitDirective.WAIT_SERVER_STARTED);
    }

}
