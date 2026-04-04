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
# App Engine API Client Configuration

The App Engine Java runtime communicates with Google Cloud APIs (such as
Datastore, Task Queue, and Memcache) using an HTTP-based RPC mechanism. The
runtime includes two HTTP client implementations for this purpose: a default
client based on Jetty and an alternative client using the JDK's built-in HTTP
facilities.

This document describes both clients and how to configure them using environment
variables and Java system properties.

## Jetty HTTP Client (Default)

The Jetty HTTP client is the default client used by the runtime. It is based on
the [Eclipse Jetty](https://eclipse.dev/jetty/) HTTP client and is optimized for
high performance and efficient connection management.

By default, the client is configured to allow a maximum of 100 concurrent
threads and 100 concurrent connections for API calls. These limits help
prevent memory exhaustion on smaller App Engine instance types (like F1 or F2)
and avoid overwhelming backend services during sudden traffic spikes or high
rates of failing requests with aggressive retry logic. If the thread limit is
reached, subsequent requests are queued until a thread becomes available.

### Configuration

You can configure the Jetty client using the following environment variables
and system properties:

*   **`APPENGINE_API_MAX_CONNECTIONS`** (Environment Variable): Sets the
    maximum number of concurrent connections in the HTTP client pool.
    *   Default: `100`
*   **`APPENGINE_API_MAX_THREADS`** (Environment Variable): Sets the
    maximum number of concurrent threads for executing API calls. If unset,
    this also defaults to 100. This is the most direct way to control API
    call throughput and prevent backend overload.
    *   Default: `100`
*   **`APPENGINE_API_CALLS_IDLE_TIMEOUT_MS`** (Environment Variable): Sets
    the idle timeout in milliseconds for connections in the connection pool.
    Connections that are idle for longer than this duration may be closed.
    *   Default: `58000` (58 seconds)

## JDK HTTP Client

The JDK HTTP client uses Java's built-in `HttpURLConnection` for API calls. It
is provided as an alternative to the Jetty client and can be useful for
troubleshooting network- or connection-related issues that might be specific to
one client implementation. In general, it may be less performant than the
default Jetty client.

To use the JDK client instead of the Jetty client, set the
`APPENGINE_API_CALLS_USING_JDK_CLIENT` environment variable to any non-null value
(e.g., `true`).

### Configuration

When using the JDK client, you can configure its behavior with the following
settings:

*   **`appengine.api.use.virtualthreads`** (Java System Property): If set to `true`,
    the JDK client will use Java Virtual Threads (when available on the JVM) to
    handle API requests. This allows for a very high number of concurrent
    requests without being limited by platform thread stack sizes. Note, 
    it is different than `appengine.use.virtualthreads`
    *   Default: `false`

If `appengine.api.use.virtualthreads` is `false` or not set, the JDK client uses a
traditional thread pool model. In this mode, you can control the thread pool
size using an environment variable:

*   **`APPENGINE_API_MAX_CONNECTIONS`** (Environment Variable): Sets the
    maximum number of concurrent connections in the HTTP client pool.
    *   Default: `100`
*   **`APPENGINE_API_MAX_THREADS`** (Environment Variable): Sets the
    maximum number of threads in the pool for handling API calls. This limits
    the number of concurrent API calls, similar to how it works for the Jetty
    client. This variable is **ignored** if `appengine.api.use.virtualthreads`
    is set to `true`.
    *   Default: `100`

## Datastore-Specific Configuration

### `beginTransaction` Retries

In addition to the HTTP client settings, the Datastore client library includes
specific retry logic for `beginTransaction()` calls. These calls can fail with
transient errors such as `DatastoreFailureException`, `DatastoreTimeoutException`,
or `ApiProxy.RPCFailedException`, especially under high contention when
multiple transactions attempt to access the same entity group simultaneously.

To handle this, `DatastoreService.beginTransaction()` automatically retries
failed attempts with exponential backoff, starting at 100ms. You can
configure the number of retry attempts using a system property:

*   **`appengine.datastore.retries`** (Java System Property): The maximum
    number of times to retry a `beginTransaction` call if it fails with
    `DatastoreFailureException`, `DatastoreTimeoutException`, or
    `ApiProxy.RPCFailedException`. This retry logic applies only to
    `beginTransaction` calls; other Datastore operations are not retried
    by this mechanism.
    *   Default: `1`


```xml
<system-properties>
  <property name="appengine.datastore.retries" value="3" />
</system-properties>
```

## Recommended value for Jetty 12.1 / Java 25
The Java 25 runtime environment is highly performant, and in rare cases of very high throughput,
this can lead to backend services (like Datastore) being temporarily overloaded,
which may result in exceptions like `DatastoreFailureException: Internal Datastore Error`.
If you encounter such issues, you can throttle the rate of API calls by reducing
the maximum number of concurrent threads used by the API client.
We recommend starting with a lower value and adjusting as needed:
`APPENGINE_API_MAX_THREADS=50`

## Configuring via `appengine-web.xml`

You can set these options by adding `<env-variables>` and
`<system-properties>` sections to your `appengine-web.xml` file.

For example, to switch to the JDK client, enable virtual threads, increase
the thread limit to 200, and set Datastore `beginTransaction` retries to 3,
you would add:

```xml
<env-variables>
  <env-var name="APPENGINE_API_CALLS_USING_JDK_CLIENT" value="true" />
  <env-var name="APPENGINE_API_MAX_CONNECTIONS" value="200" />
  <env-var name="APPENGINE_API_MAX_THREADS" value="200" />
</env-variables>
<system-properties>
  <property name="appengine.api.use.virtualthreads" value="true" />
  <property name="appengine.datastore.retries" value="3" />
</system-properties>
```
