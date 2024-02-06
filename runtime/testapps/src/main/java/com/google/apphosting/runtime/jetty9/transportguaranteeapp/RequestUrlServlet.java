package com.google.apphosting.runtime.jetty9.transportguaranteeapp;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

@SuppressWarnings("serial")
public class RequestUrlServlet extends HttpServlet
{
    @Override
    protected void doGet(HttpServletRequest request,
                         HttpServletResponse response) throws IOException
    {
        response.setContentType("text/plain");
        response.setStatus(HttpServletResponse.SC_OK);
        PrintWriter out = response.getWriter();
        out.println("requestURL=" + request.getRequestURL().toString());
        out.println("isSecure=" +  request.isSecure());
    }
}
