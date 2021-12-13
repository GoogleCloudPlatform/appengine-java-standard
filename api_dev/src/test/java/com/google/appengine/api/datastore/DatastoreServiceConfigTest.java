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

import static com.google.appengine.api.datastore.DatastoreServiceConfig.Builder.withDeadline;
import static com.google.appengine.api.datastore.DatastoreServiceConfig.Builder.withDefaults;
import static com.google.appengine.api.datastore.DatastoreServiceConfig.Builder.withImplicitTransactionManagementPolicy;
import static com.google.appengine.api.datastore.DatastoreServiceConfig.Builder.withMaxEntityGroupsPerRpc;
import static com.google.appengine.api.datastore.DatastoreServiceConfig.Builder.withReadPolicy;
import static com.google.appengine.api.datastore.DatastoreServiceConfig.DEFAULT_MAX_ENTITY_GROUPS_PER_RPC_SYS_PROP;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.appengine.api.testing.LocalServiceTestHelperRule;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DatastoreServiceConfigTest {
  @Rule public LocalServiceTestHelperRule testHelperRule = new LocalServiceTestHelperRule();

  @After
  public void tearDown() throws Exception {
    DatastoreServiceGlobalConfig.clearConfig();
  }

  @Test
  public void testImplicitTransactionManagementPolicy() {
    assertThrows(NullPointerException.class, () -> withImplicitTransactionManagementPolicy(null));
    DatastoreServiceConfig config =
        withImplicitTransactionManagementPolicy(ImplicitTransactionManagementPolicy.AUTO);
    assertThat(config.getImplicitTransactionManagementPolicy())
        .isEqualTo(ImplicitTransactionManagementPolicy.AUTO);
    assertThat(config.getReadPolicy()).isEqualTo(new ReadPolicy(ReadPolicy.Consistency.STRONG));
    assertThat(config.getDeadline()).isNull();
  }

  @Test
  public void testReadPolicy() {
    assertThrows(NullPointerException.class, () -> withReadPolicy(null));
    DatastoreServiceConfig config = withReadPolicy(new ReadPolicy(ReadPolicy.Consistency.EVENTUAL));
    assertThat(config.getImplicitTransactionManagementPolicy())
        .isEqualTo(ImplicitTransactionManagementPolicy.NONE);
    assertThat(config.getReadPolicy()).isEqualTo(new ReadPolicy(ReadPolicy.Consistency.EVENTUAL));
    assertThat(config.getDeadline()).isNull();
  }

  @Test
  public void testDeadline() {
    assertThrows(IllegalArgumentException.class, () -> withDeadline(0));
    assertThrows(IllegalArgumentException.class, () -> withDeadline(-1));
    DatastoreServiceConfig config = withDeadline(5.02);
    assertThat(config.getImplicitTransactionManagementPolicy())
        .isEqualTo(ImplicitTransactionManagementPolicy.NONE);
    assertThat(config.getReadPolicy()).isEqualTo(new ReadPolicy(ReadPolicy.Consistency.STRONG));
    assertThat(config.getDeadline()).isEqualTo(5.02);
  }

  @Test
  public void testDefaults() {
    DatastoreServiceConfig config = withDefaults();
    assertThat(config.getImplicitTransactionManagementPolicy())
        .isEqualTo(ImplicitTransactionManagementPolicy.NONE);
    assertThat(config.getReadPolicy()).isEqualTo(new ReadPolicy(ReadPolicy.Consistency.STRONG));
    assertThat(config.getDeadline()).isNull();
    config.implicitTransactionManagementPolicy(ImplicitTransactionManagementPolicy.AUTO);
    config.readPolicy(new ReadPolicy(ReadPolicy.Consistency.EVENTUAL));
    config.deadline(12.2);
    assertThat(config.getImplicitTransactionManagementPolicy())
        .isEqualTo(ImplicitTransactionManagementPolicy.AUTO);
    assertThat(config.getReadPolicy()).isEqualTo(new ReadPolicy(ReadPolicy.Consistency.EVENTUAL));
    assertThat(config.getDeadline()).isEqualTo(12.2);
    assertThat(config.getMaxEntityGroupsPerRpc().intValue()).isEqualTo(10);
  }

  @Test
  public void testMaxEntityGroupsPerRpc() {
    assertThrows(IllegalArgumentException.class, () -> withMaxEntityGroupsPerRpc(-1));

    assertThrows(IllegalArgumentException.class, () -> withMaxEntityGroupsPerRpc(0));
    DatastoreServiceConfig config1 = withMaxEntityGroupsPerRpc(1);
    assertThat(config1.getMaxEntityGroupsPerRpc().intValue()).isEqualTo(1);

    DatastoreServiceConfig config2 = withDefaults();
    assertThat(config2.getMaxEntityGroupsPerRpc().intValue()).isEqualTo(10);
    assertThrows(IllegalArgumentException.class, () -> config2.maxEntityGroupsPerRpc(-1));

    assertThrows(IllegalArgumentException.class, () -> config2.maxEntityGroupsPerRpc(0));
    config2.maxEntityGroupsPerRpc(1);
    assertThat(config2.getMaxEntityGroupsPerRpc().intValue()).isEqualTo(1);

    config2.maxEntityGroupsPerRpc(2);
    assertThat(config2.getMaxEntityGroupsPerRpc().intValue()).isEqualTo(2);
  }

  @Test
  public void testGetDefaultMaxEntityGroupsPerRpc() {
    assertThat(DatastoreServiceConfig.getDefaultMaxEntityGroupsPerRpc()).isEqualTo(10);
    String original = System.getProperty(DEFAULT_MAX_ENTITY_GROUPS_PER_RPC_SYS_PROP);
    System.setProperty(DEFAULT_MAX_ENTITY_GROUPS_PER_RPC_SYS_PROP, "2");
    try {
      assertThat(DatastoreServiceConfig.getDefaultMaxEntityGroupsPerRpc()).isEqualTo(2);
    } finally {
      if (original == null) {
        System.clearProperty(DEFAULT_MAX_ENTITY_GROUPS_PER_RPC_SYS_PROP);
      } else {
        System.setProperty(DEFAULT_MAX_ENTITY_GROUPS_PER_RPC_SYS_PROP, original);
      }
    }
  }
}
