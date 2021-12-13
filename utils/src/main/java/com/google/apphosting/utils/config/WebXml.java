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

package com.google.apphosting.utils.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 *
 *
 */
public class WebXml {
  private final List<String> servletPatterns;
  private final List<SecurityConstraint> securityConstraints;
  private final List<String> welcomeFiles;
  private final Map<String, String> mimeMappings;
  private final Map<String, String> patternToId;
  private final Map<String, String> contextParams;
  private boolean fallThroughToRuntime;
  // version can be 2.5, 3.0, 3.1, null or possible anything, but we do care about 3.0 or 3.1.
  private String servletVersion;
  private final List<String> filterClasses;

  public WebXml() {
    servletPatterns = new ArrayList<String>();
    filterClasses = new ArrayList<>();
    securityConstraints = new ArrayList<SecurityConstraint>();
    welcomeFiles = new ArrayList<String>();
    mimeMappings = new HashMap<String, String>();
    patternToId = new HashMap<String, String>();
    contextParams = new HashMap<String, String>();
  }

  /**
   * Returns true if {@code url} matches one of the servlets or
   * servlet filters listed in this web.xml.
   */
  public boolean matches(String url) {
    // URI patterns in web.xml are pretty simple.  Only one wildcard
    // is allowed, and it must be at either the beginning or the end.
    for (String pattern : servletPatterns) {
      if (pattern.length() == 0) {
        continue;
      }
      if (pattern.startsWith("*") && url.endsWith(pattern.substring(1))) {
        return true;
      } else if (pattern.endsWith("*") &&
                 url.startsWith(pattern.substring(0, pattern.length() - 1))) {
        return true;
      } else if (url.equals(pattern)) {
        return true;
      }
    }
    return false;
  }

  /**
   * @return which servlet API version is defined in the web.xml (2.5 or 3.1 or null if not
   *     defined).
   */
  public String getServletVersion() {
    return servletVersion;
  }

  public void setServletVersion(String servletVersion) {
    this.servletVersion = servletVersion;
  }

  public String getHandlerIdForPattern(String pattern) {
    return patternToId.get(pattern);
  }

  public void addServletPattern(String urlPattern, @Nullable String id) {
    YamlUtils.validateUrl(urlPattern);
    servletPatterns.add(urlPattern);
    if (id != null) {
      patternToId.put(urlPattern, id);
    }
  }

  public List<String> getServletPatterns() {
    return servletPatterns;
  }

  public List<String> getFilterClasses() {
    return filterClasses;
  }

  public void addFilterClass(String name) {
    filterClasses.add(name);
  }

  public List<SecurityConstraint> getSecurityConstraints() {
    return securityConstraints;
  }

  public SecurityConstraint addSecurityConstraint() {
    SecurityConstraint context = new SecurityConstraint();
    securityConstraints.add(context);
    return context;
  }

  public void addWelcomeFile(String welcomeFile) {
    welcomeFiles.add(welcomeFile);
  }

  public List<String> getWelcomeFiles() {
    return welcomeFiles;
  }

  public void addMimeMapping(String extension, String mimeType) {
    mimeMappings.put(extension, mimeType);
  }

  public Map<String, String> getMimeMappings() {
    return mimeMappings;
  }

  public void addContextParam(String name, String value) {
    if (name != null) {
      contextParams.put(name, value);
    }
  }

  public Map<String, String> getContextParams() {
    return contextParams;
  }
  public String getMimeTypeForPath(String path) {
    int dot = path.lastIndexOf(".");
    if (dot != -1) {
      // N.B.(schwardo): It seems like this comparison should be
      // case-insensitive, but the servlet spec is strangely silent on
      // this.  Tomcat appears to be case-sensitive so we'll do that
      // for now.
      return mimeMappings.get(path.substring(dot + 1));
    } else {
      return null;
    }
  }

  public boolean getFallThroughToRuntime() {
    return fallThroughToRuntime;
  }

  public void setFallThroughToRuntime(boolean fallThroughToRuntime) {
    this.fallThroughToRuntime = fallThroughToRuntime;
  }

  /**
   * Performs some optional validation on this {@code WebXml}.
   *
   * @throws AppEngineConfigException If any errors are found.
   */
  public void validate() {
    for (String welcomeFile : welcomeFiles) {
      if (welcomeFile.startsWith("/")) {
        throw new AppEngineConfigException("Welcome files must be relative paths: " + welcomeFile);
      }
    }
  }

  /**
   * Information about a security context, requiring SSL and/or authentication.
   * Effectively, this is a tuple of { urlpatterns..., ssl-guarantee, auth-role }.
   */
  public static class SecurityConstraint {
    public enum RequiredRole { NONE, ANY_USER, ADMIN }
    public enum TransportGuarantee { NONE, INTEGRAL, CONFIDENTIAL }

    private final List<String> patterns;
    private TransportGuarantee transportGuarantee = TransportGuarantee.NONE;
    private RequiredRole requiredRole = RequiredRole.NONE;

    private SecurityConstraint() {
      patterns = new ArrayList<String>();
    }

    public List<String> getUrlPatterns() {
      return patterns;
    }

    public void addUrlPattern(String pattern) {
      patterns.add(pattern);
    }

    public TransportGuarantee getTransportGuarantee() {
      return transportGuarantee;
    }

    public void setTransportGuarantee(TransportGuarantee transportGuarantee) {
      this.transportGuarantee = transportGuarantee;
    }

    public RequiredRole getRequiredRole() {
      return requiredRole;
    }

    public void setRequiredRole(RequiredRole requiredRole) {
      this.requiredRole = requiredRole;
    }
  }
}
