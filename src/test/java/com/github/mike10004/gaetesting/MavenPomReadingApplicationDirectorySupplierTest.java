package com.github.mike10004.gaetesting;

import com.github.mike10004.gaetesting.MavenPomReadingApplicationDirectorySupplier.MavenProjectInfo;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import org.junit.Test;

import java.io.File;
import java.io.InputStream;
import java.util.Properties;

import static org.junit.Assert.*;

public class MavenPomReadingApplicationDirectorySupplierTest {

    @Test
    public void parsePom() throws Exception {
        File cwd = new File(System.getProperty("user.dir"));
        File pomFile = new File(cwd, "pom.xml");
        String text = Files.toString(pomFile, Charsets.UTF_8);
        MavenProjectInfo info = new MavenPomReadingApplicationDirectorySupplier(pomFile, Charsets.UTF_8).parsePom(text);
        System.out.format("parsed: %s%n", info);
        Properties p = new Properties();
        try (InputStream in = getClass().getResourceAsStream("/gae-testing-support/maven.properties")) {
            p.load(in);
        }
        assertEquals("target", new File(p.getProperty("project.build.directory")), info.projectBuildDirectory);
        assertEquals("artifactId", p.getProperty("project.artifactId"), info.artifactId);
        assertEquals("version", p.getProperty("project.version"), info.version);
    }

}