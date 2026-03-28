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
connections to API backends. This limit helps prevent memory exhaustion on
smaller App Engine instance types (like F1 or F2) and avoids overwhelming
backend services during sudden traffic spikes or high rates of failing requests
with aggressive retry logic. If the connection limit is reached, subsequent
requests are queued until a connection becomes available.

### Configuration

You can configure the Jetty client using the following environment variables:

*   **`APPENGINE_API_MAX_CONNECTIONS`**: Sets the maximum number of concurrent
    connections for API calls. If you observe API call latency or
    connection-related errors under high load, you might consider adjusting this
    value.
    *   Default: `100`
*   **`APPENGINE_API_CALLS_IDLE_TIMEOUT_MS`**: Sets the idle timeout in
    milliseconds for connections in the connection pool. Connections that are
    idle for longer than this duration may be closed.
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

When using the JDK client, you can configure its threading behavior:

*   **`appengine.api.use.virtualthreads`** (Java System Property): If set to `true`,
    the JDK client will use Java Virtual Threads (when available on the JVM) to
    handle API requests. This allows for a very high number of concurrent
    requests without being limited by platform thread stack sizes. Note, 
    it is different than `appengine.use.virtualthreads`
    *   Default: `false`

If `appengine.api.use.virtualthreads` is `false` or not set, the JDK client uses a
traditional thread pool model. In this mode, you can control the thread pool
size using an environment variable:

*   **`APPENGINE_API_MAX_CONNECTIONS`**: Sets the maximum number of threads in
    the pool for handling API calls. This limits the number of concurrent API
    calls, similar to how it works for the Jetty client. This variable is
    **ignored** if `appengine.api.use.virtualthreads` is set to `true`.
    *   Default: `100`



### Example

To change the API call path to use the JDK client instead of the Jetty client,
and to enable virtual threads for this client, add the following environment
variable and system property to `appengine-web.xml` and redeploy your
application:

```xml
<env-variables>
  <env-var name="APPENGINE_API_CALLS_USING_JDK_CLIENT" value="true" />
  <env-var name="APPENGINE_API_MAX_CONNECTIONS" value="100" />
  <env-var name="APPENGINE_API_CALLS_IDLE_TIMEOUT_MS" value="58" />
</env-variables>
<system-properties>
  <property name="appengine.api.use.virtualthreads" value="true" />
</system-properties>
```
