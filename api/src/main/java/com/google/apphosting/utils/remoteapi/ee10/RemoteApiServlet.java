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

package com.google.apphosting.utils.remoteapi.ee10;

import static com.google.apphosting.datastore.DatastoreV3Pb.Error.ErrorCode.BAD_REQUEST;
import static com.google.apphosting.datastore.DatastoreV3Pb.Error.ErrorCode.CONCURRENT_TRANSACTION;

import com.google.appengine.api.oauth.OAuthRequestException;
import com.google.appengine.api.oauth.OAuthService;
import com.google.appengine.api.oauth.OAuthServiceFactory;
import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.datastore.DatastoreV3Pb.BeginTransactionRequest;
import com.google.apphosting.datastore.DatastoreV3Pb.DeleteRequest;
import com.google.apphosting.datastore.DatastoreV3Pb.GetRequest;
import com.google.apphosting.datastore.DatastoreV3Pb.GetResponse;
import com.google.apphosting.datastore.DatastoreV3Pb.NextRequest;
import com.google.apphosting.datastore.DatastoreV3Pb.PutRequest;
import com.google.apphosting.datastore.DatastoreV3Pb.Query;
import com.google.apphosting.datastore.DatastoreV3Pb.QueryResult;
import com.google.apphosting.utils.remoteapi.RemoteApiPb.ApplicationError;
import com.google.apphosting.utils.remoteapi.RemoteApiPb.Request;
import com.google.apphosting.utils.remoteapi.RemoteApiPb.Response;
import com.google.apphosting.utils.remoteapi.RemoteApiPb.TransactionQueryResult;
import com.google.apphosting.utils.remoteapi.RemoteApiPb.TransactionRequest;
import com.google.apphosting.utils.remoteapi.RemoteApiPb.TransactionRequest.Precondition;
import com.google.io.protocol.ProtocolMessage;
// <internal24>
import com.google.storage.onestore.v3.OnestoreEntity;
import com.google.storage.onestore.v3.OnestoreEntity.EntityProto;
import com.google.storage.onestore.v3.OnestoreEntity.Path.Element;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.logging.Logger;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Remote API servlet handler.
 *
 */
public class RemoteApiServlet extends HttpServlet {
  private static final Logger log = Logger.getLogger(RemoteApiServlet.class.getName());

  private static final String[] OAUTH_SCOPES = new String[] {
      "https://www.googleapis.com/auth/appengine.apis",
      "https://www.googleapis.com/auth/cloud-platform",
  };
  private static final String INBOUND_APP_SYSTEM_PROPERTY = "HTTP_X_APPENGINE_INBOUND_APPID";
  private static final String INBOUND_APP_HEADER_NAME = "X-AppEngine-Inbound-AppId";

  private HashSet<String> allowedApps = null;
  private final OAuthService oauthService;

  public RemoteApiServlet() {
    this(OAuthServiceFactory.getOAuthService());
  }

  // @VisibleForTesting
  RemoteApiServlet(OAuthService oauthService) {
    this.oauthService = oauthService;
  }

  /** Exception for unknown errors from a Python remote_api handler. */
  public static class UnknownPythonServerException extends RuntimeException {
    public UnknownPythonServerException(String message) {
      super(message);
    }
  }

  /**
   * Checks if the inbound request is valid.
   *
   * @param req the {@link HttpServletRequest}
   * @param res the {@link HttpServletResponse}
   * @return true if the application is known.
   * @throws java.io.IOException
   */
  boolean checkIsValidRequest(HttpServletRequest req, HttpServletResponse res)
      throws java.io.IOException {
    if (!checkIsKnownInbound(req) && !checkIsAdmin(req, res)) {
      return false;
    }
    return checkIsValidHeader(req, res);
  }

  /**
   * Checks if the request is coming from a known application.
   *
   * @param req the {@link HttpServletRequest}
   * @return true if the application is known.
   * @throws java.io.IOException
   */
  private synchronized boolean checkIsKnownInbound(HttpServletRequest req)
      throws java.io.IOException {
    if (allowedApps == null) {
      allowedApps = new HashSet<String>();
      String allowedAppsStr = System.getProperty(INBOUND_APP_SYSTEM_PROPERTY);
      if (allowedAppsStr != null) {
        String[] apps = allowedAppsStr.split(",");
        for (String app : apps) {
          allowedApps.add(app);
        }
      }
    }
    String inboundAppId = req.getHeader(INBOUND_APP_HEADER_NAME);
    return inboundAppId != null && allowedApps.contains(inboundAppId);
  }

  /**
   * Checks for the api-version header to prevent XSRF
   *
   * @param req the {@link HttpServletRequest}
   * @param res the {@link HttpServletResponse}
   * @return true if the header exists.
   * @throws java.io.IOException
   */
  private boolean checkIsValidHeader(HttpServletRequest req, HttpServletResponse res)
      throws java.io.IOException {
    if (req.getHeader("X-appcfg-api-version") == null) {
      res.setStatus(403);
      res.setContentType("text/plain");
      res.getWriter().println("This request did not contain a necessary header");
      return false;
    }
    return true;
  }

  /**
   * Check that the current user is signed is with admin access.
   *
   * @return true if the current user is logged in with admin access, false
   *         otherwise.
   */
  private boolean checkIsAdmin(HttpServletRequest req, HttpServletResponse res)
      throws java.io.IOException {
    UserService userService = UserServiceFactory.getUserService();

    // Check for regular (cookie-based) authentication.
    if (userService.getCurrentUser() != null) {
      if (userService.isUserAdmin()) {
        return true;
      } else {
        respondNotAdmin(res);
        return false;
      }
    }

    // Check for OAuth-based authentication.
    try {
      if (oauthService.isUserAdmin(OAUTH_SCOPES)) {
        return true;
      } else {
        respondNotAdmin(res);
        return false;
      }
    } catch (OAuthRequestException e) {
      // Invalid OAuth request; fall through to sending redirect.
    }

    res.sendRedirect(userService.createLoginURL(req.getRequestURI()));
    return false;
  }

  private void respondNotAdmin(HttpServletResponse res) throws java.io.IOException {
    res.setStatus(401);
    res.setContentType("text/plain");
    res.getWriter().println(
        "You must be logged in as an administrator, or access from an approved application.");
  }

  /**
   * Serve GET requests with a YAML encoding of the app-id and a validation
   * token.
   */
  @Override
  public void doGet(HttpServletRequest req, HttpServletResponse res) throws java.io.IOException {
    if (!checkIsValidRequest(req, res)) {
      return;
    }
    res.setContentType("text/plain");
    String appId = ApiProxy.getCurrentEnvironment().getAppId();
    StringBuilder outYaml =
        new StringBuilder().append("{rtok: ").append(req.getParameter("rtok")).append(", app_id: ")
            .append(appId).append("}");
    res.getWriter().println(outYaml);
  }

  /**
   * Serve POST requests by forwarding calls to ApiProxy.
   */
  @Override
  public void doPost(HttpServletRequest req, HttpServletResponse res) throws java.io.IOException {
    if (!checkIsValidRequest(req, res)) {
      return;
    }
    res.setContentType("application/octet-stream");

    Response response = new Response();

    try {
      byte[] responseData = executeRequest(req);
      response.setResponseAsBytes(responseData);
      res.setStatus(200);
    } catch (Exception e) {
      log.warning("Caught exception while executing remote_api command:\n" + e);
      res.setStatus(200);
      ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
      ObjectOutput out = new ObjectOutputStream(byteStream);
      out.writeObject(e);
      out.close();
      byte[] serializedException = byteStream.toByteArray();
      response.setJavaExceptionAsBytes(serializedException);
      if (e instanceof ApiProxy.ApplicationException) {
        ApiProxy.ApplicationException ae = (ApiProxy.ApplicationException) e;
        ApplicationError appError = response.getMutableApplicationError();
        appError.setCode(ae.getApplicationError());
        appError.setDetail(ae.getErrorDetail());
      }
    }
    res.getOutputStream().write(response.toByteArray());
  }

  private byte[] executeRunQuery(Request request) {
    Query queryRequest = new Query();
    parseFromBytes(queryRequest, request.getRequestAsBytes());
    int batchSize = Math.max(1000, queryRequest.getLimit());
    queryRequest.setCount(batchSize);

    QueryResult runQueryResponse = new QueryResult();
    byte[] res = ApiProxy.makeSyncCall("datastore_v3", "RunQuery", request.getRequestAsBytes());
    parseFromBytes(runQueryResponse, res);

    if (queryRequest.hasLimit()) {
      // Try to pull all results
      while (runQueryResponse.isMoreResults()) {
        NextRequest nextRequest = new NextRequest();
        nextRequest.getMutableCursor().mergeFrom(runQueryResponse.getCursor());
        nextRequest.setCount(batchSize);
        byte[] nextRes = ApiProxy.makeSyncCall("datastore_v3", "Next", nextRequest.toByteArray());
        parseFromBytes(runQueryResponse, nextRes);
      }
    }
    return runQueryResponse.toByteArray();
  }

  private byte[] executeTxQuery(Request request) {
    TransactionQueryResult result = new TransactionQueryResult();

    Query query = new Query();
    parseFromBytes(query, request.getRequestAsBytes());

    if (!query.hasAncestor()) {
      throw new ApiProxy.ApplicationException(BAD_REQUEST.getValue(),
                                              "No ancestor in transactional query.");
    }
    // Make __entity_group__ key
    OnestoreEntity.Reference egKey =
        result.getMutableEntityGroupKey().mergeFrom(query.getAncestor());
    OnestoreEntity.Path.Element root = egKey.getPath().getElement(0);
    egKey.getMutablePath().clearElement().addElement(root);
    OnestoreEntity.Path.Element egElement = new OnestoreEntity.Path.Element();
    egElement.setType("__entity_group__").setId(1);
    egKey.getMutablePath().addElement(egElement);

    // And then perform the transaction with the ancestor query and __entity_group__ fetch.
    byte[] tx = beginTransaction(false);
    parseFromBytes(query.getMutableTransaction(), tx);
    byte[] queryBytes = ApiProxy.makeSyncCall("datastore_v3", "RunQuery", query.toByteArray());
    parseFromBytes(result.getMutableResult(), queryBytes);

    GetRequest egRequest = new GetRequest();
    egRequest.addKey(egKey);
    GetResponse egResponse = txGet(tx, egRequest);
    if (egResponse.getEntity(0).hasEntity()) {
      result.setEntityGroup(egResponse.getEntity(0).getEntity());
    }
    rollback(tx);

    return result.toByteArray();
  }

  /**
   * Throws a CONCURRENT_TRANSACTION exception if the entity does not match the precondition.
   */
  private void assertEntityResultMatchesPrecondition(
      GetResponse.Entity entityResult, Precondition precondition) {
    // This handles the case where the Entity was missing in one of the two params.
    if (precondition.hasHash() != entityResult.hasEntity()) {
      throw new ApiProxy.ApplicationException(CONCURRENT_TRANSACTION.getValue(),
          "Transaction precondition failed");
    }

    if (entityResult.hasEntity()) {
      // Both params have an Entity.  Make sure the Entities match using a SHA-1 hash.
      EntityProto entity = entityResult.getEntity();
      if (Arrays.equals(precondition.getHashAsBytes(), computeSha1(entity))) {
        // They match.  We're done.
        return;
      }

      // See javadoc of computeSha1OmittingLastByteForBackwardsCompatibility for explanation.
      byte[] backwardsCompatibleHash = computeSha1OmittingLastByteForBackwardsCompatibility(entity);
      if (!Arrays.equals(precondition.getHashAsBytes(), backwardsCompatibleHash)) {
        throw new ApiProxy.ApplicationException(
            CONCURRENT_TRANSACTION.getValue(), "Transaction precondition failed");
      }
    }
    // Else, the Entity was missing from both.
  }

  private byte[] executeTx(Request request) {
    TransactionRequest txRequest = new TransactionRequest();
    parseFromBytes(txRequest, request.getRequestAsBytes());

    byte[] tx = beginTransaction(txRequest.isAllowMultipleEg());

    List<Precondition> preconditions = txRequest.preconditions();

    // Check transaction preconditions
    if (!preconditions.isEmpty()) {
      GetRequest getRequest = new GetRequest();
      for (Precondition precondition : preconditions) {
        OnestoreEntity.Reference key = precondition.getKey();
        OnestoreEntity.Reference requestKey = getRequest.addKey();
        requestKey.mergeFrom(key);
      }

      GetResponse getResponse = txGet(tx, getRequest);
      List<GetResponse.Entity> entities = getResponse.entitys();

      // Note that this is guaranteed because we don't specify allow_deferred on the GetRequest.
      // TODO: Consider supporting deferred gets here.
      assert (entities.size() == preconditions.size());
      for (int i = 0; i < entities.size(); i++) {
        // Throw an exception if any of the Entities don't match the Precondition specification.
        assertEntityResultMatchesPrecondition(entities.get(i), preconditions.get(i));
      }
    }
    // Preconditions OK.
    // Perform puts.
    byte[] res = new byte[0]; // a serialized VoidProto
    if (txRequest.hasPuts()) {
      PutRequest putRequest = txRequest.getPuts();
      parseFromBytes(putRequest.getMutableTransaction(), tx);
      res = ApiProxy.makeSyncCall("datastore_v3", "Put", putRequest.toByteArray());
    }
    // Perform deletes.
    if (txRequest.hasDeletes()) {
      DeleteRequest deleteRequest = txRequest.getDeletes();
      parseFromBytes(deleteRequest.getMutableTransaction(), tx);
      ApiProxy.makeSyncCall("datastore_v3", "Delete", deleteRequest.toByteArray());
    }
    // Commit transaction.
    ApiProxy.makeSyncCall("datastore_v3", "Commit", tx);
    return res;
  }

  private byte[] executeGetIDs(Request request, boolean isXG) {
    PutRequest putRequest = new PutRequest();
    parseFromBytes(putRequest, request.getRequestAsBytes());
    for (EntityProto entity : putRequest.entitys()) {
      assert (entity.propertySize() == 0);
      assert (entity.rawPropertySize() == 0);
      assert (entity.getEntityGroup().elementSize() == 0);
      List<Element> elementList = entity.getKey().getPath().elements();
      Element lastPart = elementList.get(elementList.size() - 1);
      assert (lastPart.getId() == 0);
      assert (!lastPart.hasName());
    }

    // Start a Transaction.

    // TODO: Shouldn't this use allocateIds instead?
    byte[] tx = beginTransaction(isXG);
    parseFromBytes(putRequest.getMutableTransaction(), tx);

    // Make a put request for a bunch of empty entities with the requisite
    // paths.
    byte[] res = ApiProxy.makeSyncCall("datastore_v3", "Put", putRequest.toByteArray());

    // Roll back the transaction so we don't actually insert anything.
    rollback(tx);
    return res;
  }

  private byte[] executeRequest(HttpServletRequest req) throws java.io.IOException {
    Request request = new Request();
    parseFromInputStream(request, req.getInputStream());
    String service = request.getServiceName();
    String method = request.getMethod();

    log.fine("remote API call: " + service + ", " + method);

    if (service.equals("remote_datastore")) {
      if (method.equals("RunQuery")) {
        return executeRunQuery(request);
      } else if (method.equals("Transaction")) {
        return executeTx(request);
      } else if (method.equals("TransactionQuery")) {
        return executeTxQuery(request);
      } else if (method.equals("GetIDs")) {
        return executeGetIDs(request, false);
      } else if (method.equals("GetIDsXG")) {
        return executeGetIDs(request, true);
      } else {
        throw new ApiProxy.CallNotFoundException(service, method);
      }
    } else {
      return ApiProxy.makeSyncCall(service, method, request.getRequestAsBytes());
    }
  }

  // Datastore utility functions.

  private static byte[] beginTransaction(boolean allowMultipleEg) {
    String appId = ApiProxy.getCurrentEnvironment().getAppId();
    byte[] req = new BeginTransactionRequest().setApp(appId)
        .setAllowMultipleEg(allowMultipleEg).toByteArray();
    return ApiProxy.makeSyncCall("datastore_v3", "BeginTransaction", req);
  }

  private static void rollback(byte[] tx) {
    ApiProxy.makeSyncCall("datastore_v3", "Rollback", tx);
  }

  private static GetResponse txGet(byte[] tx, GetRequest request) {
    parseFromBytes(request.getMutableTransaction(), tx);
    GetResponse response = new GetResponse();
    byte[] resultBytes = ApiProxy.makeSyncCall("datastore_v3", "Get", request.toByteArray());
    parseFromBytes(response, resultBytes);
    return response;
  }

  // @VisibleForTesting
  static byte[] computeSha1(EntityProto entity) {
    byte[] entityBytes = entity.toByteArray();
    return computeSha1(entityBytes, entityBytes.length);
  }

  /**
   * This is a HACK.  There used to be a bug in RemoteDatastore.java in that it would omit the last
   * byte of the Entity when calculating the hash for the Precondition.  If an app has not updated
   * that library, we may still receive hashes like this.  For backwards compatibility, we'll
   * consider the transaction valid if omitting the last byte of the Entity matches the
   * Precondition.
   */
  // @VisibleForTesting
  static byte[] computeSha1OmittingLastByteForBackwardsCompatibility(EntityProto entity) {
    byte[] entityBytes = entity.toByteArray();
    return computeSha1(entityBytes, entityBytes.length - 1);
  }

  // <internal25>
  private static byte[] computeSha1(byte[] bytes, int length) {
    MessageDigest md;
    try {
      md = MessageDigest.getInstance("SHA-1");
    } catch (NoSuchAlgorithmException e) {
      throw new ApiProxy.ApplicationException(
          CONCURRENT_TRANSACTION.getValue(), "Transaction precondition could not be computed");
    }

    md.update(bytes, 0, length);
    return md.digest();
  }

  private static void parseFromBytes(ProtocolMessage<?> message, byte[] bytes) {
    boolean parsed = message.parseFrom(bytes);
    checkParse(message, parsed);
  }

  private static void parseFromInputStream(ProtocolMessage<?> message, InputStream inputStream) {
    boolean parsed = message.parseFrom(inputStream);
    checkParse(message, parsed);
  }

  private static void checkParse(ProtocolMessage<?> message, boolean parsed) {
    if (!parsed) {
      throw new ApiProxy.ApiProxyException("Could not parse protobuf");
    }
    String error = message.findInitializationError();
    if (error != null) {
      throw new ApiProxy.ApiProxyException("Could not parse protobuf: " + error);
    }
  }
}
