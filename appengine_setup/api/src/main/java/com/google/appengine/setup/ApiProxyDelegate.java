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

import com.google.appengine.repackaged.com.google.common.collect.Lists;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.ApiConfig;
import com.google.apphosting.api.ApiProxy.ApiProxyException;
import com.google.apphosting.api.ApiProxy.LogRecord;
import com.google.apphosting.api.ApiProxy.RPCFailedException;
import com.google.apphosting.utils.remoteapi.RemoteApiPb;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.params.ConnManagerPNames;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.BasicHttpContext;

/**
 * Delegates AppEngine API calls to a local http API proxy.
 * <p/>
 * <p>Instances should be registered using ApiProxy.setDelegate(ApiProxy.Delegate).
 */
public class ApiProxyDelegate implements ApiProxy.Delegate<LazyApiProxyEnvironment> {

    private static final Logger logger = Logger.getLogger(ApiProxyDelegate.class.getName());

    public static final String RPC_DEADLINE_HEADER = "X-Google-RPC-Service-Deadline";
    public static final String RPC_STUB_ID_HEADER = "X-Google-RPC-Service-Endpoint";
    public static final String RPC_METHOD_HEADER = "X-Google-RPC-Service-Method";

    public static final String REQUEST_ENDPOINT = "/rpc_http"; // :8089
    public static final String REQUEST_STUB_ID = "app-engine-apis";
    public static final String REQUEST_STUB_METHOD = "/VMRemoteAPI.CallRemoteAPI";

    protected static final String API_DEADLINE_KEY =
            "com.google.apphosting.api.ApiProxy.api_deadline_key";

    static final int ADDITIONAL_HTTP_TIMEOUT_BUFFER_MS = 1000;

    protected int defaultTimeoutMs;
    protected final ExecutorService executor;

    protected final HttpClient httpclient;

    final IdleConnectionMonitorThread monitorThread;

    private static ClientConnectionManager createConnectionManager() {
        PoolingClientConnectionManager connectionManager = new PoolingClientConnectionManager();
        connectionManager.setMaxTotal(ApiProxyEnvironment.MAX_CONCURRENT_API_CALLS);
        connectionManager.setDefaultMaxPerRoute(ApiProxyEnvironment.MAX_CONCURRENT_API_CALLS);
        return connectionManager;
    }

    public ApiProxyDelegate() {
        this(new DefaultHttpClient(createConnectionManager()));
    }

    ApiProxyDelegate(HttpClient httpclient) {
        this.defaultTimeoutMs = 5 * 60 * 1000;
        this.executor = Executors.newCachedThreadPool();
        this.httpclient = httpclient;
        this.monitorThread = new IdleConnectionMonitorThread(httpclient.getConnectionManager());
        this.monitorThread.start();
    }

    @Override
    public byte[] makeSyncCall(
            LazyApiProxyEnvironment environment,
            String packageName,
            String methodName,
            byte[] requestData)
            throws ApiProxyException {
        return makeSyncCallWithTimeout(environment, packageName, methodName, requestData,
                defaultTimeoutMs);
    }

    private byte[] makeSyncCallWithTimeout(
            LazyApiProxyEnvironment environment,
            String packageName,
            String methodName,
            byte[] requestData,
            int timeoutMs)
            throws ApiProxyException {
        return makeApiCall(environment, packageName, methodName, requestData, timeoutMs, false);
    }

    private byte[] makeApiCall(LazyApiProxyEnvironment environment,
                               String packageName,
                               String methodName,
                               byte[] requestData,
                               int timeoutMs,
                               boolean wasAsync) {
        environment.apiCallStarted(RuntimeUtils.MAX_USER_API_CALL_WAIT_MS, wasAsync);
        try {
            return runSyncCall(environment, packageName, methodName, requestData, timeoutMs);
        } finally {
            environment.apiCallCompleted();
        }
    }


    protected byte[] runSyncCall(LazyApiProxyEnvironment environment, String packageName,
                                 String methodName, byte[] requestData, int timeoutMs) {
        HttpPost request = createRequest(environment, packageName, methodName, requestData, timeoutMs);
        try {
            BasicHttpContext context = new BasicHttpContext();
            HttpResponse response = httpclient.execute(request, context);

            if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                try (Scanner errorStreamScanner =
                             new Scanner(new BufferedInputStream(response.getEntity().getContent()));) {
                    logger.info("Error body: " + errorStreamScanner.useDelimiter("\\Z").next());
                    throw new RPCFailedException(packageName, methodName);
                }
            }
            try (BufferedInputStream bis = new BufferedInputStream(response.getEntity().getContent())) {
                RemoteApiPb.Response remoteResponse = new RemoteApiPb.Response();
                if (!remoteResponse.parseFrom(bis)) {
                    logger.info(
                            "HTTP ApiProxy unable to parse response for " + packageName + "." + methodName);
                    throw new RPCFailedException(packageName, methodName);
                }
                if (remoteResponse.hasRpcError() || remoteResponse.hasApplicationError()) {
                    throw convertRemoteError(remoteResponse, packageName, methodName, logger);
                }
                return remoteResponse.getResponseAsBytes();
            }
        } catch (IOException e) {
            logger.info(
                    "HTTP ApiProxy I/O error for " + packageName + "." + methodName + ": " + e.getMessage());
            throw new RPCFailedException(packageName, methodName);
        } finally {
            request.releaseConnection();
        }
    }

    /**
     * Create an HTTP post request suitable for sending to the API server.
     *
     * @param environment The current VMApiProxyEnvironment
     * @param packageName The API call package
     * @param methodName  The API call method
     * @param requestData The POST payload.
     * @param timeoutMs   The timeout for this request
     * @return an HttpPost object to send to the API.
     */
    static HttpPost createRequest(LazyApiProxyEnvironment environment, String packageName,
                                  String methodName, byte[] requestData, int timeoutMs) {
        RemoteApiPb.Request remoteRequest = new RemoteApiPb.Request();
        remoteRequest.setServiceName(packageName);
        remoteRequest.setMethod(methodName);
        // Commenting below line to validate the use-cases where security ticket may be needed. So far we did not need.
        //remoteRequest.setRequestId(environment.getTicket());
        remoteRequest.setRequestAsBytes(requestData);

        HttpPost request = new HttpPost("http://" + environment.getServer() + REQUEST_ENDPOINT);
        request.setHeader(RPC_STUB_ID_HEADER, REQUEST_STUB_ID);
        request.setHeader(RPC_METHOD_HEADER, REQUEST_STUB_METHOD);

        HttpParams params = new BasicHttpParams();
        params.setLongParameter(ConnManagerPNames.TIMEOUT,
                timeoutMs + ADDITIONAL_HTTP_TIMEOUT_BUFFER_MS);
        params.setIntParameter(CoreConnectionPNames.CONNECTION_TIMEOUT,
                timeoutMs + ADDITIONAL_HTTP_TIMEOUT_BUFFER_MS);
        params.setIntParameter(CoreConnectionPNames.SO_TIMEOUT,
                timeoutMs + ADDITIONAL_HTTP_TIMEOUT_BUFFER_MS);

        params.setBooleanParameter(CoreConnectionPNames.TCP_NODELAY, Boolean.TRUE);
        params.setBooleanParameter(CoreConnectionPNames.STALE_CONNECTION_CHECK, Boolean.FALSE);
        request.setParams(params);

        Double deadline = (Double) (environment.getAttributes().get(API_DEADLINE_KEY));
        if (deadline == null) {
            request.setHeader(RPC_DEADLINE_HEADER,
                    Double.toString(TimeUnit.SECONDS.convert(timeoutMs, TimeUnit.MILLISECONDS)));
        } else {
            request.setHeader(RPC_DEADLINE_HEADER, Double.toString(deadline));
        }

        Object dapperHeader = environment.getAttributes()
                .get(ApiProxyEnvironment.AttributeMapping.DAPPER_ID.attributeKey);
        if (dapperHeader instanceof String) {
            request.setHeader(
                    ApiProxyEnvironment.AttributeMapping.DAPPER_ID.headerKey, (String) dapperHeader);
        }

        ByteArrayEntity postPayload = new ByteArrayEntity(remoteRequest.toByteArray(),
                ContentType.APPLICATION_OCTET_STREAM);
        postPayload.setChunked(false);
        request.setEntity(postPayload);

        return request;
    }

    /**
     * Convert RemoteApiPb.Response errors to the appropriate exception.
     * <p/>
     * <p>The response must have exactly one of the RpcError and ApplicationError fields set.
     *
     * @param remoteResponse the Response
     * @param packageName    the name of the API package.
     * @param methodName     the name of the method within the API package.
     * @param logger         the Logger used to create log messages.
     * @return ApiProxyException
     */
    private static ApiProxyException convertRemoteError(RemoteApiPb.Response remoteResponse,
                                                        String packageName, String methodName, Logger logger) {
        if (remoteResponse.hasRpcError()) {
            return convertApiResponseRpcErrorToException(
                    remoteResponse.getRpcError(),
                    packageName,
                    methodName,
                    logger);
        }

        RemoteApiPb.ApplicationError error = remoteResponse.getApplicationError();
        return new ApiProxy.ApplicationException(error.getCode(), error.getDetail());
    }

    /**
     * Convert the RemoteApiPb.RpcError to the appropriate exception.
     *
     * @param rpcError    the RemoteApiPb.RpcError.
     * @param packageName the name of the API package.
     * @param methodName  the name of the method within the API package.
     * @param logger      the Logger used to create log messages.
     * @return ApiProxyException
     */
    private static ApiProxyException convertApiResponseRpcErrorToException(
            RemoteApiPb.RpcError rpcError, String packageName, String methodName, Logger logger) {

        int rpcCode = rpcError.getCode();
        String errorDetail = rpcError.getDetail();
        if (rpcCode > RemoteApiPb.RpcError.ErrorCode.values().length) {
            logger.severe("Received unrecognized error code from server: " + rpcError.getCode() +
                    " details: " + errorDetail);
            return new ApiProxy.UnknownException(packageName, methodName);
        }
        RemoteApiPb.RpcError.ErrorCode errorCode = RemoteApiPb.RpcError.ErrorCode.values()[
                rpcError.getCode()];
        logger.warning("RPC failed : " + errorCode + " : " + errorDetail);

        switch (errorCode) {
            case CALL_NOT_FOUND:
                return new ApiProxy.CallNotFoundException(packageName, methodName);
            case PARSE_ERROR:
                return new ApiProxy.ArgumentException(packageName, methodName);
            case SECURITY_VIOLATION:
                logger.severe("Security violation: invalid request id used!");
                return new ApiProxy.UnknownException(packageName, methodName);
            case CAPABILITY_DISABLED:
                return new ApiProxy.CapabilityDisabledException(
                        errorDetail, packageName, methodName);
            case OVER_QUOTA:
                return new ApiProxy.OverQuotaException(packageName, methodName);
            case REQUEST_TOO_LARGE:
                return new ApiProxy.RequestTooLargeException(packageName, methodName);
            case RESPONSE_TOO_LARGE:
                return new ApiProxy.ResponseTooLargeException(packageName, methodName);
            case BAD_REQUEST:
                return new ApiProxy.ArgumentException(packageName, methodName);
            case CANCELLED:
                return new ApiProxy.CancelledException(packageName, methodName);
            case FEATURE_DISABLED:
                return new ApiProxy.FeatureNotEnabledException(
                        errorDetail, packageName, methodName);
            case DEADLINE_EXCEEDED:
                return new ApiProxy.ApiDeadlineExceededException(packageName, methodName);
            default:
                return new ApiProxy.UnknownException(packageName, methodName);
        }
    }

    private class MakeSyncCall implements Callable<byte[]> {
        private final ApiProxyDelegate delegate;
        private final LazyApiProxyEnvironment environment;
        private final String packageName;
        private final String methodName;
        private final byte[] requestData;
        private final int timeoutMs;

        public MakeSyncCall(ApiProxyDelegate delegate,
                            LazyApiProxyEnvironment environment,
                            String packageName,
                            String methodName,
                            byte[] requestData,
                            int timeoutMs) {
            this.delegate = delegate;
            this.environment = environment;
            this.packageName = packageName;
            this.methodName = methodName;
            this.requestData = requestData;
            this.timeoutMs = timeoutMs;
        }

        @Override
        public byte[] call() throws Exception {
            return delegate.makeApiCall(environment,
                    packageName,
                    methodName,
                    requestData,
                    timeoutMs,
                    true);
        }
    }

    @Override
    public Future<byte[]> makeAsyncCall(
            LazyApiProxyEnvironment environment,
            String packageName,
            String methodName,
            byte[] request,
            ApiConfig apiConfig) {
        int timeoutMs = defaultTimeoutMs;
        if (apiConfig != null && apiConfig.getDeadlineInSeconds() != null) {
            timeoutMs = (int) (apiConfig.getDeadlineInSeconds() * 1000);
        }
        environment.aSyncApiCallAdded(RuntimeUtils.MAX_USER_API_CALL_WAIT_MS);
        return executor.submit(new MakeSyncCall(this, environment, packageName,
                methodName, request, timeoutMs));
    }

    @Override
    public void log(LazyApiProxyEnvironment environment, LogRecord record) {
        if (environment != null) {
            environment.addLogRecord(record);
        }
    }

    @Override
    public void flushLogs(LazyApiProxyEnvironment environment) {
        if (environment != null) {
            environment.flushLogs();
        }
    }

    @Override
    public List<Thread> getRequestThreads(LazyApiProxyEnvironment environment) {
        Object threadFactory =
                environment.getAttributes().get(ApiProxyEnvironment.REQUEST_THREAD_FACTORY_ATTR);
        if (threadFactory != null && threadFactory instanceof RequestThreadFactory) {
            return ((RequestThreadFactory) threadFactory).getRequestThreads();
        }
        logger.warning("Got a call to getRequestThreads() but no VmRequestThreadFactory is available");
        return Lists.newLinkedList();
    }

    /**
     * Simple connection watchdog verifying that our connections are alive. Any stale connections are
     * cleared as well.
     */
    class IdleConnectionMonitorThread extends Thread {

        private final ClientConnectionManager connectionManager;

        public IdleConnectionMonitorThread(ClientConnectionManager connectionManager) {
            super("IdleApiConnectionMontorThread");
            this.connectionManager = connectionManager;
            this.setDaemon(false);
        }

        @Override
        public void run() {
            try {
                while (true) {
                    connectionManager.closeExpiredConnections();
                    connectionManager.closeIdleConnections(60, TimeUnit.SECONDS);
                    Thread.sleep(5000);
                }
            } catch (InterruptedException ex) {
            }
        }
    }
}
