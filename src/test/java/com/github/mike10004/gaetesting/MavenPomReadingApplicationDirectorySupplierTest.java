package com.github.mike10004.gaetesting;

import com.github.mike10004.gaetesting.MavenPomReadingApplicationDirectorySupplier.MavenProjectInfo;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;

public class MavenPomReadingApplicationDirectorySupplierTest {

    @Test
    public void parsePom() throws Exception {
        File cwd = new File(System.getProperty("user.dir"));
        File pomFile = new File(cwd, "pom.xml");
        String text = Files.toString(pomFile, Charsets.UTF_8);
        MavenProjectInfo info = new MavenPomReadingApplicationDirectorySupplier(pomFile, Charsets.UTF_8).parsePom(text);
        System.out.format("parsed: %s%n", info);
        assertEquals("target", new File(cwd, "target"), info.projectBuildDirectory);
        assertEquals("artifactId", "gae-testing-support", info.artifactId);
        assertEquals("version", "0.1-SNAPSHOT", info.version);
    }

}