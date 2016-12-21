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

    private final GCloudAsyncRunnerFactory asyncRunnerFactory;
    private GCloudAsyncRunner asyncRunner;

    public DevServerRule(File applicationDirectory, File stagingDirectory, String javaVersion, Supplier<String> cloudSdkDetector, AppEngineSdkResolver appengineSdkResolver) {
        this(GCloudAsyncRunnerFactory.predefined(applicationDirectory, stagingDirectory, javaVersion, cloudSdkDetector, appengineSdkResolver));
    }

    public DevServerRule(GCloudAsyncRunnerFactory asyncRunnerFactory) {
        super();
        this.asyncRunnerFactory = checkNotNull(asyncRunnerFactory, "asyncRunnerFactory");
    }

    public static GCloudAsyncRunnerFactory.Builder factoryBuilder() {
        return GCloudAsyncRunnerFactory.forMavenProject();
    }

    @Override
    protected synchronized void before() throws Throwable {
        checkState(asyncRunner == null, "async runner already created");
        asyncRunner = asyncRunnerFactory.createRunner();
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
