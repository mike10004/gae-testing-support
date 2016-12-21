/*
 * (c) 2016 Novetta
 *
 * Created by mike
 */
package com.github.mike10004.gaetesting;

import com.google.common.base.Strings;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.maven.plugin.MojoExecutionException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Enumeration;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public abstract class ExplicitSdkResolver {

    private static final String SDK_GROUP_ID = "com.google.appengine";
    private static final String SDK_ARTIFACT_ID = "appengine-java-sdk";
    private static final String SDK_EXTENSION = "zip";
    public static final String OPTIMAL_VERSION = "1.9.44";

    protected final String version;

    protected ExplicitSdkResolver(String version) {
        this.version = checkNotNull(version, "version");
    }

    /**
     *
     * @param version
     * @param cacheDir
     * @return unpacked SDK directory
     * @throws MojoExecutionException
     * @throws IOException
     */
    public File resolve(File cacheDir) throws MojoExecutionException, IOException {
        File sdkArchive = new File(cacheDir, formatFilename(version));
        if (!sdkArchive.isFile()) {
            URI artifactUrl = buildArtifactUrl(version, getLocalMavenRepoPath());
            sdkArchive = resolveSdkArchive(artifactUrl, cacheDir);
        }
        File unpackedSdkDir = unpackSdk(sdkArchive);
        return unpackedSdkDir;
    }

    protected static class DownloadingResolver extends ExplicitSdkResolver {

        public static final ImmutableMap<String, String> APP_ENGINE_SDK_ZIP_SHA256SUMS = ImmutableMap.of(
                "1.9.38", "189ec08943f6d09e4a30c6f86382a9d15b61226f042ee4b7c066b2466fd980c4",
                "1.9.44", "70fd66b394348fbb6d6e1863447b3629364e049aca8dd4c1af507051b9411b44");
        private final Supplier<CloseableHttpClient> httpClientSupplier;
        private final DownloadProgressListener downloadProgressListener;

        public DownloadingResolver(String version, Supplier<CloseableHttpClient> httpClientSupplier, DownloadProgressListener downloadProgressListener) {
            super(version);
            this.httpClientSupplier = checkNotNull(httpClientSupplier);
            this.downloadProgressListener = checkNotNull(downloadProgressListener);
        }

        @Override
        protected File downloadRemoteFile(URI uri, File downloadDirectory) throws IOException {
            File sdkArchive;
            try (CloseableHttpClient client = httpClientSupplier.get()) {
                HttpGet request = new HttpGet(uri);
                sdkArchive = new File(downloadDirectory, formatFilename(version));
                ResponseHandler<File> downloader = new DownloadResponseHandler(sdkArchive, getTempDirectory().toPath());
                File downloadResult = client.execute(request, downloader);
                if (!sdkArchive.getCanonicalFile().equals(downloadResult.getCanonicalFile())) {
                    throw new IOException(String.format("files must be same: %s != %s", sdkArchive, downloadResult));
                }
            }
            checkIntegrity(version, sdkArchive);
            return sdkArchive;
        }

        protected void checkIntegrity(String version, File zipFile) throws IOException {
            String knownHash = APP_ENGINE_SDK_ZIP_SHA256SUMS.get(version);
            if (knownHash != null) {
                HashCode hashCode = Files.asByteSource(zipFile).hash(Hashing.sha256());
                if (!knownHash.equals(hashCode.toString().toLowerCase())) {
                    throw new IOException("unexpected sha256sum of downloaded file: " + hashCode);
                }
            }
        }

        @SuppressWarnings("AppEngineForbiddenCode")
        protected class DownloadResponseHandler implements ResponseHandler<File> {

            private final java.nio.file.Path temporaryDirectory;
            private final File destinationFile;

            private DownloadResponseHandler(File destinationFile, java.nio.file.Path temporaryDirectory) {
                this.destinationFile = checkNotNull(destinationFile);
                this.temporaryDirectory = temporaryDirectory;
            }

            @Override
            public File handleResponse(HttpResponse response) throws IOException {
                downloadProgressListener.serverResponded(response);
                if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                    throw new IOException("response status " + response.getStatusLine());
                }
                File tempFile = File.createTempFile("appengine-sdk", ".download", temporaryDirectory.toFile());
                Header contentTypeHeader = response.getFirstHeader(HttpHeaders.CONTENT_TYPE);
                if (contentTypeHeader != null) {
                    String value = contentTypeHeader.getValue();
                    if (!Strings.isNullOrEmpty(value)) {
                        MediaType mediaType = MediaType.parse(value);
                        if ("text".equalsIgnoreCase(mediaType.type())) {
                            throw new IOException("unexpected content-type: " + value);
                        }
                    }
                }
                HttpEntity entity = response.getEntity();
                downloadProgressListener.downloadingToTemporaryFile(entity, tempFile);
                try (InputStream inputStream = entity.getContent()) {
                    Files.asByteSink(tempFile).writeFrom(inputStream);
                }
                downloadProgressListener.copyingToPermanentFile(tempFile, destinationFile);
                try {
                    Files.copy(tempFile, destinationFile);
                } finally {
                    if (!tempFile.delete()) {
                        Logger.getLogger(ExplicitSdkResolver.class.getName()).log(Level.WARNING, "failed to delete {0}", tempFile);
                    }
                }
                downloadProgressListener.finished(destinationFile);
                return destinationFile;
            }

        }

    }

    public interface DownloadProgressListener {
        void serverResponded(HttpResponse response);

        void downloadingToTemporaryFile(HttpEntity entity, File destinationFile);

        void copyingToPermanentFile(File tempFile, File permanentFile);

        void finished(File destinationFile);
    }

    private static final DownloadProgressListener inactiveProgressListener = new DownloadProgressListener() {
        @Override
        public void serverResponded(HttpResponse response) {
        }

        @Override
        public void downloadingToTemporaryFile(HttpEntity entity, File destinationFile) {
        }

        @Override
        public void copyingToPermanentFile(File tempFile, File permanentFile) {
        }

        @Override
        public void finished(File destinationFile) {
        }
    };

    public static ExplicitSdkResolver systemHttpClientResolver(String version) {
        Supplier<CloseableHttpClient> supplier = new Supplier<CloseableHttpClient>() {
            @Override
            public CloseableHttpClient get() {
                return HttpClients.createSystem();
            }
        };
        return new DownloadingResolver(version, supplier, inactiveProgressListener);
    }

    protected File getLocalMavenRepoPath() {
        return new File(System.getProperty("user.home")).toPath().resolve(".m2").resolve("repository").toFile();
    }

    protected static String repoRelativePath(String version) {
        return "com/google/appengine/appengine-java-sdk/" + version;
    }

    public static URI buildArtifactUrl(String version, File localMavenRepo) {
        String repoRelativePath = repoRelativePath(version);
        File localRepoArtifactDir = new File(localMavenRepo, repoRelativePath);
        String artifactFilename = formatFilename(version);
        File localRepoFile = new File(localRepoArtifactDir, artifactFilename);
        if (localRepoFile.isFile()) {
            return localRepoFile.toURI();
        } else {
            return URI.create("https://repo1.maven.org/maven2/" + repoRelativePath + "/" + artifactFilename);
        }
    }

    protected static String formatFilename(String version) {
        return String.format("%s-%s.%s", SDK_ARTIFACT_ID, version, SDK_EXTENSION);
    }

    protected File getTempDirectory() {
        return new File(System.getProperty("java.io.tmpdir"));
    }

    /**
     *
     * @param version
     * @param uri
     * @param downloadDestination
     * @return pathname of SDK archive
     * @throws IOException
     */
    protected abstract File downloadRemoteFile(URI uri, File downloadDestination) throws IOException;

    /**
     *
     * @param version
     * @param uri
     * @param downloadDestination
     * @return pathname of sdk directory (unpacked)
     * @throws MojoExecutionException
     * @throws IOException
     */
    protected File resolveSdkArchive(URI uri, File downloadDestination) throws MojoExecutionException, IOException {
        File sdkArchive;
        if ("file".equals(uri.getScheme())) {
            sdkArchive = new File(uri);
            if (!Objects.equals(sdkArchive.getParentFile().getCanonicalFile(), downloadDestination.getCanonicalFile())) {
                File destinationFile = new File(downloadDestination, sdkArchive.getName());
                Files.copy(sdkArchive, destinationFile);
                sdkArchive = destinationFile;
            }
        } else {
            sdkArchive = downloadRemoteFile(uri, downloadDestination);
        }
        return sdkArchive;
    }

    /**
     * Unpacks an SDK archive zip into the directory containing the zip.
     * @param sdkArchive the zip file
     * @return the directory containing the files unpacked from the archive
     * @throws MojoExecutionException
     */
    protected static File unpackSdk(File sdkArchive) throws MojoExecutionException {
        File sdkRepoDir = sdkArchive.getParentFile();
        File sdkBaseDir = new File(sdkRepoDir, SDK_ARTIFACT_ID);

        if (sdkBaseDir.exists() && !sdkBaseDir.isDirectory()) {
            throw new MojoExecutionException("Could not unpack the SDK because there is an unexpected file at "
                    + sdkBaseDir + " which conflicts with where we plan to unpack the SDK.");
        }

        if (!sdkBaseDir.exists()) {
            sdkBaseDir.mkdirs();
        }

        // While processing the zip archive, if we find an initial entry that is a directory, and all entries are a child
        // of this directory, then we append this to the sdkBaseDir we return.
        String sdkBaseDirSuffix = null;

        try {
            ZipFile sdkZipArchive = new ZipFile(sdkArchive);
            Enumeration<? extends ZipEntry> zipEntries = sdkZipArchive.entries();

            if (!zipEntries.hasMoreElements()) {
                throw new MojoExecutionException("The SDK zip archive appears corrupted.  There are no entries in the zip index.");
            }

            ZipEntry firstEntry = zipEntries.nextElement();
            if (firstEntry.isDirectory()) {
                sdkBaseDirSuffix = firstEntry.getName();
            } else {
                //Reinitialize entries
                zipEntries = sdkZipArchive.entries();
            }

            while (zipEntries.hasMoreElements()) {
                ZipEntry zipEntry = zipEntries.nextElement();

                if (!zipEntry.isDirectory()) {
                    File zipEntryDestination = new File(sdkBaseDir, zipEntry.getName());
                    checkState(sdkBaseDirSuffix != null, "sdkBaseDirSuffix is null; is there no more than one entry in this zip?");
                    if (!zipEntry.getName().startsWith(sdkBaseDirSuffix)) {
                        //We found an entry that doesn't use this initial base directory, oh well, just set it to null.
                        sdkBaseDirSuffix = null;
                    }

                    if (!zipEntryDestination.exists()) {
                        Files.createParentDirs(zipEntryDestination);
                        Files.write(ByteStreams.toByteArray(sdkZipArchive.getInputStream(zipEntry)), zipEntryDestination);
                    }
                }
            }

        } catch (IOException e) {
            throw new MojoExecutionException("Could not open SDK zip archive.", e);
        }

        if (sdkBaseDirSuffix == null) {
            return sdkBaseDir;
        }

        return new File(sdkBaseDir, sdkBaseDirSuffix);
    }
}
