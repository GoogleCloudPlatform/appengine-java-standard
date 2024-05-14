package com.google.apphosting.runtime.jetty9.servletcontextlistenerapp;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public class EE10MainServlet  extends HttpServlet {
  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    Object listenerAttribute = req.getServletContext().getAttribute("ServletContextListenerAttribute");
    resp.getWriter().write("ServletContextListenerAttribute: " + listenerAttribute);
  }
}
