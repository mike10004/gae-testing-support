package com.github.mike10004.gaetesting;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.net.HostAndPort;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public abstract class GCloudAsyncRunnerFactory {

    public abstract GCloudAsyncRunner createRunner() throws IOException;

    public static GCloudAsyncRunnerFactory predefined(File applicationDirectory, File stagingDirectory, String javaVersion, Supplier<String> cloudSdkDetector, AppEngineSdkResolver appengineSdkResolver) {
        return new PredefinedFactory(Suppliers.ofInstance(checkNotNull(applicationDirectory)),
                Suppliers.ofInstance(checkNotNull(stagingDirectory)),
                javaVersion, cloudSdkDetector, appengineSdkResolver);
    }

    protected static class MavenFinalNameGuessingApplicationDirectorySupplier implements Supplier<File> {

        private final File projectBuildDirectory;
        private final String artifactId;
        private final String version;

        public MavenFinalNameGuessingApplicationDirectorySupplier(File projectBuildDirectory, String artifactId, String version) {
            this.projectBuildDirectory = projectBuildDirectory;
            this.artifactId = artifactId;
            this.version = version;
        }

        @Override
        public File get() {
            return new File(projectBuildDirectory, String.format("%s-%s", artifactId, version));
        }
    }

    public static Builder forMavenProject() {
        return forMavenProject(resolvePomFile());
    }

    protected static File resolvePomFile() {
        File cwd = new File(System.getProperty("user.dir"));
        return new File(cwd, "pom.xml");
    }

    public static Builder forMavenProject(String artifactId, String version, File projectBuildDirectory) {
        return builder(new MavenFinalNameGuessingApplicationDirectorySupplier(projectBuildDirectory, artifactId, version));
    }

    public static Builder forMavenProject(File mavenPomFile) {
        checkNotNull(mavenPomFile, "pom");
        checkArgument(mavenPomFile.isFile(), "file not found: %s", mavenPomFile);
        return builder(new MavenPomReadingApplicationDirectorySupplier(mavenPomFile, Charsets.UTF_8));
    }

    public static Builder builder(File applicationDirectory) {
        checkNotNull(applicationDirectory);
        checkArgument(applicationDirectory.isDirectory(), "not a directory: %s", applicationDirectory);
        return builder(Suppliers.ofInstance(applicationDirectory));
    }

    protected static Builder builder(Supplier<File> applicationDirectorySupplier) {
        checkNotNull(applicationDirectorySupplier);
        return new Builder(applicationDirectorySupplier);
    }

    @SuppressWarnings("unused")
    public static class Builder {

        private final Supplier<File> applicationDirectorySupplier;
        private Supplier<File> stagingDirectorySupplier;
        private String javaVersion = DEFAULT_JAVA_VERSION;
        private Supplier<String> cloudSdkDetector = GCloudBase.defaultCloudSdkLocationSupplier;
        private AppEngineSdkResolver appengineSdkResolver;
        private List<Configurator> configurators = new ArrayList<>();

        protected Builder(Supplier<File> applicationDirectorySupplier) {
            this.applicationDirectorySupplier = checkNotNull(applicationDirectorySupplier);
        }

        public <T> T build(Function<Builder, T> transform) {
            return transform.apply(this);
        }

        public GCloudAsyncRunnerFactory factory() {
            return build(factoryTransform);
        }

        public DevServerRule rule() {
            return build(new Function<Builder, DevServerRule>() {
                @Override
                public DevServerRule apply(Builder input) {
                    return new DevServerRule(input.makeFactory());
                }
            });
        }

        private static final Function<Builder, GCloudAsyncRunnerFactory> factoryTransform = new Function<Builder, GCloudAsyncRunnerFactory>() {
            @Override
            public GCloudAsyncRunnerFactory apply(Builder b) {
                return b.makeFactory();
            }
        };

        private GCloudAsyncRunnerFactory makeFactory() {
            if (appengineSdkResolver == null) {
                appengineSdkResolver = AppEngineSdkResolver.systemHttpClientResolver(getAppEngineTargetVersion());
            }
            return new PredefinedFactory(applicationDirectorySupplier, stagingDirectorySupplier, javaVersion, cloudSdkDetector, appengineSdkResolver) {
                @Override
                public GCloudAsyncRunner createRunner() {
                    GCloudAsyncRunner instance = super.createRunner();
                    for (Configurator configurator : configurators) {
                        configurator.configure(instance);
                    }
                    return instance;
                }
            };
        }

        public static final String DEFAULT_JAVA_VERSION = "1.7";
        public static final String DEFAULT_APPENGINE_TARGET_VERSION = "1.9.44";

        protected static String getAppEngineTargetVersion() {
            return System.getProperty("appengine.target.version", DEFAULT_APPENGINE_TARGET_VERSION);
        }

        public Builder stagingIn(Supplier<File> stagingDirectorySupplier) {
            this.stagingDirectorySupplier = checkNotNull(stagingDirectorySupplier);
            return this;
        }

        public Builder javaVersion(String javaVersion) {
            this.javaVersion = checkNotNull(javaVersion);
            return this;
        }

        public Builder configuredBy(Configurator configurator) {
            this.configurators.add(checkNotNull(configurator, "configurator"));
            return this;
        }

        public Builder withCloudSdkIn(final File cloudSdkDirectory) {
            return withCloudSdkDetector(new Supplier<String>() {
                @Override
                public String get() {
                    return cloudSdkDirectory.getAbsolutePath();
                }
            });
        }

        public Builder withCloudSdkDetector(Supplier<String> cloudSdkDetector) {
            this.cloudSdkDetector = cloudSdkDetector;
            return this;
        }

        public Builder withAppengineSdkResolver(AppEngineSdkResolver appengineSdkResolver) {
            this.appengineSdkResolver = checkNotNull(appengineSdkResolver);
            return this;
        }

        public Builder withAppengineSdkIn(File appengineSdkDirectory) {
            this.appengineSdkResolver = AppEngineSdkResolver.localOnlyResolver(appengineSdkDirectory);
            return this;
        }

        public Builder stagingIn(File stagingDirectory) {
            return stagingIn(Suppliers.ofInstance(checkNotNull(stagingDirectory, "stagingDirectory")));
        }

        public Builder stagingInNewFolder(final TemporaryFolder temporaryFolder) {
            return stagingIn(new Supplier<File>() {
                @Override
                public File get() {
                    try {
                        return temporaryFolder.newFolder();
                    } catch (IOException e) {
                        throw new GCloudExecutionException("could not create staging directory", e);
                    }
                }
            });
        }

        public Builder withHost(final HostAndPort host) {
            return configuredBy(new Configurator() {
                @Override
                public void configure(GCloudAsyncRunner instance) {
                    instance.setHost(host.toString());
                }
            });
        }

        public Builder withAdminHost(final HostAndPort adminHost) {
            return configuredBy(new Configurator() {
                @Override
                public void configure(GCloudAsyncRunner instance) {
                    instance.setAdmin_host(adminHost.toString());
                }
            });
        }
    }

    private static final Configurator inactiveConfigurator = new Configurator() {
        @Override
        public void configure(GCloudAsyncRunner instance) {
        }
    };

    public interface Configurator {
        void configure(GCloudAsyncRunner instance);
    }

    protected static class PredefinedFactory extends GCloudAsyncRunnerFactory {

        private final Supplier<File> applicationDirectorySupplier;
        private final Supplier<File> stagingDirectorySupplier;
        private final String javaVersion;
        private final Supplier<String> cloudSdkDetector;
        private final AppEngineSdkResolver appengineSdkResolver;

        public PredefinedFactory(Supplier<File> applicationDirectorySupplier, Supplier<File> stagingDirectorySupplier, String javaVersion, Supplier<String> cloudSdkDetector, AppEngineSdkResolver appengineSdkResolver) {
            this.applicationDirectorySupplier = checkNotNull(applicationDirectorySupplier);
            this.stagingDirectorySupplier = checkNotNull(stagingDirectorySupplier);
            this.javaVersion = checkNotNull(javaVersion);
            this.cloudSdkDetector = checkNotNull(cloudSdkDetector);
            this.appengineSdkResolver = checkNotNull(appengineSdkResolver, "appengineSdkResolver");
        }

        @Override
        public GCloudAsyncRunner createRunner() {
            return new GCloudAsyncRunner(applicationDirectorySupplier.get().getAbsolutePath(), stagingDirectorySupplier.get().getAbsolutePath(), javaVersion, cloudSdkDetector, appengineSdkResolver);
        }
    }
}
