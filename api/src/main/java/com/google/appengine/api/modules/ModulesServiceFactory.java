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

package com.google.appengine.api.modules;

import com.google.appengine.spi.ServiceFactoryFactory;

/**
 * Factory by which users get an implementation of the {@link ModulesService}.
 *
 * @see ModulesService
 */
public final class ModulesServiceFactory {

  /**
   * Returns an implementation of {@link ModulesService} for the current environment.
   */
  public static ModulesService getModulesService() {
    return ServiceFactoryFactory.getFactory(IModulesServiceFactory.class).getModulesService();
  }

  // Cannot instantiate.
  private ModulesServiceFactory() { }
}
