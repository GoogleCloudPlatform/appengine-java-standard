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

package com.google.appengine.tools.development.devappserver2;

import com.google.appengine.tools.info.AppengineSdk;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

/**
 * Isolates the DevAppServer and all of its dependencies into its own classloader.
 * This ClassLoader refuses to load anything off of the JVM's System ClassLoader
 * except for JRE classes (i.e. it ignores classpath and JAR manifest entries).
 *
 */
class DevAppServer2ClassLoader extends URLClassLoader {

  private final ClassLoader delegate;

  private static final String DEV_APP_SERVER_INTERFACE
      = "com.google.appengine.tools.development.DevAppServer";

  private static final String APP_CONTEXT_INTERFACE
      = "com.google.appengine.tools.development.AppContext";

  private static final String DEV_SOCKET_IMPL_FACTORY
      = "com.google.appengine.tools.development.DevSocketImplFactory";

  /**
   * Creates a new {@code DevAppServerClassLoader}, which will load
   * libraries from the App Engine Sdk.
   *
   * @param delegate A delegate ClassLoader from which a few shared
   * classes will be loaded (e.g. DevAppServer).
   */
  static DevAppServer2ClassLoader newClassLoader(ClassLoader delegate) {
    // NB(tobyr) Doing shared, then impl, in order, allows us to prefer
    // returning shared classes when asked by other classloaders. This makes
    // it so that we don't have to have the impl and shared classes
    // be a strictly disjoint set.
    List<URL> libs = new ArrayList<URL>(AppengineSdk.getSdk().getSharedLibs());
    libs.addAll(AppengineSdk.getSdk().getImplLibs());
    // Needed by admin console servlets, which are loaded by this
    // ClassLoader
    libs.addAll(AppengineSdk.getSdk().getUserJspLibs());
    return new DevAppServer2ClassLoader(libs.toArray(new URL[libs.size()]), delegate);
  }

  // NB(tobyr)
  //
  // Isolating our code may seem seem like overkill, but it's really necessary
  // in terms of integration scenarios, such as with GWT. In general, we've
  // discovered that users tend to put all sorts of nasty things on the
  // system classpath, and it interferes with our ability to properly isolate
  // user code and assign correct permissions.

  DevAppServer2ClassLoader(URL[] urls, ClassLoader delegate) {
    super(urls, null);
    this.delegate = delegate;
  }

  @Override
  protected synchronized Class<?> loadClass(String name, boolean resolve)
      throws ClassNotFoundException {

    // Special-case a few classes that need to be shared.
    if (name.equals(DEV_APP_SERVER_INTERFACE)
        || name.equals(APP_CONTEXT_INTERFACE)
        || name.equals(DEV_SOCKET_IMPL_FACTORY)
        || name.startsWith("com.google.appengine.tools.info.")) {
      Class<?> c = delegate.loadClass(name);
      if (resolve) {
        resolveClass(c);
      }
      return c;
    }

    // Otherwise, we're safe to load anything returned from the JRE or from
    // ourselves.
    return super.loadClass(name, resolve);
  }
}
