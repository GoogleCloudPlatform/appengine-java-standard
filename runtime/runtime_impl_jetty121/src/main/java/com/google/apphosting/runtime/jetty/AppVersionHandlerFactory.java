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

import java.lang.reflect.InvocationTargetException;
import com.google.apphosting.runtime.AppVersion;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;

public interface AppVersionHandlerFactory {

  enum EEVersion {
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
    String appVersionHandlerFactoryNameClassName = getAppVersionHandlerFactoryNameClassName();
    try {
      return Class.forName(appVersionHandlerFactoryNameClassName)
                .asSubclass(AppVersionHandlerFactory.class)
                .getConstructor(Server.class, String.class)
                .newInstance(server, serverInfo);
    } catch (ClassNotFoundException
          | IllegalAccessException
          | IllegalArgumentException
          | InstantiationException
          | NoSuchMethodException
          | SecurityException
          | InvocationTargetException ex) {
      throw new IllegalStateException(
          "Unable to create instance of " + appVersionHandlerFactoryNameClassName, ex);
    } catch (ClassCastException cce) {
       throw new IllegalStateException("Not a subtype of " + AppVersionHandlerFactory.class.getName(), cce);
    }
  }

  static String getAppVersionHandlerFactoryNameClassName() {
    return switch (getEEVersion()) {
      case EE11 -> "com.google.apphosting.runtime.jetty.ee11.EE11AppVersionHandlerFactory";
      case EE8 -> "com.google.apphosting.runtime.jetty.ee8.EE8AppVersionHandlerFactory";
      default -> throw new IllegalStateException("Unknown EE version: " + getEEVersion());
    };
  }

  Handler createHandler(AppVersion appVersion) throws Exception;
}
