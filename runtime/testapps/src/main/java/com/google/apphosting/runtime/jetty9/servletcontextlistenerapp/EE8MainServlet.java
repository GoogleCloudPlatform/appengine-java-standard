package com.google.apphosting.runtime.jetty9.servletcontextlistenerapp;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class EE8MainServlet extends HttpServlet {
  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    Object listenerAttribute = req.getServletContext().getAttribute("ServletContextListenerAttribute");
    resp.getWriter().write("ServletContextListenerAttribute: " + listenerAttribute);
  }
}
