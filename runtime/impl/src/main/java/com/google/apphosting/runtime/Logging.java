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

package com.google.apphosting.runtime;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import static java.nio.charset.StandardCharsets.UTF_8;

/** Configures logging for the GAE Java Runtime. */
public final class Logging {
  private static final Logger logger = Logger.getLogger(Logging.class.getName());

  private final Logger rootLogger;

  public Logging() {
    this(Logger.getLogger(""));
  }

  Logging(Logger rootLogger) {
    this.rootLogger = rootLogger;
  }

  static Properties loadUserLogProperties(String userLogConfigFilePath) {
    Properties configProps = new Properties();
    if (userLogConfigFilePath != null) {
      try (BufferedReader br = new BufferedReader(
          new InputStreamReader(new FileInputStream(userLogConfigFilePath), UTF_8))) {
        configProps.load(br);
      } catch (IllegalArgumentException | IOException e) {
        logger.log(Level.WARNING, "Unable to read the java.util.logging configuration file.", e);
      }
    }
    return configProps;
  }

  private static void processLogConfigSection(Properties configProps, ClassLoader appClassLoader) {
    String configClasses = configProps.getProperty("config");
    if (configClasses == null) {
      return;
    }
    for (String configClass : splitList(configClasses)) {
      try {
        // Use application classloader for config classes.
        Class<?> configType = appClassLoader.loadClass(configClass);
        configType.getConstructor().newInstance();
      } catch (Exception e) {
        logger.log(Level.WARNING, "Unable to instantiate config object: " + configClass, e);
      }
    }
    // Clear config section otherwise LogManager will use system classloader
    // for these classes.
    configProps.remove("config");
  }

  private void processLogHandlersAndLevels(Properties configProps) {
    // We put user configuration properties into LogManager.prop directly, so that
    // logger instantiation in user code is handled by LogManager.
    // See j/c/g/apphosting/runtime/security/shared/intercept/java/util/logging/Logger_.
    // putUserLogProperty()
    Properties logManagerProperties = null;
    try {
      Field propsField = LogManager.class.getDeclaredField("props");
      propsField.setAccessible(true);
      logManagerProperties = (Properties) propsField.get(LogManager.getLogManager());
    } catch (Exception e) {
      logger.log(Level.SEVERE, "Unable to access the LogManager properties.", e);
      return;
    }

    // Ignore levels on handlers similarly to java7 runtime.
    Set<String> handlers = splitList(configProps.getProperty("handlers"));
    for (String property : configProps.stringPropertyNames()) {
      if (property.endsWith(".level")) {
        String name = property.substring(0, property.length() - ".level".length());
        if (handlers.contains(name)) {
          // Properties.stringPropertyNames() gives a new Set so
          // it is OK to modify the properties here.
          configProps.remove(property);
        } else {
          logManagerProperties.put(name + ".level", configProps.getProperty(property));
        }
      }
    }
    configProps.remove("handlers");

    // Set up the root level if present.
    String globalLevel = configProps.getProperty(".level");
    if (globalLevel != null) {
      try {
        rootLogger.setLevel(Level.parse(globalLevel));
        rootLogger.setUseParentHandlers(false);
        logManagerProperties.put(".level", globalLevel);
      } catch (IllegalArgumentException e) {
        // Ignore the fact that we cannot parse log level.
      }
    }
  }

  private static Set<String> splitList(String list) {
    if (list == null) {
      return Collections.emptySet();
    }
    Set<String> tokens = new HashSet<>();
    StringTokenizer st = new StringTokenizer(list, " ,");
    while (st.hasMoreTokens()) {
      tokens.add(st.nextToken());
    }
    return tokens;
  }

  void redirectStdoutStderr(String identifier) {
    String prefix = "[" + identifier + "].";
    // TODO: Investigate if LogPrintStream can be replaced with a PrintStream with
    // autoFlush set to true. It appears that there are differences in behavior wrt the
    // timing of the calls to flush() on the wrapped LogStream that result in incorrect
    // log output, at least according to the unit tests.
    System.setOut(
        new LogPrintStream(new LogStream(Level.INFO, Logger.getLogger(prefix + "<stdout>"))));
    System.setErr(
        new LogPrintStream(new LogStream(Level.WARNING, Logger.getLogger(prefix + "<stderr>"))));
  }

  public void applyLogProperties(String userLogConfigFilePath, ClassLoader appClassLoader) {
    Properties configProps = loadUserLogProperties(userLogConfigFilePath);
    processLogConfigSection(configProps, appClassLoader);
    processLogHandlersAndLevels(configProps);
  }

  public void logJsonToFile(@Nullable String projectId, Path logPath, boolean clearLogHandlers) {
    PrintStream printStream;
    try {
      printStream = new PrintStream(logPath.toFile());
    } catch (FileNotFoundException e) {
      if (logger.isLoggable(Level.FINE)) {
        logger.log(Level.WARNING, "Unable to create log handler to " + logPath, e);
      }
      else {
        logger.log(Level.WARNING, "Unable to create log handler to " + logPath);
      }
      return;
    }

    // Attempt to handle the case where the user has already configured a fancy Formatter that is
    // not a SimpleFormatter. We can't necessarily do a great job in that case, because their
    // Formatter is probably expecting to format a complete LogRecord to a PrintStream, whereas we
    // just want it to format the log message along with any parameters. But in the usual case where
    // we have a SimpleFormatter, this should do the right thing.
    Formatter formatter = new SimpleFormatter();
    for (Handler handler : rootLogger.getHandlers()) {
      if (clearLogHandlers) {
        rootLogger.removeHandler(handler);
      }

      if (handler.getFormatter() != null) {
        formatter = handler.getFormatter();
      }
    }

    LogHandler logHandler =
        new JsonLogHandler(printStream, /* closePrintStreamOnClose= */ false, projectId, formatter);
    if (clearLogHandlers) {
      logHandler.init(rootLogger);
    } else {
      // logHandler.init has a side effect of clearing the log handlers, and emancipating runtime
      // loggers from their parents, whereas the only part we truly want is to add this handler:
      rootLogger.addHandler(logHandler);
    }
  }

  /**
   * A {@link PrintStream} that implements flushing behavior such that it works
   * well with server (remote) logging.
   */
  private static class LogPrintStream extends PrintStream {
    public LogPrintStream(OutputStream out) {
      super(out, false);
    }

    @Override
    public void println(Object x) {
      super.println(x);
      flush();
    }

    @Override
    public void println(String x) {
      super.println(x);
      flush();
    }

    @Override
    public void println(char[] x) {
      super.println(x);
      flush();
    }

    @Override
    public void println(double x) {
      super.println(x);
      flush();
    }

    @Override
    public void println(float x) {
      super.println(x);
      flush();
    }

    @Override
    public void println(long x) {
      super.println(x);
      flush();
    }

    @Override
    public void println(int x) {
      super.println(x);
      flush();
    }

    @Override
    public void println(char x) {
      super.println(x);
      flush();
    }

    @Override
    public void println(boolean x) {
      super.println(x);
      flush();
    }

    @Override
    public void println() {
      super.println();
      flush();
    }

    @Override
    public void print(char c) {
      super.print(c);
      if (c == '\n') {
        flush();
      }
    }

    @Override
    public void print(char[] s) {
      boolean flush = false;
      super.print(s);
      for (int i = 0; i < s.length; ++i) {
        if (s[i] == '\n') {
          flush = true;
        }
      }
      if (flush) {
        flush();
      }
    }

    @Override
    public void print(String s) {
      super.print(s);
      if (s != null && s.indexOf('\n') != -1) {
        flush();
      }
    }

    @Override
    public void print(Object obj) {
      print(String.valueOf(obj));
    }
  }

  /**
   * A buffered {@link OutputStream} that writes its contents to a {@link Logger}.
   */
  static class LogStream extends OutputStream {

    private final Logger logger;
    private final Level level;
    private ByteArrayOutputStream output = new ByteArrayOutputStream(4 * 1024);

    /**
     * Creates a new LogStream that logs to {@code logger} with messages
     * at Level, {@code level}.
     *
     * @param level The level to log at
     */
    public LogStream(Level level, Logger logger) {
      if (logger == null) {
        throw new NullPointerException("logger argument must not be null");
      }
      this.level = level;
      this.logger = logger;
    }

    @Override
    public void write(int b) throws IOException {
      output.write(b);
    }

    @Override
    public void write(byte[] b) throws IOException {
      output.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
      output.write(b, off, len);
    }

    @Override
    public void flush() throws IOException {
      byte[] buffer = output.toByteArray();
      if (buffer.length == 0) {
        return;
      }
      String msg = new String(buffer, UTF_8);
      LogRecord record = new LogRecord(level, msg);
      record.setSourceClassName(null);
      record.setSourceMethodName(null);
      record.setLoggerName(logger.getName());
      logger.log(record);
      output.reset();
    }
  }
}
