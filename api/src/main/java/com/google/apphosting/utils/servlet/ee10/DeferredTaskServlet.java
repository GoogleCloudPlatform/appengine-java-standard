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

package com.google.apphosting.utils.servlet.ee10;

import com.google.appengine.api.taskqueue.DeferredTask;
import com.google.appengine.api.taskqueue.DeferredTaskContext;
import com.google.apphosting.api.ApiProxy;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.net.HttpURLConnection;
import java.util.Map;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Implementation of {@link HttpServlet} to dispatch tasks with a {@link DeferredTask} payload; see
 * {@link com.google.appengine.api.taskqueue.TaskOptions#payload(DeferredTask)}.
 *
 * <p>This servlet is mapped to {@link DeferredTaskContext#DEFAULT_DEFERRED_URL} by default. Below
 * is a snippet of the web.xml configuration.<br>
 *
 * <pre>
 *    &lt;servlet&gt;
 *      &lt;servlet-name&gt;/_ah/queue/__deferred__&lt;/servlet-name&gt;
 *      &lt;servlet-class
 *        &gt;com.google.apphosting.utils.servlet.DeferredTaskServlet&lt;/servlet-class&gt;
 *    &lt;/servlet&gt;
 *
 *    &lt;servlet-mapping&gt;
 *      &lt;servlet-name&gt;_ah_queue_deferred&lt;/servlet-name&gt;
 *      &lt;url-pattern&gt;/_ah/queue/__deferred__&lt;/url-pattern&gt;
 *    &lt;/servlet-mapping&gt;
 * </pre>
 *
 */
public class DeferredTaskServlet extends HttpServlet {
  // Keep this in sync with X_APPENGINE_QUEUENAME and
  // in google3/apphosting/base/http_proto.cc
  static final String X_APPENGINE_QUEUENAME = "X-AppEngine-QueueName";

  static final String DEFERRED_TASK_SERVLET_KEY =
      DeferredTaskContext.class.getName() + ".httpServlet";
  static final String DEFERRED_TASK_REQUEST_KEY =
      DeferredTaskContext.class.getName() + ".httpServletRequest";
  static final String DEFERRED_TASK_RESPONSE_KEY =
      DeferredTaskContext.class.getName() + ".httpServletResponse";
  static final String DEFERRED_DO_NOT_RETRY_KEY =
      DeferredTaskContext.class.getName() + ".doNotRetry";
  static final String DEFERRED_MARK_RETRY_KEY = DeferredTaskContext.class.getName() + ".markRetry";

  /** Thrown by readRequest when an error occurred during deserialization. */
  protected static class DeferredTaskException extends Exception {
    public DeferredTaskException(Exception e) {
      super(e);
    }
  }

  @Override
  protected void service(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    // See http://b/3479189. All task queue requests have the X-AppEngine-QueueName
    // header set. Non admin users cannot set this header so it's a signal that
    // this came from task queue or an admin smart enough to set the header.
    if (req.getHeader(X_APPENGINE_QUEUENAME) == null) {
      resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Not a taskqueue request.");
      return;
    }

    String method = req.getMethod();
    if (!method.equals("POST")) {
      String protocol = req.getProtocol();
      String msg = "DeferredTaskServlet does not support method: " + method;
      if (protocol.endsWith("1.1")) {
        resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, msg);
      } else {
        resp.sendError(HttpServletResponse.SC_BAD_REQUEST, msg);
      }
      return;
    }

    // Place the current servlet, request and response in the environment for
    // situations where the task may need to get to it.
    Map<String, Object> attributes = ApiProxy.getCurrentEnvironment().getAttributes();
    attributes.put(DEFERRED_TASK_SERVLET_KEY, this);
    attributes.put(DEFERRED_TASK_REQUEST_KEY, req);
    attributes.put(DEFERRED_TASK_RESPONSE_KEY, resp);
    attributes.put(DEFERRED_MARK_RETRY_KEY, false);

    try {
      performRequest(req, resp);
      if ((Boolean) attributes.get(DEFERRED_MARK_RETRY_KEY)) {
        resp.setStatus(HttpURLConnection.HTTP_INTERNAL_ERROR);
      } else {
        resp.setStatus(HttpURLConnection.HTTP_OK);
      }
    } catch (DeferredTaskException e) {
      resp.setStatus(HttpURLConnection.HTTP_UNSUPPORTED_TYPE);
      log("Deferred task failed exception: " + e);
      return;
    } catch (RuntimeException e) {
      Boolean doNotRetry = (Boolean) attributes.get(DEFERRED_DO_NOT_RETRY_KEY);
      if (doNotRetry == null || !doNotRetry) {
        throw new ServletException(e);
      } else if (doNotRetry) {
        resp.setStatus(HttpURLConnection.HTTP_NOT_AUTHORITATIVE); // Alternate success code.
        log(DeferredTaskServlet.class.getName()
                + " - Deferred task failed but doNotRetry specified. Exception: "
                + e);
      }
    } finally {
      // Clean out the attributes.
      attributes.remove(DEFERRED_TASK_SERVLET_KEY);
      attributes.remove(DEFERRED_TASK_REQUEST_KEY);
      attributes.remove(DEFERRED_TASK_RESPONSE_KEY);
      attributes.remove(DEFERRED_DO_NOT_RETRY_KEY);
    }
  }

  /**
   * Performs a task enqueued with {@link TaskOptions#payload(DeferredTask)} by deserializing the
   * input stream of the {@link HttpServletRequest}.
   *
   * @param req The HTTP request.
   * @param resp The HTTP response.
   * @throws DeferredTaskException If an error occurred while deserializing the task.
   *     <p>Note that other exceptions may be thrown by the {@link DeferredTask#run()} method.
   */
  protected void performRequest(HttpServletRequest req, HttpServletResponse resp)
      throws DeferredTaskException {
    readRequest(req, resp).run();
  }

  /**
   * De-serializes the {@link DeferredTask} object from the input stream.
   *
   * @throws DeferredTaskException With the chained exception being one of the following:
   *     <li>{@link IllegalArgumentException}: Indicates a content-type header mismatch.
   *     <li>{@link ClassNotFoundException}: Deserialization failure.
   *     <li>{@link IOException}: Deserialization failure.
   *     <li>{@link ClassCastException}: Deserialization failure.
   */
  protected Runnable readRequest(HttpServletRequest req, HttpServletResponse resp)
      throws DeferredTaskException {
    String contentType = req.getHeader("content-type");
    if (contentType == null
        || !contentType.equals(DeferredTaskContext.RUNNABLE_TASK_CONTENT_TYPE)) {
      throw new DeferredTaskException(
          new IllegalArgumentException(
              "Invalid content-type header."
                  + " received: '"
                  + (String.valueOf(contentType))
                  + "' expected: '"
                  + DeferredTaskContext.RUNNABLE_TASK_CONTENT_TYPE
                  + "'"));
    }

    try {
      ServletInputStream stream = req.getInputStream();
      ObjectInputStream objectStream =
          new ObjectInputStream(stream) {
            @Override
            protected Class<?> resolveClass(ObjectStreamClass desc)
                throws IOException, ClassNotFoundException {
              ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
              String name = desc.getName();
              try {
                return Class.forName(name, false, classLoader);
              } catch (ClassNotFoundException ex) {
                // This one should also handle primitive types
                return super.resolveClass(desc);
              }
            }

            @Override
            protected Class<?> resolveProxyClass(String[] interfaces)
                throws IOException, ClassNotFoundException {
              // Note This logic was copied from ObjectInputStream.java in the
              // JDK, and then modified to use the thread context class loader instead of the
              // "latest" loader that is used there.
              ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
              ClassLoader nonPublicLoader = null;
              boolean hasNonPublicInterface = false;

              // define proxy in class loader of non-public interface(s), if any
              Class<?>[] classObjs = new Class<?>[interfaces.length];
              for (int i = 0; i < interfaces.length; i++) {
                Class<?> cl = Class.forName(interfaces[i], false, classLoader);
                if ((cl.getModifiers() & Modifier.PUBLIC) == 0) {
                  if (hasNonPublicInterface) {
                    if (nonPublicLoader != cl.getClassLoader()) {
                      throw new IllegalAccessError(
                          "conflicting non-public interface class loaders");
                    }
                  } else {
                    nonPublicLoader = cl.getClassLoader();
                    hasNonPublicInterface = true;
                  }
                }
                classObjs[i] = cl;
              }
              try {
                return Proxy.getProxyClass(
                    hasNonPublicInterface ? nonPublicLoader : classLoader, classObjs);
              } catch (IllegalArgumentException e) {
                throw new ClassNotFoundException(null, e);
              }
            }
          };
     // Replacing DeferredTask to Runnable as we have DeferredTask in the 2 classloaders
     // (runtime and application), but we cannot cast one with another one.
     return (Runnable) objectStream.readObject();
    } catch (ClassNotFoundException | IOException | ClassCastException e) {
      throw new DeferredTaskException(e);
    }
  }
}
