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

package com.google.apphosting.runtime.jetty94;

import static com.google.common.base.StandardSystemProperty.USER_DIR;
import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;

import com.google.appengine.tools.development.resource.ResourceExtractor;
import com.google.apphosting.base.protos.AppinfoPb;
import com.google.apphosting.utils.config.AppYaml;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class AppInfoFactoryTest {
  private static final String PACKAGE_PATH =
      AppInfoFactoryTest.class.getPackage().getName().replace('.', '/');
  private static final String PROJECT_RESOURCE_NAME =
      String.format("%s/mytestproject", PACKAGE_PATH);

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private String appRoot;
  private String fixedAppDir;

  @Before
  public void setUp() throws IOException {
    Path projPath = Paths.get(temporaryFolder.newFolder(PROJECT_RESOURCE_NAME).getPath());
    appRoot = projPath.getParent().toString();
    fixedAppDir = Paths.get(projPath.toString(), "100.mydeployment").toString();
    ResourceExtractor.toFile(PROJECT_RESOURCE_NAME, projPath.toString());
  }

  @Test
  public void getGaeService_nonDefault() throws Exception {
    AppInfoFactory factory =
        new AppInfoFactory(ImmutableMap.of("GAE_SERVICE", "mytestservice"));
    assertThat(factory.getGaeService()).isEqualTo("mytestservice");
  }

  @Test
  public void getGaeService_defaults() throws Exception {
    AppInfoFactory factory = new AppInfoFactory(ImmutableMap.of());
    assertThat(factory.getGaeService()).isEqualTo("default");
  }

  @Test
  public void getGaeVersion_nonDefaultWithDeploymentId() throws Exception {
    AppInfoFactory factory =
        new AppInfoFactory(
            ImmutableMap.of(
                "GAE_SERVICE", "mytestservice",
                "GAE_DEPLOYMENT_ID", "mydeployment",
                "GAE_VERSION", "100"));
    assertThat(factory.getGaeVersion()).isEqualTo("mytestservice:100.mydeployment");
  }

  @Test
  public void getGaeVersion_defaultWithDeploymentId() throws Exception {
    AppInfoFactory factory =
        new AppInfoFactory(
            ImmutableMap.of(
                "GAE_DEPLOYMENT_ID", "mydeployment",
                "GAE_VERSION", "100"));
    assertThat(factory.getGaeVersion()).isEqualTo("100.mydeployment");
  }

  @Test
  public void getGaeVersion_defaultWithoutDeploymentId() throws Exception {
    AppInfoFactory factory = new AppInfoFactory(ImmutableMap.of("GAE_VERSION", "100"));
    assertThat(factory.getGaeVersion()).isEqualTo("100");
  }

  @Test
  public void getGaeServiceVersion_withDeploymentId() throws Exception {
    AppInfoFactory factory =
        new AppInfoFactory(
            ImmutableMap.of(
                "GAE_DEPLOYMENT_ID", "mydeployment",
                "GAE_VERSION", "100"));
    assertThat(factory.getGaeVersion()).isEqualTo("100.mydeployment");
  }

  @Test
  public void getGaeServiceVersion_withoutDeploymentId() throws Exception {
    AppInfoFactory factory = new AppInfoFactory(ImmutableMap.of("GAE_VERSION", "100"));
    assertThat(factory.getGaeVersion()).isEqualTo("100");
  }

  @Test
  public void getGaeApplication_nonDefault() throws Exception {
    AppInfoFactory factory = new AppInfoFactory(ImmutableMap.of("GAE_APPLICATION", "s~myapp"));
    assertThat(factory.getGaeApplication()).isEqualTo("s~myapp");
  }

  @Test
  public void getGaeApplication_defaults() throws Exception {
    AppInfoFactory factory = new AppInfoFactory(ImmutableMap.of());
    assertThat(factory.getGaeApplication()).isEqualTo("s~testapp");
  }

  @Test
  public void getAppInfo_fixedApplicationPath() throws Exception {
    AppInfoFactory factory =
        new AppInfoFactory(
            ImmutableMap.of(
                "GAE_SERVICE", "mytestservice",
                "GAE_DEPLOYMENT_ID", "mydeployment",
                "GAE_VERSION", "100",
                "GAE_APPLICATION", "s~myapp"));
    AppinfoPb.AppInfo appInfo = factory.getAppInfoFromFile(null, fixedAppDir);

    assertThat(appInfo.getAppId()).isEqualTo("s~myapp");
    assertThat(appInfo.getVersionId()).isEqualTo("mytestservice:100.mydeployment");
    assertThat(appInfo.getRuntimeId()).isEqualTo("java8");
    assertThat(appInfo.getApiVersion()).isEqualTo("200");
  }

  @Test
  public void getAppInfo_appRoot() throws Exception {
    AppInfoFactory factory =
        new AppInfoFactory(
            ImmutableMap.of(
                "GAE_SERVICE", "mytestservice",
                "GAE_DEPLOYMENT_ID", "mydeployment",
                "GAE_VERSION", "100",
                "GAE_APPLICATION", "s~myapp",
                "GOOGLE_CLOUD_PROJECT", "mytestproject"));
    AppinfoPb.AppInfo appInfo = factory.getAppInfoFromFile(appRoot, null);

    assertThat(appInfo.getAppId()).isEqualTo("s~myapp");
    assertThat(appInfo.getVersionId()).isEqualTo("mytestservice:100.mydeployment");
    assertThat(appInfo.getRuntimeId()).isEqualTo("java8");
    assertThat(appInfo.getApiVersion()).isEqualTo("200");
  }

  @Test
  public void getAppInfo_noAppYaml() throws Exception {
    AppInfoFactory factory =
        new AppInfoFactory(
            ImmutableMap.of(
                "GAE_SERVICE", "mytestservice",
                "GAE_DEPLOYMENT_ID", "mydeployment",
                "GAE_VERSION", "100",
                "GAE_APPLICATION", "s~myapp",
                "GOOGLE_CLOUD_PROJECT", "bogusproject"));
    AppinfoPb.AppInfo appInfo =
        factory.getAppInfoFromFile(
            null,
            // We tell AppInfoFactory to look directly in the current working directory. There's no
            // app.yaml there:
            USER_DIR.value());

    assertThat(appInfo.getAppId()).isEqualTo("s~myapp");
    assertThat(appInfo.getVersionId()).isEqualTo("mytestservice:100.mydeployment");
    assertThat(appInfo.getRuntimeId()).isEqualTo("java8");
    assertThat(appInfo.getApiVersion()).isEmpty();
  }

  @Test
  public void getAppInfo_noDirectory() throws Exception {
    AppInfoFactory factory =
        new AppInfoFactory(
            ImmutableMap.of(
                "GAE_SERVICE", "mytestservice",
                "GAE_DEPLOYMENT_ID", "mydeployment",
                "GAE_VERSION", "100",
                "GAE_APPLICATION", "s~myapp",
                // This will make the AppInfoFactory hunt for a directory called bogusproject:
                "GOOGLE_CLOUD_PROJECT", "bogusproject"));

    assertThrows(NoSuchFileException.class, () -> factory.getAppInfoFromFile(appRoot, null));
  }

  @Test
  public void getAppInfo_givenAppYaml() throws Exception {
    AppInfoFactory factory =
        new AppInfoFactory(
            ImmutableMap.of(
                "GAE_SERVICE", "mytestservice",
                "GAE_DEPLOYMENT_ID", "mydeployment",
                "GAE_VERSION", "100",
                "GAE_APPLICATION", "s~myapp",
                "GOOGLE_CLOUD_PROJECT", "mytestproject"));

    File appYamlFile = new File(fixedAppDir + "/WEB-INF/appengine-generated/app.yaml");
    AppYaml appYaml = AppYaml.parse(new InputStreamReader(new FileInputStream(appYamlFile), UTF_8));

    AppinfoPb.AppInfo appInfo = factory.getAppInfoFromAppYaml(appYaml);

    assertThat(appInfo.getAppId()).isEqualTo("s~myapp");
    assertThat(appInfo.getVersionId()).isEqualTo("mytestservice:100.mydeployment");
    assertThat(appInfo.getRuntimeId()).isEqualTo("java8");
    assertThat(appInfo.getApiVersion()).isEqualTo("200");
  }

  @Test
  public void getAppInfo_givenVersion() throws Exception {
    AppInfoFactory factory =
        new AppInfoFactory(
            ImmutableMap.of(
                "GAE_SERVICE", "mytestservice",
                "GAE_DEPLOYMENT_ID", "mydeployment",
                "GAE_VERSION", "100",
                "GAE_APPLICATION", "s~myapp",
                "GOOGLE_CLOUD_PROJECT", "mytestproject"));

    AppinfoPb.AppInfo appInfo = factory.getAppInfoWithApiVersion("my_api_version");

    assertThat(appInfo.getAppId()).isEqualTo("s~myapp");
    assertThat(appInfo.getVersionId()).isEqualTo("mytestservice:100.mydeployment");
    assertThat(appInfo.getRuntimeId()).isEqualTo("java8");
    assertThat(appInfo.getApiVersion()).isEqualTo("my_api_version");
  }
}
