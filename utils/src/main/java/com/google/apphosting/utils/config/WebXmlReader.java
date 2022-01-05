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

import com.google.apphosting.utils.config.WebXml.SecurityConstraint;
import com.google.common.collect.ImmutableList;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.Text;

/**
 * This reads {@code web.xml}.
 *
 *
 */
public class WebXmlReader extends AbstractConfigXmlReader<WebXml> {

  // N.B.: this class is not currently used in, and
  // therefore has not been tested in, the runtime.  Before adding a
  // dependency on this code from the runtime please ensure that there
  // is no possibility for external entity references or other
  // dependencies that may cause it to fail when running under the
  // restricted environment

  // This should be kept in sync with our webdefault.xml files.
  private static final String[] DEFAULT_WELCOME_FILES =
      new String[] {
        "index.html", "index.jsp",
      };

  public static final String DEFAULT_RELATIVE_FILENAME = "WEB-INF/web.xml";

  private final String relativeFilename;

  private String servletVersion;

  /**
   * Creates a reader for web.xml.
   *
   * @param appDir The directory in which the config file resides.
   * @param relativeFilename The path to the config file, relative to
   * {@code appDir}.
   */
  public WebXmlReader(String appDir, String relativeFilename) {
    super(appDir, true);
    this.relativeFilename = relativeFilename;
  }

  /**
   * Creates a reader for web.xml.
   *
   * @param appDir The directory in which the config file resides.
   * @param relativeFilename The path to the config file, relative to {@code appDir}.
   * @param isRequired if the web.xml file is mandatory or not.
   */
  public WebXmlReader(String appDir, String relativeFilename, boolean isRequired) {
    super(appDir, isRequired);
    this.relativeFilename = relativeFilename;
  }

  /**
   * Creates a reader for web.xml.
   *
   * @param appDir The directory in which the web.xml config file resides. The path to the config
   *     file relative to the directory is assumed to be {@link #DEFAULT_RELATIVE_FILENAME}.
   */
  public WebXmlReader(String appDir, boolean isRequired) {
    this(appDir, DEFAULT_RELATIVE_FILENAME, isRequired);
  }

  /**
   * Creates a reader for web.xml.
   *
   * @param appDir The directory in which the web.xml config file resides.  The
   * path to the config file relative to the directory is assumed to be
   * {@link #DEFAULT_RELATIVE_FILENAME}.
   */
  public WebXmlReader(String appDir) {
    this(appDir, DEFAULT_RELATIVE_FILENAME, true);
  }

  @Override
  protected String getRelativeFilename() {
    return relativeFilename;
  }

  /**
   * Parses the config file.
   * @return A {@link WebXml} object representing the parsed configuration.
   */
  public WebXml readWebXml() {
    WebXml value = readConfigXml();
    if (value == null) {
      // No web.xml file, so we create a default 3.1 model:
      value = new WebXml();
      value.setServletVersion("3.1");
      for (String welcomeFile : DEFAULT_WELCOME_FILES) {
        value.addWelcomeFile(welcomeFile);
      }
    }
    return value;
  }

  /**
   * @return which servlet API version is defined in the web.xml (2.5 or 3.1 or null if not
   *     defined).
   */
  public String getServletVersion() {
    return servletVersion;
  }

  @Override
  protected WebXml processXml(InputStream is) {
    final WebXml webXml = new WebXml();
    Element root = XmlUtils.parseXml(is).getDocumentElement();
    parseWebXml(root, webXml);

    if (webXml.getWelcomeFiles().isEmpty()) {
      for (String welcomeFile : DEFAULT_WELCOME_FILES) {
        webXml.addWelcomeFile(welcomeFile);
      }
    }

    return webXml;
  }

  private void parseWebXml(Element webAppElement, WebXml webXml) {
    if (!webAppElement.getTagName().equals("web-app")) {
      throw appEngineConfigException(
          "Root element should be <web-app>, not <" + sanitizeTag(webAppElement.getTagName())
              + ">");
    }
    servletVersion = XmlUtils.getAttributeOrNull(webAppElement, "version");
    webXml.setServletVersion(servletVersion);
    for (Element childElement : XmlUtils.getChildren(webAppElement)) {
      switch (childElement.getTagName()) {
        case "servlet-mapping":
        case "filter-mapping":
          parseServletOrFilterMapping(childElement, webXml);
          break;
        case "filter":
          parseFilter(childElement, webXml);
          break;
        case "security-constraint":
          parseSecurityConstraint(childElement, webXml);
          break;
        case "mime-mapping":
          parseMimeMapping(childElement, webXml);
          break;
        case "error-page":
          parseErrorPage(childElement, webXml);
          break;
        case "welcome-file-list":
          parseWelcomeFileList(childElement, webXml);
          break;
        case "context-param":
          parseContextParam(childElement, webXml);
          break;
        default:
      }
    }
  }

  private boolean isServletVersionThreeOrHigher() {
    float servletVersionFloat = 0;
    try {
      servletVersionFloat = Float.parseFloat(servletVersion);
    } catch (Exception e) {
      // Ignore. Default to not servlet 3 or 4.
      return false;
    }
    return servletVersionFloat >= 3.0;
  }

  private void parseServletOrFilterMapping(Element mappingElement, WebXml webXml) {
    for (Element patternElement : XmlUtils.getChildren(mappingElement, "url-pattern")) {
      String id = XmlUtils.getAttributeOrNull(patternElement, "id");
      // Empty mapping is allowed for Servlet 3.x and above, whenever 4.x is out...
      if (isServletVersionThreeOrHigher() && XmlUtils.getText(patternElement).isEmpty()) {
        webXml.addServletPattern("", id);
      } else {
        webXml.addServletPattern(stringContents(patternElement), id);
      }
    }
  }

  private void parseSecurityConstraint(Element constraintElement, WebXml webXml) {
    WebXml.SecurityConstraint securityConstraint = webXml.addSecurityConstraint();
    for (Element child : XmlUtils.getChildren(constraintElement)) {
      switch (child.getTagName()) {
        case "web-resource-collection":
          for (Element patternElement : XmlUtils.getChildren(child, "url-pattern")) {
            securityConstraint.addUrlPattern(stringContents(patternElement));
          }
          break;
        case "auth-constraint":
          Optional<String> role = getOptionalChildContents(child, "role-name");
          if (role.isPresent()) {
            securityConstraint.setRequiredRole(parseRequiredRole(role.get()));
          }
          break;
        case "user-data-constraint":
          Optional<String> guarantee = getOptionalChildContents(child, "transport-guarantee");
          if (guarantee.isPresent()) {
            securityConstraint.setTransportGuarantee(parseTransportGuarantee(guarantee.get()));
          }
          break;
        default:
      }
    }
  }

  private void parseMimeMapping(Element mimeMappingElement, WebXml webXml) {
    String extension = getRequiredChildContents(mimeMappingElement, "extension");
    String mimeType = getRequiredChildContents(mimeMappingElement, "mime-type");
    webXml.addMimeMapping(extension, mimeType);
  }
  
  private void parseContextParam(Element contextParamElement, WebXml webXml) {
    if (!isServletVersionThreeOrHigher()) {
      // Ignore context-params for 2.5 because we don't want to break java7
      // customers in case web.xml was not validated earlier.
      return;
    }
    String name = getRequiredChildContents(contextParamElement, "param-name");
    String value = getRequiredChildContentsWithCData(contextParamElement, "param-value");
    webXml.addContextParam(name, value);
  }

  private void parseFilter(Element childElement, WebXml webXml) {
    if (!isServletVersionThreeOrHigher()) {
      // Ignore filters for 2.5 because we don't want to break java7
      // customers in case web.xml was not validated earlier.
      return;
    }
    Optional<String> filterClass = getOptionalChildContents(childElement, "filter-class");
    if (filterClass.isPresent()) {
      webXml.addFilterClass(filterClass.get());
    }
  }

  private void parseErrorPage(Element errorPageElement, WebXml webXml) {
    Optional<String> errorCode = getOptionalChildContents(errorPageElement, "error-code");
    if ("404".equals(errorCode.orElse(null))) {
      webXml.setFallThroughToRuntime(true);
    }
  }

  private void parseWelcomeFileList(Element welcomesElement, WebXml webXml) {
    for (String welcomeFile : getAllChildContents(welcomesElement, "welcome-file")) {
      webXml.addWelcomeFile(welcomeFile);
    }
  }

  private SecurityConstraint.RequiredRole parseRequiredRole(String role) {
    switch (role) {
      case "*":
        return SecurityConstraint.RequiredRole.ANY_USER;
      case "admin":
        return SecurityConstraint.RequiredRole.ADMIN;
      default:
        throw appEngineConfigException("Unknown role-name: must be '*' or 'admin'");
    }
  }

  private SecurityConstraint.TransportGuarantee parseTransportGuarantee(String transportGuarantee) {
    if ("NONE".equalsIgnoreCase(transportGuarantee)) {
      return SecurityConstraint.TransportGuarantee.NONE;
    } else if ("INTEGRAL".equalsIgnoreCase(transportGuarantee)) {
      return SecurityConstraint.TransportGuarantee.INTEGRAL;
    } else if ("CONFIDENTIAL".equalsIgnoreCase(transportGuarantee)) {
      return SecurityConstraint.TransportGuarantee.CONFIDENTIAL;
    } else {
      throw appEngineConfigException(
          "Unknown transport-guarantee: must be NONE, INTEGRAL, or CONFIDENTIAL.");
    }
  }

  private ImmutableList<String> getAllChildContents(Element parent, String childTagName) {
    ImmutableList.Builder<String> children = ImmutableList.builder();
    List<Element> childElements = XmlUtils.getChildren(parent, childTagName);
    for (Element childElement : childElements) {
      children.add(stringContents(childElement));
    }
    return children.build();
  }

  private ImmutableList<String> getAllChildContentsWithCData(Element parent, String childTagName) {
    ImmutableList.Builder<String> children = ImmutableList.builder();
    for (Element element : XmlUtils.getChildren(parent, "param-value")) {
      String value = element.getTextContent();
      if (value == null && element.hasChildNodes()) {
        // We try at most one more level down, where we expect CDATASection.
        Node child = element.getFirstChild();
        if (child instanceof Text) {
          value = ((Text) child).getTextContent();
        }
      }
      if (value == null) {
        throw appEngineConfigException("param-value is missing");
      }
      children.add(value);
    }
    return children.build();
  }

  private Optional<String> getOptionalChildContents(Element parent, String childTagName) {
      List<String> children = getAllChildContents(parent, childTagName);
      switch (children.size()) {
        case 0:
        return Optional.empty();
        case 1:
          return Optional.of(children.get(0));
        default:
          throw appEngineConfigException(
              "<" + sanitizeTag(parent.getTagName()) + "> has more than one "
                  + "<" + sanitizeTag(childTagName)  + ">: " + children);
      }
  }

  private Optional<String> getOptionalChildContentsWithCData(Element parent, String childTagName) {
    List<String> children = getAllChildContentsWithCData(parent, childTagName);
    switch (children.size()) {
      case 0:
        return Optional.empty();
      case 1:
        return Optional.of(children.get(0));
      default:
        throw appEngineConfigException(
            "<"
                + sanitizeTag(parent.getTagName())
                + "> has more than one "
                + "<"
                + sanitizeTag(childTagName)
                + ">: "
                + children);
    }
  }

  private String getRequiredChildContents(Element parent, String childTagName) {
    Optional<String> contents = getOptionalChildContents(parent, childTagName);
    if (!contents.isPresent()) {
      throw appEngineConfigException(
          "<" + sanitizeTag(parent.getTagName()) + "> is missing "
              + "<" + sanitizeTag(childTagName) + ">");
    }
    return contents.get();
  }

  private String getRequiredChildContentsWithCData(Element parent, String childTagName) {
    Optional<String> contents = getOptionalChildContentsWithCData(parent, childTagName);
    if (!contents.isPresent()) {
      throw appEngineConfigException(
          "<"
              + sanitizeTag(parent.getTagName())
              + "> is missing "
              + "<"
              + sanitizeTag(childTagName)
              + ">");
    }
    return contents.get();
  }

  private AppEngineConfigException appEngineConfigException(String message) {
    return new AppEngineConfigException(getFilename() + ": " + message);
  }
}
