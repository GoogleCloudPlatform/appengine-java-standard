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

import static com.google.apphosting.runtime.AppEngineConstants.GAE_RUNTIME;
import static com.google.apphosting.runtime.AppEngineConstants.IGNORE_RESPONSE_SIZE_LIMIT;

import com.google.apphosting.base.AppVersionKey;
import com.google.apphosting.base.protos.AppinfoPb;
import com.google.apphosting.base.protos.EmptyMessage;
import com.google.apphosting.base.protos.RuntimePb.UPRequest;
import com.google.apphosting.runtime.AppInfoFactory;
import com.google.apphosting.runtime.AppVersion;
import com.google.apphosting.runtime.LocalRpcContext;
import com.google.apphosting.runtime.MutableUpResponse;
import com.google.apphosting.runtime.ServletEngineAdapter;
import com.google.apphosting.runtime.anyrpc.EvaluationRuntimeServerInterface;
import com.google.apphosting.runtime.jetty.http.JettyHttpHandler;
import com.google.apphosting.runtime.jetty.proxy.JettyHttpProxy;
import com.google.common.flogger.GoogleLogger;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

/**
 * This is an implementation of ServletEngineAdapter that uses the third-party Jetty servlet engine.
 */
public class JettyServletEngineAdapter implements ServletEngineAdapter {
  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();
  private static final int MIN_THREAD_POOL_THREADS = 0;
  private static final int MAX_THREAD_POOL_THREADS = 100;

  static {
    // Set legacy system property to dummy value because external libraries
    // (google-auth-library-java)
    // test if this value is null to decide whether it is Java 7 runtime.
    System.setProperty("org.eclipse.jetty.util.log.class", "DEPRECATED");
  }

  private Server server;
  private AppVersionHandler appVersionHandler;

  public JettyServletEngineAdapter() {}

  @Override
  public void start(String serverInfo, ServletEngineAdapter.Config runtimeOptions) {
    QueuedThreadPool threadPool =
        new QueuedThreadPool(MAX_THREAD_POOL_THREADS, MIN_THREAD_POOL_THREADS);
    // Try to enable virtual threads if requested and on java21:
    if (Boolean.getBoolean("appengine.use.virtualthreads")
        && ("java21".equals(GAE_RUNTIME) || "java25".equals(GAE_RUNTIME))) {
      int maxParallelism = getMaxSafeCarrierParallelism();
      Executor virtualThreadsExecutor =
          new ForkJoinPool(
              maxParallelism, ForkJoinPool.defaultForkJoinWorkerThreadFactory, null, true);
      threadPool.setVirtualThreadsExecutor(virtualThreadsExecutor);
      logger.atInfo().log(
          "Configuring Appengine web server virtual threads with capped carrier parallelism: %d",
          maxParallelism);
    }

    server =
        new Server(threadPool) {
          @Override
          public InvocationType getInvocationType() {
            return InvocationType.BLOCKING;
          }
        };

    AppVersionHandlerFactory appVersionHandlerFactory =
        AppVersionHandlerFactory.newInstance(server, serverInfo);
    appVersionHandler = new AppVersionHandler(appVersionHandlerFactory);
    server.setHandler(appVersionHandler);

    boolean ignoreResponseSizeLimit = Boolean.getBoolean(IGNORE_RESPONSE_SIZE_LIMIT);

    AppInfoFactory appInfoFactory;
    AppVersionKey appVersionKey;
    /* The init actions are not done in the constructor as they are not used when testing */
    try {
      String appRoot = runtimeOptions.applicationRoot();
      String appPath = runtimeOptions.fixedApplicationPath();
      appInfoFactory = new AppInfoFactory(System.getenv());
      AppinfoPb.AppInfo appinfo = appInfoFactory.getAppInfoFromFile(appRoot, appPath);
      // TODO Should we also call ApplyCloneSettings()?
      LocalRpcContext<EmptyMessage> context = new LocalRpcContext<>(EmptyMessage.class);
      EvaluationRuntimeServerInterface evaluationRuntimeServerInterface =
          Objects.requireNonNull(runtimeOptions.evaluationRuntimeServerInterface());
      evaluationRuntimeServerInterface.addAppVersion(context, appinfo);
      context.getResponse();
      appVersionKey = AppVersionKey.fromAppInfo(appinfo);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }

    server.insertHandler(
        new JettyHttpHandler(
            runtimeOptions, appVersionHandler.getAppVersion(), appVersionKey, appInfoFactory));
    JettyHttpProxy.insertHandlers(server, ignoreResponseSizeLimit);
    server.addConnector(JettyHttpProxy.newConnector(server, runtimeOptions));

    try {
      server.start();
    } catch (Exception ex) {
      // TODO: Should we have a wrapper exception for this
      // type of thing in ServletEngineAdapter?
      throw new RuntimeException(ex);
    }
  }

  @Override
  public void stop() {
    try {
      server.stop();
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  @Override
  public void addAppVersion(AppVersion appVersion) {
    appVersionHandler.addAppVersion(appVersion);
  }

  @Override
  public void setSessionStoreFactory(com.google.apphosting.runtime.SessionStoreFactory factory) {
    // No op with the new Jetty Session management.
  }

  @Override
  public void serviceRequest(UPRequest upRequest, MutableUpResponse upResponse) throws Exception {
    throw new UnsupportedOperationException(
        "serviceRequest is not supported in HTTP connector mode");
  }

  /**
   * Calculates a safe maximum carrier thread count based on GAE sandbox memory boundaries to
   * prevent OS scheduling thrashing on fractional/low-core instances.
   */
  static int getMaxSafeCarrierParallelism() {
    return getMaxSafeCarrierParallelism(System.getenv("GAE_MEMORY_MB"));
  }

  static int getMaxSafeCarrierParallelism(String memoryMbStr) {
    if (memoryMbStr == null || memoryMbStr.isEmpty()) {
      return 4; // Conservative default cap for standard runtimes
    }
    try {
      int memoryMb = Integer.parseInt(memoryMbStr);
      return memoryMb <= 512 ? 1 : memoryMb <= 1024 ? 2 : 4;
    } catch (NumberFormatException e) {
      return 4; // Safety Fallback
    }
  }
}
