package com.github.mike10004.gaetesting;

import com.github.mike10004.gaetesting.AppEngineSdkResolver.DownloadProgressListener;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.google.common.base.Supplier;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import com.google.common.net.HostAndPort;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import org.apache.commons.io.FileUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collection;
import java.util.zip.ZipFile;

import static com.google.common.base.Preconditions.checkState;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("AppEngineForbiddenCode")
public class AppEngineSdkResolverTest {

    private static final String SDK_VERSION = AppEngineSdkResolver.OPTIMAL_VERSION;

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void buildUrl_extension() {
        File localMavenRepo = tmp.getRoot();
        URI url = AppEngineSdkResolver.buildArtifactUrl(SDK_VERSION, localMavenRepo);
        System.out.format("url: %s%n", url);
        assertTrue("url ends in .zip", url.toString().endsWith(".zip"));
    }

    private static URI swapSchemeAndHost(URI uri, String newScheme, HostAndPort newHost) {
        URI localhostUrl;
        try {
            localhostUrl = new URIBuilder(uri)
                    .setScheme(newScheme)
                    .setHost(newHost.toString())
                    .build();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        return localhostUrl;
    }

    @Test
    public void resolve_remote() throws Exception {
        System.out.println("resolve_remote");
        String sdkVersion = SDK_VERSION;
        String expectedPath = "/maven2/" + AppEngineSdkResolver.repoRelativePath(sdkVersion)
                + "/" + AppEngineSdkResolver.formatFilename(sdkVersion);
        System.out.format("expected URL path: %s%n", expectedPath);
        URL zip = getFakeZipResource();
        File sourceZipFile = new File(zip.toURI());
        final byte[] fakeZipBytes = Files.toByteArray(sourceZipFile);
        final HashFunction hash = Hashing.md5();
        System.out.format("localhost serving %s %s%n", hash.hashBytes(fakeZipBytes), zip);
        System.out.format("zip has %d entries%n", countEntriesInZip(new File(zip.toURI())));
        File cacheDir = tmp.newFolder();
        File resultFile;
        final WireMockServer server = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        server.stubFor(WireMock.get(WireMock.urlPathEqualTo(expectedPath))
            .willReturn(WireMock.aResponse()
                    .withStatus(200)
                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.ZIP.toString())
                    .withBody(fakeZipBytes)));
        server.start();
        try {
            AppEngineSdkResolver resolver = new TestResolver() {
                @Override
                protected File downloadRemoteFile(URI uri, File downloadDirectory) throws IOException {
                    URI localhostUrl = swapSchemeAndHost(uri, "http", HostAndPort.fromParts("localhost", server.port()));
                    System.out.format("replacing remote URI %s with %s%n", uri, localhostUrl);
                    return super.downloadRemoteFile(localhostUrl, downloadDirectory);
                }

                @Override
                protected void checkIntegrity(String version, File zipFile) throws IOException {
                    assertArrayEquals("downloaded bytes", fakeZipBytes, Files.toByteArray(zipFile));
                    HashCode hashCode = hash.hashBytes(Files.toByteArray(zipFile));
                    System.out.format("checked integrity of %s %s%n", hashCode, zipFile);
                }
            };
            resultFile = resolver.resolve(cacheDir);
        } finally {
            server.stop();
        }
        System.out.format("result: %s%n", resultFile);
        File parent = resultFile.getParentFile();
        @SuppressWarnings("unchecked")
        Collection<File> siblings = FileUtils.listFiles(parent, null, false);
        System.out.format("siblings in %s:%n", parent);
        for (File f : siblings) {
            System.out.println(f);
        }
        assertTrue("is directory: " + resultFile, resultFile.isDirectory());
    }

    private URL getFakeZipResource() throws FileNotFoundException {
        String zipResourcePath = "/fake-appengine-java-sdk-1.9.44.zip";
        URL zipResource = getClass().getResource(zipResourcePath);
        if(zipResource == null) {
            throw new FileNotFoundException("not found: " + zipResourcePath);
        }
        return zipResource;
    }

    @Test
    public void resolve_local() throws Exception {
        final File localMavenRepo = tmp.newFolder();
        String sdkVersion = SDK_VERSION;
        String relativeDir = AppEngineSdkResolver.repoRelativePath(sdkVersion);
        String filename = AppEngineSdkResolver.formatFilename(sdkVersion);
        File file = localMavenRepo.toPath().resolve(relativeDir).resolve(filename).toFile();
        Files.createParentDirs(file);
        URL zipResource = getFakeZipResource();
        Resources.asByteSource(zipResource)
                .copyTo(Files.asByteSink(file));
        System.out.format("created: %s%n", file);
        AppEngineSdkResolver sdkResolver = new AppEngineSdkResolver(sdkVersion) {
            @Override
            protected File downloadRemoteFile(URI uri, File downloadDestination) throws IOException {
                throw new IOException("illegal state");
            }

            @Override
            protected File getLocalMavenRepoPath() {
                return localMavenRepo;
            }
        };
        File sdkRoot = sdkResolver.resolve(tmp.newFolder());
        assertTrue("is directory: " + sdkRoot, sdkRoot.isDirectory());
    }

    private static int countEntriesInZip(File zipSourceFile) throws IOException {
        try (ZipFile zf = new ZipFile(zipSourceFile)) {
            return zf.size();
        }
    }

    @Test
    public void buildUrl_local() throws Exception {
        File localMavenRepo = tmp.newFolder();
        String relativeDir = AppEngineSdkResolver.repoRelativePath(SDK_VERSION);
        String filename = AppEngineSdkResolver.formatFilename(SDK_VERSION);
        File file = localMavenRepo.toPath().resolve(relativeDir).resolve(filename).toFile();
        Files.createParentDirs(file);
        System.out.format("touch: %s%n", file);
        Files.touch(file);
        URI url = AppEngineSdkResolver.buildArtifactUrl(SDK_VERSION, localMavenRepo);
        System.out.format("url: %s%n", url);
        assertEquals("scheme", "file", url.getScheme());
    }

    @Test
    public void getLocalMavenRepoPath() {
        File dir = AppEngineSdkResolver.systemHttpClientResolver(SDK_VERSION).getLocalMavenRepoPath();
        assertTrue("exists: " + dir, dir.isDirectory());
    }

    private class TestResolver extends DownloadingSdkResolver {

        private final File localMavenRepoDir;
        private File tempDir;

        public TestResolver() throws IOException {
            this(tmp.newFolder());
        }

        public TestResolver(File localMavenRepoDir) {
            this(localMavenRepoDir, new ReportingProgressListener());
        }

        public TestResolver(File localMavenRepoDir, DownloadProgressListener downloadProgressListener) {
            super(OPTIMAL_VERSION, new Supplier<CloseableHttpClient>() {
                @Override
                public CloseableHttpClient get() {
                    return HttpClients.createSystem();
                }
            }, downloadProgressListener);
            this.localMavenRepoDir = localMavenRepoDir;
        }

        @Override
        protected File getLocalMavenRepoPath() {
            return localMavenRepoDir;
        }

        @Override
        protected File getTempDirectory() {
            if (tempDir == null) {
                try {
                    tempDir = tmp.newFolder();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            return tempDir;
        }
    }

    private static class ReportingProgressListener implements DownloadProgressListener {

        @Override
        public void serverResponded(HttpResponse response) {
            System.out.println("serverResponded: " + response);
        }

        @Override
        public void downloadingToTemporaryFile(HttpEntity entity, File destinationFile) {
            System.out.format("downloading %s to %s%n", entity, destinationFile);
        }

        @Override
        public void copyingToPermanentFile(File tempFile, File permanentFile) {
            System.out.format("copying %s -> %s%n", tempFile, permanentFile);
        }

        @Override
        public void finished(File destinationFile) {
            System.out.format("finished: %s (%d bytes)%n", destinationFile, destinationFile.length());
        }
    }
}
