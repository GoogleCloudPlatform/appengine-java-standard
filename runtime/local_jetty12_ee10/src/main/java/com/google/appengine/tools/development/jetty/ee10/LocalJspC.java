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

package com.google.appengine.tools.development.jetty.ee10;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.apache.jasper.JasperException;
import org.apache.jasper.JspC;
import org.apache.jasper.compiler.AntCompiler;
import org.apache.jasper.compiler.Localizer;
import org.apache.jasper.compiler.SmapStratum;

/**
 * Simple wrapper around the Apache JSP compiler. It defines a Java compiler only to compile the
 * user defined tag files, as it seems that this cannot be avoided. For the regular JSPs, the
 * compilation phase is not done here but in single compiler invocation during deployment, to speed
 * up compilation (See cr/37599187.)
 */
public class LocalJspC {

  // Cannot use System.getProperty("java.class.path") anymore
  // as this process can run embedded in the GAE tools JVM. so we cache
  // the classpath parameter passed to the JSP compiler to be used to compile
  // the generated java files for user tag libs.
  static String classpath;

  public static void main(String[] args) throws JasperException {
    if (args.length == 0) {
      System.out.println(Localizer.getMessage("jspc.usage"));
    } else {
      JspC jspc =
          new JspC() {
            @Override
            public String getCompilerClassName() {
              return LocalCompiler.class.getName();
            }
          };
      jspc.setArgs(args);
      jspc.setCompiler("extJavac");
      jspc.setAddWebXmlMappings(true);
      classpath = jspc.getClassPath();
      jspc.execute();
    }
  }

  /** Very simple compiler for JSPc that is behaving like the ANT compiler,
   * but uses the Tools System Java compiler to speed compilation process.
   * Only the generated code for *.tag files is compiled by JSPc even with the "-compile" flag
   * not set.
   **/
  public static class LocalCompiler extends AntCompiler {

    // Cache the compiler and the file manager:
    static JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

    @Override
    protected void generateClass(Map<String, SmapStratum> smaps) {
      // Lazily check for the existence of the compiler:
      if (compiler == null) {
        throw new RuntimeException(
            "Cannot get the System Java Compiler. Please use a JDK, not a JRE.");
      }
      StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
      ArrayList<File> files = new ArrayList<>();
      files.add(new File(ctxt.getServletJavaFileName()));
      List<String> optionList = new ArrayList<>();
      // Set compiler's classpath to be same as the jspc main class's
      optionList.addAll(Arrays.asList("-classpath", LocalJspC.classpath));
      optionList.addAll(Arrays.asList("-encoding", ctxt.getOptions().getJavaEncoding()));
      Iterable<? extends JavaFileObject> compilationUnits =
          fileManager.getJavaFileObjectsFromFiles(files);
      compiler.getTask(null, fileManager, null, optionList, null, compilationUnits).call();
    }
  }
}
