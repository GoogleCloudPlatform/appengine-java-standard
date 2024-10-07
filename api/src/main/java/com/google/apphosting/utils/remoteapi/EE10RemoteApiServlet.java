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

package com.google.apphosting.utils.remoteapi;

import static com.google.apphosting.datastore.proto2api.DatastoreV3Pb.Error.ErrorCode.BAD_REQUEST;
import static com.google.apphosting.datastore.proto2api.DatastoreV3Pb.Error.ErrorCode.CONCURRENT_TRANSACTION;

import com.google.appengine.api.oauth.OAuthRequestException;
import com.google.appengine.api.oauth.OAuthService;
import com.google.appengine.api.oauth.OAuthServiceFactory;
import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb.BeginTransactionRequest;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb.DeleteRequest;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb.GetRequest;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb.GetResponse;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb.NextRequest;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb.PutRequest;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb.Query;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb.QueryResult;
import com.google.apphosting.utils.remoteapi.RemoteApiPb.ApplicationError;
import com.google.apphosting.utils.remoteapi.RemoteApiPb.Request;
import com.google.apphosting.utils.remoteapi.RemoteApiPb.Response;
import com.google.apphosting.utils.remoteapi.RemoteApiPb.TransactionQueryResult;
import com.google.apphosting.utils.remoteapi.RemoteApiPb.TransactionRequest;
import com.google.apphosting.utils.remoteapi.RemoteApiPb.TransactionRequest.Precondition;
// import com.google.io.protocol.ProtocolMessage;
import com.google.protobuf.Message;
import com.google.protobuf.ByteString;
// <internal24>
import com.google.storage.onestore.v3.proto2api.OnestoreEntity;
import com.google.storage.onestore.v3.proto2api.OnestoreEntity.EntityProto;
import com.google.storage.onestore.v3.proto2api.OnestoreEntity.Path.Element;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

/** Remote API servlet handler. */
public class EE10RemoteApiServlet extends HttpServlet {
  private static final Logger log = Logger.getLogger(EE10RemoteApiServlet.class.getName());

  private static final String[] OAUTH_SCOPES = new String[] {
      "https://www.googleapis.com/auth/appengine.apis",
      "https://www.googleapis.com/auth/cloud-platform",
  };
  private static final String INBOUND_APP_SYSTEM_PROPERTY = "HTTP_X_APPENGINE_INBOUND_APPID";
  private static final String INBOUND_APP_HEADER_NAME = "X-AppEngine-Inbound-AppId";

  private HashSet<String> allowedApps = null;
  private final OAuthService oauthService;

  public EE10RemoteApiServlet() {
    this(OAuthServiceFactory.getOAuthService());
  }

  // @VisibleForTesting
  EE10RemoteApiServlet(OAuthService oauthService) {
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
      allowedApps = new HashSet<>();
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

    Response.Builder response =  Response.newBuilder();

    try {
      byte[] responseData = executeRequest(req);
      response.mergeFrom(responseData);
      res.setStatus(200);
    } catch (Exception e) {
      log.warning("Caught exception while executing remote_api command:\n" + e);
      res.setStatus(200);
      ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
      ObjectOutput out = new ObjectOutputStream(byteStream);
      out.writeObject(e);
      out.close();
      byte[] serializedException = byteStream.toByteArray();
      response.setJavaException(ByteString.copyFrom(serializedException));
      if (e instanceof ApiProxy.ApplicationException) {
        ApiProxy.ApplicationException ae = (ApiProxy.ApplicationException) e;
        ApplicationError.Builder appError = response.getApplicationErrorBuilder();
        appError.setCode(ae.getApplicationError());
        appError.setDetail(ae.getErrorDetail());
      }
    }
    res.getOutputStream().write(response.build().toByteArray());
  }

  private byte[] executeRunQuery(Request request) {
    Query.Builder queryRequest = Query.newBuilder();
    parseFromBytes(queryRequest.build(), request.getRequest().toByteArray());
    int batchSize = Math.max(1000, queryRequest.getLimit());
    queryRequest.setCount(batchSize);

    QueryResult.Builder runQueryResponse = QueryResult.newBuilder();
    byte[] res = ApiProxy.makeSyncCall("datastore_v3", "RunQuery", request.getRequest()
        .toByteArray());
    parseFromBytes(runQueryResponse.build(), res);

    if (queryRequest.hasLimit()) {
      // Try to pull all results
      while (runQueryResponse.getMoreResults()) {
        NextRequest.Builder nextRequest = NextRequest.newBuilder();
        nextRequest.getCursorBuilder().mergeFrom(runQueryResponse.getCursor());
        nextRequest.setCount(batchSize);
        byte[] nextRes = ApiProxy.makeSyncCall("datastore_v3", "Next", nextRequest.build()
            .toByteArray());
        parseFromBytes(runQueryResponse.build(), nextRes);
      }
    }
    return runQueryResponse.build().toByteArray();
  }

  private byte[] executeTxQuery(Request request) {
    TransactionQueryResult.Builder result = TransactionQueryResult.newBuilder();

    Query.Builder query = Query.newBuilder();
    parseFromBytes(query.build(), request.getRequest().toByteArray());

    if (!query.hasAncestor()) {
      throw new ApiProxy.ApplicationException(BAD_REQUEST.getNumber(),
                                              "No ancestor in transactional query.");
    }
    // Make __entity_group__ key
    OnestoreEntity.Reference.Builder egKey =
        result.build().getEntityGroupKey().toBuilder().mergeFrom(query.getAncestor());
    OnestoreEntity.Path.Element root = egKey.getPath().getElement(0);
    egKey.getPathBuilder().clearElement().addElement(root);
    OnestoreEntity.Path.Element.Builder egElement = OnestoreEntity.Path.Element.newBuilder();
    egElement.setType("__entity_group__").setId(1);
    egKey.getPathBuilder().addElement(egElement);

    // And then perform the transaction with the ancestor query and __entity_group__ fetch.
    byte[] tx = beginTransaction(false);
    parseFromBytes(query.getTransaction(), tx);
    byte[] queryBytes = ApiProxy.makeSyncCall("datastore_v3", "RunQuery", query.build()
        .toByteArray());
    parseFromBytes(result.getResult(), queryBytes);

    GetRequest.Builder egRequest = GetRequest.newBuilder();
    egRequest.addKey(egKey);
    GetResponse.Builder egResponse = txGet(tx, egRequest);
    if (egResponse.getEntity(0).hasEntity()) {
      result.setEntityGroup(egResponse.getEntity(0).getEntity());
    }
    rollback(tx);

    return result.build().toByteArray();
  }

  /**
   * Throws a CONCURRENT_TRANSACTION exception if the entity does not match the precondition.
   */
  private void assertEntityResultMatchesPrecondition(
      GetResponse.Entity entityResult, Precondition precondition) {
    // This handles the case where the Entity was missing in one of the two params.
    if (precondition.hasHash() != entityResult.hasEntity()) {
      throw new ApiProxy.ApplicationException(CONCURRENT_TRANSACTION.getNumber(),
          "Transaction precondition failed");
    }

    if (entityResult.hasEntity()) {
      // Both params have an Entity.  Make sure the Entities match using a SHA-1 hash.
      EntityProto entity = entityResult.getEntity();
      if (Arrays.equals(precondition.getHashBytes().toByteArray(), computeSha1(entity))) {
        // They match.  We're done.
        return;
      }

      // See javadoc of computeSha1OmittingLastByteForBackwardsCompatibility for explanation.
      byte[] backwardsCompatibleHash = computeSha1OmittingLastByteForBackwardsCompatibility(entity);
      if (!Arrays.equals(precondition.getHashBytes().toByteArray(), backwardsCompatibleHash)) {
        throw new ApiProxy.ApplicationException(
            CONCURRENT_TRANSACTION.getNumber(), "Transaction precondition failed");
      }
    }
    // Else, the Entity was missing from both.
  }

  private byte[] executeTx(Request request) {
    TransactionRequest.Builder txRequest = TransactionRequest.newBuilder();
    parseFromBytes(txRequest.build(), request.getRequest().toByteArray());

    byte[] tx = beginTransaction(txRequest.hasAllowMultipleEg());

    List<Precondition> preconditions = txRequest.getPreconditionList();

    // Check transaction preconditions
    if (!preconditions.isEmpty()) {
      GetRequest.Builder getRequest = GetRequest.newBuilder();
      for (Precondition precondition : preconditions) {
        OnestoreEntity.Reference key = precondition.getKey();
        OnestoreEntity.Reference.Builder requestKey = getRequest.addKeyBuilder();
        requestKey.mergeFrom(key);
      }

      GetResponse.Builder getResponse = txGet(tx, getRequest);
      List<GetResponse.Entity> entities = getResponse.getEntityList();

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
      parseFromBytes(putRequest.getTransaction(), tx);
      res = ApiProxy.makeSyncCall("datastore_v3", "Put", putRequest.toByteArray());
    }
    // Perform deletes.
    if (txRequest.hasDeletes()) {
      DeleteRequest deleteRequest = txRequest.getDeletes();
      parseFromBytes(deleteRequest.getTransaction(), tx);
      ApiProxy.makeSyncCall("datastore_v3", "Delete", deleteRequest.toByteArray());
    }
    // Commit transaction.
    ApiProxy.makeSyncCall("datastore_v3", "Commit", tx);
    return res;
  }

  private byte[] executeGetIDs(Request request, boolean isXG) {
    PutRequest.Builder putRequest = PutRequest.newBuilder();
    parseFromBytes(putRequest.build(), request.getRequestIdBytes().toByteArray());
    for (EntityProto entity : putRequest.getEntityList()) {
      assert (entity.getPropertyCount() == 0);
      assert (entity.getRawPropertyCount() == 0);
      assert (entity.getEntityGroup().getElementCount() == 0);
      List<Element> elementList = entity.getKey().getPath().getElementList();
      Element lastPart = elementList.get(elementList.size() - 1);
      assert (lastPart.getId() == 0);
      assert (!lastPart.hasName());
    }

    // Start a Transaction.

    // TODO: Shouldn't this use allocateIds instead?
    byte[] tx = beginTransaction(isXG);
    parseFromBytes(putRequest.getTransaction(), tx);

    // Make a put request for a bunch of empty entities with the requisite
    // paths.
    byte[] res = ApiProxy.makeSyncCall("datastore_v3", "Put", putRequest.build().toByteArray());

    // Roll back the transaction so we don't actually insert anything.
    rollback(tx);
    return res;
  }

  private byte[] executeRequest(HttpServletRequest req) throws java.io.IOException {
    Request.Builder request =  Request.newBuilder();
    parseFromInputStream(request.build(), req.getInputStream());
    String service = request.getServiceName();
    String method = request.getMethod();

    log.fine("remote API call: " + service + ", " + method);

    if (service.equals("remote_datastore")) {
      if (method.equals("RunQuery")) {
        return executeRunQuery(request.build());
      } else if (method.equals("Transaction")) {
        return executeTx(request.build());
      } else if (method.equals("TransactionQuery")) {
        return executeTxQuery(request.build());
      } else if (method.equals("GetIDs")) {
        return executeGetIDs(request.build(), false);
      } else if (method.equals("GetIDsXG")) {
        return executeGetIDs(request.build(), true);
      } else {
        throw new ApiProxy.CallNotFoundException(service, method);
      }
    } else {
      return ApiProxy.makeSyncCall(service, method, request.build().toByteArray());
    }
  }

  // Datastore utility functions.

  private static byte[] beginTransaction(boolean allowMultipleEg) {
    String appId = ApiProxy.getCurrentEnvironment().getAppId();
    byte[] req = BeginTransactionRequest.newBuilder().setApp(appId)
        .setAllowMultipleEg(allowMultipleEg).build().toByteArray();
    return ApiProxy.makeSyncCall("datastore_v3", "BeginTransaction", req);
  }

  private static void rollback(byte[] tx) {
    ApiProxy.makeSyncCall("datastore_v3", "Rollback", tx);
  }

  private static GetResponse.Builder txGet(byte[] tx, GetRequest.Builder request) {
    parseFromBytes(request.getTransaction(), tx);
    GetResponse.Builder response = GetResponse.newBuilder();
    byte[] resultBytes = ApiProxy.makeSyncCall("datastore_v3", "Get", request.build().toByteArray());
    parseFromBytes(response.build(), resultBytes);
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
          CONCURRENT_TRANSACTION.getNumber(), "Transaction precondition could not be computed");
    }

    md.update(bytes, 0, length);
    return md.digest();
  }

  private static void parseFromBytes(Message message, byte[] bytes) {
    boolean parsed = message.parseFrom(bytes);
    checkParse(message, parsed);
  }

  private static void parseFromInputStream(Message message, InputStream inputStream) {
    boolean parsed = message.parseFrom(inputStream);
    checkParse(message, parsed);
  }

  private static void checkParse(Message message, boolean parsed) {
    if (!parsed) {
      throw new ApiProxy.ApiProxyException("Could not parse protobuf");
    }
    List<String> error = message.findInitializationErrors();
    if (error != null && !error.isEmpty()) {
      throw new ApiProxy.ApiProxyException("Could not parse protobuf: " + error);
    }
  }
}
