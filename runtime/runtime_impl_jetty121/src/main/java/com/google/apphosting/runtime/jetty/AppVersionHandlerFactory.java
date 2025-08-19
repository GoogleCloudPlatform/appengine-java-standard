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
package com.google.apphosting.runtime.jetty;

import com.google.apphosting.runtime.AppVersion;
import com.google.apphosting.runtime.jetty.ee10.EE10AppVersionHandlerFactory;
import com.google.apphosting.runtime.jetty.ee11.EE11AppVersionHandlerFactory;
import com.google.apphosting.runtime.jetty.ee8.EE8AppVersionHandlerFactory;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;

public interface AppVersionHandlerFactory {

  enum EEVersion
  {
    EE8,
    EE10,
    EE11
  }

  static EEVersion getEEVersion() {
    if (Boolean.getBoolean("appengine.use.EE10")) {
      return EEVersion.EE10;
    } else if (Boolean.getBoolean("appengine.use.EE11")) {
      return EEVersion.EE11;
    } else {
      return EEVersion.EE8;
    }
  }

  static AppVersionHandlerFactory newInstance(Server server, String serverInfo) {
    switch (getEEVersion()) {
      case EE10:
        return new EE10AppVersionHandlerFactory(server, serverInfo);
        case EE11:
        return new EE11AppVersionHandlerFactory(server, serverInfo);
      case EE8:
        return new EE8AppVersionHandlerFactory(server, serverInfo);
      default:
        throw new IllegalStateException("Unknown EE version: " + getEEVersion());
    }
  }

  Handler createHandler(AppVersion appVersion) throws Exception;
}
