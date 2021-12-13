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
import java.io.PrintWriter;

/**
 * Creates a new {@link AppAdmin} for a designated App Engine application.
 *
 */
public class AppAdminFactory {

  private ApplicationProcessingOptions appOptions = new ApplicationProcessingOptions();

  /**
   * Creates a new {@link AppAdmin} that can be used to administer the designated App Engine
   * application.
   *
   * @param app The application to be administered. May be {@code null}.
   * @param errorWriter A writer to which error logs can be written. The logs can be used for
   *     diagnosis if a failure occurs during operation. May be {@code null}.
   * @return a not {@code null} AppAdmin
   */
  public AppAdmin createAppAdmin(Application app, PrintWriter errorWriter) {
    return new AppAdminImpl(app, errorWriter, appOptions);
  }

  public ApplicationProcessingOptions getAppOptions() {
    return appOptions;
  }

  /**
   * Specifies the location of a java executable, used when compiling JSPs. By default, the system
   * property {@code java.home} is used to identify the currently-running JVM, and if that directory
   * contains a file named {@code bin/java} (Unix) or {@code bin\\java.exe} (Windows), that is
   * returned.
   *
   * @param java the Java executable to be used.
   */
  public void setJavaExecutable(File java) {
    appOptions.setJavaExecutable(java);
  }

  /**
   * Specifies the location of a javac executable, used when compiling JSPs. By default, the system
   * property {@code java.home} is used to identify the currently-running JVM. If that pathname ends
   * with "jre", then its parent is used instead as a hoped-for JDK root. If that directory contains
   * a file named {@code bin/javac} (Unix) or {@code bin\\javac.exe} (Windows), that is returned.
   *
   * @param javac the Java compiler executable to be used.
   */
  public void setJavaCompiler(File javac) {
    appOptions.setJavaCompiler(javac);
  }

  /**
   * Requests that *.jsp files should be compiled into Java byte code, or if false should be left
   * untouched.
   *
   * @param flag {@code true} to compile .jsp files
   */
  public void setCompileJsps(boolean flag) {
    appOptions.setCompileJsps(flag);
  }

  /**
   * Replaces the default staging options to this application options.
   *
   * @param opts the new staging options
   */
  public void setDefaultStagingOptions(StagingOptions opts) {
    appOptions.setDefaultStagingOptions(opts);
  }

  /**
   * Replaces the staging options to this application options.
   *
   * @param opts the new staging options
   */
  public void setStagingOptions(StagingOptions opts) {
    appOptions.setStagingOptions(opts);
  }

  /**
   * Use the Async quickstart generator.
   *
   * @param async {@code true} uses the async quickstart generator.
   */
  public void setUseAsyncQuickstart(boolean async) {
    appOptions.setUseAsyncQuickstart(async);
  }

  /**
   * Use Java8 and Jetty9.
   *
   * @param java8 {@code true} uses Java8 with Jetty9.
   * @deprecated This method has not effect, and should not be used anymore.
   */
  @Deprecated
  public void setUseJava8(boolean java8) {
    System.err.println("The method setUseJava8() in AppAdminFactory class is now a no-op.");
  }

  /**
   * Sets the runtime id to use in the generated app.yaml descriptor.
   *
   * @param runtime the runtime id to use.
   */
  public void setRuntime(String runtime) {
    appOptions.setRuntime(runtime);
  }

  /**
   * Enables or disables validation of the runtime id provided by the user.
   *
   * @param allowAnyRuntime {@code true} to allow an arbitrary runtime id value, {@code false} to
   *     validate it against the list of supported runtimes.
   */
  public void setAllowAnyRuntime(boolean allowAnyRuntime) {
    appOptions.setAllowAnyRuntime(allowAnyRuntime);
  }

  /**
   * Enables or disables treating (repeated) precompilation errors as fatal when updating an
   * application.
   *
   * @param fail {@code true} to abort an update if precompilation fails, {@code false} to treat it
   *     as a warning and continue updating the application.
   */
  public void setFailOnPrecompilationError(boolean fail) {
    appOptions.setFailOnPrecompilationError(fail);
  }

  public void setQuickstart(boolean enable) {
    appOptions.setQuickstart(enable);
  }
}
