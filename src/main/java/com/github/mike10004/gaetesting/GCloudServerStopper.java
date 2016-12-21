/*
 * Copyright 2014 Google Inc. All Rights Reserved.
 */
package com.github.mike10004.gaetesting;

import com.google.common.io.ByteStreams;
import com.google.common.net.HostAndPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import static com.google.common.base.Preconditions.checkNotNull;

public class GCloudServerStopper {

    private static final Logger log = LoggerFactory.getLogger(GCloudServerStopper.class);

    public static final int DEFAULT_ADMIN_PORT = 8000;

    private final HostAndPort adminHost;

    public GCloudServerStopper(HostAndPort adminHost) {
        this.adminHost = checkNotNull(adminHost);
    }

    public void execute() throws IOException {
        log.info("");
        log.info("Gcloud SDK - Stopping the Development Server");
        log.info("");

        stopDevAppServer();
    }

    protected void stopDevAppServer() throws GCloudExecutionException {
        HttpURLConnection connection;
        try {
            URL url = new URL("http", adminHost.getHost(), adminHost.getPortOrDefault(DEFAULT_ADMIN_PORT), "/quit");
            connection = (HttpURLConnection) url.openConnection();
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setRequestMethod("GET");
            ByteStreams.toByteArray(connection.getInputStream());
            connection.setReadTimeout(4000);
            connection.disconnect();

            log.info("Shutting down Cloud SDK Server on port " + 8000
                    + " and waiting 4 seconds...");
            Thread.sleep(4000);
        } catch (MalformedURLException | InterruptedException e) {
            throw new GCloudExecutionException(e);
        } catch (IOException e) {
            log.info("Was not able to contact the devappserver to shut it down.  Most likely this is due to it simply not running anymore. ", e);
        }
    }

}
