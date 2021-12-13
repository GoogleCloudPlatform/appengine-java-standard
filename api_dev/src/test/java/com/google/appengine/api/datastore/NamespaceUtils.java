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

import com.google.appengine.api.NamespaceManager;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.NamespaceResources;

/**
 * A collection of utilities for manipulating the namespace in datastore tests.
 *
 */
final class NamespaceUtils {

  public static final String NAMESPACE = "namespace";

  public static final String OTHER_NAMESPACE = "other.namespace";

  private static final String CURRENT_NAMESPACE_KEY =
      NamespaceManager.class.getName() + ".currentNamespace";

  private NamespaceUtils() {}

  public static void setEmptyDefaultApiNamespace() {
    getEnvironment().getAttributes().remove(CURRENT_NAMESPACE_KEY);
  }

  public static void setNonEmptyDefaultApiNamespace() {
    getEnvironment().getAttributes().put(CURRENT_NAMESPACE_KEY, NAMESPACE);
  }

  public static void setOtherNonEmptyDefaultApiNamespace() {
    getEnvironment().getAttributes().put(CURRENT_NAMESPACE_KEY, OTHER_NAMESPACE);
  }

  public static String getAppIdWithNamespace() {
    return getEnvironment().getAppId() + NamespaceResources.NAMESPACE_SEPARATOR + NAMESPACE;
  }

  public static String getAppId() {
    return getEnvironment().getAppId();
  }

  static ApiProxy.Environment getEnvironment() {
    return ApiProxy.getCurrentEnvironment();
  }
}
