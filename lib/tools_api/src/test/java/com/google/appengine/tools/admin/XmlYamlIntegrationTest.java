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
import com.google.apphosting.utils.config.AppEngineWebXmlReader;
import com.google.apphosting.utils.config.BackendsXml;
import com.google.apphosting.utils.config.WebXml;
import com.google.apphosting.utils.config.WebXmlReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import junit.framework.TestCase;

/**
 * An integration test that tests the relationship between {@link WebXmlReader}, {@link
 * AppEngineWebXmlReader} and {@link AppYamlTranslator}. The first two classes read XML text
 * corresponding to web.xml and appengine-web.xml and produce {@link WebXml} and {@link
 * AppEngineWebXml} objects respectively. The second class takes these objects and produces an
 * app.yaml in the Python App Engine style. We test that the output yaml expresses the same
 * semantics as the input XML.
 *
 * <p>This test is similar to {@code AppYamlTranslatorTest} except that that test builds {@link
 * WebXml} and {@link AppEngineWebXml} object programmatically instead of by parsing text.
 *
 * <p>In this test we specify XML text to read and expected Yaml text to write and test that we
 * generate the expected Yaml text.
 *
 */
public class XmlYamlIntegrationTest extends TestCase {

  // @formatter:off
  private static final String XML_HEADER = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n";

  private static final String WEB_XML_PREFIX =
      XML_HEADER
          + "\n"
          + "<web-app  version=\"2.5\">\n"
          + "  <servlet>\n"
          + "    <servlet-name>Servlet1</servlet-name>\n"
          + "    <servlet-class>com.test.Servlet1</servlet-class>\n"
          + "  </servlet>";
  private static final String WEB_XML_SUFFIX = "</web-app>";

  private static final String AE_WEB_XML_PREFIX =
      XML_HEADER
          + "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
          + "  <application>my_app_id</application>\n"
          + "  <version>1</version>\n";
  private static final String AE_WEB_XML_SUFFIX = "</appengine-web-app>";

  private static final String APP_YAML_PREFIX =
      "application: 'my_app_id'\n"
          + "runtime: java8\n"
          + "version: '1'\n"
          + "inbound_services:\n"
          + "- warmup\n"
          + "auto_id_policy: default\n"
          + "handlers:\n";
  private static final String APP_YAML_SUFFIX =
      "- url: /.*/\n"
          + "  script: unused\n"
          + "  login: optional\n"
          + "  secure: optional\n"
          + "- url: /_ah/.*\n"
          + "  script: unused\n"
          + "  login: optional\n"
          + "  secure: optional\n";
  // @formatter:on

  private static String buildWebXml(String content) {
    return WEB_XML_PREFIX + content + WEB_XML_SUFFIX;
  }

  private static String buildAEWebXml(String content) {
    return AE_WEB_XML_PREFIX + content + AE_WEB_XML_SUFFIX;
  }

  private static String buildYaml(String content, boolean useYamlSuffix) {
    String suffix = (useYamlSuffix ? APP_YAML_SUFFIX : "");
    return APP_YAML_PREFIX + content + suffix;
  }

  /**
   * Tests that a security constraint with a url pattern of the form {@code /baz/*} will generate
   * yaml containing a regex that also captures the path {@code /baz}.
   *
   * @throws Exception
   */
  public void testSecurityConstraintWildcard() throws Exception {
    // @formatter:off
    String webXmlString =
        "<servlet-mapping>\n"
            + "  <servlet-name>Servlet1</servlet-name>\n"
            + "  <url-pattern>/main</url-pattern>\n"
            + "  <url-pattern>/main/submit</url-pattern>\n"
            + "</servlet-mapping>\n"
            + "<security-constraint>\n"
            + "    <web-resource-collection>\n"
            + "      <web-resource-name>any</web-resource-name>\n"
            + "      <url-pattern>/main/*</url-pattern>\n"
            + "    </web-resource-collection>\n"
            + "    <auth-constraint>\n"
            + "      <role-name>admin</role-name>\n"
            + "    </auth-constraint>\n"
            + "    <user-data-constraint>\n"
            + "      <transport-guarantee>CONFIDENTIAL</transport-guarantee>\n"
            + "    </user-data-constraint>\n"
            + "</security-constraint>";
    String appEngineWebXmlString = "";
    String expectedExtraYaml =
        "- url: /main/.*/\n"
            + "  script: unused\n"
            + "  login: admin\n"
            + "  secure: always\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /main\n"
            + "  script: unused\n"
            + "  login: admin\n"
            + "  secure: always\n"
            + "- url: /main/\n"
            + "  script: unused\n"
            + "  login: admin\n"
            + "  secure: always\n"
            + "- url: /main/submit\n"
            + "  script: unused\n"
            + "  login: admin\n"
            + "  secure: always\n";
    // @formatter:on
    doTest(webXmlString, appEngineWebXmlString, expectedExtraYaml);
  }

  /**
   * Following on the previous test, tests that if {@code /baz} is explicitly specified in a
   * security constraint in addition to {@code /baz/*} then we will generate yaml containing only a
   * single regex that also captures the path {@code /baz}.
   *
   * @throws Exception
   */
  public void testSecurityConstraintWildcard2() throws Exception {
    // @formatter:off
    String webXmlString =
        "<servlet-mapping>\n"
            + "  <servlet-name>Servlet1</servlet-name>\n"
            + "  <url-pattern>/main</url-pattern>\n"
            + "  <url-pattern>/main/submit</url-pattern>\n"
            + "</servlet-mapping>\n"
            + "<security-constraint>\n"
            + "    <web-resource-collection>\n"
            + "      <web-resource-name>any</web-resource-name>\n"
            + "      <url-pattern>/main</url-pattern>\n"
            + "      <url-pattern>/main/*</url-pattern>\n"
            + "    </web-resource-collection>\n"
            + "    <auth-constraint>\n"
            + "      <role-name>admin</role-name>\n"
            + "    </auth-constraint>\n"
            + "    <user-data-constraint>\n"
            + "      <transport-guarantee>CONFIDENTIAL</transport-guarantee>\n"
            + "    </user-data-constraint>\n"
            + "</security-constraint>";
    String appEngineWebXmlString = "";
    String expectedExtraYaml =
        "- url: /main/.*/\n"
            + "  script: unused\n"
            + "  login: admin\n"
            + "  secure: always\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /main\n"
            + "  script: unused\n"
            + "  login: admin\n"
            + "  secure: always\n"
            + "- url: /main/\n"
            + "  script: unused\n"
            + "  login: admin\n"
            + "  secure: always\n"
            + "- url: /main/submit\n"
            + "  script: unused\n"
            + "  login: admin\n"
            + "  secure: always\n";
    // @formatter:on
    doTest(webXmlString, appEngineWebXmlString, expectedExtraYaml);
  }

  /**
   * In this test we supply the glob /* within a security constraint to make sure that is handled
   * correctly.
   *
   * @throws Exception
   */
  public void testSecurityConstraintWildcard3() throws Exception {
    // @formatter:off
    String webXmlString =
        "<servlet-mapping>\n"
            + "  <servlet-name>Servlet1</servlet-name>\n"
            + "  <url-pattern>/main</url-pattern>\n"
            + "  <url-pattern>/main/submit</url-pattern>\n"
            + "</servlet-mapping>\n"
            + "<security-constraint>\n"
            + "    <web-resource-collection>\n"
            + "      <web-resource-name>any</web-resource-name>\n"
            + "      <url-pattern>/*</url-pattern>\n"
            + "    </web-resource-collection>\n"
            + "    <auth-constraint>\n"
            + "      <role-name>admin</role-name>\n"
            + "    </auth-constraint>\n"
            + "    <user-data-constraint>\n"
            + "      <transport-guarantee>CONFIDENTIAL</transport-guarantee>\n"
            + "    </user-data-constraint>\n"
            + "</security-constraint>";
    String appEngineWebXmlString = "";
    String expectedExtraYaml =
        "- url: /\n"
            + "  script: unused\n"
            + "  login: admin\n"
            + "  secure: always\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: admin\n"
            + "  secure: always\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: admin\n"
            + "  secure: always\n"
            + "- url: /main/submit\n"
            + "  script: unused\n"
            + "  login: admin\n"
            + "  secure: always\n"
            + "- url: /main\n"
            + "  script: unused\n"
            + "  login: admin\n"
            + "  secure: always\n";
    // @formatter:on
    doTest(webXmlString, appEngineWebXmlString, expectedExtraYaml, false);
  }

  /**
   * In this test we specify a security constraint but there is no matching servlet path. The
   * security constraint will still have an effect on the generated yaml because of the welcome
   * files.
   *
   * @throws Exception
   */
  public void testSecurityConstraintWithoutMatch() throws Exception {
    // @formatter:off
    String webXmlString =
        "<security-constraint>\n"
            + "    <web-resource-collection>\n"
            + "      <web-resource-name>any</web-resource-name>\n"
            + "      <url-pattern>/main/*</url-pattern>\n"
            + "    </web-resource-collection>\n"
            + "    <user-data-constraint>\n"
            + "      <transport-guarantee>CONFIDENTIAL</transport-guarantee>\n"
            + "    </user-data-constraint>\n"
            + "</security-constraint>";
    String appEngineWebXmlString = "";
    String expectedExtraYaml =
        "- url: /main/.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: always\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /main/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: always\n";
    // @formatter:on
    doTest(webXmlString, appEngineWebXmlString, expectedExtraYaml);
  }

  private void doTest(String webXmlString, String appEngineWebXmlString, String expectedExtraYaml) {
    doTest(webXmlString, appEngineWebXmlString, expectedExtraYaml, true);
  }

  private void doTest(
      String webXmlString,
      String appEngineWebXmlString,
      String expectedExtraYaml,
      boolean useYamlSuffix) {
    WebXml webXml = new WebXmlStringReader(buildWebXml(webXmlString)).readWebXml();
    AppEngineWebXml aeWebXml =
        new AEWebXmlStringReader(buildAEWebXml(appEngineWebXmlString)).readAppEngineWebXml();
    Set<String> staticFiles = new HashSet<String>();
    ApiConfig apiConfig = null;
    BackendsXml backendsXml = new BackendsXml();
    AppYamlTranslator translator =
        new AppYamlTranslator(
            aeWebXml, webXml, backendsXml, staticFiles, apiConfig, "java8");
    assertEquals(buildYaml(expectedExtraYaml, useYamlSuffix), translator.getYaml());
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

  private class AEWebXmlStringReader extends AppEngineWebXmlReader {
    private final String xml;

    public AEWebXmlStringReader(String xml) {
      super(".");
      this.xml = xml;
    }

    @Override
    protected InputStream getInputStream() {
      return new ByteArrayInputStream(xml.getBytes(UTF_8));
    }
  }
}
