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

package com.google.appengine.tools.development.testing;

import com.google.appengine.api.appidentity.dev.LocalAppIdentityService;
import com.google.appengine.tools.development.ApiProxyLocal;

/**
 * Config for accessing the local app identity service in tests.
 *
 */
public class LocalAppIdentityServiceTestConfig implements LocalServiceTestConfig {

  private String defaultGcsBucketName = null;

  @Override
  public void setUp() {
    ApiProxyLocal proxy = LocalServiceTestHelper.getApiProxyLocal();
    if (defaultGcsBucketName != null) {
      proxy.setProperty("appengine.default.gcs.bucket.name", defaultGcsBucketName);
    }
  }

  @Override
  public void tearDown() {}

  public static LocalAppIdentityService getLocalSecretsService() {
    return (LocalAppIdentityService)
        LocalServiceTestHelper.getLocalService(LocalAppIdentityService.PACKAGE);
  }

  public LocalAppIdentityServiceTestConfig setDefaultGcsBucketName(String defaultGcsBucketName) {
    this.defaultGcsBucketName = defaultGcsBucketName;
    return this;
  }
}
