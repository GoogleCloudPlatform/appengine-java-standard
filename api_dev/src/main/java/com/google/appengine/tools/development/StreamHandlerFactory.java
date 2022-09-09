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

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * A {@link URLStreamHandlerFactory} which installs
 * {@link URLStreamHandler URLStreamHandlers} that App Engine needs to support.
 * (For example, the "http" and "https" protocols).  This factory returns
 * handlers that delegate to the
 * {@link com.google.appengine.api.urlfetch.URLFetchService} when running in an
 * App Engine container, and returns the default handlers when running outside
 * an App Engine container.
 *
 */
public class StreamHandlerFactory implements URLStreamHandlerFactory {

  private static boolean factoryIsInstalled;

  /** Need to access this method via reflection because it is protected on {@link URL}. */
  @Nullable private static final Method GET_URL_STREAM_HANDLER;

  private static void trySetAccessible(AccessibleObject o) {
    try {
      AccessibleObject.class.getMethod("trySetAccessible").invoke(o);
    } catch (NoSuchMethodException e) {
      o.setAccessible(true);
    } catch (ReflectiveOperationException e) {
      throw new LinkageError(e.getMessage(), e);
    }
  }

  static {
    Method m = null;
    try {
      m = URL.class.getDeclaredMethod("getURLStreamHandler", String.class);
      trySetAccessible(m);
    } catch (NoSuchMethodException e) {
      // Don't want to completely hose people if the jvm they're running
      // locally doesn't have this method.
      Logger.getLogger(StreamHandlerFactory.class.getName()).info(
          "Unable to register default URLStreamHandlers.  You will be unable to "
              + "access http and https URLs outside the App Engine environment.");
    }
    GET_URL_STREAM_HANDLER = m;
  }

  private Map<String, URLStreamHandler> handlers = new HashMap<String, URLStreamHandler>();

  /**
   * Installs this StreamHandlerFactory.
   *
   * @throws IllegalStateException if a factory has already been installed, and
   * it is not a StreamHandlerFactory.
   * @throws RuntimeException if an unexpected, catastrophic failure occurs
   * while installing the handler.
   */
  public static void install() {
    synchronized (StreamHandlerFactory.class) {
      if (!factoryIsInstalled) {
        StreamHandlerFactory factory = new StreamHandlerFactory();

        try {
          URL.setURLStreamHandlerFactory(factory);
        } catch (Error e) {
          // Yes, URL.setURLStreamHandlerFactory() does in fact throw Error.
          // That's Java 1.0 code for you.

          // It's possible that we were already installed for this JVM, but in a
          // different ClassLoader.
          Object currentFactory;

          try {
            Field f = URL.class.getDeclaredField("factory");
            trySetAccessible(f);
            currentFactory = f.get(null);
          } catch (Exception ex) {
            throw new RuntimeException("Failed to find the currently installed factory", ex);
          }

          if (currentFactory == null) {
            throw new RuntimeException("The current factory is null, but we were unable "
                + "to set a new factory", e);
          }

          String currentFactoryType = currentFactory.getClass().getName();
          if (currentFactoryType.equals(StreamHandlerFactory.class.getName())) {
            factoryIsInstalled = true;
            return;
          }

          throw new IllegalStateException("A factory of type " + currentFactoryType +
              " has already been installed");
        }
      }
    }
  }

  private StreamHandlerFactory() {
    for (String protocol : Arrays.asList("http", "https")) {
      URLStreamHandler fallbackHandler = getFallbackStreamHandler(protocol);
      handlers.put(protocol, new LocalURLFetchServiceStreamHandler(fallbackHandler));
    }
  }

  @Override
  public URLStreamHandler createURLStreamHandler(String protocol) {
    return handlers.get(protocol);
  }

  /**
   * All calls to this method must take place before we register the
   * stream handler factory with URL.
   */
  private static URLStreamHandler getFallbackStreamHandler(String protocol) {
    if (GET_URL_STREAM_HANDLER == null) {
      return null;
    }
    // See what handler gets returned before we customize the stream handler
    // factory.  We'll use that as our fallback for this protocol when the url
    // fetch service is not available.
    URLStreamHandler existingHandler =
        (URLStreamHandler) invoke(null, GET_URL_STREAM_HANDLER, protocol);
    if (existingHandler.getClass().getName().equals(
            LocalURLFetchServiceStreamHandler.class.getName())) {
      // Looks like the handler was already registered via a different
      // classloader.  We'll just reuse their fallback handler as our own.
      Method getFallbackHandler =
          getDeclaredMethod(existingHandler.getClass(), "getFallbackHandler");
      return (URLStreamHandler) invoke(existingHandler, getFallbackHandler);
    }
    // We're the first classloader to register a custom stream handler, so the
    // existing handler is the one we want to fall back to.
    return existingHandler;
  }

  // Reflection utilities

  static Object invoke(Object target, Method m, Object... args) {
    try {
      return m.invoke(target, args);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    } catch (InvocationTargetException e) {
      if (e.getTargetException() instanceof RuntimeException) {
        throw (RuntimeException) e.getTargetException();
      }
      throw new RuntimeException(e.getTargetException());
    }
  }

  static Method getDeclaredMethod(Class<?> cls, String methodName, Class<?>... args) {
    try {
      Method m = cls.getDeclaredMethod(methodName, args);
      trySetAccessible(m);
      return m;
    } catch (NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
  }
}
