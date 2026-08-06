<!--
 Copyright 2021 Google LLC

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

     https://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
-->
<!-- disableFinding(LINE_OVER_80) -->
<!-- disableFinding(LINE_OVER_80_LINK) -->
<!-- disableFinding(SPACES) -->

# App Engine Standard Java: Virtual Threads Concurrency & Multi-CL Architecture Plan

This document details the end-to-end (E2E) verification plan for the Java 21+ Virtual Threads concurrency bug (b/514813839), explains how virtual thread scheduling behaves across App Engine connector modes, and provides the architectural mapping for all changelists in the current development workspace.

> **Important Configuration Note:** App Engine Standard Java applications exclusively use `WEB-INF/appengine-web.xml` for all application configuration, instance class sizing, system properties, and environment variables. `app.yaml` is not used by App Engine Standard Java runtimes.

---

## 1. Executive Summary & The Concurrency Bug (b/514813839)

Under Java 21 (prior to JEP 491 / Java 24 where synchronized monitor pinning was eliminated), web applications running on App Engine Standard with virtual threads enabled via the system property `-Dappengine.use.virtualthreads=true` in `appengine-web.xml` suffer from severe latency degradation, carrier thread pool starvation, and container deadlocks on fractional or low-core instance classes (such as F1 and F2 instances with $\le 1024\text{ MB}$ RAM).

### Root Causes

1. **Carrier Pool Thrashing:** By default, `VirtualThreads.getDefaultVirtualThreadsExecutor()` delegates to `ForkJoinPool.commonPool()`, which sizes itself to physical host CPU core counts (often 64+ cores on container host machines). When container CPU share is throttled (e.g., 0.5 CPU on an F1 instance), 64+ active carrier threads fight for CPU slices, causing severe OS context switching thrashing.
2. **Monitor Lock Carrier Pinning:** When application logs are written, `AppLogsWriter` flushes log batches via an asynchronous gRPC call (`ApiProxyDelegate.makeAsyncCall("logservice", "Flush")`). Previously, when calling `flushAndWait()`, the virtual thread invoked `slowFlush.get()` inside a synchronized monitor block (`synchronized (this)` in `api/setup`, and `synchronized (lock)` in `runtime/impl`). Because Java 21 virtual threads cannot unmount while holding a synchronized monitor, the virtual thread pinned its OS carrier thread while waiting for network I/O.

### The Starvation Deadlock Under Load

On an F1 instance where carrier threads are bounded or throttled:
1. Request A calls `flushAndWait()` inside `synchronized`, pinning its carrier thread waiting for the gRPC network call.
2. Request B arrives concurrently and tries to write a log (`addLogRecordAndMaybeFlush`), attempting to enter `synchronized (lock)` and blocking.
3. Because Request B is blocked on the monitor and Request A has pinned the carrier thread waiting for network I/O, the entire container deadlocks, request queues overflow, and `500 Internal Server Error` / `502 Bad Gateway` timeouts occur.

---

## 2. Global Workspace Plan: Multi-CL Architectural Relationship

Our development workspace contains three active pending changelists and one submitted prerequisite changelist that work in concert to overhaul concurrency safety, thread reuse cleanliness, container wiring, and dependency hygiene across the App Engine Java runtime stack:

```
+---------------------------------------------------------------------------------------------------+
|                                 APP ENGINE STANDARD JAVA RUNTIME                                  |
+---------------------------------------------------------------------------------------------------+
|  [CL 946974028] (Active Pending) Virtual Threads Carrier Capping & Lock Decoupling (b/514813839)  |
|  -> Bounds ForkJoinPool parallelism & moves gRPC I/O (waitForFlush) outside synchronized blocks.  |
+---------------------------------------------------------------------------------------------------+
|  [CL 946869615] (Active Pending) Datastore ThreadLocal Stack Eviction & Rollback Trap (b/494621464)|
|  -> Purges orphaned transaction state on pooled/reused threads (Jetty / Virtual Threads).         |
+---------------------------------------------------------------------------------------------------+
|  [CL 947510879] (Active Pending) Jetty 12 / 12.1 Jakarta EE 11 (ee11) & Servlet Wiring           |
|  -> Modernizes the web server container hosting our virtual threads executor.                     |
+---------------------------------------------------------------------------------------------------+
|  [CL 950649645] (Submitted / OCL 948873605) Third-Party Dependency Upgrades                      |
|  -> Upgrades unpinned libraries & removes deprecated bridges while preserving spec exclusions.    |
+---------------------------------------------------------------------------------------------------+
```

### Detailed Breakdown of Related CLs

* **CL 946974028 (b/514813839) — Virtual Threads Carrier Capping & Lock Decoupling (Active Pending):**
  * **Action:** Implements dynamic carrier parallelism capping in `JavaRuntimeMain.configureVirtualThreadParallelism()` and `JettyServletEngineAdapter.start()`:
    * $\le 512\text{ MB}$ (F1 Class / 0.5 CPU) $\rightarrow$ 1 carrier core
    * $\le 1024\text{ MB}$ (F2 Class / 1 CPU) $\rightarrow$ 2 carrier cores
    * $> 1024\text{ MB}$ (F4 / Backend Classes) $\rightarrow$ 4 carrier cores
  * In `AppLogsWriter`, extracts `pendingFlush` inside the monitor and awaits `waitForFlush(pendingFlush)` strictly outside `synchronized`, preventing carrier pinning during gRPC network I/O. Also strips circular `logger.info()` recursion and prevents premature flush nulling.

* **CL 946869615 (b/494621464) — Datastore Rollback Trap & ThreadLocal Stack Eviction (Active Pending):**
  * **Relationship:** Directly addresses thread reuse under concurrent request execution. In container environments where threads are pooled and reused across incoming requests (such as Jetty 12 `QueuedThreadPool` or virtual threads), if an application catches a transaction exception without rolling back, or if `BeginTransaction` fails, orphaned transaction state on `ThreadLocal` stacks poisons subsequent requests on that thread.
  * **Action:** Updates `doRollbackAsync()` in `InternalTransactionV3` to return a completed future when a transaction is inactive/failed, and adds lazy eviction in `TransactionStackImpl.peek()` to guarantee thread-local cleanliness under concurrent thread reuse.

* **CL 947510879 — Jetty 12.1 / EE 11 Jakarta Servlet Wiring (Active Pending):**
  * **Relationship:** Modernizes the web server engine (`JettyServletEngineAdapter`) that hosts our virtual threads executor.
  * **Action:** Wires up EE 11 and Jakarta Servlet API support across `runtime-impl-jetty121.jar`, `runtime-shared-jetty121-ee11.jar`, and `runtime_servlets.jar`, resolving strict dependency errors and missing `ee11` symbol exports so modern Java 21+ applications run cleanly on Jetty 12.1.

* **CL 950649645 (Submitted / OCL 948873605) — Dependency Upgrades & google-http-client-jackson Removal:**
  * **Relationship:** Ensures build reactor and runtime hygiene across all modules.
  * **Action:** Upgrades unpinned dependencies (Guava, Flogger, Protobuf, JUnit, Mockito, etc.) and removes the deprecated `google-http-client-jackson:1.29.2` bridge (replacing it with `google-http-client-jackson2:1.47.1`) while strictly protecting runtime spec boundaries (`<!-- keep -->` exclusions for Servlet API, Lucene 2.9.4, Jasper, etc.).

---

## 3. Connector Architecture: RPC Mode vs. HTTP Connector Mode

**Question:** Does `appengine.use.virtualthreads` work effectively in non-HTTP connector (RPC / CGI stubby) mode?  
**Answer:** Yes. Virtual threads operate identically across both RPC connector mode and HTTP connector mode.

### How Request Dispatches Work in Jetty 12 / 12.1 (`JettyServletEngineAdapter`)

1. **Connector Ingestion:**
   * **RPC Connector Mode (`appengine.use.HttpConnector=false` / default):** Incoming requests arrive from App Engine frontends via `DelegateConnector` (`rpcConnector`), which extends Jetty's `AbstractConnector`.
   * **HTTP Connector Mode (`appengine.use.HttpConnector=true`):** Incoming requests arrive directly over HTTP/HTTP2 via HTTP connectors configured by `AppVersionHandlerFactory`.
2. **Shared Thread Pool Execution:**
   * Both connectors are attached to the same Jetty `Server` instance and share the server's `QueuedThreadPool` (`server.getThreadPool()`).
   * In `DelegateConnector.java`, incoming RPC requests are dispatched via `getExecutor().execute(runnable)`, which resolves directly to `server.getThreadPool()`.
   * When `appengine.use.virtualthreads=true` is set, `JettyServletEngineAdapter.start()` explicitly invokes `threadPool.setVirtualThreadsExecutor(virtualThreadsExecutor)`. Consequently, whether running over default RPC connectors or HTTP connectors, Jetty dispatches 100% of incoming request tasks onto virtual threads.

---

## 4. Production E2E Load Test Runbook

To definitively prove in production that the bug causes starvation/timeouts under load and that CL 946974028 eliminates it, follow this E2E testing protocol using the custom runtime bundling method documented in `TRYLATESTBITSINPROD.md`.

### Step 1: Build Local Runtime Deployment Jars

From your local checkout containing CL 946974028, build the runtime deployment artifacts:

```bash
./mvnw clean install -DskipTests
```

This produces the 7 core runtime jars under `runtime/deployment/target/runtime-deployment-*/`:
* `runtime-impl-jetty12.jar`
* `runtime-impl-jetty121.jar`
* `runtime-main.jar`
* `runtime-shared-jetty12.jar`
* `runtime-shared-jetty12-ee10.jar`
* `runtime-shared-jetty121-ee8.jar`
* `runtime-shared-jetty121-ee11.jar`

### Step 2: Configure the Test Application Harness

Create a Java 21 App Engine Standard test application (e.g., Servlet or Spring Boot on Jetty 12/12.1) configured with an `F1` instance class in `WEB-INF/appengine-web.xml` (`<instance-class>F1</instance-class>`, giving $\le 512\text{ MB}$ RAM, which bounds the carrier pool to 1 thread under our fix).

In your app's `pom.xml`, use `copy-rename-maven-plugin` (as detailed in `TRYLATESTBITSINPROD.md`) to copy the locally built runtime jars into `WEB-INF/lib/` and rename them to root names (e.g., `runtime-main.jar`).

In `WEB-INF/appengine-web.xml`, configure the custom entrypoint and system properties:

```xml
<?xml version="1.0" encoding="utf-8"?>
<appengine-web-app xmlns="http://appengine.google.com/ns/1.0">
  <runtime>java21</runtime>
  <instance-class>F1</instance-class>
  <app-engine-apis>true</app-engine-apis>

  <system-properties>
    <property name="appengine.use.virtualthreads" value="true"/>
  </system-properties>

  <entrypoint>
    java
    --add-opens java.base/java.lang=ALL-UNNAMED
    --add-opens java.base/java.nio.charset=ALL-UNNAMED
    --add-opens java.base/java.util.concurrent=ALL-UNNAMED
    --add-opens java.logging/java.util.logging=ALL-UNNAMED
    -showversion -XX:+PrintCommandLineFlags
    -Djava.class.path=runtime-main.jar
    -Dclasspath.runtimebase=.:
    com/google/apphosting/runtime/JavaRuntimeMainWithDefaults
    --fixed_application_path=.
    .
  </entrypoint>
</appengine-web-app>
```

Add a test servlet endpoint `/test-log-starvation` designed to force concurrent log buffering and async gRPC flushing.

Depending on whether your App Engine application is running with modern **Jakarta EE** (default for Java 21 on Jetty 12/12.1, using `jakarta.servlet.*`) or legacy **Java EE 8** (enabled via `<property name="appengine.use.EE8" value="true"/>` in `appengine-web.xml`, using `javax.servlet.*`), implement the test servlet as follows:

#### Jakarta EE (EE 10 / EE 11 — `jakarta.servlet.*`, Default for Java 21)

```java
package com.google.appengine.test;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Logger;

@WebServlet("/test-log-starvation")
public class LogStarvationServlet extends HttpServlet {
  private static final Logger logger = Logger.getLogger(LogStarvationServlet.class.getName());

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    // 1. Generate a log payload larger than SMALL_FLUSH (~20KB) to trigger an async gRPC flush
    char[] chars = new char[25000];
    Arrays.fill(chars, 'x');
    logger.info("Load test log batch: " + new String(chars));

    // 2. Simulate brief application processing while the async gRPC log flush is in flight
    try {
      Thread.sleep(25);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    // 3. Request completion invokes RequestManager.clearEnvironmentForCurrentThread() -> ApiProxy.flushLogs(),
    // which calls AppLogsWriter.flushAndWait(). Under legacy locking, this blocks inside synchronized (lock).
    resp.setStatus(HttpServletResponse.SC_OK);
    resp.getWriter().write("OK");
  }
}
```

#### Java EE 8 (EE 8 — `javax.servlet.*`, for apps with `<property name="appengine.use.EE8" value="true"/>`)

```java
package com.google.appengine.test;

import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Logger;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet("/test-log-starvation")
public class LogStarvationServletEE8 extends HttpServlet {
  private static final Logger logger = Logger.getLogger(LogStarvationServletEE8.class.getName());

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    char[] chars = new char[25000];
    Arrays.fill(chars, 'x');
    logger.info("Load test log batch: " + new String(chars));

    try {
      Thread.sleep(25);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    resp.setStatus(HttpServletResponse.SC_OK);
    resp.getWriter().write("OK");
  }
}
```

### Step 3: Phase 1 — Prove the Bug (Baseline Load Test on Unpatched Runtime)

Deploy the test application to production without the CL 946974028 fixes (or by deploying against standard production base runtime jars) with virtual threads enabled in `WEB-INF/appengine-web.xml`:

```xml
<system-properties>
  <property name="appengine.use.virtualthreads" value="true"/>
</system-properties>
```

*(Alternatively, configure via `<env-variables><env-var name="JAVA_TOOL_OPTIONS" value="-Dappengine.use.virtualthreads=true"/></env-variables>` in `appengine-web.xml`).*

Execute a concurrent load test using ApacheBench (`ab`), `hey`, or Locust:

```bash
# Send 50 concurrent requests continuously for 45 seconds
hey -c 50 -z 45s https://<your-test-app>.appspot.com/test-log-starvation
```

#### Observed Metrics & Failure Proof (The Bug)
* **Container Deadlocks & Timeouts:** Throughput collapses. You will observe a high percentage of `500 Internal Server Error` and `502 Bad Gateway` responses as request queues overflow.
* **Carrier Pinning:** In Cloud Logging / Sherlog trace analysis, request threads show long blocking times waiting on monitor acquisition inside `AppLogsWriter.flushAndWait()` while the single F1 carrier thread is pinned in `slowFlush.get()`.
* **Instance Thrashing:** Cloud Monitoring shows extreme CPU throttling and container instance restarts due to health-check starvation.

---

### Step 4: Phase 2 — Prove the Fix (Verification Load Test with CL 946974028)

Deploy the test application booted with your custom bundled runtime jars containing CL 946974028. Execute the load test across both connector modes:

#### Test A: Default RPC Connector Mode
In `WEB-INF/appengine-web.xml`, verify default RPC mode:

```xml
<system-properties>
  <property name="appengine.use.virtualthreads" value="true"/>
  <property name="appengine.use.HttpConnector" value="false"/>
</system-properties>
```

#### Test B: HTTP Connector Mode
In `WEB-INF/appengine-web.xml`, switch to HTTP connector mode:

```xml
<system-properties>
  <property name="appengine.use.virtualthreads" value="true"/>
  <property name="appengine.use.HttpConnector" value="true"/>
</system-properties>
```

Execute the exact same load test against both deployments:

```bash
hey -c 50 -z 45s https://<your-test-app>.appspot.com/test-log-starvation
```

#### Observed Metrics & Verification Proof (The Fix)
* **Zero Deadlocks & Zero Timeouts:** 100% of requests return `200 OK` without a single 500/502 error across both RPC and HTTP connector modes.
* **Smooth, Stable Throughput:** Because `waitForFlush(pendingFlush)` executes outside `synchronized`, virtual threads unmount cleanly during gRPC network waits without pinning the 1-carrier F1 pool. Concurrent requests acquire the monitor immediately, buffer their logs, and maintain stable p95/p99 latency under load.
* **Deterministic Parallelism:** Inspection of thread dumps or runtime telemetry confirms `ForkJoinPool` carrier threads remain bounded to 1 core on F1 instances, eliminating OS scheduling thrashing.