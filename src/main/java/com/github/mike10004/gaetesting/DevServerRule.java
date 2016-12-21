package com.github.mike10004.gaetesting;

import com.google.common.base.Supplier;
import com.google.common.net.HostAndPort;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public class DevServerRule extends ExternalResource {

    private static final String THIS_HOST = "localhost";
    private static final int DEFAULT_PORT = 8080;

    private static final Logger log = LoggerFactory.getLogger(DevServerRule.class);

    private final File applicationDirectory;
    private final File stagingDirectory;
    private final String javaVersion;
    private final Supplier<String> cloudSdkDetector;
    private GCloudAsyncRunner asyncRunner;
    private AppEngineSdkResolver appengineSdkResolver;

    public DevServerRule(File applicationDirectory, File stagingDirectory, String javaVersion, Supplier<String> cloudSdkDetector, AppEngineSdkResolver appengineSdkResolver) {
        this.applicationDirectory = checkNotNull(applicationDirectory);
        this.stagingDirectory = checkNotNull(stagingDirectory);
        this.javaVersion = checkNotNull(javaVersion);
        this.cloudSdkDetector = checkNotNull(cloudSdkDetector);
        this.appengineSdkResolver = checkNotNull(appengineSdkResolver, "appengineSdkResolver");
    }

    @Override
    protected synchronized void before() throws Throwable {
        checkState(asyncRunner == null, "async runner already created");
        asyncRunner = new GCloudAsyncRunner(applicationDirectory.getAbsolutePath(), stagingDirectory.getAbsolutePath(), javaVersion, cloudSdkDetector, appengineSdkResolver);
        configureAsyncRunner(asyncRunner);
        asyncRunner.execute();
    }

    protected void configureAsyncRunner(GCloudAsyncRunner runner) {
        // no op
    }

    @Override
    protected synchronized void after() {
        if (asyncRunner == null) {
            return;
        }
        String adminHostString = asyncRunner.getAdmin_host();
        HostAndPort adminHost = adminHostString == null ? HostAndPort.fromHost("localhost") : HostAndPort.fromString(adminHostString);
        GCloudServerStopper stopper = new GCloudServerStopper(adminHost);
        try {
            stopper.execute();
        } catch (IOException e) {
            log.error("failed to stop gcloud", e);
        }
    }

    public HostAndPort getHost() {
        checkState(asyncRunner != null, "before() has not been invoked");
        String host = asyncRunner.getHost();
        if (host != null) {
            return HostAndPort.fromString(host);
        }
        return HostAndPort.fromParts(THIS_HOST, DEFAULT_PORT);
    }
}
