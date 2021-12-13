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

import com.google.appengine.api.images.dev.LocalImagesService;

/**
 * Config for accessing the local images service in tests.
 *
 */
public class LocalImagesServiceTestConfig implements LocalServiceTestConfig {

  @Override
  public void setUp() {
    // Nothing to configure
  }

  @Override
  public void tearDown() {
    // Stateless so nothing to clean up
  }

  public static LocalImagesService getLocalImagesService() {
    return (LocalImagesService) LocalServiceTestHelper.getLocalService(LocalImagesService.PACKAGE);
  }
}