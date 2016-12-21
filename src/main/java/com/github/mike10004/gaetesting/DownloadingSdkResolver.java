package com.github.mike10004.gaetesting;

import com.google.common.base.Strings;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.google.common.base.Preconditions.checkNotNull;

class DownloadingSdkResolver extends SystemSdkResolver {

    public static final ImmutableMap<String, String> APP_ENGINE_SDK_ZIP_SHA256SUMS = ImmutableMap.of(
            "1.9.38", "189ec08943f6d09e4a30c6f86382a9d15b61226f042ee4b7c066b2466fd980c4",
            "1.9.44", "70fd66b394348fbb6d6e1863447b3629364e049aca8dd4c1af507051b9411b44");
    private final Supplier<CloseableHttpClient> httpClientSupplier;
    private final DownloadProgressListener downloadProgressListener;

    public DownloadingSdkResolver(String version, Supplier<CloseableHttpClient> httpClientSupplier, DownloadProgressListener downloadProgressListener) {
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
                    Logger.getLogger(AppEngineSdkResolver.class.getName()).log(Level.WARNING, "failed to delete {0}", tempFile);
                }
            }
            downloadProgressListener.finished(destinationFile);
            return destinationFile;
        }

    }

}
