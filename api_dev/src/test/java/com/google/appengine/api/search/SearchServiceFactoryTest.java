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

package com.google.appengine.api.search;

import static com.google.common.truth.Truth.assertThat;

import com.google.appengine.api.NamespaceManager;
import com.google.appengine.api.testing.MockEnvironment;
import com.google.apphosting.api.ApiProxy;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Test {@link SearchServiceFactory}.
 *
 */
@RunWith(JUnit4.class)
public class SearchServiceFactoryTest {

  private ApiProxy.Environment environment;

  @Before
  public void setUp() {
    environment = new MockEnvironment("some-app", "v1");
    ApiProxy.setEnvironmentForCurrentThread(environment);
  }

  @Test
  public void testSearchServiceCreated() {
    assertThat(SearchServiceFactory.getSearchService()).isNotNull();
  }

  @Test
  public void testSearchServiceDefaultNamespace() {
    assertThat(SearchServiceFactory.getSearchService().getNamespace()).isEmpty();
  }

  @Test
  public void testSearchServiceGlobalNamespace() {
    String globalNamespace = "some-namespace-13847";
    NamespaceManager.set(globalNamespace);
    assertThat(SearchServiceFactory.getSearchService().getNamespace()).isEqualTo(globalNamespace);
  }

  @Test
  public void testSearchServiceUserGivenNamespace() {
    String userSpecifiedNamespace = "some-user-given-namespace-23847389";
    assertThat(SearchServiceFactory.getSearchService(userSpecifiedNamespace).getNamespace())
        .isEqualTo(userSpecifiedNamespace);
  }
}
