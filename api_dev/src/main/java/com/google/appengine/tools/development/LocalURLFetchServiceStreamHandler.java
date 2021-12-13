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

import static com.google.appengine.tools.development.StreamHandlerFactory.getDeclaredMethod;
import static com.google.appengine.tools.development.StreamHandlerFactory.invoke;

import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.utils.security.urlfetch.URLFetchServiceStreamHandler;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Extension to {@link URLFetchServiceStreamHandler} that can fall back to a
 * default stream handler when the url fetch service is not available.
 * <p>
 * The Dev AppServer registers a custom stream handler, which is global (not
 * classloader global, jvm global).  In addition, the jdk only lets you set a
 * custom stream handler once, so once it's set, it's set.  This is
 * unfortunate, because you may have a program like an integration test or a
 * remote api test that spends some time operating in an App Engine context and
 * some time operating in a non-App Engine context.  Without the ability to fall
 * back to a default stream handler, you are effectively prevented from doing
 * anything involving stream handlers once you stop operating in an App Engine
 * context.
 *
 */
public class LocalURLFetchServiceStreamHandler extends URLFetchServiceStreamHandler {
  private static boolean useNativeHandlers = false;
  @Nullable private final URLStreamHandler fallbackHandler;
  private final Method openConnection1Arg;
  private final Method openConnection2Arg;

  /**
   * Constructs a LocalURLFetchServiceStreamHandler
   *
   * @param fallbackHandler Receives requests to open connections when the url fetch service is not
   *     available.
   */
  public LocalURLFetchServiceStreamHandler(@Nullable URLStreamHandler fallbackHandler) {
    this.fallbackHandler = fallbackHandler;
    // There's no good way to get at the openConnection() methods on a
    // StreamHandler because they are protected.  We resort to reflection,
    // which is ugly and fragile, but at least URLFetchServiceStreamHandler
    // doesn't need to know anything about it.
    openConnection1Arg = getDeclaredMethod(fallbackHandler.getClass(), "openConnection", URL.class);
    openConnection2Arg = getDeclaredMethod(
        fallbackHandler.getClass(), "openConnection", URL.class, Proxy.class);
  }
  
  private boolean useFallBackHandler() {
    // No delegate means the url fetch service is unavailable.  Alternatively
    // if appengine-web.xml is configured to use native handlers.
    return fallbackHandler != null && (ApiProxy.getDelegate() == null || useNativeHandlers);
  }

  @Override
  protected HttpURLConnection openConnection(URL u) throws IOException {
    if (useFallBackHandler()) {
      return (HttpURLConnection) invoke(fallbackHandler, openConnection1Arg, u);
    }
    return super.openConnection(u);
  }

  @Override
  protected URLConnection openConnection(URL u, Proxy p) throws IOException {

    if (useFallBackHandler()) {
      return (HttpURLConnection) invoke(fallbackHandler, openConnection2Arg, u, p);
    }
    return super.openConnection(u, p);
  }

  public URLStreamHandler getFallbackHandler() {
    return fallbackHandler;
  }

  public static void setUseNativeHandlers(boolean useNativeHandlers) {
    LocalURLFetchServiceStreamHandler.useNativeHandlers = useNativeHandlers;
  }

  @VisibleForTesting
  static boolean getUseNativeHandlers() {
    return useNativeHandlers;
  }
}
