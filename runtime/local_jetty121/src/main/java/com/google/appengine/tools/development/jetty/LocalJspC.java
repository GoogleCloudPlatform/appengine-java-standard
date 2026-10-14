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

package com.google.appengine.tools.development.jetty;

import org.apache.jasper.JasperException;
import org.apache.jasper.JspC;
import org.apache.jasper.compiler.Localizer;

/**
 * Simple wrapper around the Apache JSP compiler that uses Jasper's built-in Eclipse JDT compiler
 * ({@link org.apache.jasper.compiler.JDTCompiler}) so that a JDK is not required to compile JSP or
 * tag files.
 */
public class LocalJspC {

  public static void main(String[] args) throws JasperException {
    if (args.length == 0) {
      System.out.println(Localizer.getMessage("jspc.usage"));
    } else {
      JspC jspc = new JspC();
      jspc.setArgs(args);
      jspc.setAddWebXmlMappings(true);
      jspc.execute();
    }
  }
}
