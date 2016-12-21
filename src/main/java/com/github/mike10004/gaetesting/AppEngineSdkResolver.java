package com.github.mike10004.gaetesting;

import com.google.common.base.Supplier;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Enumeration;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public abstract class AppEngineSdkResolver {

    private static final String SDK_GROUP_ID = "com.google.appengine";
    private static final String SDK_ARTIFACT_ID = "appengine-java-sdk";
    private static final String SDK_EXTENSION = "zip";
    public static final String OPTIMAL_VERSION = "1.9.44";

    protected final String version;

    protected AppEngineSdkResolver(String version) {
        this.version = checkNotNull(version, "version");
    }

    /**
     *
     * @param version
     * @param cacheDir
     * @return unpacked SDK directory
     * @throws IOException
     */
    public File resolve(File cacheDir) throws IOException {
        File sdkArchive = new File(cacheDir, formatFilename(version));
        if (!sdkArchive.isFile()) {
            URI artifactUrl = buildArtifactUrl(version, getLocalMavenRepoPath());
            sdkArchive = resolveSdkArchive(artifactUrl, cacheDir);
        }
        File unpackedSdkDir = unpackSdk(sdkArchive);
        return unpackedSdkDir;
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

    public static AppEngineSdkResolver systemHttpClientResolver(String version) {
        Supplier<CloseableHttpClient> supplier = new Supplier<CloseableHttpClient>() {
            @Override
            public CloseableHttpClient get() {
                return HttpClients.createSystem();
            }
        };
        return new DownloadingSdkResolver(version, supplier, inactiveProgressListener);
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
     * @param uri the remote URL
     * @param downloadDestination directory in which the file at the given URL is to be downloaded
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
     * @throws IOException
     */
    protected File resolveSdkArchive(URI uri, File downloadDestination) throws IOException {
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
     * @throws IOException
     */
    protected static File unpackSdk(File sdkArchive) throws IOException {
        File sdkRepoDir = sdkArchive.getParentFile();
        File sdkBaseDir = new File(sdkRepoDir, SDK_ARTIFACT_ID);

        if (sdkBaseDir.exists() && !sdkBaseDir.isDirectory()) {
            throw new IOException("Could not unpack the SDK because there is an unexpected file at "
                    + sdkBaseDir + " which conflicts with where we plan to unpack the SDK.");
        }

        if (!sdkBaseDir.exists()) {
            sdkBaseDir.mkdirs();
        }

        // While processing the zip archive, if we find an initial entry that is a directory, and all entries are a child
        // of this directory, then we append this to the sdkBaseDir we return.
        String sdkBaseDirSuffix = null;

        ZipFile sdkZipArchive = new ZipFile(sdkArchive);
        Enumeration<? extends ZipEntry> zipEntries = sdkZipArchive.entries();

        if (!zipEntries.hasMoreElements()) {
            throw new IOException("The SDK zip archive appears corrupted.  There are no entries in the zip index.");
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

        if (sdkBaseDirSuffix == null) {
            return sdkBaseDir;
        }

        return new File(sdkBaseDir, sdkBaseDirSuffix);
    }
}
