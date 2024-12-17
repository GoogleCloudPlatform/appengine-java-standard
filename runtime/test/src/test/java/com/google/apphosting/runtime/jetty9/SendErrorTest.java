package com.google.apphosting.runtime.jetty9;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collection;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.eclipse.jetty.client.HttpClient;


@RunWith(Parameterized.class)
public class SendErrorTest extends JavaRuntimeViaHttpBase {

  @Parameterized.Parameters
  public static Collection<Object[]> parameters() {
    return Arrays.asList(
        new Object[][] {
            {"jetty94", false},
            {"jetty94", true},
            {"ee8", false},
            {"ee8", true},
            {"ee10", false},
            {"ee10", true},
        });
  }

  @Rule public TemporaryFolder temp = new TemporaryFolder();
  private final HttpClient httpClient = new HttpClient();
  private final boolean httpMode;
  private final String environment;
  private RuntimeContext<?> runtime;


  public SendErrorTest(String environment, boolean httpMode) {
    this.environment = environment;
    this.httpMode = httpMode;
    System.setProperty("appengine.use.HttpConnector", Boolean.toString(httpMode));
  }

  @Before
  public void start() throws Exception {
    String app = "com/google/apphosting/runtime/jetty9/senderrorapp/" + environment;
    copyAppToDir(app, temp.getRoot().toPath());
    httpClient.start();
    runtime = runtimeContext();
    System.err.println("==== Using Environment: " + environment + " " + httpMode + " ====");
  }

  @After
  public void after() throws Exception {
    httpClient.stop();
    runtime.close();
  }

  @Test
  public void testSendError() throws Exception {
    String url = runtime.jettyUrl("/send-error");
    ContentResponse response = httpClient.GET(url);
    assertEquals(HttpStatus.OK_200, response.getStatus());
    assertThat(response.getContentAsString(), containsString("<h1>Hello, world!</h1>"));

    url = runtime.jettyUrl("/send-error?errorCode=404");
    response = httpClient.GET(url);
    assertEquals(HttpStatus.NOT_FOUND_404, response.getStatus());
    assertThat(response.getContentAsString(), containsString("<h1>Error 404 Not Found</h1>"));

    url = runtime.jettyUrl("/send-error?errorCode=500");
    response = httpClient.GET(url);
    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR_500, response.getStatus());
    assertThat(response.getContentAsString(), containsString("<h1>Error 500 Internal Server Error</h1>"));

    url = runtime.jettyUrl("/send-error?errorCode=503");
    response = httpClient.GET(url);
    assertEquals(HttpStatus.SERVICE_UNAVAILABLE_503, response.getStatus());
    assertThat(response.getContentAsString(), containsString("<h1>Unhandled Error</h1>"));

  }

  private RuntimeContext<?> runtimeContext() throws Exception {
    RuntimeContext.Config<?> config =
        RuntimeContext.Config.builder().setApplicationPath(temp.getRoot().toString()).build();
    return RuntimeContext.create(config);
  }

}