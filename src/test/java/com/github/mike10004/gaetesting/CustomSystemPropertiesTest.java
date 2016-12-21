package com.github.mike10004.gaetesting;

import com.google.common.base.Strings;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class CustomSystemPropertiesTest {

    @Test
    public void checkDevPort() {
        checkPortWasSet("dev.server.port");
    }

    @Test
    public void checkAdminPort() {
        checkPortWasSet("dev.admin.port");
    }

    private void checkPortWasSet(String key) {
        String devServerPort = System.getProperty(key);
        assertNotNull(key + " null", devServerPort);
        assertTrue(String.format("%s invalid: '%s'", key, devServerPort), devServerPort.matches("\\d{1,5}"));
    }

    @Test
    public void checkCloudSdkDirectoryExists() {
        String key = "gae-testing-support.gcloud.gcloud_directory";
        String cloudSdkDirectory = System.getProperty(key);
        System.out.format("%s=%s%n", key, cloudSdkDirectory);
        assertFalse("cloud sdk directory setting null or empty", Strings.isNullOrEmpty(cloudSdkDirectory));
        File path = new File(cloudSdkDirectory);
        assertTrue("not a directory: " + path, path.isDirectory());
    }
}
