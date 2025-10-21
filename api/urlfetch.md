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

# Google App Engine URL Fetch API Documentation

*   [URL Fetch Service](#url-fetch-service)
    *   [Overview](#overview)
    *   [Request Protocols](#request-protocols)
    *   [Request Methods](#request-methods)
    *   [Request Proxying](#request-proxying)
    *   [Request Headers](#request-headers)
    *   [Request Timeouts](#request-timeouts)
    *   [Synchronous vs. Asynchronous Requests](#synchronous-vs-asynchronous-requests)
    *   [Secure Connections and HTTPS](#secure-connections-and-https)
    *   [Responses](#responses)
    *   [Development Server](#development-server)
    *   [Quotas and Limits for URL Fetch](#quotas-and-limits-for-url-fetch)
*   [HTTPS Requests](#https-requests)
    *   [Overview](#overview)
    *   [Recommended Approach](#recommended-approach)
    *   [Using Standard Runtime Network Classes](#using-standard-runtime-network-classes)
    *   [Using URL Fetch](#using-url-fetch)
    *   [Issuing an HTTP Request](#issuing-an-http-request)
    *   [Setting a Request Timeout](#setting-a-request-timeout)
    *   [Setting Headers](#setting-headers)
    *   [Disabling Redirects](#disabling-redirects)
    *   [Issuing an HTTPS Request](#issuing-an-https-request)
    *   [Issuing an Asynchronous Request](#issuing-an-asynchronous-request)
    *   [Issuing a Request to Another App Engine App](#issuing-a-request-to-another-app-engine-app)

## URL Fetch Service

### Overview

The URL Fetch service allows applications to issue HTTP and HTTPS requests and
receive responses.

### Request Protocols

Applications can fetch URLs using HTTP or HTTPS. The protocol is inferred from
the target URL. Valid port ranges: - 80–90 - 440–450 - 1024–65535 If no port is
specified, the default is implied by the protocol (80 for HTTP, 443 for HTTPS).

### Request Methods

Using standard Java `java.net.URLConnection`, any HTTP method is supported.
Using the URL Fetch service, the following methods are available: - GET - POST -
PUT - HEAD - DELETE - PATCH Requests can include HTTP headers and, for POST,
PUT, and PATCH, a payload.

### Request Proxying

The URL Fetch service uses an HTTP/1.1 compliant proxy. To prevent endless
recursion, a request handler cannot fetch its own URL. However, endless
recursion is still possible through other means, so exercise caution with
user-supplied URLs.

### Request Headers

Applications can set HTTP headers on outgoing requests. 

**Default behavior**:
When sending a POST request without an explicit Content-Type header, it defaults
to `application/x-www-form-urlencoded`.
 **Protected headers** (cannot be
modified by applications): - Content-Length - Host - Vary - Via -
X-Appengine-Inbound-Appid - X-Forwarded-For - X-ProxyUser-IP App Engine sets
these headers to accurate values. 
**Headers indicating App Engine requests**: -
**User-Agent**: Can be modified, but App Engine appends an identifier in the
format `"AppEngine-Google; (+http://code.google.com/appengine; appid: APPID)"`
**X-Appengine-Inbound-Appid**: Cannot be modified; added automatically by URL
Fetch when `follow redirects` is set to False

### Request Timeouts

**Default timeout**: 10 seconds **Maximum deadline**: 60 seconds for HTTP(S)
requests and Task Queue/cron job requests When using `URLConnection` with URL
Fetch, the service uses `setConnectTimeout()` plus `setReadTimeout()` as the
deadline.

### Synchronous vs. Asynchronous Requests

**Synchronous requests**: The fetch call waits until the remote host returns a
result, then returns control. If the maximum wait time is exceeded, an exception
is raised. **Asynchronous requests**: URL Fetch starts the request and returns
immediately with an object. The application can perform other tasks while the
URL is being fetched. When results are needed, calling a method on the object
waits for the request to finish if necessary. If pending requests remain when
the request handler exits, the server waits for all requests to either return or
reach their deadline.

### Secure Connections and HTTPS

Applications can fetch URLs securely using HTTPS. Request and response data are
transmitted in encrypted form. By default, the URL Fetch proxy validates the
host it contacts, detecting man-in-the-middle attacks between App Engine and the
remote host.

### Responses

The URL Fetch service returns all response data: code, headers, and body. By
default, if a redirect response is received, the service follows up to five
redirects, then returns the final resource. Redirects can be disabled to return
redirect responses to the application instead. **Response size limit**: If the
response exceeds the maximum size limit, the service raises an exception. The
response can be configured to truncate instead of raising an exception.

### Development Server

On the App Engine development server, URL Fetch calls are handled locally. The
server fetches URLs by contacting remote hosts directly using the computer's
network configuration. Ensure your computer can access the remote hosts you're
testing.

### Quotas and Limits for URL Fetch

The Java runtime allows use of standard `java.net.URLConnection` instead of
URLFetch, where quota and limit considerations don't apply. For URL Fetch
service quotas, see the Quotas documentation.

**URL Fetch Limits**:

Limit                                              | Amount
-------------------------------------------------- | ------------
Request size                                       | 10 megabytes
Request header size                                | 16 KB
Response size                                      | 32 megabytes
Maximum deadline (request handler)                 | 60 seconds
Maximum deadline (Task Queue and cron job handler) | 60 seconds

## HTTPS Requests

### Overview

This section describes issuing HTTP(S) requests from App Engine apps using URL
Fetch for second-generation runtimes.

### Recommended Approach

Use language-idiomatic solutions for HTTP(S) requests before using URL Fetch.
The primary URL Fetch use case is issuing HTTP(S) requests to another App Engine
app while asserting your app's identity.

### Using Standard Runtime Network Classes

Java applications using standard Java classes (e.g.,
`java.net.HttpURLConnection`) for HTTP(S) requests benefit from: - No 32 MB
limit on request data - Support for HTTP 2.0 - Access to Google Cloud-based APIs
via Cloud Client Libraries for Java

### Using URL Fetch

**Warning**: Don't use URL Fetch if you have set up Serverless VPC Access or use
Cloud Client Libraries for Java. URL Fetch handles all outbound requests, and
requests to VPC networks or client libraries fail. Apps using Cloud Client
Libraries for Java and attempting URL Fetch through URLConnection wrapper aren't
supported. To use URL Fetch in a Java app, add to `appengine-web.xml`:

```xml
<url-stream-handler>urlfetch</url-stream-handler>
```

Full example:

```xml
<?xml version="1.0" encoding="utf-8"?>
<appengine-web-app xmlns="http://appengine.google.com/ns/1.0">
<url-stream-handler>urlfetch</url-stream-handler> <!-- ... -->
</appengine-web-app>
```

**Note**: The metadata server can only be accessed using native Java sockets and
doesn't support the urlfetch service.

### Issuing an HTTP Request

Use `java.net.URLConnection` for basic requests:

```java
URL url = new URL("http://api.icndb.com/jokes/random");
BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
StringBuffer json = new StringBuffer();
String line;
while ((line = reader.readLine()) != null) {
  json.append(line);
}
reader.close();
```

For advanced requests, use `java.net.HttpURLConnection`:

1.  Create a URL object
2.  Call `openConnection()` on the URL object
3.  Cast to `HttpURLConnection`
4.  Set the request method
5.  Create an output stream for the request
6.  Write the payload to the stream
7.  Close the stream Example PUT request with form data:

```java
URL url = new URL("http://jsonplaceholder.typicode.com/posts/" + id);
HttpURLConnection conn = (HttpURLConnection) url.openConnection();
// Enable output
conn.setDoOutput(true);
conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
conn.setRequestProperty("Accept", "application/json");
// Set request method
conn.setRequestMethod("PUT");
// Create JSON request
JSONObject jsonObj = new JSONObject()
    .put("userId", 1)
    .put("id", id)
    .put("title", text)
    .put("body", content);
OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream());
writer.write(jsonObj.toString());
writer.close();
int respCode = conn.getResponseCode();
if (respCode == HttpURLConnection.HTTP_OK || respCode == HttpURLConnection.HTTP_CREATED) {
    req.setAttribute("error", "");
    StringBuilder response = new StringBuilder();
    String line;
    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
    while ((line = reader.readLine()) != null) {
        response.append(line);
    }
    reader.close();
    req.setAttribute("response", response.toString());
} else {
    req.setAttribute("error", conn.getResponseCode() + " " + conn.getResponseMessage());
}
```

### Setting a Request Timeout

If using URL Fetch, adjust the default deadline in `appengine-web.xml`: 

```xml
<system-properties>
  <property name="appengine.api.urlfetch.defaultDeadline" value="10"/>
</system-properties>
```

### Setting Headers

Set HTTP headers using `setRequestProperty()`: `java
conn.setRequestProperty("X-MyApp-Version", "2.7.3");`

### Disabling Redirects

**Security recommendation**: Disable redirects to prevent forwarding sensitive
information (e.g., authorization headers) to redirect destinations. By default,
`HttpURLConnection` follows HTTP redirects. To disable: `java
conn.setInstanceFollowRedirects(false);` If using the urlfetch package directly,
specify `doNotFollowRedirects() .

### Issuing an HTTPS Request

By default, the URL Fetch service validates the certificate of the host it
contacts and rejects requests if the certificate doesn't match. No explicit
action is needed to secure your request.

### Issuing an Asynchronous Request

HTTP(S) requests are synchronous by default. For asynchronous requests, use
`URLFetchService.fetchAsync()`, which returns
`java.util.concurrent.Future<HTTPResponse>`.

### Issuing a Request to Another App Engine App

When using URL Fetch to request another App Engine app, assert your app's
identity by adding the `X-Appengine-Inbound-Appid` header. If you disable
redirects, App Engine adds this header automatically. 

**Note**: Use the
`[REGION_ID].r.appspot.com` domain name rather than a custom domain for requests
between App Engine applications.
