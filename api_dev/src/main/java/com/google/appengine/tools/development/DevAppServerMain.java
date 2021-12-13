/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.appengine.tools.development;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.appengine.tools.info.AppengineSdk;
import com.google.appengine.tools.info.UpdateCheck;
import com.google.appengine.tools.util.Action;
import com.google.appengine.tools.util.Option;
import com.google.appengine.tools.util.Parser;
import com.google.appengine.tools.util.Parser.ParseResult;
import com.google.apphosting.utils.config.GenerationDirectory;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * The command-line entry point for DevAppServer.
 *
 */
public class DevAppServerMain extends SharedMain {

  public static final String GENERATE_WAR_ARG = "generate_war";
  public static final String GENERATED_WAR_DIR_ARG = "generated_war_dir";

  private static final String DEFAULT_RDBMS_PROPERTIES_FILE = ".local.rdbms.properties";
  // Keep in sync with RDBMS_PROPERTIES_FILE in
  // com.google.appengine.api.rdbms.dev.LocalRdbmsService
  private static final String RDBMS_PROPERTIES_FILE_SYSTEM_PROPERTY = "rdbms.properties.file";

  // Keep in sync with SYSTEM_PROPERTY_STATIC_MODULE_PORT_NUM_PREFIX in
  // com.google.appengine.tools.development.DevAppServerPortPropertyHelper
  private static final String SYSTEM_PROPERTY_STATIC_MODULE_PORT_NUM_PREFIX =
      "com.google.appengine.devappserver_module.";

  private final Action startAction = new StartAction();

  private String versionCheckServer = AppengineSdk.getSdk().getDefaultServer();

  private String address = DevAppServer.DEFAULT_HTTP_ADDRESS;
  private int port = DevAppServer.DEFAULT_HTTP_PORT;
  private boolean disableUpdateCheck;
  private String generatedDirectory = null;
  private String defaultGcsBucketName = null;
  private String applicationId = null;

  /**
   * Returns the list of built-in {@link Option Options} for this instance of
   * {@link DevAppServerMain}.
   *
   * @return The list of built-in options
   */
  @VisibleForTesting
  List<Option> getBuiltInOptions() {
    List<Option> options = new ArrayList<>();
    options.addAll(getSharedOptions());
    options.addAll(
        Arrays.asList(
            new Option("s", "server", false) {
              @Override
              public void apply() {
                versionCheckServer = getValue();
              }

              @Override
              public List<String> getHelpLines() {
                return ImmutableList.of(
                    " --server=SERVER            The server to use to determine the latest",
                    "  -s SERVER                   SDK version.");
              }
            },
            new Option("a", "address", false) {
              @Override
              public void apply() {
                address = getValue();
              }

              @Override
              public List<String> getHelpLines() {
                return ImmutableList.of(
                    " --address=ADDRESS          The address of the interface on the local machine",
                    "  -a ADDRESS                  to bind to (or 0.0.0.0 for all interfaces).");
              }
            },
            new Option("p", "port", false) {
              @Override
              public void apply() {
                port = Integer.parseInt(getValue());
              }

              @Override
              public List<String> getHelpLines() {
                return ImmutableList.of(
                    " --port=PORT                The port number to bind to on the local machine.",
                    "  -p PORT");
              }
            },
            new Option(null, "disable_update_check", true) {
              @Override
              public void apply() {
                disableUpdateCheck = true;
              }

              @Override
              public List<String> getHelpLines() {
                return ImmutableList.of(
                    " --disable_update_check     Disable the check for newer SDK versions.");
              }
            },
            new Option(null, "generated_dir", false) {
              @Override
              public void apply() {
                generatedDirectory = getValue();
              }

              @Override
              public List<String> getHelpLines() {
                return ImmutableList.of(
                    " --generated_dir=DIR        Set the directory where generated files are"
                        + " created.");
              }
            },
            // Because of the limited horizontal space in the help lines, I (rbarry) had to shorten
            // the
            // full name from default_gcs_bucket_name to just default_gcs_bucket.
            new Option(null, "default_gcs_bucket", false) {
              @Override
              public void apply() {
                defaultGcsBucketName = getValue();
              }

              @Override
              public List<String> getHelpLines() {
                return ImmutableList.of(
                    " --default_gcs_bucket=NAME  Set the default Google Cloud Storage bucket"
                        + " name.");
              }
            },
            new Option("A", "application", false) {
              @Override
              public void apply() {
                applicationId = getValue();
              }

              @Override
              public List<String> getHelpLines() {
                return ImmutableList.of(
                    " --application=APP_ID       Set the application, overriding the application ",
                    "  -A APP_ID                   value from the application's "
                        + "configuration files.");
              }
            },
            new Option(null, "instance_port", false) {
              @Override
              public void apply() {
                processInstancePorts(getValues());
              }
              // --instance_port Module instance port specification. The module instance port
              //   specification may take one of two forms
              //
              //   Form one (--instance_port=module_name=port) is used to specify the port for an
              //   automatic module instance (one per automatic scaling module) or a load balancing
              //   instance (one per basic/manual scaling module).
              //
              //   Form two (--instance_port=module_name.instance=port) is used to specify the port
              //   for a non load balancing instance for a basic/manual scaling module. The number
              // of
              //   instances depends on the configuration and is not validated.
              //
              //   In either form module_name is not currently validated.
              //
              //   --instance_port is provided for internal testing and not currently documented
              // though
              //   perhaps users would find it useful.
            },
            new Option(null, "promote_yaml", true) {
              @Override
              public void apply() {
                System.setProperty("appengine.promoteYaml", "true");
              }
              // Deliberately undocumented.
              // This flag is only for internal use. When supplied, DevAppserver will try to use
              // yaml instead of xml configuration files. For example, queue.yaml instead of
              // queue.xml.
            }));
    return options;
  }

  private static void processInstancePorts(List<String> optionValues) {
    for (String optionValue : optionValues) {
      String[] keyAndValue = optionValue.split("=", 2);
      if (keyAndValue.length != 2) {
        reportBadInstancePortValue(optionValue);
      }

      try {
        Integer.parseInt(keyAndValue[1]);
      } catch (NumberFormatException nfe) {
        reportBadInstancePortValue(optionValue);
      }

      System.setProperty(
          SYSTEM_PROPERTY_STATIC_MODULE_PORT_NUM_PREFIX + keyAndValue[0].trim() + ".port",
          keyAndValue[1].trim());
    }
  }

  private static void reportBadInstancePortValue(String optionValue) {
    throw new IllegalArgumentException("Invalid instance_port value " + optionValue);
  }

  /**
   * Builds the complete list of {@link Option Options} for this instance of
   * {@link DevAppServerMain}. The list consists of the built-in options.
   *
   * @return The list of all options
   */
  private List<Option> buildOptions() {
    return getBuiltInOptions();
  }

  public static void main(String[] args) throws Exception {
    SharedMain.sharedInit();
    new DevAppServerMain().run(args);
  }

  public DevAppServerMain() {
  }

  public void run(String[] args) throws Exception {
    Parser parser = new Parser();
    ParseResult result = parser.parseArgs(startAction, buildOptions(), args);
    result.applyArgs();
  }

  @Override
  public void printHelp(PrintStream out) {
    out.println("Usage: <dev-appserver> [options] <app directory> [<appn directory> ...]");
    out.println("");
    out.println("Options:");
    for (Option option : buildOptions()) {
      for (String helpString : option.getHelpLines()) {
        out.println(helpString);
      }
    }
    out.println(" --jvm_flag=FLAG            Pass FLAG as a JVM argument. May be repeated to");
    out.println("                              supply multiple flags.");
  }

  /** Recursive directory deletion. */
  public static void recursiveDelete(File dead) {
    File[] files = dead.listFiles();
    if (files != null) {
      for (File name : files) {
        recursiveDelete(name);
      }
    }
    dead.delete();
  }
  /**
   * Returns an EAR structure for the list of services, using absolute URIs in the generated
   * application.xml.
   */
  @VisibleForTesting
  File constructTemporaryEARDirectory(List<String> services) throws IOException {
    final File path = Files.createTempDirectory("tmpEarArea").toFile();
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread() {
              @Override
              public void run() {
                recursiveDelete(path);
              }
            });
    File metaInf = new File(path, "META-INF");
    metaInf.mkdir();

    try (Writer fw =
        Files.newBufferedWriter(new File(metaInf, "application.xml").toPath(), UTF_8)) {
      fw.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
      fw.write("<application ");
      fw.write("xmlns=\"http://java.sun.com/xml/ns/javaee\" ");
      fw.write("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" ");
      fw.write("xsi:schemaLocation=\"http://java.sun.com/xml/ns/javaee ");
      fw.write("http://java.sun.com/xml/ns/javaee/application_5.xsd\" ");
      fw.write("version=\"5\">");
      fw.write("<display-name>appengine-modules-ear</display-name>");
      TreeSet<String> contextRootNames = new TreeSet<>();
      for (String service : services) {
        File serviceFile = new File(service);
        fw.write("<module>");
        fw.write("<web>");
        // Absolute URI for the given service/module.
        // We do not support duplicate entries.
        fw.write("<web-uri>" + serviceFile.toURI() + "</web-uri>");
        // We need to verify the context root is unique even if the directory name is not:
        String contextRoot = serviceFile.getName();
        int index = 0;
        while (contextRootNames.contains(contextRoot)) {
          contextRoot += index++;
        }
        contextRootNames.add(contextRoot);
        fw.write("<context-root>/" + contextRoot + "</context-root>");
        fw.write("</web>");
        fw.write("</module>");
      }
      fw.write("</application>");
    }
    try (Writer fw =
        Files.newBufferedWriter(new File(metaInf, "appengine-application.xml").toPath(), UTF_8)) {
      fw.write("<?xml version=\"1.0\" encoding=\"utf-8\" standalone=\"no\"?>\n");
      fw.write("<appengine-application xmlns=\"http://appengine.google.com/ns/1.0\">");
      // Pass a default appId to the local dev server if none is set. It is not really used in the
      // local dev server, but mandatory per the schema.
      String applicationValue = applicationId == null ? "localdevapp" : applicationId;
      fw.write("<application>" + applicationValue + "</application></appengine-application>");
    }
    return path;
  }

  class StartAction extends Action {
    StartAction() {
      super("start");
    }

    @Override
    public void apply() {
      List<String> args = getArgs();
      try {
        if (args.isEmpty()) {
          printHelp(System.err);
          System.exit(1);
        }
        for (String path : args) {
          if (!new File(path).exists()) {
            System.out.println(path + " does not exist.");
            printHelp(System.err);
            System.exit(1);
          }
        }
        File appDir;
        if (args.size() == 1) {
          appDir = new File(args.get(0)).getCanonicalFile();

        } else {
          appDir = constructTemporaryEARDirectory(args);
        }

        validateWarPath(appDir);
        configureRuntime(appDir);

        UpdateCheck updateCheck = new UpdateCheck(versionCheckServer, appDir, true);
        if (updateCheck.allowedToCheckForUpdates() && !disableUpdateCheck) {
          updateCheck.maybePrintNagScreen(System.err);
        }
        updateCheck.checkJavaVersion(System.err);
        DevAppServer server =
            new DevAppServerFactory()
                .createDevAppServer(
                    appDir,
                    null,
                    null,
                    address,
                    port,
                    /* useCustomStreamHandler= */ true,
                    /* installSecurityManager= */ false,
                    ImmutableMap.<String, Object>of(),
                    /*no agent */ true,
                    applicationId);

        Map<String, String> stringProperties = getSystemProperties();
        setGeneratedDirectory(stringProperties);
        setRdbmsPropertiesFile(stringProperties, appDir);
        postServerActions(stringProperties);
        setDefaultGcsBucketName(stringProperties);
        addPropertyOptionToProperties(stringProperties);
        server.setServiceProperties(stringProperties);

        try {
          server.start().await();
        } catch (InterruptedException e) {
        }

        System.out.println("Shutting down.");
        System.exit(0);
      } catch (Exception ex) {
        ex.printStackTrace();
        System.exit(1);
      }
    }

    private void setGeneratedDirectory(Map<String, String> stringProperties) {
      if (generatedDirectory != null) {
        File dir = new File(generatedDirectory);
        String error = null;
        if (dir.exists()) {
          if (!dir.isDirectory()) {
            error = generatedDirectory + " is not a directory.";
          } else if (!dir.canWrite()) {
            error = generatedDirectory + " is not writable.";
          }
        } else if (!dir.mkdirs()) {
          error = "Could not make " + generatedDirectory;
        }
        if (error != null) {
          System.err.println(error);
          System.exit(1);
        }
       stringProperties.put(GenerationDirectory.GENERATED_DIR_PROPERTY, generatedDirectory);
      }
    }

    private void setDefaultGcsBucketName(Map<String, String> stringProperties) {
      if (defaultGcsBucketName != null) {
        stringProperties.put("appengine.default.gcs.bucket.name", defaultGcsBucketName);
      }
    }

    /**
     * Sets the property named {@link #RDBMS_PROPERTIES_FILE_SYSTEM_PROPERTY} to the default value
     * {@link #DEFAULT_RDBMS_PROPERTIES_FILE} if the property is not already set and if there is a
     * file by that name in {@code appDir}.
     *
     * @param stringProperties The map in which the value will be set
     * @param appDir The appDir, aka the WAR dir
     */
    private void setRdbmsPropertiesFile(Map<String, String> stringProperties, File appDir) {
      if (stringProperties.get(RDBMS_PROPERTIES_FILE_SYSTEM_PROPERTY) != null) {
        return;
      }
      File file = findRdbmsPropertiesFile(appDir);
      if (file != null) {
        String path = file.getPath();
        System.out.println("Reading local rdbms properties from " + path);
        stringProperties.put(RDBMS_PROPERTIES_FILE_SYSTEM_PROPERTY, path);
      }
    }

    /**
     * Returns the default rdbms properties file in the given dir if it exists.
     * @param dir The directory in which to look
     * @return The default rdbs properties file, or {@code null}.
     */
    private File findRdbmsPropertiesFile(File dir) {
      File candidate = new File(dir, DEFAULT_RDBMS_PROPERTIES_FILE);
      if (candidate.isFile() && candidate.canRead()) {
        return candidate;
      }
      return null;
    }
  }
}
