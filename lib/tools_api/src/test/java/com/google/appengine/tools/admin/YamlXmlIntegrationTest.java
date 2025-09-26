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

package com.google.appengine.tools.admin;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.apphosting.utils.config.AppEngineWebXml;
import com.google.apphosting.utils.config.AppEngineWebXml.ApiConfig;
import com.google.apphosting.utils.config.AppYaml;
import com.google.apphosting.utils.config.BackendsXml;
import com.google.apphosting.utils.config.WebXml;
import com.google.apphosting.utils.config.WebXmlReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.HashSet;
import java.util.Set;
import junit.framework.TestCase;

/**
 * An integration test that tests the relationship between {@link AppYaml} and {@link
 * AppYamlTranslator}. The first class takes an app.yaml in the Java App Engine style and produces a
 * web.xml. The second class takes a web.xml and produces an app.yaml in the Python App Engine
 * style. We test that the output yaml expresses the same semantics as the input yaml.
 *
 * <p>As of this writing we are primarily concerned with the translation of the security-related
 * elements, "secure:" and "login:".
 *
 */
public class YamlXmlIntegrationTest extends TestCase {

  private static final String YAML_INPUT_PREFIX = "application: app1\n" + "handlers:\n";

  private static final String EXPECTED_YAML_OUTPUT_PREFIX =
      "application: 'app1'\n"
          + "runtime: java7\n"
          + "version: 'ver1'\n"
          + "auto_id_policy: default\n"
          + "handlers:\n";

  private AppEngineWebXml appEngineWebXml;
  private BackendsXml backendsXml;
  private Set<String> staticFiles;
  private ApiConfig apiConfig;

  @Override
  public void setUp() throws Exception {
    appEngineWebXml = new AppEngineWebXml();
    appEngineWebXml.setAppId("app1");
    appEngineWebXml.setMajorVersionId("ver1");
    appEngineWebXml.setPrecompilationEnabled(false);
    backendsXml = new BackendsXml();
    staticFiles = new HashSet<String>();
  }

  /** Tests the case that only the admin pages require admin login. */
  public void testAdminLoginOnly() {
    String yamlInput = YAML_INPUT_PREFIX + " - url: /admin/*\n" + "   login: admin\n";

    String expectedYamlOutput =
        EXPECTED_YAML_OUTPUT_PREFIX
            + "- url: /admin/.*/\n"
            + "  script: unused\n"
            + "  login: admin\n"
            + "  secure: optional\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /admin/\n"
            + "  script: unused\n"
            + "  login: admin\n"
            + "  secure: optional\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n";

    doYamlXmlIntegrationTest(yamlInput, expectedYamlOutput);
  }

  /** Tests the case that all pages require login and the admin pages require admin login */
  public void testLoginRequiredOnly() {
    String yamlInput =
        YAML_INPUT_PREFIX
            + " - url: /*\n"
            + "   login: required\n"
            + " - url: /admin/*\n"
            + "   login: admin\n";

    String expectedYamlOutput =
        EXPECTED_YAML_OUTPUT_PREFIX
            + "- url: /admin/.*/\n"
            + "  script: unused\n"
            + "  login: admin\n"
            + "  secure: optional\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: required\n"
            + "  secure: optional\n"
            + "- url: /admin/\n"
            + "  script: unused\n"
            + "  login: admin\n"
            + "  secure: optional\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: required\n"
            + "  secure: optional\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: required\n"
            + "  secure: optional\n";

    doYamlXmlIntegrationTest(yamlInput, expectedYamlOutput);
  }

  /** Tests the case that https is required on all pages, but no login */
  public void testSSLOnly() {
    String yamlInput = YAML_INPUT_PREFIX + " - url: /*\n" + "   secure: always\n";

    String expectedYamlOutput =
        EXPECTED_YAML_OUTPUT_PREFIX
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: always\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: always\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: always\n";

    doYamlXmlIntegrationTest(yamlInput, expectedYamlOutput);
  }

  /**
   * Tests the case that login is required on all pages, admin login on admin pages, and https is
   * required on all pages.
   */
  public void testLoginAndSSL() {
    String yamlInput =
        YAML_INPUT_PREFIX
            + " - url: /*\n"
            + "   login: required\n"
            + "   secure: always\n"
            + " - url: /admin/*\n"
            + "   login: admin\n"
            + "   secure: always\n";

    String expectedYamlOutput =
        EXPECTED_YAML_OUTPUT_PREFIX
            + "- url: /admin/.*/\n"
            + "  script: unused\n"
            + "  login: admin\n"
            + "  secure: always\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: required\n"
            + "  secure: always\n"
            + "- url: /admin/\n"
            + "  script: unused\n"
            + "  login: admin\n"
            + "  secure: always\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: required\n"
            + "  secure: always\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: required\n"
            + "  secure: always\n";

    doYamlXmlIntegrationTest(yamlInput, expectedYamlOutput);
  }

  private void doYamlXmlIntegrationTest(String yamlIn, String expectedYamlOut) {
    // Use the AppYaml class to parse the input yaml string
    AppYaml yaml = AppYaml.parse(String.format(yamlIn, "app1"));
    // Use the AppYaml class to generate a WebXml
    WebXml xml = getWebXml(yaml);
    // Use AppYamlTranslator to generate an app.yaml from the WebXml
    // and check the output.
    AppYamlTranslator translator = createTranslator(xml);
    assertEquals(expectedYamlOut, translator.getYaml());
  }

  private AppYamlTranslator createTranslator(WebXml webXml) {
    return new AppYamlTranslator(
        appEngineWebXml, webXml, backendsXml, staticFiles, apiConfig, "java7");
  }

  private WebXml getWebXml(AppYaml yaml) {
    return new WebXmlStringReader(getWebXmlContents(yaml)).readWebXml();
  }

  private String getWebXmlContents(AppYaml yaml) {
    StringWriter out = new StringWriter();
    yaml.generateWebXml(out);
    return out.toString();
  }

  private class WebXmlStringReader extends WebXmlReader {
    private String xml;

    public WebXmlStringReader(String xml) {
      super(".");
      this.xml = xml;
    }

    @Override
    protected InputStream getInputStream() {
      return new ByteArrayInputStream(xml.getBytes(UTF_8));
    }
  }
}
