package com.google.apphosting.runtime.jetty9.senderrorapp;

import java.io.IOException;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/send-error")
public class SendErrorServlet extends HttpServlet {
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