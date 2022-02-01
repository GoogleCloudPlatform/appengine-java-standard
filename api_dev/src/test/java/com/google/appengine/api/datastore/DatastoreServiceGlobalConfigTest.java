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

package com.google.appengine.api.datastore;

import static com.google.common.truth.Truth.assertThat;

import com.google.appengine.api.datastore.DatastoreServiceGlobalConfig.StubApiProxyEnvironment;
import com.google.appengine.api.utils.SystemProperty;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.Environment;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Map;
import javax.annotation.Nullable;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link DatastoreServiceGlobalConfig}. */
@RunWith(JUnit4.class)
public class DatastoreServiceGlobalConfigTest {

  private static final String APP_ID = "s~project-id";

  private static final String ANOTHER_PROJECT_ID = "another-project-id";
  private static final String ANOTHER_APP_ID = "s~" + ANOTHER_PROJECT_ID;

  private static final DatastoreServiceGlobalConfig USE_API_PROXY =
      DatastoreServiceGlobalConfig.builder().useApiProxy(true).build();

  private static final DatastoreServiceGlobalConfig NO_USE_API_PROXY =
      DatastoreServiceGlobalConfig.builder().useApiProxy(false).appId(APP_ID).build();

  @Rule public final ExpectedException thrown = ExpectedException.none();

  @After
  public void after() {
    EnvProxy.clearEnvOverrideForTest();
    DatastoreServiceGlobalConfig.clear();
  }

  @Test
  public void getConfigFromEnvSimple() {
    DatastoreServiceGlobalConfig config = DatastoreServiceGlobalConfig.getConfig();
    assertThat(config).isNotNull();
    assertThat(config.useApiProxy()).isTrue();
    assertThat(config.hostOverride()).isNull();
    assertThat(config.additionalAppIds()).isNull();
    assertThat(config.appId()).isNull();
    assertThat(config.projectId()).isNull();
    assertThat(config.useProjectIdAsAppId()).isFalse();
    assertThat(config.emulatorHost()).isNull();
    assertThat(config.serviceAccount()).isNull();
    assertThat(config.privateKeyFile()).isNull();
  }

  @Test
  public void getConfigFromEnvComplex() {
    ImmutableMap.Builder<String, String> env = ImmutableMap.builder();
    env.put("DATASTORE_USE_CLOUD_DATASTORE", "true");
    env.put("DATASTORE_ADDITIONAL_APP_IDS", "s~app-id1, s~app-id2,,s~app-id3");
    env.put("DATASTORE_PROJECT_ID", "project-id");
    env.put("DATASTORE_USE_PROJECT_ID_AS_APP_ID", "true");
    env.put("DATASTORE_EMULATOR_HOST", "emulator-host");
    EnvProxy.setEnvOverrideForTest(env.buildOrThrow());

    DatastoreServiceGlobalConfig config = DatastoreServiceGlobalConfig.getConfig();
    assertThat(config.useApiProxy()).isFalse();
    assertThat(config.additionalAppIds()).containsExactly("s~app-id1", "s~app-id2", "s~app-id3");
    assertThat(config.projectId()).isEqualTo("project-id");
    assertThat(config.useProjectIdAsAppId()).isTrue();
    assertThat(config.emulatorHost()).isEqualTo("emulator-host");
  }

  @Test
  public void setConfig_SetSet() {
    DatastoreServiceGlobalConfig.setConfig(NO_USE_API_PROXY);
    thrown.expect(IllegalStateException.class);
    DatastoreServiceGlobalConfig.setConfig(NO_USE_API_PROXY);
  }

  @Test
  public void setConfig_SetClearSet() {
    DatastoreServiceGlobalConfig.setConfig(NO_USE_API_PROXY);
    DatastoreServiceGlobalConfig.clear();
    DatastoreServiceGlobalConfig.setConfig(NO_USE_API_PROXY);
  }

  @Test
  public void setConfig_ApiProxy_Development() {
    testSetConfig(SystemProperty.Environment.Value.Development, USE_API_PROXY);
  }

  @Test
  public void setConfig_ApiProxy_ProdStandardJava7() {
    testSetConfig(SystemProperty.Environment.Value.Production, USE_API_PROXY);
  }

  @Test
  public void setConfig_ApiProxy_ProdStandardJava8() {
    EnvProxy.setEnvOverrideForTest(ImmutableMap.of("GAE_ENV", "standard"));
    testSetConfig(SystemProperty.Environment.Value.Production, USE_API_PROXY);
  }

  @Test
  public void setConfig_ApiProxy_ProdFlex() {
    // This configuration won't actually work, but we don't explicitly disallow it.
    EnvProxy.setEnvOverrideForTest(ImmutableMap.of("GCLOUD_PROJECT", "true", "GAE_ENV", "flex"));
    testSetConfig(SystemProperty.Environment.Value.Production, USE_API_PROXY);
  }

  @Test
  public void setConfig_ApiProxy_ProdFlexCompat() {
    EnvProxy.setEnvOverrideForTest(ImmutableMap.of("GAE_VM", "true", "GAE_ENV", "flex-compat"));
    testSetConfig(SystemProperty.Environment.Value.Production, USE_API_PROXY);
  }

  @Test
  public void setConfig_ApiProxy_Gce() {
    // This configuration won't actually work, but we don't explicitly disallow it.
    testSetConfig(null, USE_API_PROXY);
  }

  @Test
  public void setConfig_NonApiProxy_Development() {
    testSetConfig(SystemProperty.Environment.Value.Development, NO_USE_API_PROXY);
  }

  @Test
  public void setConfig_NonApiProxy_ProdStandardJava7() {
    thrown.expect(IllegalStateException.class);
    testSetConfig(SystemProperty.Environment.Value.Production, NO_USE_API_PROXY);
  }

  @Test
  public void setConfig_NonApiProxy_ProdStandardJava8() {
    EnvProxy.setEnvOverrideForTest(ImmutableMap.of("GAE_ENV", "standard"));
    thrown.expect(IllegalStateException.class);
    testSetConfig(SystemProperty.Environment.Value.Production, NO_USE_API_PROXY);
  }

  @Test
  public void setConfig_NonApiProxy_ProdFlex() {
    EnvProxy.setEnvOverrideForTest(ImmutableMap.of("GCLOUD_PROJECT", "true", "GAE_ENV", "flex"));
    testSetConfig(SystemProperty.Environment.Value.Production, NO_USE_API_PROXY);
  }

  @Test
  public void setConfig_NonApiProxy_ProdFlexCompat() {
    EnvProxy.setEnvOverrideForTest(ImmutableMap.of("GAE_VM", "true", "GAE_ENV", "flex-compat"));
    testSetConfig(SystemProperty.Environment.Value.Production, NO_USE_API_PROXY);
  }

  @Test
  public void setConfig_NonApiProxy_Gce() {
    testSetConfig(null, NO_USE_API_PROXY);
  }

  private static void testSetConfig(
      @Nullable SystemProperty.Environment.Value environmentValue,
      DatastoreServiceGlobalConfig config) {
    SystemProperty.Environment.Value oldEnvValue = SystemProperty.environment.value();
    setEnvironmentNullSafe(environmentValue);
    try {
      DatastoreServiceGlobalConfig.setConfig(config);
    } finally {
      setEnvironmentNullSafe(oldEnvValue);
    }
  }

  private static void setEnvironmentNullSafe(@Nullable SystemProperty.Environment.Value value) {
    if (value == null) {
      System.clearProperty(SystemProperty.environment.key());
    } else {
      SystemProperty.environment.set(value.value());
    }
  }

  @Test
  public void config_AppIdAndProjectId() {
    thrown.expect(IllegalStateException.class);
    DatastoreServiceGlobalConfig.builder().appId(APP_ID).projectId("project-id").build();
  }

  @Test
  public void config_AppIdButUseProjectId() {
    thrown.expect(IllegalStateException.class);
    DatastoreServiceGlobalConfig.builder().appId(APP_ID).useProjectIdAsAppId(true).build();
  }

  @Test
  public void config_ProjectIdButNoUseProjectId() {
    thrown.expect(IllegalStateException.class);
    DatastoreServiceGlobalConfig.builder().appId(APP_ID).useProjectIdAsAppId(true).build();
  }

  @Test
  public void config_GetChosenAppId_AppId() {
    DatastoreServiceGlobalConfig config =
        DatastoreServiceGlobalConfig.builder().appId("s~project-id").build();
    assertThat(config.configuredAppId()).isEqualTo("s~project-id");
  }

  @Test
  public void config_GetChosenAppId_ProjectId() {
    DatastoreServiceGlobalConfig config =
        DatastoreServiceGlobalConfig.builder()
            .projectId("project-id")
            .useProjectIdAsAppId(true)
            .build();
    assertThat(config.configuredAppId()).isEqualTo("project-id");
  }

  @Test
  public void getCurrentApiProxyEnvironment_NoConfig() {
    Environment environment = DatastoreServiceGlobalConfig.getCurrentApiProxyEnvironment();
    assertThat(environment).isNull();
  }

  @Test
  public void getCurrentApiProxyEnvironment_SimpleConfig() {
    DatastoreServiceGlobalConfig.setConfig(
        DatastoreServiceGlobalConfig.builder().appId(APP_ID).build());

    Environment environment = DatastoreServiceGlobalConfig.getCurrentApiProxyEnvironment();
    assertThat(environment.getAppId()).isEqualTo(APP_ID);
    // No additional app IDs to add.
    assertThat(environment.getAttributes())
        .doesNotContainKey(DataTypeTranslator.ADDITIONAL_APP_IDS_MAP_ATTRIBUTE_KEY);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void getCurrentApiProxyEnvironment_ConfigWithAdditionalAppIds() {
    DatastoreServiceGlobalConfig.setConfig(
        DatastoreServiceGlobalConfig.builder()
            .appId(APP_ID)
            .additionalAppIds(ImmutableSet.of(ANOTHER_APP_ID))
            .build());

    Environment environment = DatastoreServiceGlobalConfig.getCurrentApiProxyEnvironment();
    assertThat(environment.getAppId()).isEqualTo(APP_ID);
    assertThat(environment.getAttributes())
        .containsKey(DataTypeTranslator.ADDITIONAL_APP_IDS_MAP_ATTRIBUTE_KEY);
    Map<String, String> projectIdtoAppId =
        (Map<String, String>)
            environment
                .getAttributes()
                .get(DataTypeTranslator.ADDITIONAL_APP_IDS_MAP_ATTRIBUTE_KEY);
    assertThat(projectIdtoAppId).containsExactly(ANOTHER_PROJECT_ID, ANOTHER_APP_ID);
  }

  @Test
  public void getCurrentApiProxyEnvironment_EnvironmentAlreadyInstalled() {
    DatastoreServiceGlobalConfig.setConfig(
        DatastoreServiceGlobalConfig.builder()
            .appId(APP_ID)
            .additionalAppIds(ImmutableSet.of(ANOTHER_APP_ID))
            .build());

    ApiProxy.setEnvironmentForCurrentThread(new TestApiProxyEnvironment("s~another-app-id"));

    Environment environment = DatastoreServiceGlobalConfig.getCurrentApiProxyEnvironment();
    assertThat(environment).isInstanceOf(TestApiProxyEnvironment.class);
    assertThat(environment.getAppId()).isEqualTo("s~another-app-id");
    // We will not add additional app IDs unless we set up the ApiProxy ourselves.
    assertThat(environment.getAttributes())
        .doesNotContainKey(DataTypeTranslator.ADDITIONAL_APP_IDS_MAP_ATTRIBUTE_KEY);
  }

  private static class TestApiProxyEnvironment extends StubApiProxyEnvironment {
    public TestApiProxyEnvironment(String appId) {
      super(appId);
    }
  }
}
