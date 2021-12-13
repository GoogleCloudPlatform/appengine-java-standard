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

package com.google.apphosting.runtime;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;

/**
 * ClassLoader that can add extra URLs in response to a ClassNotFoundException, if a certain
 * system property is set. The idea is that we want Java 8 apps by default to fail if they
 * reference classes using the old repackaging scheme, but we also want it to be possible
 * to make them work if it isn't possible to fix erroneous references to repackaged classes
 * for some reason.
 *
 * <p>Typically, users would put this in appengine-web.xml:
 * <pre>
 *   &lt;system-properties&gt;
 *     &lt;property name="appengine.api.legacy.repackaging" value="true"&gt;
 *   &lt;/system-properties&gt;
 * </pre>
 *
 * <p>The Java runtime can also be launched with {@code -Dappengine.api.legacy.repackaging=true} to
 * turn on this behaviour for all apps, but we do not want that for the standard runtimes.
 *
 * <p>This class also has an optimization for directory URLs that contain no classes. If the input
 * list of URLs contains such directories and {@code alwaysScanClassDirs} is false, then
 * those directories will not be consulted when trying to load a class. This can lead to substantial
 * time saving if the directories precede jars in the classpath and system calls are expensive.
 * There is no point in trying to open or stat {@code app/classes/foo/bar/Baz.class} if we know that
 * there are no {@code *.class} files under {@code app/classes}.
 *
 * <p>We must continue to look for <i>resources</i> in the original URL list, though. So in this
 * case we have a separate {@code URLClassLoader} that uses the original list of URLs, and that's
 * what we use to find resources. It is safe to do this because, unlike classes, there is no way
 * to derive a ClassLoader from a resource.
 *
 */
class ApplicationClassLoader extends URLClassLoader {
  static final String COMPAT_PROPERTY = "appengine.api.legacy.repackaging";

  private final URL[] originalUrls;
  private final URL[] legacyUrls;
  private final URLClassLoader resourceLoader;
  boolean addedLegacyUrls;

  ApplicationClassLoader(
      URL[] urls, URL[] legacyUrls, ClassLoader parent, boolean alwaysScanClassDirs) {
    super(
        alwaysScanClassDirs ? urls : excludeClasslessDirectories(urls),
        parent);
    this.originalUrls = urls;
    this.legacyUrls = legacyUrls;
    if (Arrays.equals(urls, super.getURLs())) {
      resourceLoader = null;
    } else {
      resourceLoader = new URLClassLoader(urls, parent);
    }
  }

  // @VisibleForTesting
  URL[] getActualUrls() {
    return super.getURLs();
  }

  @Override
  public URL[] getURLs() {
    return originalUrls.clone();
  }

  @Override
  public URL findResource(String name) {
    return (resourceLoader == null)
        ? super.findResource(name)
        : resourceLoader.findResource(name);
  }

  @Override
  public Enumeration<URL> findResources(String name) throws IOException {
    return (resourceLoader == null)
        ? super.findResources(name)
        : resourceLoader.findResources(name);
  }

  private static URL[] excludeClasslessDirectories(URL[] urls) {
    List<URL> classfulUrls = new ArrayList<>();
    for (URL url : urls) {
      if (!url.getPath().endsWith("/") || hasClasses(url)) {
        classfulUrls.add(url);
      }
    }
    return classfulUrls.toArray(new URL[0]);
  }

  private static boolean hasClasses(URL directoryUrl) {
    try {
      File directory = new File(directoryUrl.toURI());
      return hasClasses(directory);
    } catch (URISyntaxException e) {
      return true;  // play it safe
    }
  }

  private static boolean hasClasses(File directory) {
    File[] files = directory.listFiles();
    if (files == null) {
      return false;
    }
    for (File file : files) {
      if (file.isDirectory()) {
        if (hasClasses(file)) {
          return true;
        }
      } else if (file.getName().endsWith(".class")) {
        return true;
      }
    }
    return false;
  }

  /**
   * {@inheritDoc}
   *
   * <p>If the named class is not found in the initial set of URLs, and if {@link #COMPAT_PROPERTY}
   * is set to {@code "true"}, then we add the legacy URLs and try again.
   */
  @Override
  protected Class<?> findClass(String name) throws ClassNotFoundException {
    try {
      return super.findClass(name);
    } catch (ClassNotFoundException e) {
      if (!addedLegacyUrls && Boolean.getBoolean(COMPAT_PROPERTY)) {
        for (URL url : legacyUrls) {
          addURL(url);
        }
        addedLegacyUrls = true;
        return super.findClass(name);
      }
      throw e;
    }
  }
}
