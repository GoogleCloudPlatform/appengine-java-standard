/*
 * Copyright 2022 Google LLC
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

package com.google.appengine.setup;

import com.google.appengine.setup.timer.Timer;
import com.google.appengine.setup.utils.http.HttpRequest;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.ApiProxyException;
import com.google.apphosting.api.ApiProxy.LogRecord;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Implements the ApiProxy environment.
 * <p/>
 * Supports instantiation within a request as well as outside the context of a request.
 * <p/>
 * Instances should be registered using ApiProxy.setEnvironmentForCurrentThread(Environment).
 */
public class ApiProxyEnvironment implements ApiProxy.Environment {

    static final String GAE_APPLICATION = "GAE_APPLICATION";

    static final String GAE_SERVICE = "GAE_SERVICE";

    static final String GAE_VERSION = "GAE_VERSION";

    static final String GAE_INSTANCE = "GAE_INSTANCE";

    public static final String TICKET_HEADER = "X-AppEngine-Api-Ticket";
    public static final String EMAIL_HEADER = "X-AppEngine-User-Email";
    public static final String IS_ADMIN_HEADER = "X-AppEngine-User-Is-Admin";
    public static final String AUTH_DOMAIN_HEADER = "X-AppEngine-Auth-Domain";

    public static final String BACKEND_ID_KEY = "com.google.appengine.backend.id";
    public static final String INSTANCE_ID_KEY = "com.google.appengine.instance.id";
    public static final String REQUEST_THREAD_FACTORY_ATTR = "com.google.appengine.api.ThreadManager.REQUEST_THREAD_FACTORY";
    public static final String BACKGROUND_THREAD_FACTORY_ATTR = "com.google.appengine.api.ThreadManager.BACKGROUND_THREAD_FACTORY";
    public static final String IS_FEDERATED_USER_KEY = "com.google.appengine.api.users.UserService.is_federated_user";
    public static final String IS_TRUSTED_IP_KEY = "com.google.appengine.runtime.is_trusted_ip";
    public static final String IS_TRUSTED_IP_HEADER = "X-AppEngine-Trusted-IP-Request";

    private static final long DEFAULT_FLUSH_APP_LOGS_EVERY_BYTE_COUNT = 1024 * 1024L;
    private static final int MAX_LOG_FLUSH_SECONDS = 60;
    private static final int DEFAULT_MAX_LOG_LINE_SIZE = 8 * 1024;

    static final int MAX_CONCURRENT_API_CALLS = 100;
    static final int MAX_PENDING_API_CALLS = 1000;

    /**
     * Mapping from HTTP header keys to attribute keys.
     */
    static enum AttributeMapping {
        USER_ID(
                "X-AppEngine-User-Id",
                "com.google.appengine.api.users.UserService.user_id_key",
                "", false),
        USER_ORGANIZATION(
                "X-AppEngine-User-Organization",
                "com.google.appengine.api.users.UserService.user_organization",
                "", false),
        FEDERATED_IDENTITY(
                "X-AppEngine-Federated-Identity",
                "com.google.appengine.api.users.UserService.federated_identity",
                "", false),
        FEDERATED_PROVIDER(
                "X-AppEngine-Federated-Provider",
                "com.google.appengine.api.users.UserService.federated_authority",
                "", false),
        DATACENTER(
                "X-AppEngine-Datacenter",
                "com.google.apphosting.api.ApiProxy.datacenter",
                "", false),
        REQUEST_ID_HASH(
                "X-AppEngine-Request-Id-Hash",
                "com.google.apphosting.api.ApiProxy.request_id_hash",
                null, false),
        REQUEST_LOG_ID(
                "X-AppEngine-Request-Log-Id",
                "com.google.appengine.runtime.request_log_id",
                null, false),
        DAPPER_ID("X-Google-DapperTraceInfo",
                "com.google.appengine.runtime.dapper_id",
                null, false),
        DEFAULT_VERSION_HOSTNAME(
                "X-AppEngine-Default-Version-Hostname",
                "com.google.appengine.runtime.default_version_hostname",
                null, false),
        DEFAULT_NAMESPACE_HEADER(
                "X-AppEngine-Default-Namespace",
                "com.google.appengine.api.NamespaceManager.appsNamespace",
                null, false),
        CURRENT_NAMESPACE_HEADER(
                "X-AppEngine-Current-Namespace",
                "com.google.appengine.api.NamespaceManager.currentNamespace",
                null, false),
        LOAS_PEER_USERNAME(
                "X-AppEngine-LOAS-Peer-Username",
                "com.google.net.base.peer.loas_peer_username",
                "", true),
        GAIA_ID(
                "X-AppEngine-Gaia-Id",
                "com.google.appengine.runtime.gaia_id",
                "", true),
        GAIA_AUTHUSER(
                "X-AppEngine-Gaia-Authuser",
                "com.google.appengine.runtime.gaia_authuser",
                "", true),
        GAIA_SESSION(
                "X-AppEngine-Gaia-Session",
                "com.google.appengine.runtime.gaia_session",
                "", true),
        APPSERVER_DATACENTER(
                "X-AppEngine-Appserver-Datacenter",
                "com.google.appengine.runtime.appserver_datacenter",
                "", true),
        APPSERVER_TASK_BNS(
                "X-AppEngine-Appserver-Task-Bns",
                "com.google.appengine.runtime.appserver_task_bns",
                "", true);

        String headerKey;
        String attributeKey;
        Object defaultValue;
        private boolean trustedAppOnly;

        /**
         * Creates a mapping between an incoming request header and the thread local request attribute
         * corresponding to that header.
         *
         * @param headerKey      The HTTP header key.
         * @param attributeKey   The attribute key.
         * @param defaultValue   The default value to set if the header is missing, or null if no
         *                       attribute should be set when the header is missing.
         * @param trustedAppOnly If true the attribute should only be set for trusted apps.
         */
        private AttributeMapping(
                String headerKey, String attributeKey, Object defaultValue, boolean trustedAppOnly) {
            this.headerKey = headerKey;
            this.attributeKey = attributeKey;
            this.defaultValue = defaultValue;
            this.trustedAppOnly = trustedAppOnly;
        }
    }

    /**
     * Helper method to use during the transition from metadata to environment variables.
     *
     * @param environmentMap the
     * @param envKey         The name of the environment variable to check first.
     * @return If set the environment variable corresponding to envKey, the metadata entry otherwise.
     */
    private static String getEnvOrMetadata(Map<String, String> environmentMap,
                                           String envKey) {
        return environmentMap.get(envKey);
    }

    public static ApiProxyEnvironment createFromHeaders(Map<String, String> envMap,
                                                          HttpRequest request,
                                                          String server,
                                                          Timer wallTimer,
                                                          Long millisUntilSoftDeadline) {
        String appId = getEnvOrMetadata(envMap, GAE_APPLICATION);
        String module = getEnvOrMetadata(envMap, GAE_SERVICE);
        String majorVersion = getEnvOrMetadata(envMap, GAE_VERSION);
        String instance = getEnvOrMetadata(envMap, GAE_INSTANCE);
        String ticket = request.getHeader(TICKET_HEADER);
        String email = request.getHeader(EMAIL_HEADER);
        boolean admin = false;
        String value = request.getHeader(IS_ADMIN_HEADER);
        if (value != null && !value.trim().isEmpty()) {
            try {
                admin = Integer.parseInt(value.trim()) != 0;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(e.getMessage(), e);
            }
        }
        String authDomain = request.getHeader(AUTH_DOMAIN_HEADER);
        boolean trustedApp = request.getHeader(IS_TRUSTED_IP_HEADER) != null;

        Map<String, Object> attributes = new HashMap<>();
        for (AttributeMapping mapping : AttributeMapping.values()) {
            if (mapping.trustedAppOnly && !trustedApp) {
                continue;
            }
            String headerValue = request.getHeader(mapping.headerKey);
            if (headerValue != null) {
                attributes.put(mapping.attributeKey, headerValue);
            } else if (mapping.defaultValue != null) {
                attributes.put(mapping.attributeKey, mapping.defaultValue);
            }
        }

        boolean federatedId = request.getHeader(AttributeMapping.FEDERATED_IDENTITY.headerKey) != null;
        attributes.put(IS_FEDERATED_USER_KEY, federatedId);

        attributes.put(BACKEND_ID_KEY, module);
        attributes.put(INSTANCE_ID_KEY, instance);

        if (trustedApp) {
            boolean trustedIp = "1".equals(request.getHeader(IS_TRUSTED_IP_HEADER));
            attributes.put(IS_TRUSTED_IP_KEY, trustedIp);
        }

        ApiProxyEnvironment requestEnvironment = new ApiProxyEnvironment(server, ticket, appId,
                module, majorVersion, instance, email, admin, authDomain,
                wallTimer, millisUntilSoftDeadline, attributes);
        attributes.put(REQUEST_THREAD_FACTORY_ATTR, new RequestThreadFactory(requestEnvironment));
        attributes.put(BACKGROUND_THREAD_FACTORY_ATTR, Executors.defaultThreadFactory());

        return requestEnvironment;
    }

    private final String server;
    private final String ticket;
    private final String appId;
    private final String service;
    private final String version;
    private final String email;
    private final boolean admin;
    private final String authDomain;
    private final Map<String, Object> attributes;
    private final Timer wallTimer;
    private final Long millisUntilSoftDeadline;
    private final AppLogsWriter appLogsWriter;

    final Semaphore pendingApiCallSemaphore;

    final Semaphore runningApiCallSemaphore;

    /**
     * Constructs a VM AppEngine API environment.
     *
     * @param server                  the host:port address of the VM's HTTP proxy server.
     * @param ticket                  the request ticket (if null the default one will be computed).
     * @param appId                   the application ID (required if ticket is null).
     * @param service                  the module name (required if ticket is null).
     * @param version            the major application version (required if ticket is null).
     * @param instance                the VM instance ID (required if ticket is null).
     * @param email                   the user's e-mail address (may be null).
     * @param admin                   true if the user is an administrator.
     * @param authDomain              the user's authentication domain (may be null).
     * @param wallTimer               optional wall clock timer for the current request (required for deadline).
     * @param millisUntilSoftDeadline optional soft deadline in milliseconds relative to 'wallTimer'.
     * @param attributes              map containing any attributes set on this environment.
     */
    public ApiProxyEnvironment(
            String server, String ticket, String appId, String service,
            String version, String instance, String email, boolean admin,
            String authDomain, Timer wallTimer, Long millisUntilSoftDeadline,
            Map<String, Object> attributes) {
        if (server == null || server.isEmpty()) {
            throw new IllegalArgumentException("proxy server host:port must be specified");
        }
        if (millisUntilSoftDeadline != null && wallTimer == null) {
            throw new IllegalArgumentException("wallTimer required when setting millisUntilSoftDeadline");
        }
        if (ticket == null || ticket.isEmpty()) {
            if ((appId == null || appId.isEmpty()) ||
                    (service == null || service.isEmpty()) ||
                    (version == null || version.isEmpty()) ||
                    (instance == null || instance.isEmpty())) {
                throw new IllegalArgumentException(
                        "When ticket == null the following must be specified: appId=" + appId +
                                ", module=" + service + ", version=" + version + "instance=" + instance);
            }
            String escapedAppId = appId.replace(':', '_').replace('.', '_');
            this.ticket = escapedAppId + '/' + service + '.' + version + "." + instance;
        } else {
            this.ticket = ticket;
        }
        this.server = server;
        this.appId = appId;
        this.service = service == null ? "default" : service;
        this.version = version;
        this.email = email == null ? "" : email;
        this.admin = admin;
        this.authDomain = authDomain == null ? "" : authDomain;
        this.wallTimer = wallTimer;
        this.millisUntilSoftDeadline = millisUntilSoftDeadline;
        this.attributes = Collections.synchronizedMap(attributes);

        this.appLogsWriter = new AppLogsWriter(
                new LinkedList<>(), DEFAULT_FLUSH_APP_LOGS_EVERY_BYTE_COUNT,
                DEFAULT_MAX_LOG_LINE_SIZE, MAX_LOG_FLUSH_SECONDS);
        this.pendingApiCallSemaphore = new Semaphore(MAX_PENDING_API_CALLS);
        this.runningApiCallSemaphore = new Semaphore(MAX_CONCURRENT_API_CALLS);
    }

    public void addLogRecord(LogRecord record) {
        appLogsWriter.addLogRecordAndMaybeFlush(record);
    }

    public void flushLogs() {
        appLogsWriter.flushAndWait();
    }

    public String getServer() {
        return server;
    }

    public String getTicket() {
        return ticket;
    }

    @Override
    public String getAppId() {
        return appId;
    }

    @Override
    public String getModuleId() {
        return service;
    }

    @Override
    public String getVersionId() {
        return version;
    }

    @Override
    public String getEmail() {
        return email;
    }

    @Override
    public boolean isLoggedIn() {
        return getEmail() != null && !getEmail().trim().isEmpty();
    }

    @Override
    public boolean isAdmin() {
        return admin;
    }

    @Override
    public String getAuthDomain() {
        return authDomain;
    }

    @Deprecated
    @Override
    public String getRequestNamespace() {
        Object currentNamespace =
                attributes.get(AttributeMapping.CURRENT_NAMESPACE_HEADER.attributeKey);
        if (currentNamespace instanceof String) {
            return (String) currentNamespace;
        }
        return "";
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public long getRemainingMillis() {
        if (millisUntilSoftDeadline == null) {
            return Long.MAX_VALUE;
        }
        return millisUntilSoftDeadline - (wallTimer.getNanoseconds() / 1000000L);
    }

    /**
     * Notifies the environment that an API call was queued up.
     *
     * @throws ApiProxyException
     */
    public void aSyncApiCallAdded(long maxWaitMs) throws ApiProxyException {
        try {
            if (pendingApiCallSemaphore.tryAcquire(maxWaitMs, TimeUnit.MILLISECONDS)) {
                return;
            }
            throw new ApiProxyException("Timed out while acquiring a pending API call semaphore.");
        } catch (InterruptedException e) {
            throw new ApiProxyException(
                    "Thread interrupted while acquiring a pending API call semaphore.");
        }
    }

    /**
     * Notifies the environment that an API call was started.
     *
     * @param releasePendingCall If true a pending call semaphore will be released (required if this
     *                           API call was requested asynchronously).
     * @throws ApiProxyException If the thread was interrupted while waiting for a semaphore.
     */
    public void apiCallStarted(long maxWaitMs, boolean releasePendingCall) throws ApiProxyException {
        try {
            if (runningApiCallSemaphore.tryAcquire(maxWaitMs, TimeUnit.MILLISECONDS)) {
                return;
            }
            throw new ApiProxyException("Timed out while acquiring an API call semaphore.");
        } catch (InterruptedException e) {
            throw new ApiProxyException("Thread interrupted while acquiring an API call semaphore.");
        } finally {
            if (releasePendingCall) {
                pendingApiCallSemaphore.release();
            }
        }
    }

    /**
     * Notifies the environment that an API call completed.
     */
    public void apiCallCompleted() {
        runningApiCallSemaphore.release();
    }
}
