package com.google.apphosting.runtime.jetty9;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Result;
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
    String app = "senderror" + environment;
    copyAppToDir(app, temp.getRoot().toPath());
    runtime = runtimeContext();
    System.err.println("==== Using Environment: " + environment + " " + httpMode + " ====");
  }

  @After
  public void after() throws Exception {
    if (runtime != null) {
      runtime.close();
    }
  }

  @Test
  public void testSendError() throws Exception {
    String url = runtime.jettyUrl("/senderror");
    CompletableFuture<Result> completionListener = new CompletableFuture<>();
    httpClient.newRequest(url).send(completionListener::complete);

    Result result = completionListener.get(5, TimeUnit.SECONDS);
    ContentResponse response = result.getRequest().send();
    assertEquals(500, response.getStatus());
    assertThat(response.getContentAsString(), containsString("Something went wrong."));
  }

  private RuntimeContext<?> runtimeContext() throws Exception {
    RuntimeContext.Config<?> config =
        RuntimeContext.Config.builder().setApplicationPath(temp.getRoot().toString()).build();
    return RuntimeContext.create(config);
  }

}