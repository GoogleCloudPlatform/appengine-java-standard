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

package com.google.appengine.tools.development.devappserver2;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.appengine.tools.development.DevAppServer;
import com.google.appengine.tools.development.SharedMain;
import com.google.appengine.tools.development.proto.Config;
import com.google.appengine.tools.util.Action;
import com.google.appengine.tools.util.Option;
import com.google.appengine.tools.util.Parser;
import com.google.appengine.tools.util.Parser.ParseResult;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A runnable program that acts as an instance that can be run from within the
 * Python devappserver2. See apphosting/tools/devappserver2/java_runtime.py.
 *
 */
public class StandaloneInstance extends SharedMain {
  private static final String SDK_ROOT_ENVIRONMENT_VAR = "SDKROOT";
  private static final String JAVA_PATH_PREFIX = "google/appengine/tools/java/";
  private static final String JAVA_LIB_PATH = JAVA_PATH_PREFIX + "lib";

  private static final Logger logger = Logger.getLogger(StandaloneInstance.class.getName());

  private final Action startAction = new StartAction();
  private final File sdkRoot;
  private final File javaLibs;

  public static void main(String[] args) throws Exception {
    SharedMain.sharedInit();
    System.setProperty("com.google.appengine.devappserver2", "true");
    new StandaloneInstance().run(args);
  }

  private StandaloneInstance() {
    String sdkRootPath = System.getenv(SDK_ROOT_ENVIRONMENT_VAR);
    if (sdkRootPath == null) {
      logger.severe("Environment does not have SDKROOT variable set");
      System.exit(1);
    }
    sdkRoot = new File(sdkRootPath);

    javaLibs = new File(sdkRoot, JAVA_LIB_PATH);
    if (!javaLibs.isDirectory()) {
      logger.log(Level.SEVERE, "Java libraries directory does not exist or cannot be read: {0}",
          javaLibs);
      System.exit(1);
    }
  }

  private void run(String[] args) throws Exception {
    Parser parser = new Parser();
    ParseResult result = parser.parseArgs(startAction, buildOptions(), args);
    result.applyArgs();
  }

  class StartAction extends Action {
    StartAction() {
      super("start");
    }

    @Override
    public void apply() {
      List<String> args = getArgs();
      if (args.size() != 2) {
        printHelp(System.err);
        System.exit(1);
      }
      try {
        apply(args);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    private void apply(List<String> args) throws IOException {
      File inputConfigFile = new File(args.get(0));
      Config inputConfig;
      try (InputStream in = new FileInputStream(inputConfigFile)) {
        inputConfig = Config.parseFrom(in);
      } finally {
        boolean deleted = inputConfigFile.delete();
        if (!deleted) {
          logger.log(Level.WARNING, "Could not delete {0}", inputConfigFile);
        }
      }
      String apiHost = inputConfig.getApiHost();
      int apiPort = inputConfig.getApiPort();
      String applicationRoot = inputConfig.getApplicationRoot().toStringUtf8();
      File appDir = new File(applicationRoot);
      validateWarPath(appDir);
      configureRuntime(appDir);

      boolean noJavaAgent = true;
      // TODO: Implement Java Agent support.

      Map<String, ?> containerConfigOptions = ImmutableMap.of(
          "com.google.appengine.apiHost", apiHost,
          "com.google.appengine.apiPort", apiPort);
      DevAppServer2Factory devAppServer2Factory = new DevAppServer2Factory();
      File externalResourceDir = null;
      File webXmlLocation = null;
      File appEngineWebXmlLocation = null;
      // TODO: figure out if we need to supply values for these, and delete the parameters
      // if not.
      boolean useCustomStreamHandler = true;
      boolean installSecurityManager = false;
      DevAppServer server = devAppServer2Factory.createDevAppServer(
          appDir, externalResourceDir, webXmlLocation, appEngineWebXmlLocation, "localhost", 0,
          useCustomStreamHandler, installSecurityManager, containerConfigOptions, noJavaAgent);
      Map<String, String> stringProperties = getSystemProperties();
      postServerActions(stringProperties);
      addPropertyOptionToProperties(stringProperties);
      server.setServiceProperties(stringProperties);
      CountDownLatch serverLatch;
      try {
        serverLatch = server.start();
        int port = server.getPort();
        File outputPortFile = new File(args.get(1));
        try (PrintWriter printWriter = new PrintWriter(outputPortFile, UTF_8.name())) {
          printWriter.println(port);
        }
        serverLatch.await();
      } catch (Exception e) {
        logger.log(Level.SEVERE, "Failed to start server", e);
      }
    }
  }

  @Override
  protected void printHelp(PrintStream out) {
    out.println("Usage: <StandaloneInstance> [options] <app directory> <port file>");
    out.println("  where <port file> is the file to which the HTTP server port is written");
    out.println("");
    out.println("Options:");
    for (Option option : buildOptions()) {
      for (String helpString : option.getHelpLines()) {
        out.println(helpString);
      }
    }
  }

  private List<Option> buildOptions() {
    return getSharedOptions();
  }
}
