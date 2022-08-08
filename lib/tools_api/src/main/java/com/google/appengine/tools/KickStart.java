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

package com.google.appengine.tools;

import static com.google.common.base.StandardSystemProperty.JAVA_CLASS_PATH;
import static com.google.common.base.StandardSystemProperty.JAVA_HOME;
import static com.google.common.base.StandardSystemProperty.JAVA_SPECIFICATION_VERSION;
import static com.google.common.base.StandardSystemProperty.OS_NAME;
import static com.google.common.base.StandardSystemProperty.USER_DIR;

import com.google.appengine.tools.development.DevAppServerMain;
import com.google.apphosting.utils.config.AppEngineConfigException;
import com.google.apphosting.utils.config.AppEngineWebXml;
import com.google.apphosting.utils.config.AppEngineWebXmlReader;
import com.google.common.base.Ascii;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Launches a process in an operating-system agnostic way. Helps us avoid
 * idiosyncrasies in scripts for different platforms. Currently this only
 * works for DevAppServerMain.
 *
 * Takes a command line invocation like:
 *
 * <pre>
 * java -cp ../lib/appengine-tools-api.jar com.google.appengine.tools.KickStart \
 *   --jvm_flag="-Dlog4j.configuration=log4j.props"
 *   com.google.appengine.tools.development.DevAppServerMain \
 *   --jvm_flag="-agentlib:jdwp=transport=dt_socket,server=y,address=7000"
 *   --address=localhost --port=5005 appDir
 * </pre>
 *
 * and turns it into:
 *
 * <pre>
 * java -cp &lt;an_absolute_path&gt;/lib/appengine-tools-api.jar \
 *   -Dlog4j.configuration=log4j.props \
 *   -agentlib:jdwp=transport=dt_socket,server=y,address=7000 \
 *   com.google.appengine.tools.development.DevAppServerMain \
 *   --address=localhost --port=5005 &lt;an_absolute_path&gt;/appDir
 * </pre>
 *
 * while also setting its working directory (if appropriate).
 * <p>
 * All arguments between {@code com.google.appengine.tools.KickStart} and
 * {@code com.google.appengine.tools.development.DevAppServerMain}, as well as
 * all {@code --jvm_flag} arguments after {@code DevAppServerMain}, are consumed
 * by KickStart. The remaining options after {@code DevAppServerMain} are
 * given as arguments to DevAppServerMain, without interpretation by
 * KickStart.
 *
 * At present, the only valid option to KickStart itself is:
 * <DL>
 * <DT>--jvm_flag=&lt;vm_arg&gt;</DT><DD>Passes &lt;vm_arg&gt; as a JVM
 * argument for the child JVM.  May be repeated.</DD>
 * </DL>
 * Additionally, if the --external_resource_dir argument is specified, we use it
 * to set the working directory instead of the application war directory.
 *
 */
public class KickStart {

  private static final Logger logger = Logger.getLogger(KickStart.class.getName());

  private static final String JVM_FLAG = "--jvm_flag";
  private static final String JVM_FLAG_ERROR_MESSAGE =
      JVM_FLAG + "=<flag> expected.\n" + JVM_FLAG + " may be repeated to supply multiple flags";

  private static final String START_ON_FIRST_THREAD_FLAG = "--startOnFirstThread";
  private static final String START_ON_FIRST_THREAD_ERROR_MESSAGE =
      START_ON_FIRST_THREAD_FLAG + "=<boolean> expected";

  private static final String SDK_ROOT_FLAG = "--sdk_root";
  private static final String SDK_ROOT_ERROR_MESSAGE = SDK_ROOT_FLAG + "=<path> expected";

  private Process serverProcess = null;

  static class AppEnvironment {
    static final AppEnvironment DEFAULT = new AppEnvironment("default", null);

    final String serviceName;
    final String encoding;

    AppEnvironment(String serviceName, String encoding) {
      this.serviceName = serviceName;
      this.encoding = encoding;
    }
  }

  public static void main(String[] args) {
    new KickStart(args);
  }

  @CanIgnoreReturnValue
  private KickStart(String[] args) {
    String entryClass = null;

    ProcessBuilder builder = new ProcessBuilder();
    String home = JAVA_HOME.value();
    String javaExe = home + File.separator + "bin" + File.separator + "java";

    List<String> jvmArgs = new ArrayList<>();
    ArrayList<String> appServerArgs = new ArrayList<>();

    List<String> command = builder.command();
    command.add(javaExe);

    boolean startOnFirstThread = Ascii.equalsIgnoreCase(OS_NAME.value(), "Mac OS X");
    boolean testMode = false;

    for (int i = 0; i < args.length; i++) {
      // This section is for flags that either we don't care about and we
      // pass on to DevAppServerMain, or we do care about and we don't pass
      // on.
      if (args[i].startsWith(JVM_FLAG)) {
        jvmArgs.add(extractValue(args[i], JVM_FLAG_ERROR_MESSAGE));
      } else if (args[i].startsWith(SDK_ROOT_FLAG)) {
        String sdkRoot = new File(extractValue(args[i], SDK_ROOT_ERROR_MESSAGE)).getAbsolutePath();
        System.setProperty("appengine.sdk.root", sdkRoot);
        jvmArgs.add("-Dappengine.sdk.root=" + sdkRoot);
      } else if (args[i].startsWith(START_ON_FIRST_THREAD_FLAG)) {
        startOnFirstThread =
            Boolean.parseBoolean(extractValue(args[i], START_ON_FIRST_THREAD_ERROR_MESSAGE));
      } else if (args[i].equals("--test_mode")) {
        testMode = true;
      } else if (entryClass == null) {
        if (args[i].charAt(0) == '-') {
          throw new IllegalArgumentException(
              "This argument may not precede the classname: " + args[i]);
        } else {
          entryClass = args[i];
          if (!entryClass.equals(DevAppServerMain.class.getName()) && !testMode) {
            throw new IllegalArgumentException("KickStart only works for DevAppServerMain");
          }
        }
      } else {
        appServerArgs.add(args[i]);
      }
    }

    if (entryClass == null) {
      throw new IllegalArgumentException("missing entry classname");
    }

    File newWorkingDir = newWorkingDir(appServerArgs.toArray(new String[0]));
    builder.directory(newWorkingDir);

    if (startOnFirstThread) {
      // N.B.: If we're on Mac OS X, add
      // -XstartOnFirstThread to suppress the addition of an app to
      // the Dock even though we're going to initialize AWT (to work
      // around a subsequent crash in the stub implementation of the
      // Images API).
      //
      // For more details, see http://b/issue?id=1709075.
      jvmArgs.add("-XstartOnFirstThread");
    }
    if (!JAVA_SPECIFICATION_VERSION.value().equals("1.8")) {
      // Java11 or later need more flags:
      jvmArgs.add("--add-opens");
      jvmArgs.add("java.base/java.net=ALL-UNNAMED");
      jvmArgs.add("--add-opens");
      jvmArgs.add("java.base/sun.net.www.protocol.http=ALL-UNNAMED");
      jvmArgs.add("--add-opens");
      jvmArgs.add("java.base/sun.net.www.protocol.https=ALL-UNNAMED");
    }

    // Whatever classpath we were invoked with might have been relative.
    // We make all paths in the classpath absolute.
    String classpath = JAVA_CLASS_PATH.value();
    if (classpath == null) {
      throw new IllegalArgumentException("classpath must not be null");
    }
    StringBuilder newClassPath = new StringBuilder();
    List<String> paths = Splitter.onPattern(File.pathSeparator).splitToList(classpath);
    for (int i = 0; i < paths.size(); ++i) {
      newClassPath.append(new File(paths.get(i)).getAbsolutePath());
      if (i != paths.size() - 1) {
        newClassPath.append(File.pathSeparator);
      }
    }

    // User may have erroneously not included the webapp dir or got the args wrong. The first arg is
    // the name of the DevAppServerMain class, so there should be at least 2 args. The last arg
    // should be the app dir
    if (appServerArgs.isEmpty() || Iterables.getLast(appServerArgs).startsWith("-")) {
      new DevAppServerMain().printHelp(System.out);
      System.exit(1);
    }
    Path workingDir = Paths.get(Iterables.getLast(appServerArgs));
    new DevAppServerMain().validateWarPath(workingDir.toFile());

    String appDir = null;
    List<String> absoluteAppServerArgs = new ArrayList<>(appServerArgs.size());

    // Make any of the appserver arguments that need to be absolute, absolute.
    // This currently includes sdk_root, the external resource dir,
    // and the application root (last arg)
    for (int i = 0; i < appServerArgs.size(); ++i) {
      String arg = appServerArgs.get(i);
      if (i == appServerArgs.size() - 1) {
        // The last argument may be the app root
        if (!arg.startsWith("-")) {
          File file = new File(arg);
          if (file.exists()) {
            arg = new File(arg).getAbsolutePath();
            appDir = arg;
          }
        }
      }
      absoluteAppServerArgs.add(arg);
    }
    AppEnvironment appEnvironment = readAppEnvironment(appDir);

    String encoding = appEnvironment.encoding;
    if (encoding == null) {
      encoding = "UTF-8";
    }
    jvmArgs.add("-Dfile.encoding=" + encoding);

    // Build up the command
    command.addAll(jvmArgs);
    command.add("-classpath");
    command.add(newClassPath.toString());
    command.add(entryClass);
    // Pass the current working directory so relative files can be interpreted the natural way.
    command.add("--property=kickstart.user.dir=" + USER_DIR.value());

    command.addAll(absoluteAppServerArgs);
    // Setup environment variables.
    String gaeEnv = "localdev";
    String gaeRuntime = "java8";
    builder.environment().put("GAE_ENV", gaeEnv);
    builder.environment().put("GAE_RUNTIME", gaeRuntime);
    builder.environment().put("GAE_SERVICE", appEnvironment.serviceName);
    builder.environment().put("GAE_INSTANCE", UUID.randomUUID().toString());
    builder.inheritIO();

    logger.fine("Executing " + command);
    System.out.println("Executing " + command);

    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        if (serverProcess != null) {
          serverProcess.destroy();
        }
      }
    });

    try {
      serverProcess = builder.start();
    } catch (IOException e) {
      throw new RuntimeException("Unable to start the process", e);
    }

    try {
      serverProcess.waitFor();
    } catch (InterruptedException e) {
      // If we're interrupted, we just quit.
    }

    serverProcess.destroy();
    serverProcess = null;
  }

  private static String extractValue(String argument, String errorMessage) {
    int indexOfEqualSign = argument.indexOf('=');
    if (indexOfEqualSign == -1) {
      throw new IllegalArgumentException(errorMessage);
    }
    return argument.substring(argument.indexOf('=') + 1);
  }

  /**
   * Encapsulates the logic to determine the working directory that should be set for the dev
   * appserver process. If one is explicitly specified it will be used. Otherwise the last
   * command-line argument will be used.
   *
   * @param args The command-line arguments. The last command-line argument is used as the working
   *     directory.
   * @return The working directory to use. If the path to an existing directory was not specified
   *     then we exit with a failure.
   */
  private static File newWorkingDir(String[] args) {
    // User may have erroneously not included the webapp dir
    // or got the args wrong. The first arg is the name of
    // the DevAppServerMain class, so there should be at least 2
    // args. The last arg should be the app dir
    if (args.length < 1 || args[args.length - 1].startsWith("-")) {
      new DevAppServerMain().printHelp(System.out);
      System.exit(1);
    }
    File workingDir = new File(args[args.length - 1]);
    new DevAppServerMain().validateWarPath(workingDir);

    return workingDir;
  }

  static AppEnvironment readAppEnvironment(String appDir) {
    if (appDir == null) {
      return AppEnvironment.DEFAULT;
    }
    List<AppEngineWebXml> configs = new ArrayList<>();
    File config = new File(appDir, "WEB-INF/appengine-web.xml");
    if (config.exists()) {
      try {
        configs.add(new AppEngineWebXmlReader(appDir).readAppEngineWebXml());
      } catch (AppEngineConfigException e) {
        System.err.println("Error reading module: " + config.getAbsolutePath());
        return AppEnvironment.DEFAULT;
      }
    } else {
      // EAR project possibly, check all modules one by one.
      File ear = new File(appDir);
      if (!ear.exists()) {
        System.err.println("Application does not exist: " + ear.getAbsolutePath());
        return AppEnvironment.DEFAULT;
      }
      for (File war : ear.listFiles()) {
        File currConfig = new File(war, "WEB-INF/appengine-web.xml");
        if (currConfig.exists()) {
          try {
            configs.add(new AppEngineWebXmlReader(war.getAbsolutePath()).readAppEngineWebXml());
          } catch (AppEngineConfigException e) {
            System.err.println("Error reading module: " + war.getAbsolutePath());
            // We continue and skip this module...
          }
        }
      }
    }
    String serviceName = "default";
    String encoding = null;
    for (AppEngineWebXml currConfig : configs) {
      if (currConfig.getService() != null) {
        serviceName = currConfig.getService();
      } else if (currConfig.getModule() != null) {
        serviceName = currConfig.getModule();
      }
      Map<String, String> systemProperties = currConfig.getSystemProperties();
      if (systemProperties.containsKey("appengine.file.encoding")) {
        // If more than one appengine-web.xml specifies this, we take the last one.
        encoding = systemProperties.get("appengine.file.encoding");
      }
    }
    return new AppEnvironment(serviceName, encoding);
  }
}
