package com.github.mike10004.gaetesting;

import com.google.common.base.Strings;
import com.google.common.base.Supplier;
import com.google.common.io.Files;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

import static com.google.common.base.Preconditions.checkNotNull;

class MavenPomReadingApplicationDirectorySupplier implements Supplier<File> {

    private final File pomFile;
    private final Charset pomCharset;

    public MavenPomReadingApplicationDirectorySupplier(File pomFile, Charset pomCharset) {
        this.pomFile = checkNotNull(pomFile);
        this.pomCharset = checkNotNull(pomCharset);
    }

    @Override
    public File get() {
        String pomXml;
        try {
            pomXml = Files.toString(pomFile, pomCharset);
        } catch (IOException e) {
            throw new DocumentParsingException(e);
        }
        MavenProjectInfo info = parsePom(pomXml);
        return new File(info.projectBuildDirectory, String.format("%s-%s", info.artifactId, info.version));
    }

    @SuppressWarnings("unused")
    protected static class DocumentParsingException extends GCloudExecutionException {
        public DocumentParsingException() {
        }

        public DocumentParsingException(String message) {
            super(message);
        }

        public DocumentParsingException(String message, Throwable cause) {
            super(message, cause);
        }

        public DocumentParsingException(Throwable cause) {
            super(cause);
        }
    }

    protected MavenProjectInfo parsePom(String pomXml) throws DocumentParsingException {
        File projectBuildDirectory = new File(pomFile.getParentFile(), "target");
        String artifactId, version;
        Document doc = Jsoup.parse(pomXml, "", Parser.xmlParser());
        Element projectElement = doc.getElementsByTag("project").first();
        Element artifactIdElement = projectElement.getElementsByTag("artifactId").first();
        artifactId = artifactIdElement.text();
        if (Strings.isNullOrEmpty(artifactId)) {
            throw new DocumentParsingException("artifactId text is empty/null");
        }
        Elements versionElements = projectElement.getElementsByTag("version");
        if (versionElements.isEmpty()) {
            Elements parentElements = projectElement.getElementsByTag("parent");
            if (parentElements.isEmpty()) {
                throw new DocumentParsingException("no <version> tag and no <parent> tag");
            }
            Element parent = parentElements.first();
            versionElements = parent.getElementsByTag("version");
            if (versionElements.isEmpty()) {
                throw new DocumentParsingException("no <version> tag within <parent> element");
            }
        }
        version = versionElements.first().text();
        return new MavenProjectInfo(projectBuildDirectory, artifactId, version);
    }

    protected static class MavenProjectInfo {
        public final File projectBuildDirectory;
        public final String artifactId, version;

        public MavenProjectInfo(File projectBuildDirectory, String artifactId, String version) {
            this.projectBuildDirectory = projectBuildDirectory;
            this.artifactId = artifactId;
            this.version = version;
        }

        @Override
        public String toString() {
            return "MavenProjectInfo{" +
                    "projectBuildDirectory=" + projectBuildDirectory +
                    ", artifactId='" + artifactId + '\'' +
                    ", version='" + version + '\'' +
                    '}';
        }
    }

}
