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

package com.google.appengine.tools.development;

import com.google.appengine.tools.development.ApplicationConfigurationManager.ModuleConfigurationHandle;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Automatic {@link Module} implementation.
 */
class AutomaticModule extends AbstractModule<AutomaticInstanceHolder> {

  AutomaticModule(ModuleConfigurationHandle moduleConfigurationHandle, String serverInfo,
      File externalResourceDir, String address, DevAppServer devAppServer) {
    super(moduleConfigurationHandle, serverInfo, externalResourceDir, address,
      devAppServer, makeInstanceHolders());
  }

  private static List<AutomaticInstanceHolder> makeInstanceHolders() {
    return ImmutableList.of(new AutomaticInstanceHolder(ContainerUtils.loadContainer(),
        LocalEnvironment.MAIN_INSTANCE));
  }

  @Override
  public LocalServerEnvironment doConfigure(
      ModuleConfigurationHandle moduleConfigurationHandle, String serverInfo,
      File externalResourceDir, String address, Map<String, Object> containerConfigProperties,
      DevAppServer devAppServer) throws Exception{
    int port = DevAppServerPortPropertyHelper.getPort(getModuleName(),
        devAppServer.getServiceProperties());
    return getInstanceHolders().get(0).getContainerService().configure(serverInfo, address, port,
        moduleConfigurationHandle, externalResourceDir, containerConfigProperties,
        LocalEnvironment.MAIN_INSTANCE, devAppServer);
  }

  @Override
  public int getInstanceCount() {
    return 0;
  }
}
