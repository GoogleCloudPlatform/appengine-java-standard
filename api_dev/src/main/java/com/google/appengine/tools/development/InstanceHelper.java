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

import com.google.appengine.tools.development.AbstractContainerService.LocalInitializationEnvironment;
import com.google.appengine.tools.development.InstanceStateHolder.InstanceState;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.Environment;
import com.google.apphosting.utils.config.AppEngineWebXml;
import com.google.apphosting.utils.config.WebModule;
import com.google.common.flogger.GoogleLogger;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;

/**
 * Utility functions to access a server instance.
 */
public class InstanceHelper {
  // This should be kept in sync with
  // com.google.apphosting.utils.jetty.DevAppEngineWebAppContext
  private static final String X_GOOGLE_DEV_APPSERVER_SKIPADMINCHECK =
      "X-Google-DevAppserver-SkipAdminCheck";
  private static final int AH_REQUEST_INFINITE_TIMEOUT = 0;
  private static final int AH_REQUEST_DEFAULT_TIMEOUT = 30 * 1000;

  private static final GoogleLogger LOGGER = GoogleLogger.forEnclosingClass();

  private final String serverOrBackendName;
  private final int instance;
  private final InstanceStateHolder instanceStateHolder;
  private final ContainerService containerService;

  /**
   * Constructs an {@link InstanceHelper}.
   *
   * @param serverOrBackendName For server instances the server name and for backend instances the
   *     backend name.
   * @param instance The instance number or -1 for load balancing servers and automatic servers.
   * @param instanceStateHolder Holder for the instances state.
   * @param containerService The container service for the instance.
   */
  InstanceHelper(String serverOrBackendName, int instance, InstanceStateHolder instanceStateHolder,
      ContainerService containerService) {
    this.serverOrBackendName = serverOrBackendName;
    this.instance = instance;
    this.instanceStateHolder = instanceStateHolder;
    this.containerService = containerService;
  }

  /**
   * Triggers an HTTP GET to /_ah/start in a background thread
   *
   * <p>This method will keep on trying until it receives a non-error response code from the server.
   *
   * @param runOnSuccess {@link Runnable#run} invoked when the startup request succeeds.
   */
  public void sendStartRequest(final Runnable runOnSuccess) {
    LOGGER.atFiner().withCause(new Exception("Start sendStartRequest")).log(
        "Entering send start request for serverOrBackendName=%s instance=%s",
        serverOrBackendName, instance);

    if (instance < 0) {
      throw new IllegalStateException("Attempt to send a start request to server/backend "
          + serverOrBackendName + " instance " + instance);
    }

    InstanceState unchangedState =
        instanceStateHolder.testAndSetIf(InstanceState.RUNNING_START_REQUEST,
            InstanceState.SLEEPING);
    if (unchangedState == null) {
      // We take this branch if the instance was in the InstanceState.SLEEPING
      // state and we have updated it. In this case we sent the startup message.
      // We send the startup request to the servlet in a separate thread to not
      // block the main thread and to allow multiple servers to run the
      // /_ah/start operation in parallel. Also, note a server can loop forever
      // on /_ah/start
      Thread requestThread =
          new Thread(
              () -> {
                sendStartRequest(AH_REQUEST_INFINITE_TIMEOUT, runOnSuccess);
              });
      requestThread.setDaemon(true);
      requestThread.setName(
          "BackendServersStartRequestThread." + instance + "." + serverOrBackendName);
      requestThread.start();
    } else if (unchangedState != InstanceState.RUNNING_START_REQUEST
        && unchangedState != InstanceState.RUNNING) {
      // We take this branch if the instance was in an invalid state.
      //
      // In the case some other thread is sending or has sent the startup
      // request we do not send another but do not report an error as this
      // is an expected situation.
      InstanceStateHolder.reportInvalidStateChange(serverOrBackendName, instance,
          unchangedState, InstanceState.RUNNING_START_REQUEST, InstanceState.SLEEPING);
    }
  }

  /**
   * Triggers an HTTP GET to /_ah/start
   *
   *  This method will keep on trying until it receives a non-error response
   * code from the server.
   *
   * @param timeoutInMs Timeout in milliseconds, 0 indicates no timeout.
   * @param runOnSuccess {@link Runnable#run} invoked when the startup request succeeds.
   */
  private void sendStartRequest(int timeoutInMs, Runnable runOnSuccess) {
    try {
      // use http as http is used in prod
      String urlString = String.format("http://%s:%d/_ah/start", containerService.getAddress(),
          containerService.getPort());
      LOGGER.atFiner().log("sending start request to: %s", urlString);

      // need to use HttpClient as the URL stream handler is using the
      // URLFetch API
      RequestConfig requestConfig = RequestConfig.custom()
          .setConnectTimeout(timeoutInMs)
          .setSocketTimeout(timeoutInMs)
          .setConnectionRequestTimeout(timeoutInMs)
          .build();
      HttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(requestConfig).build();

      HttpGet request = new HttpGet(urlString);
      // set the magic header that tells the dev appserver to skip
      // authentication - this lets us hit protected urls
      request.addHeader(X_GOOGLE_DEV_APPSERVER_SKIPADMINCHECK, "true");
      try {
        HttpResponse response = httpClient.execute(request);
        int returnCode = response.getStatusLine().getStatusCode();

        byte[] buffer = new byte[1024];
        InputStream in = response.getEntity().getContent();
        while (in.read(buffer) != -1) {
          // Servers looping forever on /_ah/start could
          // potentially generate a lot of data. Grab it in small batches and
          // drop to make sure we don't end up loading it into ram.
        }
        if ((returnCode >= 200 && returnCode < 300) || returnCode == 404) {
          LOGGER.atFine().log(
              "backend server %d.%s request to /_ah/start completed, code=%d",
              instance, serverOrBackendName, returnCode);
          instanceStateHolder.testAndSet(InstanceState.RUNNING,
              InstanceState.RUNNING_START_REQUEST);
          runOnSuccess.run();
        } else {
          LOGGER.atWarning().log(
              "Start request to /_ah/start on server %d.%s failed (HTTP status=%s). Retrying...",
              instance, serverOrBackendName, response.getStatusLine());
          // in prod the server will retry start requests until a 2xx or 404
          // return code is received. Sleep here to rate limit retries.
          Thread.sleep(1000);
          sendStartRequest(timeoutInMs, runOnSuccess);
        }
      } finally {
        request.releaseConnection();
      }
    } catch (MalformedURLException e) {
      LOGGER.atSevere().log(
          "Unable to send start request to server: %d.%s, MalformedURLException: %s",
          instance, serverOrBackendName, e.getMessage());
    } catch (Exception e) {
      LOGGER.atWarning().log(
          "Got exception while performing /_ah/start request on server: %d.%s, %s: %s",
          instance, serverOrBackendName, e.getClass().getName(), e.getMessage());
    }
  }

  /**
   * This method will trigger any shutdown hooks registered with the current
   * server.
   *
   * Some class loader trickery is required to make sure that we get the
   * {@link com.google.appengine.api.LifecycleManager} responsible for this
   * server instance.
   */
  private void triggerLifecycleShutdownHookImpl() {
    // store the current environment
    Environment prevEnvironment = ApiProxy.getCurrentEnvironment();
    try {
      // get the classloader used by user servlets
      ClassLoader serverClassLoader = containerService.getAppContext().getClassLoader();

      // Get hold of the LifeCycleManager instance
      Class<?> lifeCycleManagerClass =
          Class.forName("com.google.appengine.api.LifecycleManager", true, serverClassLoader);
      Method lifeCycleManagerGetter = lifeCycleManagerClass.getMethod("getInstance");
      Object userThreadLifeCycleManager = lifeCycleManagerGetter.invoke(null, new Object[0]);

      // get the shutdown method with the specified max time (not enforced),
      Method beginShutdown = lifeCycleManagerClass.getMethod("beginShutdown", long.class);
      // we need to set the environment for it to be able to get the version
      AppEngineWebXml appEngineWebXml = containerService.getAppEngineWebXmlConfig();
      // For the LocalInitializationEnvironment we need the actual server name. For backends
      // this will be 'default'. Confusingly a serverOrBackendName is NOT a server name in the case
      // of a backend but an id from backends.xml which is a major version in prod but not yet in
      // dev.
      String moduleName = WebModule.getModuleName(appEngineWebXml);
      // TODO: Set version to backend name after confirming with ludo@
      // for backends call DevAppServerModulesFilter.injectBackendServiceCurrentApiInfo.
      ApiProxy.setEnvironmentForCurrentThread(
          new LocalInitializationEnvironment(
              appEngineWebXml.getAppId(),
              moduleName,
              appEngineWebXml.getMajorVersionId(),
              instance,
              containerService.getPort()));

      // do the shutdown, catch any exceptions in the user supplied code.
      // beginShutdown is blocking and will return when the user supplied
      // shutdown hook completes
      try {
        beginShutdown.invoke(userThreadLifeCycleManager, AH_REQUEST_DEFAULT_TIMEOUT);
      } catch (Exception e) {
        LOGGER.atWarning().log(
            "got exception when running shutdown hook on server %d.%s",
            instance, serverOrBackendName);
        // print the stack trace to help the user debug
        e.printStackTrace();
      }
    } catch (Exception e) {
      LOGGER.atSevere().log(
          "Exception during reflective call to LifecycleManager.beginShutdown on server %d.%s, "
              + "got %s: %s",
          instance, serverOrBackendName, e.getClass().getName(), e.getMessage());
    } finally {
      // restore the environment
      ApiProxy.setEnvironmentForCurrentThread(prevEnvironment);
    }
  }

  /**
   * Shut down the server.
   *
   * <p>Will trigger any shutdown hooks installed by the {@link
   * com.google.appengine.api.LifecycleManager}
   *
   * @throws Exception
   */
  public void shutdown() throws Exception {
    synchronized (instanceStateHolder) {
      // TODO: This calls user code, can we do this outside the synchronized block.
      if (instanceStateHolder.test(InstanceState.RUNNING, InstanceState.RUNNING_START_REQUEST)) {
        triggerLifecycleShutdownHookImpl();
      }
      containerService.shutdown();
      instanceStateHolder.set(InstanceState.SHUTDOWN);
    }
  }
}
