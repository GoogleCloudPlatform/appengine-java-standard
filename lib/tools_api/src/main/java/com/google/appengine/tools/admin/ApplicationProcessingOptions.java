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

package com.google.appengine.tools.admin;

import com.google.apphosting.utils.config.StagingOptions;
import java.io.File;

/**
 * Options used in preparing an application directory for upload.
 */
public class ApplicationProcessingOptions {
  private static final String JAVA_CMD_PROP = "appengine.java";
  private static final String JAVAC_CMD_PROP = "appengine.javac";

    private File java;
    private File javac;
    private boolean compileJsps = true;
    private boolean doBatch = true;
    private boolean useAsyncQuickstart = false;
    private String runtime;
    private boolean allowAnyRuntime = false;
    private boolean failOnPrecompilationError = false;
    private boolean ignoreEndpointsFailures = true;
    private boolean quickstart = false;
    private boolean callerUploadingDispatch = false;
    private StagingOptions stagingOptions = StagingOptions.EMPTY;
    private StagingOptions defaultStagingOptions = StagingOptions.ANCIENT_DEFAULTS;

    /**
     * Returns an appropriate "java" executable. If a prior call to
     * {@link #setJavaExecutable(File)} was made, that value is returned (on
     * windows, the algorithm is forgiving if ".exe" was omitted, and will add
     * it). If not, the system property {@code java.home} is used to identify
     * the currently-running JVM, and if that directory contains a file named
     * {@code bin/java} (Unix) or {@code bin\\java.exe} (Windows), that is
     * returned.
     *
     * @return the Java executable, as a {@link File}.
     * @throws IllegalStateException if the java cannot be found by the
     *         heuristic above, but {@link #setJavaExecutable(File)} has not
     *         been called, or if it has been called, but the specified file
     *         cannot be found.
     */
    public File getJavaExecutable() {
      if (java != null) {
        return java;
      }

      String javaProp = System.getProperty(JAVA_CMD_PROP);
      if (javaProp != null) {
        java = new File(javaProp);
        if (!java.exists()) {
          if (Utility.isOsWindows() && !javaProp.endsWith(".exe")
              && (new File(javaProp + ".exe")).exists()) {
            java = new File(javaProp + ".exe");
          } else {
            throw new IllegalStateException("cannot find java executable \"" + javaProp + "\"");
          }
        }
      } else {
        String javaHome = System.getProperty("java.home");
        String javaCmd =
            javaHome + File.separator + "bin" + File.separator + "java"
                + (Utility.isOsWindows() ? ".exe" : "");
        java = new File(javaCmd);
        if (!java.exists()) {
          java = null;
          throw new IllegalStateException("cannot find java executable "
              + "based on java.home, tried \"" + javaCmd + "\"");
        }
      }
      return java;
    }

    /**
     * Explicitly requests a specific {@code java} program be used for launched
     * tasks, such as compiling JSP files.
     *
     * @param java the executable file to run.
     */
    void setJavaExecutable(File java) {
      this.java = java;
    }

    /**
     * Returns an appropriate "javac" executable. If a prior call to
     * {@link #setJavaCompiler(File)} was made, that value is returned (on
     * windows, the algorithm is forgiving if ".exe" was omitted, and will add
     * it). If not, the system property {@code java.home} is used to identify
     * the currently-running JVM. If that pathname ends with "jre", then its
     * parent is used instead as a hoped-for JDK root. If that directory
     * contains a file named {@code bin/javac} (Unix) or {@code bin\\javac.exe}
     * (Windows), that is returned.
     *
     * @return the Java compiler, as a {@link File}.
     * @throws IllegalStateException if the javac cannot be found by the
     *         heuristic above, but {@link #setJavaCompiler(File)} has not be
     *         called, or if it has been called but the file does not exist.
     */
    public File getJavaCompiler() {
      if (javac != null) {
        return javac;
      }

      String javacProp = System.getProperty(JAVAC_CMD_PROP);
      if (javacProp != null) {
        javac = new File(javacProp);
        if (!javac.exists()) {
          if (Utility.isOsWindows() && !javacProp.endsWith(".exe")
              && (new File(javacProp + ".exe")).exists()) {
            javac = new File(javacProp + ".exe");
          } else {
            throw new IllegalStateException("cannot find javac executable \"" + javacProp + "\"");
          }
        }
      } else {
        String javaHome = System.getProperty("java.home");
        String javacDir = javaHome;
        String javacCmd =
            javacDir + File.separator + "bin" + File.separator + "javac"
                + (Utility.isOsWindows() ? ".exe" : "");
        javac = new File(javacCmd);
        if (!javac.exists()) {
          javac = null;
          javacDir = (new File(javaHome)).getParentFile().getPath();
          String javacCmd2 =
              javacDir + File.separator + "bin" + File.separator + "javac"
                  + (Utility.isOsWindows() ? ".exe" : "");
          javac = new File(javacCmd2);
          if (!javac.exists()) {
            javac = null;
            throw new IllegalStateException("cannot find javac executable "
                + "based on java.home, tried \"" + javacCmd + "\" and \"" + javacCmd2 + "\"");
          }
        }
      }
      return javac;
    }

    /**
     * Explicitly requests a specific {@code javac} program be used for launched
     * Java compilations, such as compiling JSP files into classes.
     *
     * @param javac the executable file to run.
     */
    void setJavaCompiler(File javac) {
      this.javac = javac;
    }

    /** Returns whether we should attempt to compile JSPs */
    public boolean isCompileJspsSet() {
      return compileJsps;
    }

    /**
     * Requests that *.jsp files should be compiled into Java byte code, or if
     * false should be left untouched.
     *
     * @param doJsps {@code true} to compile .jsp files
     */
    void setCompileJsps(boolean doJsps) {
      this.compileJsps = doJsps;
    }

    /**
     * Returns whether we should use batch upload
     */
    public boolean isBatchModeSet() {
      return doBatch;
    }

    /**
     * Requests we use the upload batch mode
     *
     * @param doBatch {@code true} to use batch mode
     */
    void setBatchMode(boolean doBatch) {
      this.doBatch = doBatch;
    }

    /** Sets whether we should use the Async quickstart generator. */
    public void setUseAsyncQuickstart(boolean b) {
      useAsyncQuickstart = b;
    }

    /** Returns whether we should use the Async quickstart generator. */
    public boolean isUseAsyncQuickstart() {
      return useAsyncQuickstart;
    }

    /** Sets the default staging options. */
    public void setDefaultStagingOptions(StagingOptions opts) {
      defaultStagingOptions = opts;
    }

    /** Get the default staging options. */
    public StagingOptions getDefaultStagingOptions() {
      return defaultStagingOptions;
    }

    /** Sets the staging options. */
    public void setStagingOptions(StagingOptions opts) {
      stagingOptions = opts;
    }
  
    /** Get the staging options. */
    public StagingOptions getStagingOptions() {
      return stagingOptions;
    }

    /** Sets the runtime id. */
    public void setRuntime(String s) {
      runtime = s;
    }

    /** Returns the runtime id. */
    public String getRuntime() {
      return runtime;
    }

    /** Sets whether to skip validation of the runtime id provided by the user. */
    public void setAllowAnyRuntime(boolean b) {
      allowAnyRuntime = b;
    }

    /** Returns whether to skip validation of the runtime id provided by the user. */
    public boolean isAllowAnyRuntime() {
      return allowAnyRuntime;
    }

    /** Sets whether to abort an update in case precompilation fails. */
    public void setFailOnPrecompilationError(boolean b) {
      failOnPrecompilationError = b;
    }

    /** Returns whether to abort an update in case precompilation fails. */
    public boolean isFailOnPrecompilationError() {
      return failOnPrecompilationError;
    }

    /** Sets whether Endpoints config update failures should be ignored. */
    public void setIgnoreEndpointsFailures(boolean b) {
      ignoreEndpointsFailures = b;
    }

    /** Return whether Endpoints config update failures should be ignored. */
    public boolean isIgnoreEndpointsFailures() {
      return ignoreEndpointsFailures;
    }

    /** Sets whether Quickstart should be applied when staging */
    public void setQuickstart(boolean b) {
      quickstart = b;
    }

    /** Return whether Quickstart should be applied when staging */
    public boolean isQuickstart() {
      return quickstart;
    }

    /** Sets whether caller will upload dispatch.xml. */
    public void setCallerUploadingDispatch(boolean b) {
      callerUploadingDispatch = b;
    }

    /** Return whether caller will upload dispatch.xml. */
    boolean isCallerUploadingDispatch() {
      return callerUploadingDispatch;
    }
  }
