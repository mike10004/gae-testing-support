/*
 * Copyright 2014 Google Inc. All Rights Reserved.
 */
package com.github.mike10004.gaetesting;

import com.google.appengine.repackaged.com.google.common.io.Files;
import com.google.appengine.tools.admin.AppCfg;
import com.google.apphosting.utils.config.AppEngineApplicationXml;
import com.google.apphosting.utils.config.AppEngineApplicationXmlReader;
import com.google.apphosting.utils.config.AppEngineWebXml;
import com.google.apphosting.utils.config.AppEngineWebXmlReader;
import com.google.apphosting.utils.config.EarHelper;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableSet;
import org.apache.commons.io.FileUtils;
import org.ini4j.Ini;

import javax.validation.constraints.NotNull;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URL;
import java.net.URLConnection;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;

import static com.google.common.base.Charsets.UTF_8;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@SuppressWarnings({"AppEngineForbiddenCode", "unused"})
public abstract class GCloudBase {

    public static final String DEFAULT_JAVA_VERSION = "1.7";
    private static final ImmutableSet<String> PERMITTED_JAVA_VERSIONS = ImmutableSet.of("1.7", "1.8", "1.9");

    private final Supplier<String> cloudSdkResolver;
    private final AppEngineSdkResolver appengineSdkResolver;

    public static final Supplier<String> defaultCloudSdkLocationSupplier = new Supplier<String>() {
        @Override
        public String get() {
            return Utils.getCloudSDKLocation();
        }
    };

    public GCloudBase(String application_directory, String staging_directory, String javaVersion, Supplier<String> cloudSdkResolver, AppEngineSdkResolver appengineSdkResolver) {
        this.application_directory = checkNotNull(application_directory, "application_directory");
        this.staging_directory = checkNotNull(staging_directory, "staging_directory");
        this.javaVersion = checkNotNull(javaVersion, "javaVersion");
        this.cloudSdkResolver = checkNotNull(cloudSdkResolver, "cloudSdkResolver");
        this.appengineSdkResolver = checkNotNull(appengineSdkResolver);
        checkArgument(PERMITTED_JAVA_VERSIONS.contains(javaVersion), "invalid java version %s; must be one of %s", javaVersion, PERMITTED_JAVA_VERSIONS);
    }

    private final @NotNull String javaVersion;

    /**
     * gcloud installation gcloud_directory
     *
     * <code>@parameter expression="${gcloud.gcloud_directory}"</code>
     */
    protected String gcloud_directory;

    /**
     * docker_host
     *
     * <code>@parameter expression="${gcloud.docker_host}" default-value="ENV_or_default"</code>
     */
    protected String docker_host = "ENV_or_default";
    /**
     * docker_tls_verify
     *
     * <code>@parameter expression="${gcloud.docker_tls_verify}" default-value="ENV_or_default"</code>
     */
    protected String docker_tls_verify = "ENV_or_default";

    /**
     * docker_host_cert_path
     *
     * <code>@parameter expression="${gcloud.docker_cert_path}" default-value="ENV_or_default"</code>
     */
    protected String docker_cert_path = "ENV_or_default";

    /**
     * Override the default verbosity for this command. This must be a standard
     * logging verbosity level: [debug, info, warning, error, critical, none]
     * (Default: [info]).
     *
     * <code>@parameter expression="${gcloud.verbosity}"</code>
     */
    protected String verbosity = "info";

    /**
     * Google Cloud Platform gcloud_project to use for this invocation.
     *
     * <code>@parameter expression="${gcloud.gcloud_project}"</code>
     */
    protected String gcloud_project;

    /**
     * version The version of the app that will be created or replaced by this
     * deployment.
     *
     * <code>@parameter expression="${gcloud.version}"</code>
     */
    protected String version;

    /**
     * Quiet mode, if true does not ask to perform the action.
     *
     * <code>@parameter expression="${gcloud.quiet}" default-value="true"</code>
     */
    protected boolean quiet = true;

    /**
     * The location of the appengine application to run.
     *
     * <code>@parameter expression="${gcloud.application_directory}"</code>
     */
    protected final @NotNull String application_directory;


    /**
     * Use this option if you are deploying using a remote docker host.
     *
     * <code>@parameter expression="${gcloud.remote}"</code>
     */
    protected boolean remote;

    /**
     * Perform a hosted (´remote´) or local Docker build.
     * To perform a local build, you must have your local docker environment
     * configured correctly.
     *
     * <code>@parameter expression="${gcloud.docker_build}"</code>
     */
    protected String docker_build;

    /**
     * The directory for the Staging phase. It has to be under target/ and is deleted
     * at each run or deploy command.
     *
     * <code>@parameter expression="${gcloud.staging_directory}" default-value="${project.build.directory}/appengine-staging"</code>
     */
    protected final @NotNull String staging_directory;

    /**
     * specify the default runtime you would like to use.
     Valid runtimes are ['java', 'php55',
     'python', 'custom', 'python-compat', 'java7',
     'python27', 'go']. (default: )
     *
     * <code>@parameter expression="${gcloud.runtime}"</code>
     */

    protected String runtime;

    /**
     * server The App Engine server to connect to.
     *
     * <code>@parameter expression="${gcloud.server}"</code>
     */
    protected String server;

    /**
     * force Force deploying, overriding any previous in-progress deployments to
     * this version.
     *
     * <code>@parameter expression="${gcloud.force}"</code>
     */
    protected boolean force;

    /**
     * Set the encoding to be used when compiling Java source files (default
     * "UTF-8")
     *
     * <code>@parameter expression="${gcloud.compile_encoding}"</code>
     */
    protected String compile_encoding;
    /**
     * Delete the JSP source files after compilation
     *
     * <code>@parameter expression="${gcloud.delete_jsps}"</code>
     */
    protected boolean delete_jsps;
    /**
     * Do not jar the classes generated from JSPs
     *
     * <code>@parameter expression="${gcloud.disable_jar_jsps}"</code>
     */
    protected boolean disable_jar_jsps;
    /**
     * Jar the WEB-INF/classes content
     *
     * <code>@parameter expression="${gcloud.enable_jar_classes}"</code>
     */
    protected boolean enable_jar_classes;
    /**
     * Split large jar files (&gt; 32M) into smaller fragments
     *
     * <code>@parameter expression="${gcloud.enable_jar_splitting}"</code>
     */
    protected boolean enable_jar_splitting;
    /**
     * Do not use symbolic links when making the temporary (staging)
     * gcloud_directory used in uploading Java apps
     *
     * <code>@parameter expression="${gcloud.no_symlinks}"</code>
     */
    protected boolean no_symlinks;
    /**
     * Do not delete temporary (staging) gcloud_directory used in uploading Java
     * apps
     *
     * <code>@parameter expression="${gcloud.retain_upload_dir}"</code>
     */
    protected boolean retain_upload_dir;
    /**
     * When --enable-jar-splitting is specified and --jar-splitting-excludes
     * specifies a comma-separated list of suffixes, a file in a jar whose name
     * ends with one of the suffixes will not be included in the split jar
     * fragments
     *
     * <code>@parameter expression="${gcloud.jar_splitting_excludes}"</code>
     */
    protected String jar_splitting_excludes;

    /**
     * Tell if the command will be for run or deploy. Default is false: command is
     * for `gcloud run`.
     *
     */
    protected boolean deployCommand = false;

    /**
     * Directory containing the App Engine app.yaml/Dockerfile files.
     *
     * <code>@parameter expression="${gcloud.appengine_config_directory}"</code>
     * default-value="${project.basedir}/src/main/appengine"
     */
    protected String appengine_config_directory;

    protected abstract ArrayList<String> getCommand(String appDir) throws GCloudExecutionException, IOException;

    protected ArrayList<String> setupInitialCommands(ArrayList<String> commands) throws GCloudExecutionException, IOException {
        String pythonLocation = Utils.getPythonExecutableLocation();

        commands.add(pythonLocation);
        if (Utils.canDisableImportOfPythonModuleSite()) {
            commands.add("-S");
        }

        if (gcloud_directory == null) {
            gcloud_directory = cloudSdkResolver.get();
        }
        File s = new File(gcloud_directory);
        File script = new File(s, "/lib/googlecloudsdk/gcloud/gcloud.py");

        if (!script.exists()) {
            script = new File(s, "/lib/gcloud.py");
        }
        if (!script.exists()) {
            getLog().error("Cannot determine the default location of the Google Cloud SDK.");
            getLog().error("If you need to install the Google Cloud SDK, follow the instructions located at https://cloud.google.com/appengine/docs/java/managed-vms");
            getLog().error("You can then set it via <gcloud_directory> </gcloud_directory> in the pom.xml");
            throw new GCloudExecutionException("Unknown Google Cloud SDK location: " + gcloud_directory);
        }

        if (deployCommand) {
            commands.add(script.getAbsolutePath());
            if (quiet) {
                commands.add("--quiet");
            }
            if (verbosity != null) {
                commands.add("--verbosity=" + verbosity);
            }
            if (gcloud_project != null) {
                commands.add("--project=" + gcloud_project);
            }
            commands.add("preview");
            commands.add("app");

        } else { // run command
            File devServer = new File(gcloud_directory + "/platform/google_appengine/dev_appserver.py");
            // Check if we need to install the app-engine-java component!
            if (!devServer.exists()) {
                installJavaAppEngineComponent(pythonLocation);
            }

            commands.add(gcloud_directory + "/platform/google_appengine/dev_appserver.py");
            commands.add("--skip_sdk_update_check=true");
            if (verbosity != null) {
                commands.add("--dev_appserver_log_level=" + verbosity);
            }
            if (gcloud_project != null) {
                commands.add("-A");
                commands.add(gcloud_project);
            } else {
                commands.add("-A");
                commands.add("app"); // local sdk default project name
            }
        }

        return commands;
    }

    protected static enum WaitDirective {

        WAIT_SERVER_STARTED,
        WAIT_SERVER_STOPPED
    }

    protected void startCommand(File appDirFile, ArrayList<String> devAppServerCommand, WaitDirective waitDirective) throws GCloudExecutionException, IOException {
        getLog().info("Running " + Joiner.on(" ").join(devAppServerCommand));

        Thread stdOutThread;
        Thread stdErrThread;
        try {

            ProcessBuilder processBuilder = new ProcessBuilder(devAppServerCommand);

            processBuilder.directory(appDirFile);

            processBuilder.redirectErrorStream(true);
            Map<String, String> env = processBuilder.environment();
            String env_docker_host = env.get("DOCKER_HOST");
            String docker_host_tls_verify = env.get("DOCKER_TLS_VERIFY");
            String docker_host_cert_path = env.get("DOCKER_CERT_PATH");
            boolean userDefined = (env_docker_host != null)
                    || (docker_host_tls_verify != null)
                    || (docker_host_cert_path != null);

            if (!userDefined) {
                if ("ENV_or_default".equals(docker_host)) {
                    if (env_docker_host == null) {
                        if (env.get("DEVSHELL_CLIENT_PORT") != null) {
                            // we know we have a good chance to be in an old Google devshell:
                            env_docker_host = "unix:///var/run/docker.sock";
                        } else {
                            // we assume docker machine environment (Windows, Mac, and some Linux)
                            env_docker_host = "tcp://192.168.99.100:2376";
                        }
                    }
                } else {
                    env_docker_host = docker_host;
                }
                env.put("DOCKER_HOST", env_docker_host);
                // we handle TLS extra variables only when we are tcp:
                if (env_docker_host.startsWith("tcp")) {
                    if ("ENV_or_default".equals(docker_tls_verify)) {
                        if (env.get("DOCKER_TLS_VERIFY") == null) {
                            env.put("DOCKER_TLS_VERIFY", "1");
                        }
                    } else {
                        env.put("DOCKER_TLS_VERIFY", docker_tls_verify);
                    }
                    // do not set the cert path if we do a dockerless deploy command:
                    boolean dockerless = deployCommand && remote;
                    if (!dockerless) {
                        if ("ENV_or_default".equals(docker_cert_path)) {
                            if (env.get("DOCKER_CERT_PATH") == null) {
                                env.put("DOCKER_CERT_PATH",
                                        System.getProperty("user.home")
                                                + File.separator
                                                + ".docker"
                                                + File.separator
                                                + "machine"
                                                + File.separator
                                                + "machines"
                                                + File.separator
                                                + "default"
                                );
                            }
                        } else {
                            env.put("DOCKER_CERT_PATH", docker_cert_path);
                        }
                    }
                }
            }
            //export DOCKER_CERT_PATH=/Users/ludo/.boot2docker/certs/boot2docker-vm
            //export DOCKER_TLS_VERIFY=1
            //export DOCKER_HOST=tcp://192.168.59.103:2376

            // for the docker library path:
            env.put("PYTHONPATH", gcloud_directory + "/platform/google_appengine/lib/docker");

            final Process devServerProcess = processBuilder.start();

            final CountDownLatch waitStartedLatch = new CountDownLatch(1);

            final Scanner stdOut = new Scanner(devServerProcess.getInputStream());
            stdOutThread = new Thread("standard-out-redirection-devappserver") {
                @Override
                public void run() {
                    boolean serverStartedOK = false;
                    try {
                        long healthCount = 0;
                        while (stdOut.hasNextLine() && !Thread.interrupted()) {
                            String line = stdOut.nextLine();
                            // emit this every 30 times, no need for more...
                            if (line.contains("GET /_ah/health?IsLastSuccessful=yes HTTP/1.1\" 200 2")) {
                                waitStartedLatch.countDown();
                                if (healthCount % 20 == 0) {
                                    getLog().info(line);
                                }
                                healthCount++;
                            } else if (line.contains("Dev App Server is now running")) {
                                // App Engine V1
                                waitStartedLatch.countDown();
                                serverStartedOK = true;

                            } else if (line.contains("INFO:oejs.Server:main: Started")) {
                                // App Engine V2
                                waitStartedLatch.countDown();
                                serverStartedOK = true;

                            } else {
                                getLog().info(line);
                            }
                        }
                    } finally {
                        waitStartedLatch.countDown();
                        if ((!serverStartedOK) && (!deployCommand)) {
                            throw new RuntimeException("The Java Dev Server has stopped.");

                        }
                    }
                }
            };
            stdOutThread.setDaemon(true);
            stdOutThread.start();

            final Scanner stdErr = new Scanner(devServerProcess.getErrorStream());
            stdErrThread = new Thread("standard-err-redirection-devappserver") {
                @Override
                public void run() {
                    while (stdErr.hasNextLine() && !Thread.interrupted()) {
                        getLog().error(stdErr.nextLine());
                    }
                }
            };
            stdErrThread.setDaemon(true);
            stdErrThread.start();
            if (waitDirective == WaitDirective.WAIT_SERVER_STOPPED) {
                Runtime.getRuntime().addShutdownHook(new Thread("destroy-devappserver") {
                    @Override
                    public void run() {
                        if (devServerProcess != null) {
                            devServerProcess.destroy();
                        }
                    }
                });

                devServerProcess.waitFor();
                int status = devServerProcess.exitValue();
                if (status != 0) {
                    getLog().error("Error: gcloud app command with exit code : " + status);
                    throw new GCloudExecutionException("Error: gcloud app command exit code is: " + status);
                }
            } else if (waitDirective == WaitDirective.WAIT_SERVER_STARTED) {
                waitStartedLatch.await();
                getLog().info("");
                getLog().info("App Engine Dev Server started in Async mode and running.");
                getLog().info("you can stop it with this command: mvn gcloud:run_stop");
            }
        } catch (InterruptedException e) {
            throw new GCloudExecutionException(e);
        }
    }

    protected String getApplicationDirectory() {
        return application_directory;
    }

    protected String getProjectIdfromMetaData() {
        try {
            URL url = new URL("http://metadata/computeMetadata/v1/project/project-id");
            URLConnection connection = url.openConnection();
            connection.setRequestProperty("X-Google-Metadata-Request", "True");
            try (BufferedReader reader
                         = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(), UTF_8))) {
                return reader.readLine();
            }
        } catch (IOException ignore) {
            // return null if can't determine
            return null;
        }
    }

    protected String getAppId() throws FileNotFoundException {

        if (gcloud_project != null) {
            return gcloud_project;
        }

        try { // Check for Cloud SDK properties:
            String userHome;
            if (System.getProperty("os.name").contains("Windows")) {
                userHome = System.getenv("APPDATA");
            } else {
                userHome = System.getProperty("user.home") + "/.config";
            }
            //Default value:
            File cloudSDKProperties = new File(userHome + "/gcloud/properties");
            // But can be overriden: take this one if it is:
            String env = System.getenv("CLOUDSDK_CONFIG");
            if (env != null) {
                cloudSDKProperties = new File(env, "properties");
            }
            if (cloudSDKProperties.exists()) {
                org.ini4j.Ini ini = new org.ini4j.Ini();
                ini.load(new FileReader(cloudSDKProperties));
                Ini.Section section = ini.get("core");
                String project = section.get("project");
                if (project != null) {
                    getLog().info("Getting project name: " + project
                            + " from gcloud settings.");
                    return project;
                }
            }
            // now try the metadata server location:
            String project = getProjectIdfromMetaData();
            if (project != null) {
                getLog().info("Getting project name: " + project
                        + " from the metadata server.");
                return project;
            }
        } catch (IOException ioe) {
            // nothing for now. Trying to read appengine-web.xml.
        }

        String appDir = getApplicationDirectory();
        if (EarHelper.isEar(appDir)) { // EAR project
            AppEngineApplicationXmlReader reader
                    = new AppEngineApplicationXmlReader();
            AppEngineApplicationXml appEngineApplicationXml = reader.processXml(
                    new FileInputStream(new File(appDir, "META-INF/appengine-application.xml")));
            return appEngineApplicationXml.getApplicationId();

        }
        if (new File(appDir, "WEB-INF/appengine-web.xml").exists()) {
            return getAppEngineWebXml(appDir).getAppId();
        } else {
            return null;
        }
    }

    protected AppEngineWebXml getAppEngineWebXml(String webAppDir) throws GCloudExecutionException {
        AppEngineWebXmlReader reader = new AppEngineWebXmlReader(webAppDir);
        AppEngineWebXml appengineWebXml = reader.readAppEngineWebXml();
        return appengineWebXml;
    }

    private static final String _SDK_VERSION = "1.9.38"; // 1.9.44

    private File downloadCacheDirectory;

    protected File getDownloadCacheDirectory() {
        if (downloadCacheDirectory != null) {
            return downloadCacheDirectory;
        }
        String syspropSpec = System.getProperty("dev.server.download.cacheDirectory");
        if (syspropSpec != null) {
            return new File(syspropSpec);
        }
        return FileUtils.getTempDirectory();
    }

    protected void resolveAndSetSdkRoot() throws IOException {
        File sdkBaseDir = appengineSdkResolver.resolve(getDownloadCacheDirectory());
        System.setProperty("appengine.sdk.root", sdkBaseDir.getCanonicalPath());
    }

    /**
     *
     * @return the java version used the pom (target) and 1.7 if not present.
     */
    protected String getJavaVersion() {
        return javaVersion;
    }

    protected void checkStagingDirectoryLocation(File destinationDir) throws IOException {

    }

    protected File executeAppCfgStagingCommand(String appDir)
            throws IOException {

        ArrayList<String> arguments = new ArrayList<>();
        File destinationDir = new File(staging_directory);
        checkStagingDirectoryLocation(destinationDir);
        if (destinationDir.exists()) {
            FileUtils.deleteDirectory(destinationDir);
        }

        getLog().info("Creating staging directory in: " + destinationDir.getAbsolutePath());
        resolveAndSetSdkRoot();
        // System.setProperty("appengine.sdk.root", gcloud_directory +"/platform/google_appengine/google/appengine/tools/java");
        AppEngineWebXml appengineWeb = getAppEngineWebXml(appDir);
        if ("true".equals(appengineWeb.getBetaSettings().get("java_quickstart"))) {
            arguments.add("--enable_quickstart");
        }
        arguments.add("--disable_update_check");
        File  appDirFile= new File(appDir);

        if (!new File(appDirFile, ".appyamlgenerated").exists()) {
            PrintWriter out;
            out = new PrintWriter(new File(appDirFile, ".appyamlgenerated"));
            DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
            Date date = new Date();
            System.out.println(dateFormat.format(date));
            out.println("generated by the Maven Plugin on " + dateFormat.format(date));
            out.close();
        }

        arguments.add("-A");
        arguments.add("notused");

        if (version != null && !version.isEmpty()) {
            arguments.add("-V");
            arguments.add(version);
        }

        if (server != null && !server.isEmpty()) {
            arguments.add("-s");
            arguments.add(server);
        }

        if (gcloud_project != null) {
            arguments.add("-A");
            arguments.add(gcloud_project);
        }

        if (enable_jar_splitting) {
            arguments.add("--enable_jar_splitting");
        }

        if (jar_splitting_excludes != null && !jar_splitting_excludes.isEmpty()) {
            arguments.add("--jar_splitting_excludes=" + jar_splitting_excludes);
        }

        if (retain_upload_dir) {
            arguments.add("--retain_upload_dir");
        }

        if (compile_encoding != null) {
            arguments.add("--compile-encoding=" + compile_encoding);
        }

        if (force) {
            arguments.add("-f");
        }

        if (delete_jsps) {
            arguments.add("--delete_jsps");
        }

        if (enable_jar_classes) {
            arguments.add("--enable_jar_classes");
        }

        if (runtime != null) {
            arguments.add("--runtime=" + runtime);
        }
    /* Complicated matrix...
    vm:true java7 in pom ok   ->runtime:java7
    vm:false java8 in pom  ok   ->error for now (unless override in runtime flag)
    vm:true java8 in pm , generate a dockerfile and runtime:custom
    env:2 java 7in pom ->ok, runtime is java
    env:2 java8 in pom ->ok runtime is java
    */
        boolean isVm = appengineWeb.getUseVm();
        boolean isStandard = ("1".equals(appengineWeb.getEnv())
                || "std".equals(appengineWeb.getEnv())) && isVm==false;
        boolean isFlex = "2".equals(appengineWeb.getEnv())
                || "flex".equals(appengineWeb.getEnv())
                || "flexible".equals(appengineWeb.getEnv());

        //config error: vm false
        if (isStandard && getJavaVersion().equals("1.8")) {
            throw new GCloudExecutionException("For now, Standard GAE runtime only works with Java7, but the pom.xml is targetting 1.8");
        }

        // Forcing Java runtime for Flex or 1.8 , otherwise it is default Java7
        // FYI: the 'java' runtime for Managed VMs is the real java8
        if (isFlex || getJavaVersion().equals("1.8"))  {
            arguments.add("-R");
            arguments.add("-r");
            arguments.add("java");
        }
        arguments.add("stage");
        arguments.add(appDir);
        arguments.add(destinationDir.getAbsolutePath());
        getLog().info("Running appcfg " + Joiner.on(" ").join(arguments));
        AppCfg.main(arguments.toArray(new String[arguments.size()]));
        // For now, treat custom as java7 so that the app run command works.
        try {
            File fileAppYaml = new File(destinationDir, "/app.yaml");
            String content = Files.toString(fileAppYaml, Charsets.UTF_8);
            if (isVm && getJavaVersion().equals("1.8")) {
                content = content.replace("runtime: java", "runtime: custom");
                Files.write(content, fileAppYaml, Charsets.UTF_8);
                File dockerFile = new File(destinationDir, "/Dockerfile");
                if (!dockerFile.exists()) {
                    Files.write("FROM gcr.io/google_appengine/jetty9-compat\nADD . /app\n", dockerFile, Charsets.UTF_8);
                }
            }
        } catch (IOException ioe) {
            System.out.println("Error " + ioe);
        }

        File[] yamlFiles = new File(destinationDir, "/WEB-INF/appengine-generated").listFiles();
        for (File f : yamlFiles) {
            Files.copy(f, new File(appDir, f.getName()));
        }
        File qs = new File(destinationDir, "/WEB-INF/quickstart-web.xml");
        if (qs.exists()) {
            Files.copy(qs, new File(appDir, "/WEB-INF/quickstart-web.xml"));
        }
        // Delete the xml as we have now the index.yaml equivalent
        File index = new File(appDir, "/WEB-INF/datastore-indexes.xml");
        if (index.exists()) {
            index.delete();
        }
        return destinationDir;
    }

    /**
     * Executes the gcloud components update app-engine-java command to install
     * the extra component needed for the Maven plugin.
     * @param pythonLocation
     * @throws IOException
     * @throws GCloudExecutionException
     */
    private void installJavaAppEngineComponent(String pythonLocation ) throws GCloudExecutionException, IOException {
        ArrayList<String> installCommand = new ArrayList<>();
        installCommand.add(pythonLocation);
        if (Utils.canDisableImportOfPythonModuleSite()) {
            installCommand.add("-S");
        }
        installCommand.add(gcloud_directory + "/lib/gcloud.py");
        installCommand.add("components");
        installCommand.add("update");
        installCommand.add("app-engine-java");
        installCommand.add("--quiet");
        ProcessBuilder pb = new ProcessBuilder(installCommand);
        getLog().info("Installing the Cloud SDK app-engine-java component");
        getLog().info("Please, be patient, it takes a while on slow network...");

        try {
            Process process = pb.start();
            final Scanner stdOut = new Scanner(process.getInputStream());
            new Thread("standard-out-redirection") {
                @Override
                public void run() {
                    while (stdOut.hasNextLine() && !Thread.interrupted()) {
                        getLog().info(stdOut.nextLine());
                    }
                }
            };
            process.waitFor();
            getLog().info("Cloud SDK app-engine-java component installed.");

        } catch (InterruptedException ex) {
            throw new GCloudExecutionException("Error: cannot execute gcloud command " + ex);
        }
    }

    private org.slf4j.Logger log;

    protected org.slf4j.Logger getLog() {
        org.slf4j.Logger log_ = log;
        if (log_ == null) {
            log = log_ = org.slf4j.LoggerFactory.getLogger(getClass());
        }
        return log_;
    }

    public abstract void execute() throws IOException;
}
