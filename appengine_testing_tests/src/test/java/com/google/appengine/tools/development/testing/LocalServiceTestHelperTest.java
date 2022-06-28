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

package com.google.appengine.tools.development.testing;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.truth.Truth.assertThat;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import com.google.appengine.api.NamespaceManager;
import com.google.appengine.api.ThreadManager;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;
import com.google.appengine.tools.development.ApiProxyLocal;
import com.google.appengine.tools.development.Clock;
import com.google.appengine.tools.development.LocalEnvironment;
import com.google.appengine.tools.development.RequestEndListenerHelper;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper.RequestMillisTimer;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.Environment;
import com.google.common.collect.ImmutableMap;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public class LocalServiceTestHelperTest {
  private static final TimeZone LOS_ANGELES = TimeZone.getTimeZone("America/Los_Angeles");
  private static final TimeZone NEW_YORK = TimeZone.getTimeZone("America/New_York");
  private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

  private TimeZone original;

  private static final long JOIN_WAIT = 5000;

  @Before
  public void setUp() throws Exception {
    original = TimeZone.getDefault();
    TimeZone.setDefault(LOS_ANGELES);
  }

  @After
  public void done() throws Exception {
    TimeZone.setDefault(original);
  }

  @Test
  public void testNewHelper_MultipleModulesServiceTestConfig_ThrowsIllegalArgument() {
    try {
      new LocalServiceTestHelper(
          new LocalModulesServiceTestConfig(), new LocalModulesServiceTestConfig());
      fail("Expected an IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      assertThat(e)
          .hasMessageThat()
          .isEqualTo("Multiple LocalModulesServiceTestConfig instances provided");
    }
  }

  @Test
  public void testEnvProperlySet_Defaults() {
    LocalServiceTestHelper helper = new LocalServiceTestHelper();
    helper.setUp();
    assertEquals("test", ApiProxy.getCurrentEnvironment().getAppId());
    assertEquals("1.0.1", ApiProxy.getCurrentEnvironment().getVersionId());
    assertThat(ApiProxy.getCurrentEnvironment().getAttributes()).isNotEmpty();
    assertNull(ApiProxy.getCurrentEnvironment().getAuthDomain());
    assertNull(ApiProxy.getCurrentEnvironment().getEmail());
    assertEquals(Long.MAX_VALUE, ApiProxy.getCurrentEnvironment().getRemainingMillis());
    assertThat(NamespaceManager.getGoogleAppsNamespace()).isEmpty();
    UserService us = UserServiceFactory.getUserService();
    assertFalse(us.isUserLoggedIn());
    try {
      assertFalse(us.isUserAdmin());
      fail("expected ise");
    } catch (IllegalStateException ise) {
      // expected because user is not logged in
    }
    ApiProxyLocal apiProxyLocal = (ApiProxyLocal) ApiProxy.getDelegate();
    assertSame(Clock.DEFAULT, apiProxyLocal.getClock());
  }

  @Test
  public void testEnvProperlySet_Custom() {
    Clock alwaysEpoch =
        new Clock() {
          @Override
          public long getCurrentTime() {
            return 0;
          }
        };
    RequestMillisTimer remainingMillis = () -> 666L;
    LocalServiceTestHelper helper =
        new LocalServiceTestHelper()
            .setEnvAppId("custom app")
            .setEnvVersionId("2.0")
            .setEnvAttributes(new HashMap<>(ImmutableMap.<String, Object>of("yam", "yar")))
            .setEnvAuthDomain("auth domain")
            .setEnvEmail("custom email")
            .setEnvIsAdmin(true)
            .setEnvIsLoggedIn(true)
            .setEnvRequestNamespace("custom namespace")
            .setClock(alwaysEpoch)
            .setRemainingMillisTimer(remainingMillis);
    helper.setUp();
    assertEquals("custom app", ApiProxy.getCurrentEnvironment().getAppId());
    assertEquals("2.0.1", ApiProxy.getCurrentEnvironment().getVersionId());
    assertEquals("yar", ApiProxy.getCurrentEnvironment().getAttributes().get("yam"));
    assertEquals("auth domain", ApiProxy.getCurrentEnvironment().getAuthDomain());
    assertEquals("custom email", ApiProxy.getCurrentEnvironment().getEmail());
    assertEquals(666L, ApiProxy.getCurrentEnvironment().getRemainingMillis());
    assertEquals("custom namespace", NamespaceManager.getGoogleAppsNamespace());
    UserService us = UserServiceFactory.getUserService();
    assertTrue(us.isUserLoggedIn());
    assertTrue(us.isUserAdmin());
    ApiProxyLocal apiProxyLocal = (ApiProxyLocal) ApiProxy.getDelegate();
    assertSame(alwaysEpoch, apiProxyLocal.getClock());
  }

  @Test
  public void testTimeZone_Default() {
    assertEquals(LOS_ANGELES, TimeZone.getDefault());
    LocalServiceTestHelper helper = new LocalServiceTestHelper();
    // constructing the helper doesn't change the tz
    assertEquals(LOS_ANGELES, TimeZone.getDefault());
    helper.setUp();
    // setting up the helper does change the tz
    assertEquals(UTC, TimeZone.getDefault());
    helper.tearDown();
    assertEquals(LOS_ANGELES, TimeZone.getDefault());
  }

  @Test
  public void testTimeZone_Override() {
    assertEquals(LOS_ANGELES, TimeZone.getDefault());
    LocalServiceTestHelper helper = new LocalServiceTestHelper().setTimeZone(NEW_YORK);
    // constructing the helper doesn't change the tz
    assertEquals(LOS_ANGELES, TimeZone.getDefault());
    helper.setUp();
    // setting up the helper does change the tz
    assertEquals(NEW_YORK, TimeZone.getDefault());
    helper.tearDown();
    assertEquals(LOS_ANGELES, TimeZone.getDefault());
  }

  @Test
  public void testTimeZone_OverrideWithNull() {
    // Grab the timezone that the JVM was started with.
    String timeZoneProperty = System.getProperty("user.timezone");
    checkArgument(
        timeZoneProperty != null && !timeZoneProperty.isEmpty(), "-Duser.timezone was not set");
    TimeZone jvmDefaultTimeZone = TimeZone.getTimeZone(timeZoneProperty);
    checkArgument(
        !jvmDefaultTimeZone.hasSameRules(LOS_ANGELES),
        "Need to be configured to a timezone other than %s (or its aliases)",
        LOS_ANGELES.getID());

    assertEquals(LOS_ANGELES, TimeZone.getDefault());
    LocalServiceTestHelper helper = new LocalServiceTestHelper().setTimeZone(null);
    // constructing the helper doesn't change the tz
    assertEquals(LOS_ANGELES, TimeZone.getDefault());
    helper.setUp();
    // setting up the helper reverts the tz to its value when the jvm was
    // started
    assertEquals(jvmDefaultTimeZone, TimeZone.getDefault());
    helper.tearDown();
    assertEquals(LOS_ANGELES, TimeZone.getDefault());
  }

  @Test
  public void testRequestThreads() throws InterruptedException {
    LocalServiceTestHelper helper = new LocalServiceTestHelper();
    helper.setUp();
    ApiProxy.Environment env = ApiProxy.getCurrentEnvironment();
    final AtomicReference<ApiProxy.Environment> threadEnv = new AtomicReference<>();
    try {
      Thread t =
          ThreadManager.createThreadForCurrentRequest(
              new Runnable() {
                @Override
                public void run() {
                  threadEnv.set(ApiProxy.getCurrentEnvironment());
                }
              });
      t.start();
      t.join(JOIN_WAIT);
      assertFalse(t.isAlive());
      assertEquals(env, threadEnv.get());
    } finally {
      helper.tearDown();
    }
  }

  @Test
  public void testRequestThreadsInterruptedAtEndOfTest() {
    LocalServiceTestHelper helper = new LocalServiceTestHelper();
    helper.setUp();
    final AtomicBoolean interrupted = new AtomicBoolean(false);
    Thread t = null;
    try {
      t =
          ThreadManager.createThreadForCurrentRequest(
              new Runnable() {
                @Override
                public void run() {
                  while (true) {
                    try {
                      Thread.sleep(50);
                    } catch (InterruptedException e) {
                      interrupted.set(true);
                      break;
                    }
                  }
                }
              });
      t.start();
      assertFalse(interrupted.get());
    } finally {
      helper.tearDown();
    }
    assertFalse(t.isAlive());
    assertTrue(interrupted.get());
  }

  @Test
  public void testCustomDelegate() {
    LocalServiceTestHelper helper = new LocalServiceTestHelper();
    helper.setUp();
    ApiProxyLocal apiProxyLocal = (ApiProxyLocal) ApiProxy.getDelegate();
    ApiProxy.setDelegate(Mockito.mock(ApiProxy.Delegate.class));
    assertSame(apiProxyLocal, LocalServiceTestHelper.getApiProxyLocal());
    helper.tearDown();
    assertNull(LocalServiceTestHelper.getApiProxyLocal());
  }

  static class TestRequestEndListener extends RequestEndListenerHelper {
    private int calls = 0;

    @Override
    public void onRequestEnd(Environment environment) {
      ++calls;
    }

    public int getCalls() {
      return calls;
    }
  }

  @Test
  public void testRequestEndListenerCalled() throws InterruptedException {
    LocalServiceTestHelper helper = new LocalServiceTestHelper();
    helper.setUp();
    final TestRequestEndListener requestEnd = new TestRequestEndListener();
    try {
      Thread t =
          ThreadManager.createThreadForCurrentRequest(
              () -> {
                requestEnd.register();
              });
      t.start();
      t.join(JOIN_WAIT);
      assertFalse(t.isAlive());
    } finally {
      helper.tearDown();
    }

    assertEquals(1, requestEnd.getCalls());
  }

  @Test
  public void testAllRequestEndListenersCalled() throws InterruptedException {
    LocalServiceTestHelper helper = new LocalServiceTestHelper();
    helper.setUp();
    final TestRequestEndListener requestEndThis = new TestRequestEndListener();
    final TestRequestEndListener requestEndThat = new TestRequestEndListener();
    try {
      Thread t =
          ThreadManager.createThreadForCurrentRequest(
              () -> {
                requestEndThis.register();
                requestEndThat.register();
              });
      t.start();
      t.join(JOIN_WAIT);
      assertFalse(t.isAlive());
    } finally {
      helper.tearDown();
    }

    assertEquals(1, requestEndThis.getCalls());
    assertEquals(1, requestEndThat.getCalls());
  }

  @Test
  public void testRequestEndListenerManualCall() throws InterruptedException {
    LocalServiceTestHelper helper = new LocalServiceTestHelper();
    helper.setUp();
    final TestRequestEndListener requestEnd = new TestRequestEndListener();
    try {
      Thread t =
          ThreadManager.createThreadForCurrentRequest(
              () -> {
                requestEnd.register();
              });
      t.start();
      t.join(JOIN_WAIT);
      assertFalse(t.isAlive());
      assertEquals(0, requestEnd.getCalls());

      // Manually run the end listeners.
      ((LocalEnvironment) ApiProxy.getCurrentEnvironment()).callRequestEndListeners();

      assertEquals(1, requestEnd.getCalls());
    } finally {
      helper.tearDown();
    }

    assertEquals(1, requestEnd.getCalls());
  }

  @Test
  public void testSetEnvInstance_instanceNotNumeric() {
    LocalServiceTestHelper helper = new LocalServiceTestHelper();
    try {
      helper.setEnvInstance("abc");
      fail("Expected a NumberFormatException");
    } catch (NumberFormatException nfe) {
      assertThat(nfe).hasMessageThat().isEqualTo("For input string: \"abc\"");
    }
  }

  @Test
  public void testSetEnvInstance_instanceTooSmall() {
    LocalServiceTestHelper helper = new LocalServiceTestHelper();
    try {
      helper.setEnvInstance("-2");
      fail("Expected an IllegalArgumentException");
    } catch (IllegalArgumentException iae) {
      assertThat(iae)
          .hasMessageThat()
          .isEqualTo("envInstanceId must be >= -1 and envInstanceId=-2");
    }
  }

  @Test
  public void testTearDown_modulesServiceTestHelperExceptionAfter() {
    LocalServiceTestConfig testConfig =
        new LocalServiceTestConfig() {
          @Override
          public void setUp() {}

          @Override
          public void tearDown() {
            throw new RuntimeException("Expected exception.");
          }
        };

    LocalModulesServiceTestConfig moduleServiceTestConfig =
        new LocalModulesServiceTestConfig() {

          @Override
          public void tearDown() {
            throw new RuntimeException("Not expected exception.");
          }
        };

    LocalServiceTestHelper helper = new LocalServiceTestHelper(testConfig, moduleServiceTestConfig);
    helper.setUp();
    try {
      helper.tearDown();
      fail();
    } catch (RuntimeException re) {
      assertThat(re).hasMessageThat().isEqualTo("Expected exception.");
    }
  }

  @Test
  public void testTearDown_modulesServiceTestHelperFirst() {
    LocalServiceTestConfig testConfig =
        new LocalServiceTestConfig() {
          @Override
          public void setUp() {}

          @Override
          public void tearDown() {}
        };

    LocalModulesServiceTestConfig moduleServiceTestConfig =
        new LocalModulesServiceTestConfig() {

          @Override
          public void tearDown() {
            throw new RuntimeException("Expected exception.");
          }
        };

    LocalServiceTestHelper helper = new LocalServiceTestHelper(testConfig, moduleServiceTestConfig);
    helper.setUp();
    try {
      helper.tearDown();
      fail();
    } catch (RuntimeException re) {
      assertThat(re).hasMessageThat().isEqualTo("Expected exception.");
    }
  }

  @Test
  public void testCopyEnvironment_doesNotThrow() {
    LocalServiceTestHelper helper = new LocalServiceTestHelper();
    helper.setEnvInstance("2");
    helper.setUp();
    // The following call must not throw.
    LocalServiceTestHelper.copyEnvironment(ApiProxy.getCurrentEnvironment());
  }

  @Test
  public void testTearDown_doesNotLeakThreads() {
    LocalServiceTestHelper helper =
        new LocalServiceTestHelper(
            new LocalDatastoreServiceTestConfig().setApplyAllHighRepJobPolicy().setNoStorage(true));
    DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
    ThreadGroup rootThreadGroup = rootThreadGroup();
    Deque<Integer> activeThreadCounts = new ArrayDeque<>();

    // Set up and tear down the helper repeatedly, to test whether it leaks threads. Once we have
    // done at least 10 iterations, we stop as soon as the number of threads decreases, or stays the
    // same for 10 consecutive iterations.
    for (int i = 0; i < 1_000; i++) {
      helper.setUp();
      datastore.allocateIds("Test", 1);
      helper.tearDown();
      activeThreadCounts.add(rootThreadGroup.activeCount());
      if (activeThreadCounts.size() > 10) {
        activeThreadCounts.removeFirst();
        int max = activeThreadCounts.stream().max(Comparator.naturalOrder()).get();
        int last = activeThreadCounts.getLast();
        if (last < max) {
          System.out.printf(
              "Thread count reduced from %d to %d after %d iterations\n", max, last, i);
          return;
        }
        int first = activeThreadCounts.getFirst();
        if (first == last) {
          System.out.printf("Thread count stable at %d after %d iterations\n", first, i);
          return;
        }
      }
    }
    ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
    for (ThreadInfo threadInfo : threadMXBean.dumpAllThreads(false, false)) {
      System.err.println(threadInfo);
    }
    fail("Thread count increased without bound, reaching " + rootThreadGroup.activeCount());
  }

  private static ThreadGroup rootThreadGroup() {
    for (ThreadGroup g = Thread.currentThread().getThreadGroup(); ; g = g.getParent()) {
      if (g.getParent() == null) {
        return g;
      }
    }
  }
}
