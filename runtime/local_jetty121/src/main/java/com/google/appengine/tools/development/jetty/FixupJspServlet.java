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

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.jasper.servlet.JspServlet;
import org.apache.tomcat.InstanceManager;

/** {@code FixupJspServlet} adds some logic to work around bugs in the Jasper {@link JspServlet}. */
public class FixupJspServlet extends JspServlet {

  /**
   * The request attribute that contains the name of the JSP file, when the request path doesn't
   * refer directly to the JSP file (for example, it's instead a servlet mapping).
   */
  private static final String JASPER_JSP_FILE = "org.apache.catalina.jsp_file";

  private static final String WEB31XML =
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
          + "<web-app version=\"3.1\" xmlns=\"http://java.sun.com/xml/ns/javaee\""
          + " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
          + "xsi:schemaLocation=\"http://java.sun.com/xml/ns/javaee "
          + "http://java.sun.com/xml/ns/javaee/web-app_3_1.xsd\">"
          + "</web-app>";

  @Override
  public void init(ServletConfig config) throws ServletException {
    config
        .getServletContext()
        .setAttribute(InstanceManager.class.getName(), new InstanceManagerImpl());
    config.getServletContext().setAttribute("org.apache.tomcat.util.scan.MergedWebXml", WEB31XML);
    super.init(config);
  }

  @Override
  public void service(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    fixupJspFileAttribute(request);
    super.service(request, response);
  }

  private static class InstanceManagerImpl implements InstanceManager {
    @Override
    public Object newInstance(String className)
        throws IllegalAccessException,
            InvocationTargetException,
            InstantiationException,
            ClassNotFoundException {
      return newInstance(className, this.getClass().getClassLoader());
    }

    @Override
    public Object newInstance(String fqcn, ClassLoader classLoader)
        throws IllegalAccessException,
            InvocationTargetException,
            InstantiationException,
            ClassNotFoundException {
      Class<?> cl = classLoader.loadClass(fqcn);
      return newInstance(cl);
    }

    @Override
    @SuppressWarnings("ClassNewInstance")
    // We would prefer clazz.getConstructor().newInstance() here, but that throws
    // NoSuchMethodException. It would also lead to a change in behaviour, since an exception
    // thrown by the constructor would be wrapped in InvocationTargetException rather than being
    // propagated from newInstance(). Although that's funky, and the reason for preferring
    // getConstructor().newInstance(), we don't know if something is relying on the current
    // behaviour.
    public Object newInstance(Class<?> clazz)
        throws IllegalAccessException, InvocationTargetException, InstantiationException {
      return clazz.newInstance();
    }

    @Override
    public void newInstance(Object o) {}

    @Override
    public void destroyInstance(Object o)
        throws IllegalAccessException, InvocationTargetException {}
  }

  // NB This method is here, because there appears to be
  // a bug in either Jetty or Jasper where <jsp-file> entries in web.xml
  // don't get handled correctly. This interaction between Jetty and Jasper
  // appears to have always been broken, irrespective of App Engine
  // integration.
  //
  // Jetty hands the name of the JSP file to Jasper (via a request attribute)
  // without a leading slash. This seems to cause all sorts of problems.
  //   - Jasper turns around and asks Jetty to lookup that same file
  // (using ServletContext.getResourceAsStream). Jetty rejects, out-of-hand,
  // any resource requests that don't start with a leading slash.
  //   - Jasper seems to plain blow up on jsp paths that don't have a leading
  // slash.
  //
  // If we enforce a leading slash, Jetty and Jasper seem to co-operate
  // correctly.
  private void fixupJspFileAttribute(HttpServletRequest request) {
    String jspFile = (String) request.getAttribute(JASPER_JSP_FILE);

    if (jspFile != null) {
      if (jspFile.length() == 0) {
        jspFile = "/";
      } else if (jspFile.charAt(0) != '/') {
        jspFile = "/" + jspFile;
      }
      request.setAttribute(JASPER_JSP_FILE, jspFile);
    }
  }
}
