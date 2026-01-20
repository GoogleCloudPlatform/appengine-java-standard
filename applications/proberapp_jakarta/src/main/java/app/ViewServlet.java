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
package app;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Enumeration;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Returns a dump of many server configurations to debug.
 *
 */
@WebServlet(
    name = "viewer",
    urlPatterns = {"/view"})
public class ViewServlet extends HttpServlet {

  /**
   * Processes requests for both HTTP <code>GET</code> and <code>POST</code> methods.
   *
   * @param request servlet request
   * @param response servlet response
   * @throws ServletException if a servlet-specific error occurs
   * @throws IOException if an I/O error occurs
   */
  protected void processRequest(HttpServletRequest request, HttpServletResponse response)
      throws Exception {
    response.setContentType("text/html;charset=UTF-8");
    try (PrintWriter out = response.getWriter()) {
      out.println("<!DOCTYPE html>");
      out.println("<html>");
      out.println("<head>");
      out.println("<title>Servlet System Viewer.</title>");
      out.println("</head>");
      out.println("<body>");
      out.println("<h1>System.getProperties()</h1>");
      out.println("<ul>");
      for (Object element : System.getProperties().keySet()) {
        String key = (String) element;
        String value = System.getProperty(key).replace("+", " ");
        out.println("<li>" + key + "=" + value);
      }
      out.println("</ul>");

      out.println("<h1>System.getenv()</h1>");
      out.println("<ul>");
      Map<String, String> variables = System.getenv();

      variables
          .entrySet()
          .forEach(
              (entry) -> {
                String name = entry.getKey();
                String value = entry.getValue();
                out.println("<li>" + name + "=" + value);
              });
      out.println("</ul>");

      out.println("<h1>Headers</h1>");
      out.println("<ul>");
      for (Enumeration<String> e = request.getHeaderNames(); e.hasMoreElements(); ) {
        String key = e.nextElement();
        out.println("<li>" + key + "=" + request.getHeader(key));
      }
      out.println("</ul>");

      out.println("<h1>Cookies</h1>");
      out.println("<ul>");
      Cookie[] cookies = request.getCookies();
      if (cookies != null && cookies.length != 0) {
        for (Cookie co : cookies) {
          out.println("<li>" + co.getName() + "=" + co.getValue());
        }
      }
      out.println("</ul>");

      out.println("</body>");
      out.println("</html>");
    }
  }

  // <editor-fold defaultstate="collapsed" desc="HttpServlet methods. Click on the + sign on the
  // left to edit the code.">
  /**
   * Handles the HTTP <code>GET</code> method.
   *
   * @param request servlet request
   * @param response servlet response
   * @throws ServletException if a servlet-specific error occurs
   * @throws IOException if an I/O error occurs
   */
  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    try {
      processRequest(request, response);
    } catch (Exception ex) {
      Logger.getLogger(ViewServlet.class.getName()).log(Level.SEVERE, null, ex);
    }
  }

  /**
   * Handles the HTTP <code>POST</code> method.
   *
   * @param request servlet request
   * @param response servlet response
   * @throws ServletException if a servlet-specific error occurs
   * @throws IOException if an I/O error occurs
   */
  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    try {
      processRequest(request, response);
    } catch (Exception ex) {
      Logger.getLogger(ViewServlet.class.getName()).log(Level.SEVERE, null, ex);
    }
  }

  /**
   * Returns a short description of the servlet.
   *
   * @return a String containing servlet description
   */
  @Override
  public String getServletInfo() {
    return "System Viewer";
  } // </editor-fold>
}
