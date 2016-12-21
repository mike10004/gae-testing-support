package com.github.mike10004.gaetesting;

import com.github.mike10004.gaetesting.GCloudAsyncRunnerFactory.Configurator;
import com.google.common.base.Optional;
import com.google.common.base.Supplier;
import com.google.common.io.Files;
import com.google.common.net.HostAndPort;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static com.google.common.base.Preconditions.checkState;
import static org.junit.Assert.*;


public class DevServerRuleTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private Path unpackSampleProject() throws IOException, URISyntaxException {
        File sampleProjectArchive = new File(getClass().getResource("/appengine-helloworld.zip").toURI());
        File destParent = temporaryFolder.newFolder();
        System.out.format("unpacking %s to %s%n", sampleProjectArchive, destParent);
        try (ZipFile zf = new ZipFile(sampleProjectArchive)) {
            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.isDirectory()) {
                    File destFile = new File(destParent, entry.getName());
                    Files.createParentDirs(destFile);
                    try (InputStream in = zf.getInputStream(entry)) {
                        Files.asByteSink(destFile).writeFrom(in);
                        System.out.format("%s%n", destFile);
                    }
                }
            }
        }
        return destParent.toPath().resolve("helloworld");
    }

    @Test
    public void startAndStopServer() throws Throwable {
        final int devServerPort = Integer.parseInt(System.getProperty("dev.server.port"));
        final int adminHostPort = Integer.parseInt(System.getProperty("dev.admin.port"));
        Path unpackedProjectDir = unpackSampleProject();
        Supplier<String> cloudSdkDetector = new Supplier<String>(){
            @Override
            public String get() {
                String path = System.getProperty("gae-testing-support.gcloud.gcloud_directory");
                return Optional.fromNullable(path).or(GCloudBase.defaultCloudSdkLocationSupplier);
            }
        };
        checkState(new File(cloudSdkDetector.get()).isDirectory(), "not a directory: %s", cloudSdkDetector.get());
        AppEngineSdkResolver appengineSdkResolver = AppEngineSdkResolver.systemHttpClientResolver(SystemSdkResolver.OPTIMAL_VERSION);
        GCloudAsyncRunnerFactory factory = GCloudAsyncRunnerFactory.forMavenProject(unpackedProjectDir.resolve("pom.xml").toFile())
                .stagingInNewFolder(temporaryFolder)
                .withAppengineSdkResolver(appengineSdkResolver)
                .withCloudSdkDetector(cloudSdkDetector)
                .withHost(HostAndPort.fromParts("localhost", devServerPort))
                .withAdminHost(HostAndPort.fromParts("localhost", adminHostPort))
                .build();
        DevServerRule rule = new DevServerRule(factory);
        rule.before();
        try {
            URI uri = URI.create("http://" + rule.getHost() + "/");
            StatusLine statusLine;
            String responseData;
            try (CloseableHttpClient client = HttpClients.createSystem()) {
                HttpGet request = new HttpGet(uri);
                System.out.format("request: GET %s%n", uri);
                try (CloseableHttpResponse response = client.execute(request)) {
                    statusLine = response.getStatusLine();
                    System.out.format("response: %s%n", statusLine);
                    responseData = EntityUtils.toString(response.getEntity());
                }
            }
            assertEquals("status", 200, statusLine.getStatusCode());
            assertNotNull("responseData", responseData);
        } finally {
            rule.after();
        }

    }
}