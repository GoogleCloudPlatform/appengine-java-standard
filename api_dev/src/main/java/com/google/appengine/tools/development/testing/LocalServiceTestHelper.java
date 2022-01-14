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

import com.google.appengine.api.NamespaceManager;
import com.google.appengine.tools.development.ApiProxyLocal;
import com.google.appengine.tools.development.ApiProxyLocalFactory;
import com.google.appengine.tools.development.Clock;
import com.google.appengine.tools.development.LocalEnvironment;
import com.google.appengine.tools.development.LocalRpcService;
import com.google.appengine.tools.development.LocalServerEnvironment;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.Environment;
import com.google.apphosting.utils.config.WebModule;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Helper class for testing against local app engine services.
 * Construct the helper with one {@link LocalServiceTestConfig} instance for
 * each service that you wish to access as part of your test.  Then call
 * {@link #setUp()} before each test executes and {@link #tearDown()} after
 * each test executes.  No specific test-harness is assumed, but here's a
 * JUnit 3 example that uses task queues and the datastore.
 *
 * <blockquote>
 * <pre>
 * public void MyTest extends TestCase {
 *
 *   private final LocalServiceTestHelper helper = new LocalServiceTestHelper(
 *     new LocalTaskQueueTestConfig(), new LocalDatastoreServiceTestConfig());
 *
 *   &#64;Override
 *   public void setUp() {
 *     super.setUp();
 *     helper.setUp();
 *   }
 *
 *   &#64;Override
 *   public void tearDown() {
 *     helper.tearDown();
 *     super.tearDown();
 *   }
 * }
 * </pre>
 * </blockquote>
 *
*/
public class LocalServiceTestHelper {
  public interface RequestMillisTimer {
    /**
     * @return The amount of time that the {@link Environment} should say is left in the request.
     *         Expressed in milliseconds. If infinite time is allowed, then reply with
     *         {@link Long#MAX_VALUE}.
     */
    long getRemainingMillis();

    /**
     * The Timer instance used by local services if no override is provided via
     * {@link LocalServiceTestHelper#setRemainingMillisTimer(RequestMillisTimer)}.
     */
    RequestMillisTimer DEFAULT = new RequestMillisTimer() {
      @Override
      public long getRemainingMillis() {
        return Long.MAX_VALUE;
      }
    };
  }

  // Keep these in sync with other occurrences of
  // CURRENT_NAMESPACE_KEY and APPS_NAMESPACE_KEY
  private static final String APPS_NAMESPACE_KEY =
      NamespaceManager.class.getName() + ".appsNamespace";

  // Assigned as part of setUp(). We maintain a static reference rather than
  // relying on ApiProxy.getDelegate() because usercode may install its own
  // Delegate which does not implement ApiProxyLocal. Storing this in a static
  // seems bad but it's actually no worse than ApiProxy.setDelegate(), which is
  // also global.
  private static ApiProxyLocal apiProxyLocal;

  static final String DEFAULT_APP_ID = "test";
  static final String DEFAULT_VERSION_ID = "1.0";

  private final Logger logger = Logger.getLogger(getClass().getName());
  private final List<LocalServiceTestConfig> configs;
  private String envAppId = DEFAULT_APP_ID;
  // envModuleId, envVersionId and envInstance are copied to the environments returned by
  // newDefaultTestEnvironment() so envXXX changes made after construction will not be
  // reflected in the returned environment.
  private String envModuleId = WebModule.DEFAULT_MODULE_NAME;
  private String envVersionId = DEFAULT_VERSION_ID;
  private int envInstance = LocalEnvironment.MAIN_INSTANCE;
  private String envEmail;
  private boolean envIsLoggedIn;
  private boolean envIsAdmin;
  private String envAuthDomain;
  private RequestMillisTimer timer = RequestMillisTimer.DEFAULT;
  private ConcurrentMap<String, Object> envAttributes = new ConcurrentHashMap<String, Object>();
  private Clock clock;
  // By default, tests do not enforce API deadlines
  private boolean enforceApiDeadlines = false;
  // By default, tests do not simulate prod latencies
  private boolean simulateProdLatencies = false;

  private final LocalModulesServiceTestConfig modulesServiceTestConfig;

  // The dev appserver sets the default time zone to be UTC so the testing
  // environment does as well.
  private TimeZone timeZone = TimeZone.getTimeZone("UTC");

  // Maintains the value of the default time zone before we set it.
  private TimeZone originalDefaultTimeZone;

  /**
   * Constructs a LocalServiceTestHelper with the provided configurations.
   *
   * @param configs for the local services that need to be set up and torn down.
   */
  public LocalServiceTestHelper(LocalServiceTestConfig... configs) {
    ImmutableList.Builder<LocalServiceTestConfig> builder = ImmutableList.builder();
    LocalModulesServiceTestConfig configuredModulesServiceTestConfig = null;
    for (LocalServiceTestConfig config : configs) {
      if (config instanceof LocalModulesServiceTestConfig) {
        if (configuredModulesServiceTestConfig != null) {
          throw new IllegalArgumentException(
              "Multiple LocalModulesServiceTestConfig instances provided");
        }
        configuredModulesServiceTestConfig = (LocalModulesServiceTestConfig) config;
      } else {
        builder.add(config);
      }

    }
    this.configs = builder.build();
    this.modulesServiceTestConfig = configuredModulesServiceTestConfig;
  }

  /**
   * The value to be returned by
   * {@code ApiProxy.getCurrentEnvironment().getAppId()}
   *
   * @param envAppId
   * @return {@code this} (for chaining)
   */
  public LocalServiceTestHelper setEnvAppId(String envAppId) {
    this.envAppId = envAppId;
    return this;
  }

  /**
   * The value to be returned by
   * {@code ApiProxy.getCurrentEnvironment().getModuleId()}
   *
   * @param envModuleId
   * @return {@code this} (for chaining)
   */
  public LocalServiceTestHelper setEnvModuleId(String envModuleId) {
    this.envModuleId = envModuleId;
    return this;
  }

  /**
   * The value to be returned by
   * {@code ApiProxy.getCurrentEnvironment().getVersionId()}
   *
   * @param envVersionId
   * @return {@code this} (for chaining)
   */
  public LocalServiceTestHelper setEnvVersionId(String envVersionId) {
    this.envVersionId = envVersionId;
    return this;
  }

  /**
   * The current instance id held by ApiProxy.getCurrentEnvironment()
   *
   * @param envInstance must an integer >= -1 (default instance)
   * @return {@code this} (for chaining)
   */
  public LocalServiceTestHelper setEnvInstance(String envInstance) {
    int intValue = Integer.parseInt(envInstance);
    Preconditions.checkArgument(intValue >= LocalModulesServiceTestConfig.MAIN_INSTANCE,
        "envInstanceId must be >= -1 and envInstanceId=" + envInstance);
    this.envInstance = intValue;
    return this;
  }

  /**
   * The value to be returned by
   * {@code ApiProxy.getCurrentEnvironment().getEmail()}
   *
   * @param envEmail
   * @return {@code this} (for chaining)
   */
  public LocalServiceTestHelper setEnvEmail(String envEmail) {
    this.envEmail = envEmail;
    return this;
  }

  /**
   * The value to be returned by
   * {@code ApiProxy.getCurrentEnvironment().isLoggedIn()}
   *
   * @param envIsLoggedIn
   * @return {@code this} (for chaining)
   */
  public LocalServiceTestHelper setEnvIsLoggedIn(boolean envIsLoggedIn) {
    this.envIsLoggedIn = envIsLoggedIn;
    return this;
  }

  /**
   * The value to be returned by
   * {@code ApiProxy.getCurrentEnvironment().isAdmin()}
   *
   * @param envIsAdmin
   * @return {@code this} (for chaining)
   */
  public LocalServiceTestHelper setEnvIsAdmin(boolean envIsAdmin) {
    this.envIsAdmin = envIsAdmin;
    return this;
  }

  /**
   * The value to be returned by
   * {@code ApiProxy.getCurrentEnvironment().getAuthDomain()}
   *
   * @param envAuthDomain
   * @return {@code this} (for chaining)
   */
  public LocalServiceTestHelper setEnvAuthDomain(String envAuthDomain) {
    this.envAuthDomain = envAuthDomain;
    return this;
  }

  /**
   * Sets the object that will return the value to be returned by
   * {@code ApiProxy.getCurrentEnvironment().getRemainingMillis()}
   *
   * @param timer The timer that returns the amount of time left.
   * @return {@code this} (for chaining)
   */
  public LocalServiceTestHelper setRemainingMillisTimer(RequestMillisTimer timer) {
    this.timer = timer;
    return this;
  }

  /**
   * The value to be returned by
   * {@code ApiProxy.getCurrentEnvironment().getRequestNamespace()}
   *
   * @param envRequestNamespace
   * @return {@code this} (for chaining)
   */
  public LocalServiceTestHelper setEnvRequestNamespace(String envRequestNamespace) {
    envAttributes.put(APPS_NAMESPACE_KEY, envRequestNamespace);
    return this;
  }

  /**
   * The value to be returned by
   * {@code ApiProxy.getCurrentEnvironment().getAttributes()}
   *
   * @param envAttributes
   * @return {@code this} (for chaining)
   */
  public LocalServiceTestHelper setEnvAttributes(Map<String, Object> envAttributes) {
    this.envAttributes = new ConcurrentHashMap<String, Object>(envAttributes);
    return this;
  }

  /**
   * Sets the clock with which all local services will be initialized.  Note
   * that once a local service is initialized its clock cannot be altered.
   *
   * @param clock
   * @return {@code this} (for chaining)
   */
  public LocalServiceTestHelper setClock(Clock clock) {
    this.clock = clock;
    return this;
  }

  /**
   * Determines whether or not API calls should be subject to the same
   * deadlines as in production.  The default is {@code false}.
   * @param val
   * @return {@code this} (for chaining)
   */
  public LocalServiceTestHelper setEnforceApiDeadlines(boolean val) {
    this.enforceApiDeadlines = val;
    return this;
  }

  /**
   * Determines whether or not local services should simulate production
   * latencies.  The default is {@code false}.
   * @param val
   * @return {@code this} (for chaining)
   */
  public LocalServiceTestHelper setSimulateProdLatencies(boolean val) {
    this.simulateProdLatencies = val;
    return this;
  }

  /**
   * Sets the time zone in which tests will execute.  If not set we use the
   * same timezone that we use in production and the dev appserver: UTC.  Note
   * that if your code has permission to modify the <code>user.timezone</code>
   * system property, this will change the default timezone for the JVM.
   * However, if your code does not have this permission, the timezone will only
   * be altered for the current thread.
   *
   * @param timeZone the time zone
   * @return {@code this} (for chaining)
   */
  public LocalServiceTestHelper setTimeZone(TimeZone timeZone) {
    this.timeZone = timeZone;
    return this;
  }

  /**
   * Set up an environment in which tests that use local services can execute.
   *
   * @return {@code this} (for chaining)
   */
  public final LocalServiceTestHelper setUp() {
    originalDefaultTimeZone = TimeZone.getDefault();
    TimeZone.setDefault(timeZone);

    // The user provided an explicit configuration
    if (modulesServiceTestConfig != null
        // and the user did not designate a current instance
        && envInstance == LocalModulesServiceTestConfig.MAIN_INSTANCE
        // and the designated module version requires an instance
        && modulesServiceTestConfig.requiresEnvironmentInstance(envModuleId, envVersionId)) {
        // we arbitrarily choose instance 0 which is guaranteed to exist. Note we must set the
        // instance before calling newEnvironment which establishes the current instance in the
        // thread local ApiProxy.Environment.
        envInstance = 0;
    }

    // Note It is important that we set the environment before
    // invoking config.setUp() below for each of the LocalServiceTestConfigs.
    // The LocalTaskQueueTestConfig needs the environment to be available.
    ApiProxy.setEnvironmentForCurrentThread(newEnvironment());
    apiProxyLocal = new ApiProxyLocalFactory().create(newLocalServerEnvironment());
    if (clock != null) {
      apiProxyLocal.setClock(clock);
    }
    ApiProxy.setDelegate(apiProxyLocal);

    if (modulesServiceTestConfig != null) {
      modulesServiceTestConfig.setUp();
      modulesServiceTestConfig.verifyEnvironment(envModuleId, envVersionId, envInstance);
    }

    for (LocalServiceTestConfig config : configs) {
      config.setUp();
    }
    return this;
  }

  /**
   * Constructs the {@link com.google.apphosting.api.ApiProxy.Environment} that will be installed.
   * Subclass and override to provide your own implementation.
   */
  // N.B. Notice that the environment constructed returns
  // the live values of the member variables of
  // {@code LocalServiceTestHelper}. That explains why all the getters
  // are re-implemented in this subclass of
  // {@code TestEnvironment}. It's not clear whether this behavior is
  // actually desirable, but it is the way our testing framework has
  // been for a while, so I'm not changing it.
  protected ApiProxy.Environment newEnvironment() {
    LocalEnvironment env =
        new LocalEnvironment(
            envAppId,
            envModuleId,
            envVersionId,
            envInstance,
            TestLocalServerEnvironment.DEFAULT_TEST_PORT,
            null) {
          @Override
          public String getEmail() {
            return envEmail;
          }

          @Override
          public boolean isLoggedIn() {
            return envIsLoggedIn;
          }

          @Override
          public boolean isAdmin() {
            return envIsAdmin;
          }

          @Override
          public String getAuthDomain() {
            return envAuthDomain;
          }

          @Override
          public long getRemainingMillis() {
            return timer.getRemainingMillis();
          }
        };
    env.getAttributes().putAll(envAttributes);
    return env;
  }

  /**
   * Constructs a new {@link com.google.apphosting.api.ApiProxy.Environment} by
   * copying the data from the given one. The {@code Map} from
   * {@code getAttributes} will be shallow-copied.
   */
  static ApiProxy.Environment copyEnvironment(ApiProxy.Environment copyFrom){
    return new TestEnvironment(copyFrom);
  }

  /**
   * Constructs a new default {@link com.google.apphosting.api.ApiProxy.Environment}.
   */
  static ApiProxy.Environment newDefaultTestEnvironment() {
    return new TestEnvironment();
  }

  private static class TestEnvironment extends LocalEnvironment {
    private String email;
    private boolean isLoggedIn;
    private boolean isAdmin;
    private String authDomain;

    private TestEnvironment() {
      super(
          DEFAULT_APP_ID,
          WebModule.DEFAULT_MODULE_NAME,
          DEFAULT_VERSION_ID,
          LocalEnvironment.MAIN_INSTANCE,
          TestLocalServerEnvironment.DEFAULT_TEST_PORT,
          null);
    }

    private TestEnvironment(String appId,
        String majorVersionId,
        String email,
        boolean isLoggedIn,
        boolean isAdmin,
        String authDomain,
        String moduleName,
        int instance,
        Map<String, Object> attributes) {
      super(
          appId,
          moduleName,
          majorVersionId,
          instance,
          TestLocalServerEnvironment.DEFAULT_TEST_PORT,
          null);
      this.email = email;
      this.isLoggedIn = isLoggedIn;
      this.isAdmin = isAdmin;
      this.authDomain = authDomain;
      this.attributes.putAll(attributes);
    }

    public TestEnvironment(ApiProxy.Environment copyFrom) {
      this(copyFrom.getAppId(),
           getMajorVersion(copyFrom.getVersionId()),
           copyFrom.getEmail(),
           copyFrom.isLoggedIn(),
           copyFrom.isAdmin(),
           copyFrom.getAuthDomain(),
           copyFrom.getModuleId(),
           getInstance(copyFrom),
           copyFrom.getAttributes());
    }

    private static int getInstance(ApiProxy.Environment environment) {
      int result = LocalEnvironment.MAIN_INSTANCE;
      if (environment.getAttributes().containsKey(LocalEnvironment.INSTANCE_ID_ENV_ATTRIBUTE)) {
        result = Integer.parseInt(
            (String) environment.getAttributes().get(LocalEnvironment.INSTANCE_ID_ENV_ATTRIBUTE));
      }
      return result;
    }

    @Override
    public String getEmail() {
      return email;
    }

    @Override
    public boolean isLoggedIn() {
      return isLoggedIn;
    }

    @Override
    public boolean isAdmin() {
      return isAdmin;
    }

    @Override
    public String getAuthDomain() {
      return authDomain;
    }
  }

  /**
   * Constructs the {@link LocalServerEnvironment} that will be installed.
   * Subclass and override to provide your own implementation.
   */
  protected LocalServerEnvironment newLocalServerEnvironment() {
    return new TestLocalServerEnvironment(enforceApiDeadlines, simulateProdLatencies);
  }

  /**
   * Tear down the environment in which tests that use local services can
   * execute.
   */
  public final void tearDown() {
    try {
      RuntimeException firstException = null;
      for (LocalServiceTestConfig config : configs) {
        firstException = tearDownService(firstException, config);
      }

      if (modulesServiceTestConfig != null) {
        firstException = tearDownService(firstException, modulesServiceTestConfig);
      }

      if (firstException != null) {
        // just throw the first
        throw firstException;
      }

      // TODO it feels like calling the requestEndListerns should happen before
      // tearing down the testconfigs. See if that causes problems.
      endRequest();

      try {
        apiProxyLocal.stop();
      } catch (Throwable t) {
        logger.log(Level.SEVERE, "Could not shut down ApiProxy", t);
      }
      // not strictly necessary to null these out but there's no harm either
      ApiProxy.setDelegate(null);
      ApiProxy.setEnvironmentForCurrentThread(null);
      apiProxyLocal = null;
    } finally {
      TimeZone.setDefault(originalDefaultTimeZone);
    }
  }

  private RuntimeException tearDownService(
      RuntimeException firstException, LocalServiceTestConfig config) {
    try {
      config.tearDown();
    } catch (RuntimeException rte) {
      // best effort to tear down all configs
      if (firstException == null) {
        // we'll throw this one later
        firstException = rte;
      } else {
        // log everything else so at least there's a record of it
        logger.log(
            Level.SEVERE,
            "Caught exception tearing down config of type " + config.getClass().getName(),
            rte);
      }
    }
    return firstException;
  }

  /**
   * Indicate the request has ended so that local services can do any post request work. This
   * method is optional and is automatically done in {@link LocalServiceTestHelper#tearDown()}.
   * You only need to call this method if you want to call {@link LocalServiceTestHelper#tearDown()}
   * from your own tearDown / @After method but need to end the request to verify any behavior.
   */
  public static void endRequest() {
    ((LocalEnvironment) ApiProxy.getCurrentEnvironment()).callRequestEndListeners();
  }

  /**
   * Convenience function for getting ahold of the currently
   * registered {@link ApiProxyLocal}.
   */
  public static ApiProxyLocal getApiProxyLocal() {
    return apiProxyLocal;
  }

  /**
   * Convenience function for getting ahold of a specific local service.
   * For example, to get ahold of the LocalDatastoreService you would
   * call {@code getLocalService(LocalDatastoreService.PACKAGE)}.
   */
  public static LocalRpcService getLocalService(String serviceName) {
    return getApiProxyLocal().getService(serviceName);
  }
}
