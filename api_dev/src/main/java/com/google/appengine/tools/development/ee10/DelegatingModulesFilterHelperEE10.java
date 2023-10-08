/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.google.appengine.tools.development.ee10;

import com.google.appengine.tools.development.BackendServers;
import com.google.appengine.tools.development.DelegatingModulesFilterHelper;
import com.google.appengine.tools.development.Modules;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 *
 * @author ludo
 */
public class DelegatingModulesFilterHelperEE10  extends DelegatingModulesFilterHelper {
    
  public DelegatingModulesFilterHelperEE10(BackendServers backendServers, Modules modules) {
    super(backendServers, modules);
  }
  public void forwardToInstance(String moduleOrBackendName, int instance,
      HttpServletRequest hrequest, HttpServletResponse response)
      throws IOException, ServletException {
      if (isBackend(moduleOrBackendName)) {
        ((BackendServersEE10)backendServers).forwardToServer(moduleOrBackendName, instance, hrequest, response);
     } else {
       ((ModulesEE10)modules).forwardToInstance(moduleOrBackendName, instance, hrequest, response);
     }
  }   
}
