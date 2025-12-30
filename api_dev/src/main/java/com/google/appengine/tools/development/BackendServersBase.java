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

package com.google.appengine.tools.development;

import com.google.appengine.api.backends.BackendService;
import com.google.appengine.api.backends.dev.LocalServerController;
import com.google.appengine.tools.development.AbstractContainerService.PortMappingProvider;
import com.google.appengine.tools.development.ApplicationConfigurationManager.ModuleConfigurationHandle;
import com.google.appengine.tools.development.InstanceStateHolder.InstanceState;
import com.google.appengine.tools.info.AppengineSdk;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.utils.config.BackendsXml;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controls backend servers configured in appengine-web.xml. Each server is started on a separate
 * port. All servers run the same code as the main app.
 */
public class BackendServersBase
    implements BackendContainer, LocalServerController, PortMappingProvider {
  public static final String SYSTEM_PROPERTY_STATIC_PORT_NUM_PREFIX =
      "com.google.appengine.devappserver.";

  private static final Integer DEFAULT_INSTANCES = 1;
  private static final String DEFAULT_INSTANCE_CLASS = "B1";
  private static final Integer DEFAULT_MAX_CONCURRENT_REQUESTS = 10;


  // the maximum number of requests that can be queued before a 500 error is
  // returned if pending queues are enabled on the server instance
  private static final int MAX_PENDING_QUEUE_LENGTH = 20;
  // maximum time a request can be in a pending queue until it is dropped
  private static final int MAX_PENDING_QUEUE_TIME_MS = 10 * 1000;
  // maximum time a request can wait for a start request to complete
  private static final int MAX_START_QUEUE_TIME_MS = 30 * 1000;

  private String address;
  private ModuleConfigurationHandle moduleConfigurationHandle;
  private File externalResourceDir;
  private Map<String, Object> containerConfigProperties;
  private ImmutableMap<BackendServersBase.ServerInstanceEntry, ServerWrapper> backendServers =
      ImmutableMap.copyOf(new HashMap<>());
  private Map<String, String> portMapping =
      ImmutableMap.copyOf(new HashMap<>());
  // Should not be used until startup() is called.
  protected Logger logger = Logger.getLogger(BackendServersBase.class.getName());

  private Map<String, String> serviceProperties = new HashMap<>();

  // A reference to the devAppServer that initiated this BackendServers instance.
  private DevAppServer devAppServer;
  private ApiProxyLocal apiProxyLocal;
  // Singleton so BackendServers can to be accessed from the
  // {@ link DevAppServerModulesFilter} configured in the webdefaults.xml file.
  // The filter is configured in the xml file to ensure that it runs after the
  // StaticFileFilter but before any other filters.
  private static BackendServersBase instance;

  public static BackendServersBase getInstance() {
    if (instance == null) {
      try {
        instance =
            Class.forName(AppengineSdk.getSdk().getBackendServersClassName())
                .asSubclass(BackendServersBase.class)
                .getDeclaredConstructor()
                .newInstance();

      } catch (ClassNotFoundException
          | IllegalAccessException
          | IllegalArgumentException
          | InstantiationException
          | NoSuchMethodException
          | SecurityException
          | InvocationTargetException ex) {
        Logger.getLogger(BackendServersBase.class.getName()).log(Level.SEVERE, null, ex);
      }
    }
    return instance;
  }

  @Override
  public void init(String address, ModuleConfigurationHandle moduleConfigurationHandle,
      File externalResourceDirectory, Map<String, Object> containerConfigProperties,
      DevAppServer devAppServer) {
    this.moduleConfigurationHandle = moduleConfigurationHandle;
    this.externalResourceDir = externalResourceDirectory;
    this.address = address;
    this.containerConfigProperties = containerConfigProperties;
    this.devAppServer = devAppServer;
  }

  @Override
  public void setServiceProperties(Map<String, String> properties) {
    this.serviceProperties = properties;
  }

  @Override
  public void shutdownAll() throws Exception {
    for (ServerWrapper server : backendServers.values()) {
      logger.finer("server shutdown: " + server);
      server.shutdown();
    }
    backendServers = ImmutableMap.copyOf(new HashMap<ServerInstanceEntry, ServerWrapper>());
  }

  @Override
  public TreeMap<String, BackendStateInfo> getBackendState(String requestHostName) {
    TreeMap<String, BackendStateInfo> serverInfoMap = new TreeMap<String, BackendStateInfo>();
    for (ServerWrapper serverWrapper : backendServers.values()) {
      String name = serverWrapper.serverEntry.getName();

      String listenAddress;
      if (requestHostName == null) {
        listenAddress = portMapping.get(serverWrapper.getDnsPrefix());
      } else {
        listenAddress = requestHostName + ":" + serverWrapper.port;
      }

      BackendStateInfo ssi = serverInfoMap.get(name);
      // if not in the current map, add a new entry
      if (ssi == null) {
        ssi = new BackendStateInfo(serverWrapper.serverEntry);
        serverInfoMap.put(name, ssi);
      }
      // if forwarding instance, update state and address
      if (serverWrapper.isLoadBalanceServer()) {
        ssi.setState(serverWrapper.getStateHolder().getDisplayName());
        ssi.setAddress(listenAddress);
      } else {
        // else, add instance to list of instances
        ssi.add(new InstanceStateInfo(serverWrapper.serverInstance, listenAddress,
            serverWrapper.getStateHolder().getDisplayName()));
      }
    }
    return serverInfoMap;
  }

  @Override
  public synchronized void startBackend(String serverToStart) throws IllegalStateException {
    if (!checkServerExists(serverToStart)) {
      String message = String.format("Tried to start unknown server %s", serverToStart);
      logger.warning(message);
      throw new IllegalStateException(message);
    }
    // When instances are put in STOPPED state they are restarted to a fresh
    // running copy. To Start them the only thing needed is to send a start
    // request.
    for (ServerWrapper server : backendServers.values()) {
      // Note monitor on stateHolder is NOT held between tests.
      if (server.getName().equals(serverToStart)) {
        if (!server.getStateHolder().test(InstanceState.STOPPED)) {
          continue;
        }
        if (server.isLoadBalanceServer()) {
          // No start request needed, just set state to RUNNING.
          server.getStateHolder().testAndSet(InstanceState.RUNNING,
              InstanceState.STOPPED);
          continue;
        }
        server.getStateHolder().testAndSet(InstanceState.SLEEPING,
            InstanceState.STOPPED);
        server.sendStartRequest();
      }
    }
  }

  @Override
  public synchronized void stopBackend(String serverToStop) throws Exception {
    if (!checkServerExists(serverToStop)) {
      String message = String.format("Tried to stop unknown server %s", serverToStop);
      logger.warning(message);
      throw new IllegalStateException(message);
    }
    // For each instance: first shut it down (to stop any running requests and
    // to flush the local state). Then start it up but set the state to stopped
    // so that incoming requests get a 500 response (stopped servers return 500
    // in prod).
    for (ServerWrapper server : backendServers.values()) {
      // TODO: Note monitor on stateHolder is NOT held between tests
      if (server.getName().equals(serverToStop)) {
        if (server.getStateHolder().test(InstanceState.STOPPED)) {
          continue;
        }
        if (server.isLoadBalanceServer()) {
          // No restart needed (no requests run code on load balance instances).
          // Just set state to STOPPED.
          server.getStateHolder().testAndSet(InstanceState.STOPPED,
              InstanceState.RUNNING);
          continue;
        }
        logger.fine("Stopping server: " + server.getDnsPrefix());
        server.shutdown();
        server.createConnection();
        server.startup(true);
      }
    }
  }

  public void configureAll(ApiProxyLocal local) throws Exception {
    this.apiProxyLocal = local;
    BackendsXml backendsXml = moduleConfigurationHandle.getBackendsXml();
    if (backendsXml == null) {
      logger.fine("Got null backendsXml config.");
      return;
    }
    List<BackendsXml.Entry> servers = backendsXml.getBackends();
    if (servers.isEmpty()) {
      logger.fine("No backends configured.");
      return;
    }

    if (!backendServers.isEmpty()) {
      throw new Exception("Tried to start backend servers but some are already running.");
    }
    logger.finer("Found " + servers.size() + " configured backends.");

    Map<BackendServersBase.ServerInstanceEntry, ServerWrapper> serverMap = Maps.newHashMap();
    for (BackendsXml.Entry entry : servers) {
      entry = resolveDefaults(entry);

      // start the individual instances. A special instance with id -1 is
      // created that act as a load balancer for requests that are destined to
      // a server without instance id specified
      for (int serverInstance = -1; serverInstance < entry.getInstances(); serverInstance++) {
        int port = checkForStaticPort(entry.getName(), serverInstance);
        ServerWrapper serverWrapper =
            new ServerWrapper(ContainerUtils.loadContainer(), entry, serverInstance, port);
        serverMap.put(new ServerInstanceEntry(entry.getName(), serverInstance), serverWrapper);
      }
    }
    // this map is used by a large number of threads and should only change
    // on complete reload. By making it immutable we can share it without
    // worrying about threading issues
    this.backendServers = ImmutableMap.copyOf(serverMap);

    String prettyAddress = address;
    if ("0.0.0.0".equals(address)) {
      prettyAddress = "127.0.0.1";
    }

    Map<String, String> portMap = Maps.newHashMap();
    for (ServerWrapper serverWrapper : backendServers.values()) {
      logger.finer(
          "starting server: " + serverWrapper.serverInstance + "." + serverWrapper.getName()
              + " on " + address + ":" + serverWrapper.port);
      // A servlet or a servlet filter may install its own Delegate during initialization, so make
      // sure that the ApiProxyLocal that we originally installed is available as each container
      // initializes.
      ApiProxy.Delegate<?> configured = ApiProxy.getDelegate();
      try {
        ApiProxy.setDelegate(local);
        serverWrapper.createConnection();
      } finally {
        ApiProxy.setDelegate(configured);
      }

      // create the port map used by the BackendService API
      portMap.put(serverWrapper.getDnsPrefix(), prettyAddress + ":" + serverWrapper.port);
    }
    this.portMapping = ImmutableMap.copyOf(portMap);
  }

  @Override
  public void startupAll() throws Exception {
    for (ServerWrapper serverWrapper : backendServers.values()) {
      logger.finer(
          "starting server: " + serverWrapper.serverInstance + "." + serverWrapper.getName()
              + " on " + address + ":" + serverWrapper.port);
      // A servlet or a servlet filter may install its own Delegate during initialization, so make
      // sure that the ApiProxyLocal that we originally installed is available as each container
      // initializes.
      ApiProxy.Delegate<?> configured = ApiProxy.getDelegate();
      try {
        ApiProxy.setDelegate(apiProxyLocal);
        serverWrapper.startup(false);
      } finally {
        ApiProxy.setDelegate(configured);
      }
    }

    // start requests to all server instances
    for (ServerWrapper serverWrapper : backendServers.values()) {
      if (serverWrapper.isLoadBalanceServer()) {
        continue;
      }
      serverWrapper.sendStartRequest();
    }
  }

  private BackendsXml.Entry resolveDefaults(BackendsXml.Entry entry) {
    return new BackendsXml.Entry(
        entry.getName(),
        entry.getInstances() == null ? DEFAULT_INSTANCES : entry.getInstances(),
        entry.getInstanceClass() == null ? DEFAULT_INSTANCE_CLASS : entry.getInstanceClass(),
        entry.getMaxConcurrentRequests() == null ? DEFAULT_MAX_CONCURRENT_REQUESTS :
                                                   entry.getMaxConcurrentRequests(),
        entry.getOptions(),
        entry.getState() == null ? BackendsXml.State.STOP : entry.getState());
  }

  /**
   * This method guards access to servers to limit the number of concurrent
   * requests. Each request running on a server must acquire a serving permit.
   * If no permits are available a 500 response should be sent.
   *
   * @param serverName The server for which to acquire a permit.
   * @param instanceNumber The server instance for which to acquire a permit.
   * @param allowQueueOnBackends If set to false the method will return
   *        instantly, if set to true (and the specified server allows pending
   *        queues) this method can block for up to 10 s waiting for a serving
   *        permit to become available.
   * @return true if a permit was acquired, false otherwise
   */
  public boolean acquireServingPermit(
      String serverName, int instanceNumber, boolean allowQueueOnBackends) {
    logger.finest(
        String.format("trying to get serving permit for server %d.%s", instanceNumber, serverName));
    try {
      ServerWrapper server = getServerWrapper(serverName, instanceNumber);
      int maxQueueTime = 0;

      synchronized (server.getStateHolder()) {
        if (!server.getStateHolder().acceptsConnections()) {
          logger.finest(server + ": got request but server is not in a serving state");
          return false;
        }
        if (server.getApproximateQueueLength() > MAX_PENDING_QUEUE_LENGTH) {
          logger.finest(server + ": server queue is full");
          return false;
        }
        if (server.getStateHolder().test(InstanceState.SLEEPING)) {
          logger.finest(server + ": waking up sleeping server");
          server.sendStartRequest();
        }

        // we wait on start requests for both workers and backends
        if (server.getStateHolder().test(InstanceState.RUNNING_START_REQUEST)) {
          maxQueueTime = MAX_START_QUEUE_TIME_MS;
        } else if (allowQueueOnBackends && server.getMaxPendingQueueSize() > 0) {
          // if queuing is allowed and server has pending queue, set queue time
          maxQueueTime = MAX_PENDING_QUEUE_TIME_MS;
        }
      }

      boolean gotPermit = server.acquireServingPermit(maxQueueTime);
      logger.finest(server + ": tried to get server permit, timeout=" + maxQueueTime + " success="
          + gotPermit);
      return gotPermit;
    } catch (InterruptedException e) {
      logger.finest(
          instanceNumber + "." + serverName + ": got interrupted while waiting for serving permit");
      return false;
    }
  }

  /**
   * Reserves an instance for this request. For workers this method will return
   * -1 if no free instances are available. For backends this method will assign
   * this request to the instance with the shortest queue and block until that
   * instance is ready to serve the request.
   *
   * @param requestedServer Name of the server the request is to.
   * @return the instance id of an available server instance, or -1 if no
   *         instance is available.
   */
  public int getAndReserveFreeInstance(String requestedServer) {
    logger.finest("trying to get serving permit for server " + requestedServer);

    ServerWrapper server = getServerWrapper(requestedServer, -1);
    if (server == null) {
      return -1;
    }
    if (!server.getStateHolder().acceptsConnections()) {
      return -1;
    }
    int instanceNum = server.getInstances();
    for (int i = 0; i < instanceNum; i++) {
      if (acquireServingPermit(requestedServer, i, false)) {
        return i;
      }
    }
    // no free servers, queue if pending queues are enabled
    if (server.getMaxPendingQueueSize() > 0) {
      return addToShortestInstanceQueue(requestedServer);
    } else {
      logger.finest("no servers free");
      return -1;
    }
  }

  /**
   * Will add this request to the queue of the instance with the approximate
   * shortest queue. This method will block for up to 10 seconds until a permit
   * is received.
   *
   * @param requestedServer the server name
   * @return the instance where the serving permit was reserved, or -1 if all
   *         instance queues are full
   */
  int addToShortestInstanceQueue(String requestedServer) {
    logger.finest(requestedServer + ": no instances free, trying to find a queue");
    int shortestQueue = MAX_PENDING_QUEUE_LENGTH;
    ServerWrapper instanceWithShortestQueue = null;
    for (ServerWrapper server : backendServers.values()) {
      if (!server.getStateHolder().acceptsConnections()) {
        continue;
      }
      int serverQueue = server.getApproximateQueueLength();
      if (shortestQueue > serverQueue) {
        instanceWithShortestQueue = server;
        shortestQueue = serverQueue;
      }
    }

    // add ourselves to the serving queue of this instance (blocking)
    try {
      if (shortestQueue < MAX_PENDING_QUEUE_LENGTH) {
        logger.finest("adding request to queue on instance: " + instanceWithShortestQueue);
        if (instanceWithShortestQueue.acquireServingPermit(MAX_PENDING_QUEUE_TIME_MS)) {
          logger.finest("ready to serve request on instance: " + instanceWithShortestQueue);
          return instanceWithShortestQueue.serverInstance;
        }
      }
    } catch (InterruptedException e) {
      logger.finer("interrupted while queued at server " + instanceWithShortestQueue);
    }
    return -1;
  }

  /**
   * Method for returning a serving permit after a request has completed.
   *
   * @param serverName The server name
   * @param instance The server instance
   */
  public void returnServingPermit(String serverName, int instance) {
    ServerWrapper server = getServerWrapper(serverName, instance);
    server.releaseServingPermit();
  }

  /**
   * Returns the port for the requested instance.
   */
  public int getPort(String serverName, int instance) {
    ServerWrapper server = getServerWrapper(serverName, instance);
    return server.port;
  }

  /**
   * Verifies if a specific server/instance is configured.
   *
   * @param serverName The server name
   * @param instance The server instance
   * @return true if the server/instance is configured, false otherwise.
   */
  public boolean checkInstanceExists(String serverName, int instance) {
    return getServerWrapper(serverName, instance) != null;
  }

  /**
   * Verifies if a specific server is configured.
   *
   * @param serverName The server name
   * @return true if the server is configured, false otherwise.
   */
  public boolean checkServerExists(String serverName) {
    return checkInstanceExists(serverName, -1);
  }

  /**
   * Verifies if a specific server is stopped.
   *
   * @param serverName The server name
   * @return true if the server is stopped, false otherwise.
   */
  public boolean checkServerStopped(String serverName) {
    return checkInstanceStopped(serverName, -1);
  }

  /**
   * Verifies if a specific server/instance is stopped.
   *
   * @param serverName The server name
   * @param instance The server instance
   * @return true if the server/instance is stopped, false otherwise.
   */
  public boolean checkInstanceStopped(String serverName, int instance) {
    return !getServerWrapper(serverName, instance)
        .getStateHolder().acceptsConnections();
  }


  /**
   * Allows the servers API to get the current mapping from server and instance
   * to listening ports.
   *
   * @return The port map
   */
  @Override
  public Map<String, String> getPortMapping() {
    return this.portMapping;
  }

  /**
   * Returns the server instance serving on a specific local port
   *
   * @param port the local tcp port that received the request
   * @return the server instance, or -1 if no server instance is running on that
   *         port
   */
  public int getServerInstanceFromPort(int port) {
    ServerWrapper server = getServerWrapperFromPort(port);
    if (server != null) {
      return server.serverInstance;
    } else {
      return -1;
    }
  }

  /**
   * Returns the server serving on a specific local port
   *
   * @param port the local tcp port that received the request
   * @return the server name, or null if no server instance is running on that
   *         port
   */
  public String getServerNameFromPort(int port) {
    ServerWrapper server = getServerWrapperFromPort(port);
    if (server != null) {
      return server.getName();
    } else {
      return null;
    }
  }

  /**
   * Convenience method for getting the ServerWrapper running on a specific port
   */
  private ServerWrapper getServerWrapperFromPort(int port) {
    for (Entry<ServerInstanceEntry, ServerWrapper> entry : backendServers.entrySet()) {
      if (entry.getValue().port == port) {
        return entry.getValue();
      }
    }
    return null;
  }

  /**
   * Convenience method for getting the ServerWrapper for a specific
   * server/instance
   */
  protected ServerWrapper getServerWrapper(String serverName, int instanceNumber) {
    return backendServers.get(new ServerInstanceEntry(serverName, instanceNumber));
  }

  /**
   * Verifies if the specific port is statically configured, if not it will
   * return 0 which instructs jetty to pick the port
   *
   *  Ports can be statically configured by a system property at
   * com.google.appengine.server.<server-name>.port for the server and
   * com.google.appengine.server.<server-name>.<instance-id>.port for individual
   * instances
   *
   * @param server the name of the configured server
   * @param instance the instance number to configure
   * @return the statically configured port, or 0 if none is configured
   */
  private int checkForStaticPort(String server, int instance) {
    StringBuilder key = new StringBuilder();
    key.append(SYSTEM_PROPERTY_STATIC_PORT_NUM_PREFIX);
    key.append(server);
    if (instance >= 0) {
      key.append("." + instance);
    }
    key.append(".port");
    String configuredPort = serviceProperties.get(key.toString());
    if (configuredPort != null) {
      return Integer.parseInt(configuredPort);
    } else {
      // dynamic port, return 0 and have jetty choose
      return 0;
    }
  }

  /**
   * Class that allows the key in the server map to be the
   * (servername,instanceid) tuple. Overrides equals() and hashcode() to
   * function as a hashtable key
   *
   */
  static class ServerInstanceEntry {
    private final int instanceNumber;
    private final String serverName;

    /**
     * @param serverName
     * @param instanceNumber
     */
    public ServerInstanceEntry(String serverName, int instanceNumber) {
      this.serverName = serverName;
      this.instanceNumber = instanceNumber;
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof ServerInstanceEntry)) {
        return false;
      }

      ServerInstanceEntry that = (ServerInstanceEntry) o;
      if (this.serverName != null) {
        if (!this.serverName.equals(that.serverName)) {
          return false;
        }
      } else {
        if (that.serverName != null) {
          return false;
        }
      }

      if (this.instanceNumber != that.instanceNumber) {
        return false;
      }
      return true;
    }

    @Override
    public int hashCode() {
      int hash = 17;
      hash = 31 * hash + instanceNumber;
      if (serverName != null) {
        hash = 31 * hash + serverName.hashCode();
      }
      return hash;
    }

    @Override
    public String toString() {
      return instanceNumber + "." + serverName;
    }
  }

  /**
   * Wraps a container service and contains extra information such as the
   * instanceid of the current container as well as the port number it is
   * running on.
   */
  protected class ServerWrapper {

    private final ContainerService container;
    private final int serverInstance;
    private int port;
    private final BackendsXml.Entry serverEntry;

    private final InstanceStateHolder stateHolder;
    private final InstanceHelper instanceHelper;

    // Semaphore guarding access to this server instance. The number of permits
    // decide the number of concurrent request the server can handle. Fair is
    // set to true to emulate a FIFO queue for incoming requests if pending
    // queues are enabled for this server.
    // The serving queue starts with 0 permits until the start request
    // completes, then SERVER_CONFIG_JAVA_CONCURRENT_REQUESTS are released
    private final Semaphore servingQueue = new Semaphore(0, true);

    public ServerWrapper(ContainerService containerService,
        BackendsXml.Entry serverEntry,
        int instance, int port) {
      this.container = containerService;
      this.serverEntry = serverEntry;
      this.serverInstance = instance;
      this.port = port;
      stateHolder = new InstanceStateHolder(serverEntry.getName(), instance);
      instanceHelper = new InstanceHelper(serverEntry.getName(), instance, stateHolder,
          container);
      // TODO: Avoid repeatedly issuing the same environment mismatch errors with
      //                multiple backends. Investigate if ignoring mismatches
      //                for backends/repeated instances is the right thing to do
      //                when the base policy is to throw exceptions.
      // We already checked for environmental variables in the frontend, ignore for backends.
    }
    /**
     * Shut down the server.
     *
     * Will trigger any shutdown hooks installed by the
     * {@link com.google.appengine.api.LifecycleManager}
     *
     * @throws Exception
     */
    void shutdown() throws Exception {
      // Note that for shutdown the caller pushes an APIProxy.Environment and for
      // startup this class pushes one.
      instanceHelper.shutdown();
    }

    void createConnection() throws Exception {
      getStateHolder().testAndSet(InstanceState.INITIALIZING, InstanceState.SHUTDOWN);

      // TODO: Pass backend name which should be the current server
      //    version for the default server to configure so it is available to
      //    the server's API. Other servers only have one version in
      //    java dev appserver.
      Map<String, Object> instanceConfigProperties =
          ImmutableMap.<String, Object>builder()
              .putAll(containerConfigProperties)
              .put(BackendService.BACKEND_ID_ENV_ATTRIBUTE, serverEntry.getName())
              .put(BackendService.INSTANCE_ID_ENV_ATTRIBUTE, serverInstance)
              .buildOrThrow();
      getContainer().configure(ContainerUtils.getServerInfo(),
          address,
          port,
          moduleConfigurationHandle,
          externalResourceDir,
          instanceConfigProperties,
          serverInstance,
          devAppServer);
      getContainer().createConnection();
      getContainer().setApiProxyDelegate(apiProxyLocal);

      // update the port
      this.port = getContainer().getPort();
    }

    void startup(boolean setStateToStopped) throws Exception {
      getContainer().startup();
      if (setStateToStopped) {
        getStateHolder().testAndSet(InstanceState.STOPPED, InstanceState.INITIALIZING);
      } else {
        logger.info(
            "server: " + serverInstance + "." + serverEntry.getName() + " is running on port "
                + this.port);
        if (isLoadBalanceServer()) {
          getStateHolder().testAndSet(InstanceState.RUNNING, InstanceState.INITIALIZING);
        } else {
          getStateHolder().testAndSet(InstanceState.SLEEPING, InstanceState.INITIALIZING);
        }
      }
    }

    void sendStartRequest() {
      instanceHelper.sendStartRequest(
          () -> {
            // release permits so any queued requests can continue. The number
            // of permits control how many concurrent requests the server can
            // handle.
            servingQueue.release(serverEntry.getMaxConcurrentRequests());
          });
    }

     /**
     * Acquires a serving permit for this server.
     *
     * @param maxWaitTimeInMs Max wait time in ms
     * @return true if a serving permit was acquired within the allowed time,
     *         false if not permit was required.
     * @throws InterruptedException If the thread was interrupted while waiting.
     */
    boolean acquireServingPermit(int maxWaitTimeInMs) throws InterruptedException {
      logger.finest(
          this + ": acquiring serving permit, available: " + servingQueue.availablePermits());
      return servingQueue.tryAcquire(maxWaitTimeInMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Returns a serving permit to the pool of available permits
     */
    void releaseServingPermit() {
      servingQueue.release();
      logger.finest(
          this + ": returned serving permit, available: " + servingQueue.availablePermits());
    }

    /**
     * Returns the approximate number of threads waiting for serving permits.
     *
     * @return the number of waiting threads.
     */
    int getApproximateQueueLength() {
      return servingQueue.getQueueLength();
    }

    /**
     * Returns the number of requests that can be queued on this specific
     * server. For servers without pending queue no queue (size=0) is allowed.
     */
    int getMaxPendingQueueSize() {
      return serverEntry.isFailFast() ? 0 : MAX_PENDING_QUEUE_LENGTH;
    }

    /**
     * The dns prefix for this server, basically the first part of:
     * <instance>.<server_name>.<app-id>.appspot.com for a specific instance,
     * and <server_name>.<app-id>.appspot.com for just the server.
     */
    public String getDnsPrefix() {
      if (!isLoadBalanceServer()) {
        return serverInstance + "." + getName();
      } else {
        return getName();
      }
    }

    String getName() {
      return serverEntry.getName();
    }

    public int getInstances() {
      return serverEntry.getInstances();
    }

    InstanceStateHolder getStateHolder() {
      return stateHolder;
    }

    boolean isLoadBalanceServer() {
      return serverInstance == -1;
    }

    @Override
    public String toString() {
      return serverInstance + "." + serverEntry.getName() + " state=" + stateHolder;
    }

    public ContainerService getContainer() {
      return container;
    }
  }
}
