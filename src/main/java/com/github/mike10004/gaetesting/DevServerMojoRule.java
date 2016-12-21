package com.github.mike10004.gaetesting;

import com.google.common.base.Joiner;
import com.google.common.base.Supplier;
import com.google.common.net.HostAndPort;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.rules.ExternalResource;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public class DevServerMojoRule extends ExternalResource {

    private static final String THIS_HOST = "localhost";
    private static final int DEFAULT_PORT = 8080;
    private static final int DEFAULT_ADMIN_PORT = 8000;

    private static final Logger log = Logger.getLogger(DevServerMojoRule.class.getName());

    private final File applicationDirectory;
    private final File stagingDirectory;
    private final String javaVersion;
    private final Supplier<String> cloudSdkDetector;
    private ExposedGCloudAppAsyncStart startMojo;
    private ExplicitSdkResolver appengineSdkResolver;

    public DevServerMojoRule(File applicationDirectory, File stagingDirectory, String javaVersion, Supplier<String> cloudSdkDetector, ExplicitSdkResolver appengineSdkResolver) {
        this.applicationDirectory = checkNotNull(applicationDirectory);
        this.stagingDirectory = checkNotNull(stagingDirectory);
        this.javaVersion = checkNotNull(javaVersion);
        this.cloudSdkDetector = checkNotNull(cloudSdkDetector);
        this.appengineSdkResolver = checkNotNull(appengineSdkResolver, "appengineSdkResolver");
    }

    @Override
    protected synchronized void before() throws Throwable {
        checkState(startMojo == null, "start mojo already created");
        startMojo = new ExposedGCloudAppAsyncStart(applicationDirectory.getAbsolutePath(), stagingDirectory.getAbsolutePath(), javaVersion, cloudSdkDetector, appengineSdkResolver) {
            @Override
            protected ArrayList<String> getCommand(String appDir) throws MojoExecutionException {
                ArrayList<String> command = super.getCommand(appDir);
                try {
                    Joiner.on(' ').appendTo(System.out, command).println();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                return command;
            }
        };
        configureStartMojo(startMojo);
        startMojo.execute();
    }

    protected void configureMojoCommon(ExposedGCloudAppRun mojo) {

    }

    protected void configureStartMojo(ExposedGCloudAppAsyncStart startMojo) {
        configureMojoCommon(startMojo);
    }

    protected void configureStopMojo(ExposedGCloudAppStop stopMojo) {
        copy(startMojo, stopMojo);
    }

    @Override
    protected synchronized void after() {
        if (startMojo == null) {
            return;
        }
        ExposedGCloudAppStop stopMojo = new ExposedGCloudAppStop(applicationDirectory.getAbsolutePath(), stagingDirectory.getAbsolutePath(), javaVersion, cloudSdkDetector, appengineSdkResolver);
        configureStopMojo(stopMojo);
        try {
            stopMojo.execute();
        } catch (MojoExecutionException | MojoFailureException e) {
            log.log(Level.SEVERE, "failed to stop gcloud", e);
        }
    }

    protected void copy(ExposedGCloudAppAsyncStart src, ExposedGCloudAppStop dst) {

    }

    public HostAndPort getHost() {
        String host = startMojo.getHost();
        if (host != null) {
            return HostAndPort.fromString(host);
        }
        return HostAndPort.fromParts(THIS_HOST, DEFAULT_PORT);
    }
}
