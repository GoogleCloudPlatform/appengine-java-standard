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

package allinone;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.Filter;
import com.google.appengine.api.memcache.MemcacheService;
import com.google.appengine.api.memcache.MemcacheServiceFactory;
import com.google.appengine.api.memcache.Stats;
import com.google.appengine.api.taskqueue.DeferredTask;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.appengine.api.urlfetch.HTTPResponse;
import com.google.appengine.api.urlfetch.URLFetchService;
import com.google.appengine.api.urlfetch.URLFetchServiceFactory;
import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;
import com.google.apphosting.api.ApiProxy;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.io.ByteStreams;
import com.google.common.io.CharStreams;
import com.google.common.io.CountingInputStream;
import com.google.common.math.BigIntegerMath;
import com.google.errorprone.annotations.FormatMethod;
import com.sun.management.HotSpotDiagnosticMXBean;
import com.sun.management.VMOption;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.CompilationMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Principal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import javax.management.MBeanServer;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.swing.JEditorPane;

/**
 * Servlet capable of performing a variety of actions based on the value of
 * the query parameters.
 *
 * <p>Originally this code was compatible with {@code <internal4>},
 * but it has evolved enough since then that compatibility should not be expected.
 *
 * <p>This servlet also accepts POST requests and returns a plain text response containing
 * the number of bytes read.
 *
 */
public class MainServlet extends HttpServlet {
  private static final Logger logger = Logger.getLogger(MainServlet.class.getName());

  private static final Charset US_ASCII_CHARSET = US_ASCII;
  private static final Level[] LOG_LEVELS =
      new Level[] {Level.FINEST, Level.FINE, Level.CONFIG, Level.INFO, Level.WARNING, Level.SEVERE};
  // "Car" datastore entity.
  private static final String CAR_KIND = "Car";
  private static final String COLOR_PROPERTY = "color";
  private static final String BRAND_PROPERTY = "brand";
  private static final String[] COLORS = new String[] { "green", "red", "blue" };
  private static final String[] BRANDS = new String[] { "toyota", "honda", "nissan" };
  // "Log" datastore entity.
  private static final String LOG_KIND = "Log";
  private static final String URL_PROPERTY = "url";
  private static final String DATE_PROPERTY = "date";

  private static final ImmutableSet<String> VALID_PARAMETERS =
      ImmutableSet.of(
          "add_tasks",
          "awt_text",
          "clear_pinned_buffers",
          "datastore_count",
          "datastore_cron",
          "datastore_entities",
          "datastore_queries",
          "deferred_task",
          "deferred_task_verify",
          "direct_byte_buffer_size",
          "fetch_project_id_from_metadata",
          "fetch_service_account_scopes_from_metadata",
          "fetch_service_account_token_from_metadata",
          "fetch_url",
          "fetch_url_using_httpurlconnection",
          "forward",
          "get_attribute",
          "get_environment",
          "get_header",
          "get_metadata",
          "get_named_dispatcher",
          "get_system_property",
          "jmx_info",
          "jmx_list_vm_options",
          "jmx_thread_dump",
          "list_attributes",
          "list_environment",
          "list_headers",
          "list_processes",
          "list_system_properties",
          "log_flush",
          "log_lines",
          "log_remaining_time",
          "math_loops",
          "math_ms",
          "memcache_loops",
          "memcache_size",
          "oom",
          "pin_byte_buffer",
          "pin_byte_buffer_size",
          "random_response_size",
          "response_size",
          "set_servlet_attributes",
          "silent",
          "sql_columns",
          "sql_db",
          "sql_len",
          "sql_replace",
          "sql_rows",
          "sql_timeout",
          // "spanner_id",
          // "spanner_db",
          "task_url",
          "user",
          "validate_fs");

  private static final List<ByteBuffer> PINNED_BUFFERS = new ArrayList<>();

  private final Random random = new Random();
  private ServletContext context;

  @Override
  public void init(ServletConfig config) {
    this.context = config.getServletContext();
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException,
      IOException {
    validateParameters(req);
    // In silent mode, regular response output is suppressed and a single "OK"
    // string is printed if all actions ran successfully.
    boolean silent = req.getParameter("silent") != null;
    resp.setContentType("text/plain");
    PrintWriter responseWriter = resp.getWriter();
    PrintWriter w = (silent ? new PrintWriter(new StringWriter()) : responseWriter);
    // These options are also present in the python allinone application, sometimes
    // with slightly different names, e.g. "queries" vs "datastore_queries".
    Integer mathMsParam = getIntParameter("math_ms", req);
    if (mathMsParam != null) {
      performMathMs(mathMsParam, w);
    }
    Integer mathLoopsParam = getIntParameter("math_loops", req);
    if (mathLoopsParam != null) {
      performMathLoops(mathLoopsParam, w);
    }
    Integer memcacheLoopsParam = getIntParameter("memcache_loops", req);
    if (memcacheLoopsParam != null) {
      Integer size = getIntParameter("memcache_size", req);
      performMemcacheLoops(memcacheLoopsParam, size, w);
    }
    Integer logLinesParam = getIntParameter("log_lines", req);
    if (logLinesParam != null) {
      performLogging(logLinesParam, w);
    }
    if (req.getParameter("log_flush") != null) {
      performLogFlush(w);
    }
    Integer responseSizeParam = getIntParameter("response_size", req);
    if (responseSizeParam != null) {
      performResponseSize(responseSizeParam, w);
    }
    Integer queriesParam = getIntParameter("datastore_queries", req);
    if (queriesParam != null) {
      performQueries(queriesParam, w);
    }
    Integer entitiesParam = getIntParameter("datastore_entities", req);
    if (entitiesParam != null) {
      performAddEntities(entitiesParam, w);
    }
    if (req.getParameter("datastore_count") != null) {
      performCount(w);
    }
    if (req.getParameter("datastore_cron") != null) {
      performCron(req.getRequestURI(), w);
    }
    if (req.getParameter("user") != null) {
      performUserLogin(w, req.getRequestURI(), req.getUserPrincipal());
    }
    String urlParam = req.getParameter("fetch_url");
    if (urlParam != null) {
      performUrlFetch(w, urlParam);
    }
    String urlParam2 = req.getParameter("fetch_url_using_httpurlconnection");
    if (urlParam2 != null) {
      performUrlFetchUsingHttpURLConnection(w, urlParam2);
    }
    // These options are Java-specific.
    Integer randomResponseSizeParam = getIntParameter("random_response_size", req);
    if (randomResponseSizeParam != null) {
      performRandomResponseSize(randomResponseSizeParam, w);
    }
    boolean pinByteBuffers = (req.getParameter("pin_byte_buffer") != null);
    Integer byteBufferSizeParam = getIntParameter("byte_buffer_size", req);
    if (byteBufferSizeParam != null) {
      ByteBuffer b = performByteBufferAllocation(byteBufferSizeParam, false, w);
      if (pinByteBuffers) {
        emit(w, "Pinned buffer");
        PINNED_BUFFERS.add(b);
      }
    }
    Integer directByteBufferSizeParam = getIntParameter("direct_byte_buffer_size", req);
    if (directByteBufferSizeParam != null) {
      ByteBuffer b = performByteBufferAllocation(directByteBufferSizeParam, true, w);
      if (pinByteBuffers) {
        emit(w, "Pinned buffer");
        PINNED_BUFFERS.add(b);
      }
    }
    if (req.getParameter("clear_pinned_buffers") != null) {
      emit(w, "Cleared pinner buffers");
      PINNED_BUFFERS.clear();
    }
    if (req.getParameter("list_system_properties") != null) {
      performListSystemProperties(w);
    }
    String sysPropName = req.getParameter("get_system_property");
    if (sysPropName != null) {
      emit(w, System.getProperty(sysPropName));
    }
    if (req.getParameter("list_environment") != null) {
      performListEnvironment(w);
    }
    String envVarName = req.getParameter("get_environment");
    if (envVarName != null) {
      emit(w, System.getenv(envVarName));
    }
    String headerName = req.getParameter("get_header");
    if (headerName != null) {
      emit(w, req.getHeader(headerName));
    }
    if (req.getParameter("list_attributes") != null) {
      performListAttributes(w);
    }
    String attrName = req.getParameter("get_attribute");
    if (attrName != null) {
      emit(w, String.valueOf(ApiProxy.getCurrentEnvironment().getAttributes().get(attrName)));
    }
    String servletAttributeString = req.getParameter("set_servlet_attributes");
    if (servletAttributeString != null) {
      performSetServletAttributes(req, servletAttributeString, w);
    }
    String dispatcherName = req.getParameter("get_named_dispatcher");
    if (dispatcherName != null) {
       emitf(w, "%s", context.getNamedDispatcher(dispatcherName));
    }
    if (req.getParameter("fetch_project_id_from_metadata") != null) {
      performFetchProjectIdFromMetadata(w);
    }
    if (req.getParameter("fetch_service_account_token_from_metadata") != null) {
      performFetchServiceAccountTokenFromMetadata(w);
    }
    if (req.getParameter("fetch_service_account_scopes_from_metadata") != null) {
      performFetchServiceAccountScopesFromMetadata(w);
    }
    if (req.getParameter("log_remaining_time") != null) {
      performLogRemainingTime(w);
    }
    if (req.getParameter("deferred_task") != null) {
      performAddDeferredTask(w);
    }
    if (req.getParameter("deferred_task_verify") != null) {
      performVerifyDeferredTask(w);
    }
    Integer tasksParam = getIntParameter("add_tasks", req);
    if (tasksParam != null) {
      String taskUrl = req.getParameter("task_url");
      performAddTasks(tasksParam, taskUrl, w);
    }
    String dbParam = req.getParameter("sql_db");
    if (dbParam != null) {
      Integer timeout = getIntParameter("sql_timeout", req);
      Integer rows = getIntParameter("sql_rows", req);
      Integer columns = getIntParameter("sql_columns", req);
      Integer len = getIntParameter("sql_len", req);
      boolean replace = req.getParameter("sql_replace") != null;
      performCloudSqlAccess(dbParam, (rows != null ? rows : 10),
          (columns != null ? columns : 1), (len != null ? len : 10),
          (timeout != null ? timeout : 0), replace, w);
    }
    /*
    // The spanner functionality is commented out because it results
    // in one version violations.
    String spannerId = req.getParameter("spanner_id");
    if (spannerId != null) {
      String spannerDb = req.getParameter("spanner_db");
      performSpannerAccess(spannerId, spannerDb, w);
    }
    */
    String awtText = req.getParameter("awt_text");
    if (awtText != null) {
      performAwtTextRendering(awtText, w);
    }
    if (req.getParameter("oom") != null) {
      throw new OutOfMemoryError("intentional termination");
    }
    if (req.getParameter("jmx_info") != null) {
      performJmxInfo(w);
    }
    if (req.getParameter("jmx_list_vm_options") != null) {
      performJmxListVmOptions(w);
    }
    if (req.getParameter("jmx_thread_dump") != null) {
      performJmxThreadDump(w);
    }
    String metadataURL = req.getParameter("get_metadata");
    if (metadataURL != null) {
      emit(w, fetchMetadata(new URL(metadataURL)));
    }
    if (req.getParameter("list_headers") != null) {
      performListHeaders(req, w);
    }
    if (req.getParameter("list_processes") != null) {
      performListProcesses(w);
    }
    if (req.getParameter("validate_fs") != null) {
      performReadOnlyFSCheck(w);
    }
    String forward = req.getParameter("forward");
    if (forward != null && req.getAttribute("forwarded") == null) {
      req.setAttribute("forwarded", true);
      RequestDispatcher dispatcher = req.getRequestDispatcher("/?" + forward);
      dispatcher.forward(req, resp);
    }
    w.flush();
    if (silent) {
      responseWriter.println("OK");
    }
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException,
      IOException {
    resp.setContentType("text/plain");
    PrintWriter w = resp.getWriter();
    CountingInputStream payload = new CountingInputStream(req.getInputStream());
    ByteStreams.copy(payload, ByteStreams.nullOutputStream());
    w.print(payload.getCount());
    w.flush();
  }

  /**
   * Performs some cpu intensive work for the specified amount of time.
   *
   * @param ms the (approximate) time in milliseconds
   * @param w response writer
   */
  private void performMathMs(int ms, PrintWriter w) {
    emitf(w, "Burning cpu for %d ms", ms);
    runRepeatedly(ms, new Runnable() {
      @Override
      public void run() {
        performMath(random.nextBoolean());
      }
    });
    logger.info("Cpu burned");
  }

  /**
   * Performs some cpu intensive work for the specified number of iterations.
   *
   * @param count the number of iterations
   * @param w response writer
   */
  private void performMathLoops(int count, PrintWriter w) {
    emitf(w, "Burning cpu for %d loops", count);
    for (int i = 0; i < count; ++i) {
        performMath(random.nextBoolean());
    }
    logger.info("Cpu burned");
  }

  /**
   * Performs some cpu intensive work.
   *
   * <p>We try to make it harder for Hotspot to optimize away the computation by adding a random
   * parameter and by including an unreachable (but not obviously so) "throw" statement.
   *
   * @param addOne whether to add a spurious "one" value as part of the computation
   */
  private static void performMath(boolean addOne) {
    int x = 0;
    for (int i = 0; i < 200; ++i) {
      x += BigIntegerMath.log2(BigIntegerMath.factorial(i)
          .add(addOne ? BigInteger.ONE : BigInteger.ZERO), RoundingMode.DOWN);
    }
    if (x != 109766 && !addOne) {
      throw new AssertionError("incorrect result");
    }
  }

  /**
   * Performs some memcache work.
   *
   * @param count the number of iterations to perform
   * @param size memcache value size
   * @param w response writer
   */
  private void performMemcacheLoops(int count, int size, PrintWriter w) {
    emitf(w, "Running memcache for %d loops with value size %d", count, size);
    MemcacheService memcacheService = MemcacheServiceFactory.getMemcacheService();
    for (int i = 0; i < count; ++i) {
      String key = "test_key:" + random.nextInt(10000);
      memcacheService.put(key, createRandomString(size));
      memcacheService.get(key);
    }
    Stats stats = memcacheService.getStatistics();
    emitf(w, "Cache hits: %d", stats.getHitCount());
    emitf(w, "Cache misses: %d", stats.getMissCount());
  }

  /**
   * Performs some logging actions at random log levels.
   *
   * @param count the number of log entries to create
   * @param w response writer
   */
  private void performLogging(int count, PrintWriter w) {
    emitf(w, "Logging %d entries", count);
    logger.info("Starting logging");
    for (int i = 0; i < count; ++i) {
      logger.log(LOG_LEVELS[random.nextInt(LOG_LEVELS.length)],
          "An informative log message with some interesting words.");
    }
    logger.info("Done logging");
  }

  /**
   * Flushes the logs.
   *
   * @param w response writer
   */
  private static void performLogFlush(PrintWriter w) {
    emit(w, "Flushing logs");
    ApiProxy.flushLogs();
  }

  /**
   * Generates a response of the specified size.
   *
   * @param size desired number of characters in the response
   * @param w response writer
   */
  private static void performResponseSize(int size, PrintWriter w) {
    w.print(Strings.repeat("a", size));
  }

  /**
   * Generates a random response of the specified size.
   *
   * @param size desired number of characters in the response
   * @param w response writer
   */
  private void performRandomResponseSize(int size, PrintWriter w) {
    while (size > 0) {
      String s = Integer.toString(random.nextInt(Integer.MAX_VALUE));
      if (s.length() > size) {
        s = s.substring(0, size);
      }
      w.print(s);
      size -= s.length();
    }
  }

  /**
   * Performs the specified number of datastore queries.
   *
   * @param count the number of queries to perform
   * @param w response writer
   */
  private void performQueries(int count, PrintWriter w) {
    DatastoreService datastoreService = DatastoreServiceFactory.getDatastoreService();
    for (int i = 0; i < count; ++i) {
      Query query = new Query(CAR_KIND);
      Filter filter = null;
      if (random.nextBoolean()) {
        filter = new Query.FilterPredicate(COLOR_PROPERTY, Query.FilterOperator.EQUAL,
            COLORS[random.nextInt(COLORS.length)]);

      } else {
        filter = new Query.FilterPredicate(BRAND_PROPERTY, Query.FilterOperator.EQUAL,
            BRANDS[random.nextInt(BRANDS.length)]);
      }
      query.setFilter(filter);
      List<Entity> results = datastoreService.prepare(query)
          .asList(FetchOptions.Builder.withLimit(20));
      emitf(w, "Retrieved %d entities", results.size());
    }
  }

  /**
   * Adds the specified number of entities to the datastore.
   *
   * @param count the number of entities to persist
   * @param w response writer
   */
  private void performAddEntities(int count, PrintWriter w) {
    DatastoreService datastoreService = DatastoreServiceFactory.getDatastoreService();
    for (int i = 0; i < count; ++i) {
      Entity car = new Entity(CAR_KIND);
      car.setProperty(COLOR_PROPERTY, COLORS[random.nextInt(COLORS.length)]);
      car.setProperty(BRAND_PROPERTY, BRANDS[random.nextInt(BRANDS.length)]);
      datastoreService.put(car);
    }
    emitf(w, "Added %d entities", count);
  }

  /**
   * Counts the "car" entities in the datastore.
   *
   * @param w response writer
   */
  private static void performCount(PrintWriter w) {
    DatastoreService datastoreService = DatastoreServiceFactory.getDatastoreService();
    Query query = new Query(CAR_KIND);
    int count = datastoreService.prepare(query).countEntities(FetchOptions.Builder.withDefaults());
    emitf(w, "Found %d entities", count);
  }

  /**
   * Inserts a "log" entity into the datastore with the current request URL and timestamp.
   *
   * @param w response writer
   */
  private static void performCron(String url, PrintWriter w) {
    DatastoreService datastoreService = DatastoreServiceFactory.getDatastoreService();
    Entity log = new Entity(LOG_KIND);
    log.setProperty(URL_PROPERTY, url);
    log.setProperty(DATE_PROPERTY, new Date());
    datastoreService.put(log);
    emit(w, "Persisted log entry");
  }

  /** Prints out logout url if there is a logged in user, otherwise prints out a login url. */
  private static void performUserLogin(PrintWriter w, String url, Principal principal) {
    UserService userService = UserServiceFactory.getUserService();
    if (principal != null) {
      emitf(w, "Hello %s. Sign out with %s", principal.getName(),
          userService.createLogoutURL(url));
    } else {
      emitf(w, "Sign in with %s", userService.createLoginURL(url));
    }
  }

  /** Issues url fetch request to a specified url. */
  private static void performUrlFetch(PrintWriter w, String url) throws IOException {
    URLFetchService urlFetchService = URLFetchServiceFactory.getURLFetchService();
    HTTPResponse httpResponse = urlFetchService.fetch(new URL(url));
    emitf(w, "Response code: %s", httpResponse.getResponseCode());
  }

  /** Fetches a URL using a {@code java.net.HttpURLConnection}. */
  private static void performUrlFetchUsingHttpURLConnection(PrintWriter w, String url)
      throws IOException {
    HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
    try (InputStream input = connection.getInputStream()) {
      long n = ByteStreams.exhaust(input);
      emitf(w, "Response code: %d", connection.getResponseCode());
      emitf(w, "Bytes read: %d", n);
    }
  }

  /**
   * Allocates a ByteBuffer of the specified size.
   *
   * @param size requested buffer size
   * @param direct whether to allocate a direct byte buffer instead of a regular one
   * @param w response writer
   * @return the allocated ByteBuffer
   */
  private static ByteBuffer performByteBufferAllocation(int size, boolean direct, PrintWriter w) {
    ByteBuffer buffer = direct ? ByteBuffer.allocateDirect(size) : ByteBuffer.allocate(size);
    // Write at 4K intervals to hit all the pages.
    for (int offset = 0; offset < size; offset += 4096) {
      buffer.put(offset, (byte) 0xCC);
    }
    emitf(w, "Allocated %d bytes in a %s buffer", size, (direct ? "direct" : "regular"));
    return buffer;
  }

  /**
   * Lists all the system properties in sorted order.
   *
   * @param w response writer
   */
  private static void performListSystemProperties(PrintWriter w) {
    Properties props = System.getProperties();
    SortedSet<String> sortedNames = new TreeSet<>(props.stringPropertyNames());
    for (String name : sortedNames) {
      String value = props.getProperty(name);
      emitf(w, "%s = %s", name, value);
    }
  }

  /**
   * Lists all the environment variables in sorted order.
   *
   * @param w response writer
   */
  private static void performListEnvironment(PrintWriter w) {
    SortedMap<String, String> vars = new TreeMap<>(System.getenv());
    for (Map.Entry<String, String> var : vars.entrySet()) {
      emitf(w, "%s = %s", var.getKey(), var.getValue());
    }
  }

  /**
   * Lists all the headers in the incoming request.
   *
   * @param req Request
   * @param w response writer
   */
  private static void performListHeaders(HttpServletRequest req, PrintWriter w) {
    @SuppressWarnings("unchecked")
    List<String> headerNames = Collections.list(req.getHeaderNames());
    for (String headerName : headerNames) {
      emitf(w, "%s = %s", headerName, req.getHeader(headerName));
    }
  }

  /**
   * Checks if the filesystem is read only. Only temp directory should be writable.
   *
   * @param w response writer
   */
  private static void performReadOnlyFSCheck(PrintWriter w) throws IOException {
    Path tempFile = Files.createTempFile("temp", ".txt");
    try (BufferedWriter tempFileWriter = Files.newBufferedWriter(tempFile, UTF_8)) {
      tempFileWriter.append("Writing to temp file");
    }
    logger.info("Writing to temp file succeeded.");
    Path readonlyFile = Paths.get("/readonly.txt");
    try (BufferedWriter tempFileWriter = Files.newBufferedWriter(readonlyFile, UTF_8)) {
      tempFileWriter.append("Writing to readonly file");
      throw new AssertionError("File system is not readonly.");
    } catch (IOException ex) {
      logger.info("Unable to write to /test.txt as expected. " + ex.getMessage());
    }
    emitf(w, "Readonly filesystem check: OK");
  }

  /**
   * Lists all processes from /proc and their owners.
   *
   * @param w response writer
   */
  private static void performListProcesses(PrintWriter w) throws IOException {
    Path proc = Paths.get("/proc");
    try (Stream<Path> stream =
        Files.list(proc).filter(path -> path.toString().matches("/proc/\\d+"))) {
      for (Path path : stream.toArray(Path[]::new)) {
        String user = Files.getOwner(path).getName();
        Path commFile = Paths.get(path.toAbsolutePath().toString(), "comm");
        String processName = Files.readAllLines(commFile).stream().findFirst().orElse("unknown");
        emitf(w, "%s:%s", processName, user);
      }
    }
  }

  /**
   * Lists all the {@link ApiProxy.Environment} attributes in sorted order.
   *
   * @param w response writer
   */
  private static void performListAttributes(PrintWriter w) {
    SortedMap<String, Object> attributes = new TreeMap<>(
        ApiProxy.getCurrentEnvironment().getAttributes());
    for (Map.Entry<String, Object> entry : attributes.entrySet()) {
      emitf(w, "%s = %s", entry.getKey(), entry.getValue());
    }
  }

  /**
   * Sets some servlet attributes, then lists all servlet attributes. {@code servletAttributeString}
   * looks like {@code foo=bar:baz=buh} and is interpreted to mean that the attribute {@code foo}
   * should be set to {@code bar}, etc. The reply lists each attribute on its own line, with
   * a format like {@code foo = bar}.
   */
  private static void performSetServletAttributes(
      HttpServletRequest req, String servletAttributeString, PrintWriter w) {
    Splitter eq = Splitter.on('=');
    Splitter.on(':').splitToStream(servletAttributeString)
        .map(eq::splitToList)
        .forEach(list -> req.setAttribute(list.get(0), list.get(1)));
    @SuppressWarnings("unchecked")
    Enumeration<String> names = req.getAttributeNames();
    Collections.list(names).stream()
        .sorted()
        .forEach(attr -> emitf(w, "%s = %s", attr, req.getAttribute(attr)));
  }

  private static void performFetchProjectIdFromMetadata(PrintWriter w) throws IOException {
    URL url = new URL("http://metadata.google.internal/computeMetadata/v1/project/project-id");
    String token = fetchMetadata(url);
    emitf(w, "Project id: %s", token);
  }

  private static void performFetchServiceAccountTokenFromMetadata(PrintWriter w)
      throws IOException {
    URL url = new URL("http://metadata.google.internal/computeMetadata/v1/instance"
        + "/service-accounts/default/token");
    String token = fetchMetadata(url);
    emitf(w, "Token: %s", token);
  }

  private static void performFetchServiceAccountScopesFromMetadata(PrintWriter w)
      throws IOException {
    URL url = new URL("http://metadata.google.internal/computeMetadata/v1/instance"
        + "/service-accounts/default/scopes");
    String scopes = fetchMetadata(url);
    emitf(w, "Scopes: %s", scopes);
  }

  /**
   * Logs the remaining time for the request, in milliseconds, and also writes it out as part of the
   * HTTP response.
   *
   * <p>Writing the value into the logs is useful when invoking this handler using task queues.
   *
   * @param w response writer
   */
  private static void performLogRemainingTime(PrintWriter w) {
    long t = ApiProxy.getCurrentEnvironment().getRemainingMillis();
    emitf(w, "Remaining time for request: %d ms", t);
  }

  /**
   * Adds a number of tasks to the default queque.
   *
   * <p>Note that special characters in the url will have to be url-encoded when passed as a query
   * parameter. E.g. {@code &} must be replaced by {@code %26}. For example, {@code
   * /?tasks=1&task_url=/?memcache_loops=10%26size=100} will issue a {@code GET} for {@code
   * /?memcache_loops=10&size=100}.
   *
   * @param count number of tasks to add
   * @param url target URL for the task
   * @param w response writer
   */
  private static void performAddTasks(int count, String url, PrintWriter w) {
    emitf(w, "Adding %d tasks for URL %s", count, url);
    for (int i = 0; i < count; ++i) {
      TaskOptions taskoptions = TaskOptions.Builder
          .withMethod(TaskOptions.Method.GET)
          .url(url);
      QueueFactory.getDefaultQueue().add(taskoptions);
    }
    logger.info("Done adding tasks");
  }

  /**
   * Post a deferred task to the default queue.
   *
   * @param w response writer
   */
  private static void performAddDeferredTask(PrintWriter w) {
    emitf(w, "Adding a deferred task...");
    QueueFactory.getDefaultQueue()
        .add(TaskOptions.Builder.withPayload(new MyDeferredTaskCallBack()));

    logger.info("Done adding deferred task.");
  }

  /**
   * Verify that the deferred task has been called back.
   *
   * @param w response writer
   */
  private static void performVerifyDeferredTask(PrintWriter w) {
    try {
      emitf(
          w,
          "Verify deferred task: %b",
          MyDeferredTaskCallBack.callBackDone.await(10, TimeUnit.SECONDS));
    } catch (InterruptedException ex) {
      emitf(w, "Failed to verify the call back to deferred task...");
    }
    logger.info("Done verifying deferred task.");
  }

  /** Simple deferred task call back that change a global variable. */
  private static class MyDeferredTaskCallBack implements DeferredTask {

    // Static variable that will be updated in the deferred task queue call back.
    static CountDownLatch callBackDone = new CountDownLatch(1);

    @Override
    public void run() {
      callBackDone.countDown();
      System.out.println("Deferred task payload called back.");
    }
  }

  /**
   * Access a Cloud SQL database.
   *
   * @param db database connection string
   * @param rows number of rows to insert
   * @param columns number of columns in the test table to be created
   * @param len length of the values to insert
   * @param timeout statement timeout (zero means no timeout)
   * @param replace if true, use REPLACE INTO instead of INSERT INTO
   * @param w response writer
   */
  private void performCloudSqlAccess(
      String db, int rows, int columns, int len, int timeout, boolean replace, PrintWriter w)
      throws IOException, ServletException {
    String dbUrl = "jdbc:google:mysql://" + db;
    try {
      Class.forName("com.mysql.jdbc.GoogleDriver");
    } catch (ClassNotFoundException e) {
      throw new ServletException("Error loading Google JDBC Driver", e);
    }

    logger.info(String.format("connecting to database %s", dbUrl));
    try (Connection conn = DriverManager.getConnection(dbUrl)) {
      emitf(w, "connected to database %s", dbUrl);
      ResultSet result = conn.createStatement().executeQuery("SELECT 1");
      result.next();
      emit(w, "executed a query, read one result row");
      String tableName = String.format("test%s", Integer.toString(random.nextInt(10000)));
      conn.createStatement().executeUpdate(drop(tableName));
      try {
        conn.createStatement().executeUpdate(create(tableName, columns));
        emitf(w, "created table %s with %d columns", tableName, columns);

        conn.setAutoCommit(false);
        try {
          conn.setTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED);
          PreparedStatement s = conn.prepareStatement(replace ? replaceInto(tableName, columns)
              : insertInto(tableName, columns));
          s.setQueryTimeout(timeout);
          for (int i = 0; i < rows; ++i) {
            for (int j = 1; j <= columns; ++j) {
              s.setString(j, createRandomString(len));
            }
            s.addBatch();
          }
          s.executeBatch();
          conn.commit();
        } finally {
          conn.setAutoCommit(true);
        }
        emitf(w, "executed %d %s as a batch", rows, replace ? "REPLACE INTO" : "INSERT INTO");
      } finally {
        conn.createStatement().executeUpdate(drop(tableName));
        emitf(w, "dropped table %s", tableName);
      }
    } catch (SQLException e) {
      throw new ServletException("Error executing SQL", e);
    }
  }

  // /**
  // * Accesses a Spanner database.
  // *
  // * @param spannerId spanner ID string
  // * @param spannerDb spanner database ID string
  // * @param w response writer
  // */
  // private void performSpannerAccess(String spannerId, String spannerDb, PrintWriter w) {
  //  SpannerOptions options = SpannerOptions.newBuilder().build();
  //  Spanner spanner = options.getService();
  //  try {
  //    DatabaseClient dbClient =
  //        spanner.getDatabaseClient(DatabaseId.of(options.getProjectId(), spannerId, spannerDb));
  //    emitf(w, "connected to spanner db at %s:%s", spannerId, spannerDb);
  //    try (com.google.cloud.spanner.ResultSet resultSet =
  //        dbClient.singleUse().executeQuery(Statement.of("SELECT 1"))) {
  //      emitf(w, "executed select statement %s", "SELECT 1");
  //      while (resultSet.next()) {
  //        emitf(w, "result set value: %d", resultSet.getLong(0));
  //      }
  //    }
  //  } finally {
  //    spanner.close();
  //  }
  // }

  /**
   * Renders a string into an image using AWT.
   *
   * @param awtText the string to render
   * @param w response writer
   */
  private static void performAwtTextRendering(String awtText, PrintWriter w) throws IOException {
    int width = 2000;
    int height = 400;
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    Graphics g = image.createGraphics();
    JEditorPane jep = new JEditorPane("text/html", awtText);
    jep.setSize(width, height);
    jep.print(g);
    g.dispose();
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ImageIO.write(image, "png", baos);
    byte[] output = baos.toByteArray();
    emitf(w, "image size: %d", output.length);
  }

  /**
   * Prints JMX info about memory, loaded classes, threads, etc.
   *
   * @param w response writer
   */
  private static void performJmxInfo(PrintWriter w) throws IOException {
    MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
    MemoryUsage heapUsage = memory.getHeapMemoryUsage();
    emitf(w, "heap.init = %d", heapUsage.getInit());
    emitf(w, "heap.used = %d", heapUsage.getUsed());
    emitf(w, "heap.max = %d", heapUsage.getMax());
    emitf(w, "heap.committed = %d", heapUsage.getCommitted());
    MemoryUsage nonHeapUsage = memory.getNonHeapMemoryUsage();
    emitf(w, "non_heap.init = %d", nonHeapUsage.getInit());
    emitf(w, "non_heap.used = %d", nonHeapUsage.getUsed());
    emitf(w, "non_heap.max = %d", nonHeapUsage.getMax());
    emitf(w, "non_heap.committed = %d", nonHeapUsage.getCommitted());
    ClassLoadingMXBean classLoading = ManagementFactory.getClassLoadingMXBean();
    emitf(w, "loaded.classes = %d", classLoading.getLoadedClassCount());
    emitf(w, "unloaded.classes = %d", classLoading.getUnloadedClassCount());
    emitf(w, "total.loaded.classes = %d", classLoading.getTotalLoadedClassCount());
    ThreadMXBean threading = ManagementFactory.getThreadMXBean();
    emitf(w, "thread.count = %d", threading.getThreadCount());
    emitf(w, "daemon.thread.count = %d", threading.getDaemonThreadCount());
    emitf(w, "total.started.thread.count = %d", threading.getTotalStartedThreadCount());
    emitf(w, "peak.thread.count = %d", threading.getPeakThreadCount());
    CompilationMXBean compilation = ManagementFactory.getCompilationMXBean();
    emitf(w, "compiler.time = %d", compilation.getTotalCompilationTime());
    for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
      String name = gc.getName().replace(" ", "_");
      emitf(w, "gc.%s.count = %d", name, gc.getCollectionCount());
      emitf(w, "gc.%s.time = %d", name, gc.getCollectionTime());
    }
    for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
      String name = pool.getName().replace(" ", "_");
      emitf(w, "memory.%s.type = %s", name, pool.getType().name().toLowerCase());
      emitf(w, "memory.%s.used = %d", name, pool.getUsage().getUsed());
      emitf(w, "memory.%s.max = %d", name, pool.getUsage().getMax());
      emitf(w, "memory.%s.peak.used = %d", name, pool.getPeakUsage().getUsed());
      emitf(w, "memory.%s.peak.max = %d", name, pool.getPeakUsage().getMax());
    }
  }

  /**
   * Lists all writable JMX VM options from HotSpotDiagnosticMXBean.
   *
   * @param w response writer
   */
  private static void performJmxListVmOptions(PrintWriter w) throws IOException {
    MBeanServer server = ManagementFactory.getPlatformMBeanServer();
    HotSpotDiagnosticMXBean bean = ManagementFactory.newPlatformMXBeanProxy(server,
        "com.sun.management:type=HotSpotDiagnostic", HotSpotDiagnosticMXBean.class);
    for (VMOption option : bean.getDiagnosticOptions()) {
      emitf(w, "%s = %s", option.getName(), option.getValue());
    }
  }

  /**
   * Emits thread dump information.
   *
   * @param w response writer
   */
  private static void performJmxThreadDump(PrintWriter w) throws IOException {
    ThreadMXBean threading = ManagementFactory.getThreadMXBean();
    for (ThreadInfo i : threading.dumpAllThreads(false, false)) {
      emitf(w, "%s", i.toString());
    }
 }

  private static String drop(String tableName) {
    return String.format("DROP TABLE IF EXISTS %s", tableName);
  }

  private static String create(String tableName, int columns) {
    StringBuilder sb = new StringBuilder();
    sb.append("CREATE TABLE ");
    sb.append(tableName);
    sb.append(" (");
    for (int j = 1; j <= columns; ++j) {
      if (j != 1) {
        sb.append(", ");
      }
      sb.append("s");
      sb.append(j);
      sb.append(" VARCHAR(255)");
    }
    sb.append(")");
    return sb.toString();
  }

  private static String insertInto(String tableName, int columns) {
    return String.format("INSERT INTO %s VALUES (?" + Strings.repeat(",?", columns - 1) + ")",
        tableName);
  }

  private static String replaceInto(String tableName, int columns) {
    return String.format("REPLACE INTO %s VALUES (?" + Strings.repeat(",?", columns - 1) + ")",
        tableName);
  }

  /**
   * Returns the contents of the metadata item at the specified url.
   *
   * @param url The url to fetch, usually starting with {@code http://metadata.google.internal}.
   * @throws IOException In case of error. The exception message will contain the server response.
   */
  private static String fetchMetadata(URL url) throws IOException {
    String data = null;
    HttpURLConnection connection = null;
    try {
      connection = (HttpURLConnection) url.openConnection();
      connection.setRequestProperty("Metadata-Flavor", "Google");
      InputStream input = connection.getInputStream();
      if (connection.getResponseCode() == 200) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, UTF_8))) {
          data = Joiner.on("\n").join(CharStreams.readLines(reader));
        }
      }
    } catch (IOException e) {
      if (connection != null) {
        IOException newException;
        try {
          InputStream input = connection.getErrorStream();
          if (input != null) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, UTF_8))) {
              String error = Joiner.on("\n").join(CharStreams.readLines(reader));
              newException = new IOException("Failed to fetch metadata: " + error);
            }
          } else {
            newException = e;
          }
        } catch (IOException e2) {
          newException = e2;
        }
        throw newException;
      }
    }
    return data;
  }

  /**
   * Returns an Integer corresponding to the value of a request parameter.
   *
   * @param name the name of the request parameter to parse
   * @param req the HttpServletRequest
   * @return the value of the specified parameter, or null if there is no such parameter
   * @throws ServletException if the parameter has an invalid value
   */
  private static Integer getIntParameter(String name, HttpServletRequest req)
      throws ServletException {
    String value = req.getParameter(name);
    if (value != null) {
      try {
        return Integer.parseInt(value);
      } catch (NumberFormatException e) {
        throw new ServletException("parameter " + name + "is not a valid integer");
      }
    }
    return null;
  }

  /**
   * Runs an action repeatedly until at least the specified number of milliseconds has elapsed.
   *
   * @param ms the minimum elapsed time in milliseconds
   * @param action the action to run
   */
  private static void runRepeatedly(int ms, Runnable action) {
    long remaining = ms;
    while (remaining > 0) {
      long start = System.currentTimeMillis();
      action.run();
      long stop = System.currentTimeMillis();
      remaining -= (stop - start);
    }
  }

  /**
   * Validate all request query parameters against the list of supported parameters.
   *
   * @param req servlet request
   * @throws ServletException in case of unrecognized parameters
   */
  private static void validateParameters(HttpServletRequest req) throws ServletException {
    @SuppressWarnings("unchecked") // legacy API returns raw Enumeration
    List<String> parameterNames = Collections.list(req.getParameterNames());
    Set<String> params = Sets.newTreeSet(parameterNames);
    Set<String> invalidParams = Sets.difference(params, VALID_PARAMETERS);
    if (!invalidParams.isEmpty()) {
      throw new ServletException("unrecognized query parameters: "
          + Joiner.on(",").join(invalidParams));
    }
  }

  /**
   * Log a message at INFO level, then write it to the response as HTML.
   *
   * @param w response writer
   * @param msg the message to emit
   */
  private static void emit(PrintWriter w, String msg) {
    logger.info(msg);
    w.printf("%s\n", msg);
  }

  /**
   * Format and log a message at INFO level, then write it to the response as HTML.
   *
   * <p>The message is formatted using {@code String.format}
   *
   * @param w response writer
   * @param format the format string to use
   * @param args arguments to use
   */
  @FormatMethod
  private static void emitf(PrintWriter w, String format, Object... args) {
    emit(w, String.format(format, args));
  }

  /**
   * Returns a random string of the specified length.
   *
   * @param size the desired length for the string
   */
  private String createRandomString(int size) {
    byte[] bytes = new byte[size];
    for (int i = 0; i < size; ++i) {
      bytes[i] = (byte) (random.nextInt(127 - 32) + 32);
    }
    return new String(bytes, US_ASCII_CHARSET);
  }
}
