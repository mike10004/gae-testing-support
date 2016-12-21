package com.github.mike10004.gaetesting;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
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
        return new PredefinedFactory(applicationDirectory, Suppliers.ofInstance(stagingDirectory), javaVersion, cloudSdkDetector, appengineSdkResolver);
    }

    public static Builder builder(File applicationDirectory) {
        checkArgument(applicationDirectory.isDirectory(), "not a directory: %s", applicationDirectory);
        return new Builder(applicationDirectory);
    }

    @SuppressWarnings("unused")
    public static class Builder {

        private final File applicationDirectory;
        private Supplier<File> stagingDirectorySupplier;
        private String javaVersion = DEFAULT_JAVA_VERSION;
        private Supplier<String> cloudSdkDetector = GCloudBase.defaultCloudSdkLocationSupplier;
        private AppEngineSdkResolver appengineSdkResolver;
        private List<Configurator> configurators = new ArrayList<Configurator>();

        protected Builder(File applicationDirectory) {
            this.applicationDirectory = checkNotNull(applicationDirectory);
        }

        public GCloudAsyncRunnerFactory build() {
            if (appengineSdkResolver == null) {
                appengineSdkResolver = AppEngineSdkResolver.systemHttpClientResolver(getAppEngineTargetVersion());
            }
            return new PredefinedFactory(applicationDirectory, stagingDirectorySupplier, javaVersion, cloudSdkDetector, appengineSdkResolver) {
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

        private final File applicationDirectory;
        private final Supplier<File> stagingDirectorySupplier;
        private final String javaVersion;
        private final Supplier<String> cloudSdkDetector;
        private final AppEngineSdkResolver appengineSdkResolver;

        public PredefinedFactory(File applicationDirectory, Supplier<File> stagingDirectorySupplier, String javaVersion, Supplier<String> cloudSdkDetector, AppEngineSdkResolver appengineSdkResolver) {
            this.applicationDirectory = checkNotNull(applicationDirectory);
            this.stagingDirectorySupplier = checkNotNull(stagingDirectorySupplier);
            this.javaVersion = checkNotNull(javaVersion);
            this.cloudSdkDetector = checkNotNull(cloudSdkDetector);
            this.appengineSdkResolver = checkNotNull(appengineSdkResolver, "appengineSdkResolver");
        }

        @Override
        public GCloudAsyncRunner createRunner() {
            return new GCloudAsyncRunner(applicationDirectory.getAbsolutePath(), stagingDirectorySupplier.get().getAbsolutePath(), javaVersion, cloudSdkDetector, appengineSdkResolver);
        }
    }
}
