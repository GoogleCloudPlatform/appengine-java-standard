package com.google.apphosting.runtime.jetty9.senderrorapp;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class SendErrorServletEE8 extends HttpServlet {
  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    int errorCode;
    if (req.getParameter("errorCode") == null) {
      errorCode = 0;
    } else {
      try {
        errorCode = Integer.parseInt(req.getParameter("errorCode"));
      } catch (NumberFormatException e) {
        errorCode = -1;
      }
    }
    switch (errorCode) {
      case -1:
        throw new RuntimeException("try to handle me");
      case 0:
        req.getRequestDispatcher("/hello.html").forward(req, resp);
        break;
      default:
        resp.sendError(errorCode);
        break;
    }
  }
}