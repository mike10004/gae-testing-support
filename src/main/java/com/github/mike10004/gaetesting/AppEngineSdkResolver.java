package com.github.mike10004.gaetesting;

import com.github.mike10004.gaetesting.SystemSdkResolver.DownloadProgressListener;
import com.google.common.base.Supplier;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.File;
import java.io.IOException;

public abstract class AppEngineSdkResolver {
    /**
     * Returns the App Engine SDK directory.
     * @param cacheDir cache directory that may already contain the SDK
     * @return unpacked SDK directory
     * @throws IOException on I/O failure
     */
    public abstract File resolve(File cacheDir) throws IOException;

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

    public static AppEngineSdkResolver localOnlyResolver(final File directory) {
        return new AppEngineSdkResolver() {
            @Override
            public File resolve(File cacheDir) throws IOException {
                if (!directory.isDirectory()) {
                    throw new IOException("Not a directory: " + directory);
                }
                return directory;
            }
        };
    }
}
