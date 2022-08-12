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

package app;

import static com.google.common.base.Throwables.getStackTraceAsString;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.Truth8.assertThat;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertThrows;

import com.google.appengine.api.appidentity.AppIdentityService;
import com.google.appengine.api.appidentity.AppIdentityServiceFactory;
import com.google.appengine.api.appidentity.PublicCertificate;
import com.google.appengine.api.blobstore.BlobKey;
import com.google.appengine.api.blobstore.BlobstoreService;
import com.google.appengine.api.blobstore.BlobstoreServiceFactory;
import com.google.appengine.api.capabilities.CapabilitiesService;
import com.google.appengine.api.capabilities.CapabilitiesServiceFactory;
import com.google.appengine.api.capabilities.Capability;
import com.google.appengine.api.capabilities.CapabilityStatus;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Transaction;
import com.google.appengine.api.images.Composite;
import com.google.appengine.api.images.Image;
import com.google.appengine.api.images.ImagesService;
import com.google.appengine.api.images.ImagesServiceFactory;
import com.google.appengine.api.images.Transform;
import com.google.appengine.api.mail.MailService;
import com.google.appengine.api.mail.MailServiceFactory;
import com.google.appengine.api.memcache.MemcacheService;
import com.google.appengine.api.memcache.MemcacheServiceFactory;
import com.google.appengine.api.memcache.Stats;
import com.google.appengine.api.modules.ModulesService;
import com.google.appengine.api.modules.ModulesServiceFactory;
import com.google.appengine.api.oauth.OAuthRequestException;
import com.google.appengine.api.oauth.OAuthService;
import com.google.appengine.api.oauth.OAuthServiceFactory;
import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TaskHandle;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.appengine.api.taskqueue.TransientFailureException;
import com.google.appengine.api.urlfetch.FetchOptions;
import com.google.appengine.api.urlfetch.HTTPHeader;
import com.google.appengine.api.urlfetch.HTTPMethod;
import com.google.appengine.api.urlfetch.HTTPRequest;
import com.google.appengine.api.urlfetch.HTTPResponse;
import com.google.appengine.api.urlfetch.URLFetchService;
import com.google.appengine.api.urlfetch.URLFetchServiceFactory;
import com.google.appengine.api.users.User;
import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;
import com.google.appengine.api.utils.SystemProperty;
import com.google.appengine.tools.remoteapi.RemoteApiInstaller;
import com.google.appengine.tools.remoteapi.RemoteApiOptions;
import com.google.apphosting.api.ApiProxy;
import com.google.cloud.MonitoredResource;
import com.google.cloud.Timestamp;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.bigquery.Dataset;
import com.google.cloud.bigquery.DatasetInfo;
import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.DatastoreOptions;
import com.google.cloud.datastore.Entity;
import com.google.cloud.datastore.Key;
import com.google.cloud.datastore.KeyFactory;
import com.google.cloud.datastore.StringValue;
import com.google.cloud.logging.LogEntry;
import com.google.cloud.logging.Logging;
import com.google.cloud.logging.LoggingOptions;
import com.google.cloud.logging.MetricInfo;
import com.google.cloud.logging.Payload.StringPayload;
import com.google.cloud.spanner.Spanner;
import com.google.cloud.spanner.SpannerOptions;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.common.base.Ascii;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteStreams;
import com.google.common.primitives.Longs;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;

/** Prober allinone test. */
@WebServlet(name = "ProberApp", value = "/")
public final class ProberApp extends HttpServlet {
  private static final Logger logger = Logger.getLogger(ProberApp.class.getName());

  private interface TestMethod {
    void test(HttpServletRequest request) throws Exception;
  }

  private static HTTPResponse getResponse(URL url) throws TimeoutException {
    FetchOptions fetchOptions =
        FetchOptions.Builder.withDefaults()
            // N.B. Turning off redirects has the (barely documented) side-effect of
            // bypassing FastNet by using http_over_rpc
            .doNotFollowRedirects()
            .setDeadline(120.0);
    HTTPRequest httpRequest = new HTTPRequest(url, HTTPMethod.GET, fetchOptions);
    // Disable gzip compression on the response
    httpRequest.setHeader(new HTTPHeader("Accept-Encoding", "identity"));

    return getResponse(httpRequest);
  }

  private static HTTPResponse getResponse(HTTPRequest httpRequest) throws TimeoutException {
    HTTPResponse response = null;
    try {
      URLFetchService urlFetchService = URLFetchServiceFactory.getURLFetchService();
      response = urlFetchService.fetch(httpRequest);
    } catch (IOException ioe) {
      // URLFetch documentation states that an exception is thrown,
      // i.e. error code not set, when the response is too large.
      if (!ioe.getMessage().contains("Timeout while fetching URL")) {
        throw new TimeoutException("fetch failed: " + ioe.getMessage());
      }
    }
    return response;
  }

  private static String getLocationId() {
    // Hostname example:
    // "https://java8-allinone-dot-ti-probers-qa-us-c.uc.r.prom-qa.sandbox.google.com"
    String hostname =
        ApiProxy.getCurrentEnvironment()
            .getAttributes()
            .get("com.google.appengine.runtime.default_version_hostname")
            .toString();
    List<String> tokens = Splitter.on('.').splitToList(hostname);
    assertThat(tokens.size()).isGreaterThan(2);
    return tokens.get(1);
  }

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    // This is a guard to help prevent an infinite loop when testing the prober in a browser.
    // It's not ideal to have a Servlet handler at the root ("/") because then it will handle every
    // request not already specified to a servlet, but since our prober configuration makes that
    // difficult to change, we need this safeguard to prevent and infinite loop since this servlet
    // calls itself multiple times.
    if (!request.getServletPath().equals("/")) {
      logger.log(Level.SEVERE, "Ignoring servlet request for:" + request.getServletPath());
      return;
    }
    response.setContentType("text/plain");
    ImmutableMap.Builder<String, TestMethod> testMethodsBuilder =
        ImmutableMap.<String, TestMethod>builder()
            .put("httpGet", ProberApp::testHttpGet)
            .put("urlFetch", ProberApp::testUrlFetch)
            .put("f1", ProberApp::testF1)
            .put("env", ProberApp::testEnv)
            .put("metadata", ProberApp::testMetadata)
            .put("mysql", ProberApp::testMysql)
            .put("responseSize", ProberApp::testResponseSize)
            .put("asciiEncoding", ProberApp::testAsciiEncoding)
            .put("jsp", ProberApp::testJsp)
            .put("threadPool", ProberApp::testThreadPool)
            .put("testUDPUsingDNS", ProberApp::testUDPUsingDNS)
            .put("datastore", ProberApp::testPutDatastoreEntity)
            .put("gcs", ProberApp::testListStorageBuckets)
            .put("spanner", ProberApp::testGetSpannerService)
            // TODO(b/172597194): Un-Ignore this test
            // .put("logging", ProberApp::testCreateLoggingMetric)
            .put("bigquery", ProberApp::testCreateBigqueryDataset)
            .put("sessions", ProberApp::testSessions)
            .put("GAEDatastore", ProberApp::testAddGAEEntity)
            .put("GAEMemcache", ProberApp::testMemcacheLoops)
            .put("GAEUtils", ProberApp::testGAEUtils)
            .put("GAEUsers", ProberApp::testUsers)
            .put("GAEModules", ProberApp::testModules)
            .put("GAECapabilities", ProberApp::testCapabilities)
            .put("GcsBucketName", ProberApp::testGetDefaultGcsBucketName)
            .put("parseFullAppId", ProberApp::testParseFullAppId)
            .put("publicCertificates", ProberApp::testGetPublicCertificates)
            .put("accessToken", ProberApp::testGetAccessToken)
            .put("serviceAccountName", ProberApp::testGetServiceAccountName)
            .put("GAEMail", ProberApp::testMail)
            .put("TransformImage", ProberApp::testTransformImage)
            .put("CompositeImages", ProberApp::testCompositeImages)
            .put("validOauth", ProberApp::testValidOauthRequest)
            .put("invalidOauth", ProberApp::testInvalidOauthRequest)
            .put("GAEBlobstore", ProberApp::testBlobstore);

    // TODO(b/180055316): Add test for all runtimes when Remote API is enabled for Java 11 wormhole
    if (System.getenv("GAE_RUNTIME").equals("java8")) {
      testMethodsBuilder.put("remoteAPI", ProberApp::testRemoteAPI);
      testMethodsBuilder.put("sslRequest", ProberApp::testSslRequest);
    }
    // Cloud Tasks QA is only available in us-central1/2.
    // TODO(b/197253337): Enable in prod once task queues are added to prod apps in sisyfus.
    if (isRunningInQa() && "uc".equals(getLocationId())) {
      testMethodsBuilder
          .put("pushQueue", ProberApp::testPushQueue)
          .put("pullQueue", ProberApp::testPullQueue);
    }
    boolean failure = false;

    for (Map.Entry<String, TestMethod> entry : testMethodsBuilder.buildOrThrow().entrySet()) {
      try {
        entry.getValue().test(request);
      } catch (Throwable t) {
        String msg = entry.getKey() + ": " + getStackTraceAsString(t);
        logger.log(Level.SEVERE, msg);
        response.getWriter().println(msg);
        failure = true;
      }
    }

    if (!failure) {
      response.getWriter().print("All probers are successful!");
    }
  }

  // For testHttpGet
  private static final String HUMANS = "https://www.google.com/humans.txt";
  private static final String EXPECTED =
      "Google is built by a large team of engineers, designers, "
          + "researchers, robots, and others in many different sites across the "
          + "globe. It is updated continuously, and built with more tools and "
          + "technologies than we can shake a stick at. If you'd like to help us "
          + "out, see careers.google.com.\n";

  /** Tests HTTP connection. */
  private static void testHttpGet(HttpServletRequest request) throws IOException {
    String failureMsg = "";

    HttpURLConnection connection = (HttpURLConnection) new URL(HUMANS).openConnection();

    try (InputStream is = connection.getInputStream()) {
      String responseBody = new String(ByteStreams.toByteArray(is), UTF_8);

      if (!responseBody.equals(EXPECTED)) {
        failureMsg +=
            String.format("response body differs; got: '%s'; want: '%s'.", responseBody, EXPECTED);
      }

      int code = connection.getResponseCode();
      if (code != 200) {
        failureMsg += "\nstatus code; got: " + code + "want: " + 200;
      }

      if (!failureMsg.isEmpty()) {
        throw new AssertionError(failureMsg);
      }

    } finally {
      connection.disconnect();
    }
  }

  /** Tests the URLFetch API. */
  private static void testUrlFetch(HttpServletRequest request) throws IOException {
    FetchOptions fetchOptions =
        FetchOptions.Builder.withDefaults()
            // N.B. Turning off redirects has the (barely documented) side-effect of
            // bypassing FastNet by using http_over_rpc
            .doNotFollowRedirects()
            .setDeadline(120.0);
    HTTPRequest httpRequest = new HTTPRequest(new URL(HUMANS), HTTPMethod.GET, fetchOptions);
    // Disable gzip compression on the response
    httpRequest.setHeader(new HTTPHeader("Accept-Encoding", "identity"));

    HTTPResponse response = null;
    try {
      URLFetchService urlFetchService = URLFetchServiceFactory.getURLFetchService();
      response = urlFetchService.fetch(httpRequest);
      String content = new String(response.getContent(), UTF_8);
      assertThat(content).isEqualTo(EXPECTED);
    } catch (IOException ioe) {
      // URLFetch documentation states that an exception is thrown,
      // i.e. error code not set, when the response is too large.
      if (!ioe.getMessage().contains("Timeout while fetching URL")) {
        throw new IOException("fetch failed: " + ioe.getMessage());
      }
    }
  }

  /** Tests the F1 memory allocation limits. */
  private static void testF1(HttpServletRequest request) {
    final byte[] array = new byte[25 * 1024 * 1024];
    // TODO(b/165798264) Find a more principled value for this allocation.
    if (array.length != 25 * 1024 * 1024) {
      throw new AssertionError(
          String.format("Cannot allocate large memory chunk (of %d bytes)", array.length));
    }
  }

  // For testEnv
  // Java doesn't give us a way to modify our environment,
  // so we simply reply with success to make the prober happy here.
  private static final ImmutableMap<String, String> EXPECTED_ENV_REGEX =
      ImmutableMap.<String, String>builder()
          .put("GAE_ENV", "standard")
          .put("GAE_INSTANCE", "\\w+")
          .put("GAE_SERVICE", "[\\w-]+")
          .put("GAE_DEPLOYMENT_ID", "\\w+")
          .put("GOOGLE_CLOUD_PROJECT", "[a-zA-Z0-9-]+")
          .put("GAE_RUNTIME", "[a-zA-Z0-9]+")
          .put("FROM_APP_YAML", "from_app_yaml")
          .buildOrThrow();

  private static final ImmutableMap<String, String> EXPECTED_HEADER_REGEX =
      ImmutableMap.<String, String>builder()
          .put("X-AppEngine-Country", "[^']+")
          .put("X-Cloud-Trace-Context", "[^']+")
          .buildOrThrow();

  /** Tests existence of environment variables. */
  private static void testEnv(HttpServletRequest request) {
    String failureMsg = "";

    // Env validation.
    Map<String, String> definitions = System.getenv();
    for (String envName : EXPECTED_ENV_REGEX.keySet()) {
      String definition = definitions.get(envName);
      if (definition == null) {
        failureMsg += "env " + envName + " is not defined\n";
        continue;
      }

      String regex = EXPECTED_ENV_REGEX.get(envName);
      if (!Pattern.matches(regex, definition)) {
        failureMsg += "env " + envName + "=" + definition + " mismatches regex: " + regex + "\n";
      }
    }

    // Header validation.
    for (String headerName : EXPECTED_HEADER_REGEX.keySet()) {
      String value = request.getHeader(headerName);
      if (value == null) {
        failureMsg += "header " + headerName + " is not defined\n";
        continue;
      }

      String regex = EXPECTED_HEADER_REGEX.get(headerName);
      if (!Pattern.matches(regex, value)) {
        failureMsg += "header " + headerName + "=" + value + " mismatches regex: " + regex + "\n";
      }
    }

    if (!failureMsg.isEmpty()) {
      throw new AssertionError(failureMsg);
    }
  }

  // For testMetadata
  public static final String METADATA_SERVICE_BASE = "http://metadata/computeMetadata/v1/";

  private static boolean isValidProjectNumber(String value) {
    Long number = Longs.tryParse(value);
    return number != null && number != 0;
  }

  private static Connection makeConnection(
      String driver, String socketFactory, String connectString) throws IOException, SQLException {

    try {
      Class.forName(driver);
    } catch (ClassNotFoundException e) {
      throw new IOException("Error loading Driver", e);
    }

    Properties connectionProperties = new Properties();
    connectionProperties.put("user", System.getenv("DB_USER"));
    connectionProperties.put("password", System.getenv("DB_PASSWORD"));
    connectionProperties.put("useSSL", "true");
    connectionProperties.put("socketFactory", socketFactory);

    return DriverManager.getConnection(connectString, connectionProperties);
  }

  /** Tests the metadata server. */
  private static void testMetadata(HttpServletRequest request) throws IOException {
    String failureMsg = "";

    String result = getMetadata("/project/project-id");
    String expected = System.getenv("GOOGLE_CLOUD_PROJECT");
    if (!expected.equals(result)) {
      failureMsg += String.format("Project ID; got: '%s' want: '%s'\n", result, expected);
    }

    result = getMetadata("/project/numeric-project-id");
    if (!isValidProjectNumber(result)) {
      failureMsg += String.format("Project number '%s' unexpected!\n", result);
    }

    result = getMetadata("/instance/service-accounts/default/email");
    if (!result.contains("@")) {
      failureMsg += String.format("Email '%s' unexpected!\n", result);
    }

    result = getMetadata("instance/service-accounts/default/token");
    if (!result.contains("access_token") || !result.contains("Bearer")) {
      failureMsg += String.format("Token '%s' unexpected!\n", result);
    }

    if (!failureMsg.isEmpty()) {
      throw new AssertionError(failureMsg);
    }
  }

  private static String getMetadata(String resource) throws IOException {
    URL url = new URL(METADATA_SERVICE_BASE + resource);
    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    connection.setRequestProperty("Metadata-Flavor", "Google");
    try {
      InputStream is = connection.getInputStream();
      return new String(ByteStreams.toByteArray(is), UTF_8);
    } finally {
      connection.disconnect();
    }
  }

  // For testMysql
  public static final String SQL_RESPONSE = "Success from MySQL!";

  /** Tests MySQL connection. */
  private static void testMysql(HttpServletRequest request) throws SQLException, IOException {
    String dbName = System.getenv("MYSQL_DATABASE_NAME");
    if (dbName == null) {
      dbName = "ti-probers-regional-mysql";
    }
    String connectString =
        String.format(
            "jdbc:mysql://google/%s?cloudSqlInstance=%s:%s:%s",
            System.getenv("MYSQL_DB_DATABASE"),
            System.getenv("GOOGLE_CLOUD_PROJECT"),
            System.getenv("DB_REGION"),
            dbName);

    logger.log(Level.INFO, "calling testMysql()");
    // We do not want to explicitly close the connection here, the runtime is supposed to do
    // it automatically only for java8, not anymore for java11/17. See b/182391413 .
    Connection conn =
        makeConnection(
            "com.mysql.jdbc.Driver", "com.google.cloud.sql.mysql.SocketFactory", connectString);

    String selectCommand = "SELECT 'Success from MySQL!' as message";
    Statement statement = conn.createStatement();
    ResultSet resultSet = statement.executeQuery(selectCommand);

    if (resultSet.next()) {
      String res = resultSet.getString("message");
      if (!SQL_RESPONSE.equals(res)) {
        throw new AssertionError(
            String.format(
                "from SQL database query '%s' got: '%s'; want: '%s'\n",
                selectCommand, res, SQL_RESPONSE));
      }
    } else {
      throw new AssertionError(
          String.format("Failed to get data from mysql query '%s' db\n", selectCommand));
    }
    if (!System.getenv("GAE_RUNTIME").equals("java8")) {
      resultSet.close();
      statement.close();
      conn.close();
    }
  }

  // For testResponseSize
  private static final int KB = 1024;
  private static final int MB = KB * KB;

  /**
   * Test max response size.
   *
   * <p>Only test up to a bit over the current max response size, allocation of buffers and the like
   * will slow down the response processing above a certain point, production is not currently
   * designed to support responses significantly larger than what we state the maximum is.
   */
  private static void testResponseSize(HttpServletRequest request) throws Exception {
    String baseUrl = request.getRequestURL().toString();
    for (int size : ImmutableList.of(100, KB, MB, 8 * MB, 8 * MB + 1, 16 * MB)) {
      URL url = new URL(baseUrl + "large?size=" + size);
      HTTPResponse response = getResponse(url);
      assertThat(response).isNotNull();
      assertThat(response.getContent()).hasLength(size);
      assertThat(response.getResponseCode()).isEqualTo(HttpServletResponse.SC_OK);
    }
  }

  /** Tests that the encoding is US_ASCII as specified in appengine-web.xml. */
  private static void testAsciiEncoding(HttpServletRequest request) {
    String expectedEncoding = US_ASCII.displayName();
    String actualEncoding = System.getProperty("file.encoding");
    assertThat(actualEncoding).isEqualTo(expectedEncoding);
    Charset expectedCharset = Charset.forName(expectedEncoding);
    byte[] expectedBytes = "Éamonn".getBytes(expectedCharset);
    @SuppressWarnings("DefaultCharset")
    byte[] actualBytes = "Éamonn".getBytes();
    assertThat(actualBytes).isEqualTo(expectedBytes);
  }

  /** This test makes sure the JSP response is not truncated. (b/12534606) */
  private static void testJsp(HttpServletRequest request) throws Exception {
    String baseUrl = request.getRequestURL().toString();
    URL url = new URL(baseUrl + "large.jsp");
    HTTPResponse response = getResponse(url);
    // Make sure response was not truncated.
    String responseStr = new String(response.getContent(), UTF_8);
    assertThat(responseStr.length()).isGreaterThan(1024 * 1024);
    assertThat(responseStr).endsWith("</html>\n");
  }

  /**
   * Test that verifies that the runtime does not wait indefinitely for daemon threads to complete.
   */
  private static void testThreadPool(HttpServletRequest request) throws Exception {
    String baseUrl = request.getRequestURL().toString();
    URL url = new URL(baseUrl + "threadpool");
    HTTPResponse httpResponse = getResponse(url);
    assertThat(httpResponse.getResponseCode()).isEqualTo(200);
    assertThat(new String(httpResponse.getContent(), UTF_8)).isEqualTo("Request completed.\n");
  }

  // For testUDPUsingDNS
  private static final Random random = new Random(System.currentTimeMillis());

  // Use HonestDNS
  private static final byte[] DNS_SERVER_RAW_ADDR = {8, 8, 8, 8};

  private static final int DNS_PORT = 53;

  /**
   * Constructs a DNS query packet for the domain consisting of the given labels. See
   * <a href="https://tools.ietf.org/html/rfc1035#section-4.1.1">RFC1035, section 4.1.1</a>.
   *
   * @param packetId the 16-bit packet id
   * @param labels the "labels" making up the domain, for example {@code google}, {@code com}.
   */
  private static byte[] dnsQueryPacket(short packetId, String... labels) throws IOException {
    try (ByteArrayOutputStream bout = new ByteArrayOutputStream();
        DataOutputStream dout = new DataOutputStream(bout)) {
      dout.writeShort(packetId);     // [0-1] packet id
      dout.writeShort(0x100);        // [2-3] flags, here just recursion desired (RD)
      dout.writeShort(1);            // [4-5] number of queries (QDCOUNT)
      dout.writeShort(0);            // [6-7] unused count field for DNS response packets
      dout.writeShort(0);            // [8-9] unused count field for DNS response packets
      dout.writeShort(0);            // [10-11] unused count field for DNS response packets
      for (String label : labels) {
        dout.write(label.length());  // label length
        dout.writeBytes(label);      // label bytes
      }
      dout.write(0);                 // empty label to indicate end of query
      dout.writeShort(1);            // QTYPE, set to 1 = A (host address)
      dout.writeShort(1);            // QCLASS, set to 1 = IN (internet)
      dout.flush();
      return bout.toByteArray();
    }
  }

  /** Test outbound sockets. */
  private static void testUDPUsingDNS(HttpServletRequest request) throws IOException {
    try (DatagramSocket socket = new DatagramSocket()) {
      short packetId = (short) random.nextInt();
      byte[] requestBuf = dnsQueryPacket(packetId, "www", "google", "com");

      InetAddress dnsServerAddr = InetAddress.getByAddress(DNS_SERVER_RAW_ADDR);
      DatagramPacket packet =
          new DatagramPacket(requestBuf, requestBuf.length, dnsServerAddr, DNS_PORT);
      socket.send(packet);

      byte[] responseBuf = new byte[2];
      DatagramPacket response = new DatagramPacket(responseBuf, 2);
      socket.receive(response);

      try (ByteArrayInputStream bin = new ByteArrayInputStream(responseBuf);
          DataInputStream din = new DataInputStream(bin)) {
        assertThat(din.readShort()).isEqualTo(packetId);
      }
    }
  }

  /**
   * Tests SSL using the Apache HttpClient library.
   *
   * <p>This test is a port of the following Python e2e test:
   * apphosting/api/remote_socket/e2e/ssl_aeta_test.py
   *
   * <p>The file roots.jks it depends on was generated by
   * google3/security/cacerts:roots_jks.
   */
  private static void testSslRequest(HttpServletRequest request) throws Exception {
    KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
    try (FileInputStream is = new FileInputStream("WEB-INF/data/roots.jks")) {
      ks.load(is, "changeit".toCharArray());
    } catch (FileNotFoundException e) {
      // Some public probers do not expose this file, so we skip the test.
      return;
    }
    TrustManagerFactory tmf =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    tmf.init(ks);
    TrustManager[] tm = tmf.getTrustManagers();
    SSLContext c = SSLContext.getInstance("TLSv1.2");
    KeyManager[] km = new KeyManager[0];
    c.init(km, tm, new SecureRandom());
    SSLSocketFactory f = c.getSocketFactory();
    try (Socket s = f.createSocket("smtp.gmail.com", 465)) {
      assertThat(s.isConnected()).isTrue();
      SSLSocket ss = (SSLSocket) s;
      SSLSession session = ss.getSession();
      // If the server certificate cannot be validated, the next line will throw a
      // javax.net.ssl.SSLPeerUnverifiedException with the message: "peer not authenticated".
      assertThat(session.getPeerPrincipal()).isNotNull();
    }
  }

  /** Tests Datastore Cloud API usage. */
  private static void testPutDatastoreEntity(HttpServletRequest request) {
    Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
    KeyFactory keyFactory = datastore.newKeyFactory().setKind("Task");
    Key key = keyFactory.newKey("xyz");
    Entity task =
        Entity.newBuilder(key)
            .set(
                "description",
                StringValue.newBuilder("hello world").setExcludeFromIndexes(true).build())
            .set("created", Timestamp.now())
            .set("done", false)
            .build();
    datastore.put(task);
  }

  /** Tests GCS Cloud API usage. */
  private static void testListStorageBuckets(HttpServletRequest request) {
    Storage storage = StorageOptions.getDefaultInstance().getService();
    assertThat(storage.list().iterateAll()).isNotEmpty();
  }

  /** Tests Spanner Cloud API usage. */
  private static void testGetSpannerService(HttpServletRequest request) {
    SpannerOptions options = SpannerOptions.newBuilder().build();
    Spanner spanner = options.getService();
    assertThat(spanner).isNotNull();
    spanner.close();
  }

  /** Tests Logging Cloud API usage. */
  @SuppressWarnings("unused")
  private static void testCreateLoggingMetric(HttpServletRequest request) {
    LoggingOptions options = LoggingOptions.getDefaultInstance();
    Logging logging = options.getService();
    String metricId = "test-metric";
    MetricInfo metricInfo =
        MetricInfo.newBuilder(metricId, "severity >= ERROR")
            .setDescription("Log entries with severity higher or equal to ERROR")
            .build();
    if (logging.getMetric(metricId) == null) {
      logging.create(metricInfo);
    }
    LogEntry firstEntry =
        LogEntry.newBuilder(StringPayload.of("message"))
            .setLogName("test-log")
            .setResource(
                MonitoredResource.newBuilder("global")
                    .addLabel("project_id", options.getProjectId())
                    .build())
            .build();
    logging.write(Collections.singleton(firstEntry));
  }

  /** Tests BigQuery Cloud API Usage. */
  private static void testCreateBigqueryDataset(HttpServletRequest request) {
    BigQuery bigquery = BigQueryOptions.getDefaultInstance().getService();
    String datasetId = "my_dataset_id";
    Dataset dataset = bigquery.getDataset(datasetId);
    if (dataset == null) {
      dataset = bigquery.create(DatasetInfo.newBuilder(datasetId).build());
    }
    assertThat(dataset.exists()).isTrue();
  }

  /** Test Remote API access. */
  private static void testRemoteAPI(HttpServletRequest request) throws Exception {
    // We trim the "http://" prefix and trailing "/" to match the format documented in
    // https://cloud.google.com/appengine/docs/standard/java/tools/remoteapi#configuring_remote_api_on_a_standalone_client: [APP_ID].[REGION_ID].r.appspot.com
    String serverString =
        request.getRequestURL().toString().replaceFirst("^https?://", "").replaceFirst("/$", "");

    RemoteApiOptions options =
        new RemoteApiOptions().server(serverString, 443).useApplicationDefaultCredential();
    RemoteApiInstaller installer = new RemoteApiInstaller();
    installer.install(options);

    try {
      DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
      Transaction txn = ds.beginTransaction();
      txn.rollback();
    } finally {
      installer.uninstall();
    }
  }

  /** Test sessions. Hit servlet twice and verify session count changes. */
  private static void testSessions(HttpServletRequest request) throws Exception {
    String baseUrl = request.getRequestURL().toString();
    URL url = new URL(baseUrl + "/session");

    FetchOptions fetchOptions =
        FetchOptions.Builder.withDefaults()
            // N.B. Turning off redirects has the (barely documented) side-effect of
            // bypassing FastNet by using http_over_rpc
            .doNotFollowRedirects()
            .setDeadline(20.0);
    HTTPRequest httpRequest = new HTTPRequest(url, HTTPMethod.GET, fetchOptions);

    // First request
    HTTPResponse response = getResponse(httpRequest);
    String content1 = new String(response.getContent(), UTF_8);
    Matcher matcher1 = COUNT_PATTERN.matcher(content1);
    assertWithMessage("Should start with 'Count=N': %s", content1).that(matcher1.find()).isTrue();
    String count1 = matcher1.group(1);

    // Retrieve Cookie
    Optional<String> cookie =
        response.getHeaders().stream()
            .filter(header -> Ascii.equalsIgnoreCase(header.getName(), "Set-Cookie"))
            .map(HTTPHeader::getValue)
            .findFirst();
    assertThat(cookie).isPresent();

    // Set cookie on subsequent request
    httpRequest.setHeader(new HTTPHeader("Cookie", cookie.get()));

    // Second request
    HTTPResponse response2 = getResponse(httpRequest);
    String content2 = new String(response2.getContent(), UTF_8);
    Matcher matcher2 = COUNT_PATTERN.matcher(content2);
    assertWithMessage("Should start with 'Count=N': %s", content2).that(matcher2.find()).isTrue();
    String count2 = matcher2.group(1);
    assertThat(count2).isNotEqualTo(count1);
  }

  private static final Pattern COUNT_PATTERN = Pattern.compile("^Count=(\\d+)");

  /** Adds a car to the App Engine datastore. */
  private static void testAddGAEEntity(HttpServletRequest request) {
    DatastoreService datastoreService = DatastoreServiceFactory.getDatastoreService();
    // FQN as conflicting with Cloud Datastore Entity.
    com.google.appengine.api.datastore.Entity car =
        new com.google.appengine.api.datastore.Entity("Car");
    car.setProperty("color", "blue " + random.nextInt());
    car.setProperty("brand", "tesla " + random.nextInt());
    datastoreService.put(car);
  }

  /** Performs some GAE memcache work. */
  private static void testMemcacheLoops(HttpServletRequest request) {
    MemcacheService memcacheService = MemcacheServiceFactory.getMemcacheService();
    String key = "test_key:" + random.nextInt(10000);
    String value = "value" + random.nextInt();
    memcacheService.put(key, value);

    Stats stats = memcacheService.getStatistics();
    assertThat(stats).isNotNull();
  }

  /** Test com.google.appengine.api.utils APIs. */
  private static void testGAEUtils(HttpServletRequest request) {
    String version = SystemProperty.version.get();
    assertThat(version).startsWith("Google App Engine/");

    assertThat(SystemProperty.environment.value())
        .isEqualTo(SystemProperty.Environment.Value.Production);
  }

  /** Test the Users API. */
  private static void testUsers(HttpServletRequest request) {
    UserService userService = UserServiceFactory.getUserService();
    Boolean isLoggedIn = userService.isUserLoggedIn();
    assertThat(isLoggedIn).isFalse();

    User user = userService.getCurrentUser();
    assertThat(user).isNull();

    // Exception is expected because the current user is not logged in.
    assertThrows(IllegalStateException.class, userService::isUserAdmin);

    String loginURL = userService.createLoginURL("/login_dest");
    assertThat(loginURL).isNotEmpty();

    String logoutURL = userService.createLogoutURL("/logout_dest");
    assertThat(logoutURL).isNotEmpty();
  }

  /** Test the Modules API. */
  private static void testModules(HttpServletRequest request) throws Exception {
    ModulesService modules = ModulesServiceFactory.getModulesService();

    Set<String> allModules = modules.getModules();
    assertThat(allModules).isNotEmpty();

    String currentModule = modules.getCurrentModule();
    assertThat(currentModule).isNotEmpty();

    String defaultVersion = modules.getDefaultVersion(currentModule);
    assertThat(defaultVersion).isNotEmpty();

    Set<String> allVersions = modules.getVersions(currentModule);
    assertThat(allVersions).isNotEmpty();

    String currentVersion = modules.getCurrentVersion();
    assertThat(currentVersion).isNotEmpty();

    String currentInstance = modules.getCurrentInstanceId();
    assertThat(currentInstance).isNotEmpty();
  }

  private static void testGetDefaultGcsBucketName(HttpServletRequest request) {
    AppIdentityService appIdentityService = AppIdentityServiceFactory.getAppIdentityService();
    String bucket = appIdentityService.getDefaultGcsBucketName();
    assertThat(bucket).isNotEmpty();
  }

  private static void testParseFullAppId(HttpServletRequest request) {
    AppIdentityService appIdentityService = AppIdentityServiceFactory.getAppIdentityService();
    String fullAppId = ApiProxy.getCurrentEnvironment().getAppId();
    AppIdentityService.ParsedAppId parsedAppId = appIdentityService.parseFullAppId(fullAppId);

    assertThat(parsedAppId.getId()).isEqualTo(System.getenv("GOOGLE_CLOUD_PROJECT"));
    assertThat(parsedAppId.getPartition()).isNotEmpty();
    // No custom domain is set, so an empty string should be returned.
    assertThat(parsedAppId.getDomain()).isEmpty();
  }

  private static void testGetPublicCertificates(HttpServletRequest request) throws Exception {
    AppIdentityService appIdentityService = AppIdentityServiceFactory.getAppIdentityService();

    AppIdentityService.SigningResult signingResult =
        appIdentityService.signForApp("test_sign_blob".getBytes(UTF_8));
    assertThat(signingResult).isNotNull();

    Collection<PublicCertificate> publicCertificates =
        appIdentityService.getPublicCertificatesForApp();
    assertThat(publicCertificates).isNotEmpty();
  }

  private static void testGetAccessToken(HttpServletRequest request) {
    AppIdentityService appIdentityService = AppIdentityServiceFactory.getAppIdentityService();
    ArrayList<String> scopes = new ArrayList<>();
    scopes.add("https://www.googleapis.com/auth/cloud-platform.read-only");

    AppIdentityService.GetAccessTokenResult accessToken = appIdentityService.getAccessToken(scopes);
    assertThat(accessToken).isNotNull();

    AppIdentityService.GetAccessTokenResult accessTokenUncached =
        appIdentityService.getAccessTokenUncached(scopes);
    assertThat(accessTokenUncached).isNotNull();
  }

  private static void testGetServiceAccountName(HttpServletRequest request) {
    AppIdentityService appIdentityService = AppIdentityServiceFactory.getAppIdentityService();
    String serviceAccount = appIdentityService.getServiceAccountName();
    assertThat(serviceAccount).startsWith(System.getenv("GOOGLE_CLOUD_PROJECT"));
    assertThat(serviceAccount).endsWith("gserviceaccount.com");
  }

  /** Tests the Capabilities API. */
  private static void testCapabilities(HttpServletRequest request) {
    CapabilitiesService capabilitiesService = CapabilitiesServiceFactory.getCapabilitiesService();
    ImmutableList<Capability> apis =
        ImmutableList.of(
            Capability.DATASTORE_WRITE,
            Capability.IMAGES,
            Capability.MAIL,
            Capability.MEMCACHE,
            Capability.TASKQUEUE,
            Capability.URL_FETCH);
    apis.forEach(
        api ->
            assertThat(capabilitiesService.getStatus(api).getStatus())
                .isEqualTo(CapabilityStatus.ENABLED));
  }

  /** Tests the Mail API. */
  private static void testMail(HttpServletRequest request) throws Exception {
    MailService mailService = MailServiceFactory.getMailService();
    String sender = getAllowlistedMailSender();

    // As long as no exception is thrown, we can think 'send()' and 'sendToAdmins()' succeed.
    mailService.send(
        new MailService.Message(
            sender, "nobody@google.com", "java-prober-test-subject", "java-prober-test-textbody"));
    // TODO(jihuinie): decide if we want to test sendToAdmins() in probers
    // mailService.sendToAdmins(
    //     new MailService.Message(sender, /* to= */ null, "test-subject", "test-textbody"));
  }

  /** Returns an allowlisted mail sender based on the current running GAE environment. */
  private static String getAllowlistedMailSender() {
    String projectId = System.getenv("GOOGLE_CLOUD_PROJECT");
    // The GAE PROD and QA environments have different mail sender allowlists. See
    // 'mail_service_sender_domain' in google3/production/borg/pod/app-engine/appserver/server.pi.
    if (isRunningInQa()) {
      return "test-user@" + projectId + ".prommail-qa.corp.google.com";
    }
    return "test-user@" + projectId + ".appspotmail.com";
  }

  private static boolean isRunningInQa() {
    ModulesService modules = ModulesServiceFactory.getModulesService();
    String currentModule = modules.getCurrentModule();
    String defaultVersion = modules.getDefaultVersion(currentModule);
    String versionHostname = modules.getVersionHostname(currentModule, defaultVersion);
    return versionHostname.endsWith("prom-qa.sandbox.google.com");
  }

  private static void testTransformImage(HttpServletRequest request) throws Exception {
    ImagesService imagesService = ImagesServiceFactory.getImagesService();
    Image image = readImage();
    assertThat(imagesService.histogram(image)).isNotNull();

    int resizeWidth = 50;
    int resizeHeight = 50;
    Transform resize = ImagesServiceFactory.makeResize(resizeWidth, resizeHeight, true);
    Image resizedImage = imagesService.applyTransform(resize, image);

    // The transform is applied in place to the provided image.
    assertThat(image.getWidth()).isEqualTo(resizeWidth);
    assertThat(image.getHeight()).isEqualTo(resizeHeight);
    assertThat(resizedImage.getWidth()).isEqualTo(resizeWidth);
    assertThat(resizedImage.getHeight()).isEqualTo(resizeHeight);
  }

  private static void testCompositeImages(HttpServletRequest request) throws Exception {
    ImagesService imagesService = ImagesServiceFactory.getImagesService();
    Image image = readImage();

    int width = image.getWidth();
    int height = image.getHeight();
    List<Composite> composites = new ArrayList<>();
    composites.add(
        ImagesServiceFactory.makeComposite(image, 0, 0, 1f, Composite.Anchor.TOP_LEFT));
    composites.add(
        ImagesServiceFactory.makeComposite(image, width, 0, 1f, Composite.Anchor.TOP_LEFT));
    Image compositeImage = imagesService.composite(composites, width * 2, height, 0);

    assertThat(compositeImage.getWidth()).isEqualTo(width * 2);
    assertThat(compositeImage.getHeight()).isEqualTo(height);
  }

  private static Image readImage() throws Exception {
    try (FileInputStream fileInputStream = new FileInputStream(new File("WEB-INF/image.jpg"))) {
      FileChannel fileChannel = fileInputStream.getChannel();
      ByteBuffer byteBuffer = ByteBuffer.allocate((int) fileChannel.size());
      fileChannel.read(byteBuffer);
      Image image = ImagesServiceFactory.makeImage(byteBuffer.array());
      assertThat(image).isNotNull();
      return image;
    }
  }

  private static final String SCOPE = "https://www.googleapis.com/auth/userinfo.email";

  private static void testInvalidOauthRequest(HttpServletRequest request) throws Exception {
    OAuthService oauthService = OAuthServiceFactory.getOAuthService();

    // The current request doesn't contain valid OAuth credential.
    assertThrows(OAuthRequestException.class, oauthService::getCurrentUser);
    assertThrows(OAuthRequestException.class, oauthService::isUserAdmin);
    assertThrows(OAuthRequestException.class, () -> oauthService.getClientId(SCOPE));
    assertThrows(OAuthRequestException.class, () -> oauthService.getAuthorizedScopes(SCOPE));
  }

  private static void testValidOauthRequest(HttpServletRequest request) throws Exception {
    String accessToken =
        AppIdentityServiceFactory.getAppIdentityService()
            .getAccessToken(ImmutableList.of(SCOPE))
            .getAccessToken();
    URL url = new URL(request.getRequestURL() + "validoauth");
    assertThat(new String(getResponseWithAccessToken(url, accessToken).getContent(), UTF_8))
        .isEqualTo("ValidOauth passed\n");
  }

  private static HTTPResponse getResponseWithAccessToken(URL url, String accessToken)
      throws Exception {
    FetchOptions fetchOptions =
        FetchOptions.Builder.withDefaults()
            // N.B. Turning off redirects has the (barely documented) side-effect of
            // bypassing FastNet by using http_over_rpc
            .doNotFollowRedirects()
            .setDeadline(120.0);
    HTTPRequest httpRequest = new HTTPRequest(url, HTTPMethod.GET, fetchOptions);
    // Disable gzip compression on the response
    httpRequest.setHeader(new HTTPHeader("Accept-Encoding", "identity"));
    httpRequest.setHeader(new HTTPHeader("Authorization", "Bearer " + accessToken));
    return URLFetchServiceFactory.getURLFetchService().fetch(httpRequest);
  }

  private static void testPushQueue(HttpServletRequest request) throws Exception {
    Queue queue = QueueFactory.getQueue("push-queue");
    assertThat(queue).isNotNull();

    int maxTries = 3;
    for (int i = 0; i < maxTries; i++) {
      try {
        // The task execution is not validated here because of its high latency and
        // the time limit of the prober app.
        queue.add();
        break;
      } catch (TransientFailureException e) {
        if (i == maxTries - 1) {
          throw new AssertionError("exhausted retry limit to add a push task to push-queue", e);
        }
      }
    }
  }

  private static void testPullQueue(HttpServletRequest request) throws Exception {
    Queue queue = QueueFactory.getQueue("pull-queue");
    assertThat(queue).isNotNull();

    String payload = "test_payload" + random.nextInt(10000);
    int maxTries = 3;
    for (int i = 0; i < maxTries; i++) {
      try {
        queue.add(
            TaskOptions.Builder.withMethod(TaskOptions.Method.PULL).payload(payload).tag(payload));
        break;
      } catch (TransientFailureException e) {
        if (i == maxTries - 1) {
          throw new AssertionError("exhausted retry limit to add a task to pull-queue", e);
        }
      }
    }

    List<TaskHandle> tasks = queue.leaseTasksByTag(10, SECONDS, 1, payload);
    assertThat(tasks).hasSize(1);
    String gotPayload = new String(tasks.get(0).getPayload(), UTF_8);
    assertThat(gotPayload).isEqualTo(payload);

    for (int i = 0; i < maxTries; i++) {
      try {
        queue.deleteTask(tasks.get(0));
        break;
      } catch (TransientFailureException e) {
        if (i == maxTries - 1) {
          throw new AssertionError("exhausted retry limit to delete a task from pull-queue", e);
        }
      }
    }
  }

  private static final String UPLOAD_NAME = "java-blobstore-test";
  private static final String BLOBKEY_HEADER = "Test-Blobkey";

  private static void testBlobstore(HttpServletRequest request) throws Exception {
    BlobstoreService blobstoreService = BlobstoreServiceFactory.getBlobstoreService();

    // Once the upload is completed, Blobstore service will make a callback to /blob where
    // the get and serve blob operations are performed and validated. The Blobkey of the uploaded
    // blob will be stored as the value of the response header BLOBKEY_HEADER.
    HttpResponse response =
        uploadFileToUrl(
            UPLOAD_NAME, "WEB-INF/image.jpg", blobstoreService.createUploadUrl("/blob"));
    logger.log(Level.INFO, "testBlobstore blobstore response=" + response);
    if (response.getStatusLine().getStatusCode() == 503) {
      // Sometimes the upload service is not available.
      logger.log(Level.INFO, "blobstore upload service is not available - continuing.");
    } else {
      Header[] headers = response.getHeaders(BLOBKEY_HEADER);
      assertThat(headers).hasLength(1);
      // If no exception is thrown, we can think `delete()` succeeds.
      blobstoreService.delete(new BlobKey(headers[0].getValue()));
    }
  }

  private static HttpResponse uploadFileToUrl(String uploadName, String filepath, String uploadUrl)
      throws Exception {
    CloseableHttpClient httpClient = HttpClients.createDefault();

    // The SSL certificate in GAE QA environment doesn't include `uc.r.prom-qa.sandbox.google.com`,
    // and this blocks the QA app from sending HTTP request to itself. We need to suppress the cert
    // validation in QA for now.
    // TODO(jihuinie): remove when b/190648760 is fixed.
    if (isRunningInQa()) {
      httpClient = makeHttpClientWithoutSslValidation();
    }

    // To upload to Blobstore, we must perform a multipart upload and include a file.
    HttpPost req = new HttpPost(uploadUrl);
    req.setEntity(
        MultipartEntityBuilder.create()
            .addTextBody("sample_text", "foo", ContentType.TEXT_PLAIN)
            .addBinaryBody(uploadName, new File(filepath))
            .build());
    return httpClient.execute(req);
  }

  private static CloseableHttpClient makeHttpClientWithoutSslValidation() throws Exception {
    SSLContext sslContext =
        new SSLContextBuilder()
            .loadTrustMaterial(
                null, (TrustStrategy) (X509Certificate[] chain, String authType) -> true)
            .build();
    SSLConnectionSocketFactory sslSocketFactory =
        new SSLConnectionSocketFactory(sslContext, new NoopHostnameVerifier());
    return HttpClients.custom().setSSLSocketFactory(sslSocketFactory).build();
  }
}
