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

package com.google.appengine.tools.enhancer;

import com.google.appengine.tools.info.AppengineSdk;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A ClassLoader for the ORM (DataNucleus) enhancer. 
 * <p>
 * This ClassLoader allows us to isolate the enhancer from our dependencies
 * as well as prevent log4j from loading (which causes the enhancer 
 * to automatically use log4j for logging). 
 *  
 */
public class EnhancerLoader extends URLClassLoader {

  /**
   * Creates a new EnhancerLoader capable of running the Enhancer.
   * @deprecated Use {@link #EnhancerLoader(java.util.Set, String)}
   */
  @Deprecated
  public EnhancerLoader(Set<URL> enhanceTargets) {
    this(enhanceTargets, Enhancer.DEFAULT_ENHANCER_VERSION);
  }

  /**
   * Creates a new EnhancerLoader capable of running the Enhancer.
   */
  public EnhancerLoader(Set<URL> enhanceTargets, String datanucleusVersion) {
    super(
        getClassPath(enhanceTargets, datanucleusVersion), ClassLoaderUtil.getPlatformClassLoader());
  }

  @SuppressWarnings("URLEqualsHashCode")
  private static URL[] getClassPath(Set<URL> enhanceTargets, String datanucleusVersion) {
    AppengineSdk sdk = AppengineSdk.getSdk();
    List<URL> datanucleusToolLibs = sdk.getDatanucleusLibs(datanucleusVersion);
    Set<URL> newTargets = removeOrmLibs(enhanceTargets, datanucleusToolLibs);
    Set<URL> libs = new HashSet<URL>(sdk.getSharedLibs());
    libs.addAll(new HashSet<URL>(datanucleusToolLibs));
    libs.addAll(newTargets);
    URL[] urls = new URL[libs.size()];
    return libs.toArray(urls);
  }

  /**
   * Removes ORM jars from the set of user libraries supplied to the enhancer.
   *
   * DataNucleus has something against having two identical jars being available
   * from two different URLs for the same ClassLoader. Since DataNucleus jars
   * are available in both the SDK and the user's WEB-INF/lib, we run into this
   * problem fairly easily. We solve it by removing the user's versions of the
   * libraries from the set of classes we serve.
   */
  private static Set<URL> removeOrmLibs(Set<URL> enhanceTargets, List<URL> ormLibs) {
    if (enhanceTargets == null) {
      throw new NullPointerException("enhanceTargets cannot be null");
    }

    if (ormLibs == null) {
      throw new NullPointerException("ormLibs cannot be null");
    }

    @SuppressWarnings("URLEqualsHashCode")
    Set<URL> newTargets = new HashSet<URL>();

    // TODO Could make this faster than O(MxN), but M and N will always be small
    nextUrl:
    for (URL url : enhanceTargets) {
      String userFileName = getFileName(url);
      for (URL ormUrl : ormLibs) {
        if (userFileName.equals(getFileName(ormUrl))) {
          continue nextUrl;
        }
      }
      newTargets.add(url);
    }
    
    return newTargets;
  }

  private static String getFileName(URL url) {
    // NB Not using url.toURI() because it fails depending upon
    // whether or not the URL has encodings.
    String path = url.getPath();
    int trailingSlash = path.lastIndexOf('/');
    return path.substring(trailingSlash + 1);
  }
      
  /**
   * Loads classes from only ourself and the bootstrap classloader.
   * Does not load classes from the system classloader. Does not 
   * load log4j classes from anywhere.
   */
  @Override
  protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
    
    // Don't allow log4j classes, so we can force java.util.logging.
    if (name.startsWith("org.apache.log4j.")) {
      throw new ClassNotFoundException(name);
    }
    
    // Try loading from the bootstrap classloader or ourself.
    return super.loadClass(name, resolve);    
  }

  /** Utility to handle JDK 9 specific ClassLoader method for getPlatformClassLoader() */
  static class ClassLoaderUtil {

    public static ClassLoader getPlatformClassLoader() {
      try {
        Method getPlatformClassLoader = ClassLoader.class.getMethod("getPlatformClassLoader");
        return (ClassLoader) getPlatformClassLoader.invoke(null);
      } catch (ReflectiveOperationException ignore) {
        return null;
      }
    }

    private ClassLoaderUtil() {}
  }
}
