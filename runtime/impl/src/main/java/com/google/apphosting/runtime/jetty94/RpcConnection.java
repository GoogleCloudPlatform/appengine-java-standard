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

package com.google.apphosting.runtime.jetty94;

import com.google.apphosting.base.AppVersionKey;
import com.google.apphosting.base.protos.HttpPb.HttpRequest;
import com.google.apphosting.base.protos.HttpPb.ParsedHttpHeader;
import com.google.apphosting.base.protos.RuntimePb.UPRequest;
import com.google.apphosting.base.protos.RuntimePb.UPResponse;
import com.google.apphosting.runtime.MutableUpResponse;
import com.google.apphosting.runtime.jetty9.JettyConstants;
import com.google.apphosting.runtime.jetty9.RpcEndPoint;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import org.eclipse.jetty.http.BadMessageException;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpScheme;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.server.HttpChannel;
import org.eclipse.jetty.server.HttpInput.Content;
import org.eclipse.jetty.server.HttpTransport;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.webapp.WebAppContext;

/**
 * A custom version of HttpConnection that uses UPRequestParser and
 * UPResponseGenerator instead of the standard HTTP stream parser and
 * generator.
 */
public class RpcConnection implements Connection, HttpTransport {

  // This should be kept in sync with HTTPProto::X_GOOGLE_INTERNAL_SKIPADMINCHECK.
  private static final String X_GOOGLE_INTERNAL_SKIPADMINCHECK = "X-Google-Internal-SkipAdminCheck";

  // Keep in sync with com.google.apphosting.utils.jetty.AppEngineAuthentication.
  private static final String SKIP_ADMIN_CHECK_ATTR =
      "com.google.apphosting.internal.SkipAdminCheck";

  static final String ASYNC_ENABLE_PPROPERTY = "com.google.appengine.enable_async";
  static final boolean NORMALIZE_INET_ADDR =
      Boolean.parseBoolean(
          System.getProperty(
              "com.google.appengine.nomalize_inet_addr",
              Boolean.toString(!"java8".equals(System.getenv("GAE_RUNTIME")))));

  private final List<Listener> listeners = new CopyOnWriteArrayList<>();
  private final RpcConnector connector;
  private final RpcEndPoint endPoint;
  private final MutableUpResponse upResponse;
  private ByteString aggregate = ByteString.EMPTY;
  private volatile Throwable abortedError;

  public RpcConnection(RpcConnector connector, RpcEndPoint endPoint) {
    this.connector = connector;
    this.endPoint = endPoint;
    this.upResponse = endPoint.getUpResponse();
  }

  @Override
  public void addListener(Listener listener) {
    listeners.add(listener);
  }

  @Override
  public void onOpen() {
    for (Listener listener : listeners) {
      listener.onOpened(this);
    }
  }

  @Override
  public void onClose() {
    for (Listener listener : listeners) {
      listener.onClosed(this);
    }
  }

  @Override
  public EndPoint getEndPoint() {
    return endPoint;
  }

  @Override
  public void close() {
    endPoint.close();
  }

  @Override
  public long getMessagesIn() {
    return 1;
  }

  @Override
  public long getMessagesOut() {
    return 1;
  }

  @Override
  public long getBytesIn() {
    return 0;
  }

  @Override
  public long getBytesOut() {
    return 0;
  }

  @Override
  public long getCreatedTimeStamp() {
    return endPoint.getCreatedTimeStamp();
  }

  public void handle(final AppVersionKey appVersionKey) throws ServletException, IOException {

    HttpRequest rpc = endPoint.getUpRequest().getRequest();
    final byte[] postdata = rpc.getPostdata().toByteArray();
    final CountDownLatch blockEndRequest = new CountDownLatch(1);

    HttpChannel channel =
        new HttpChannel(connector, connector.getHttpConfiguration(), endPoint, this) {
          @Override
          protected void handleException(Throwable th) {
            boolean requestWasCommitted = isCommitted();
            super.handleException(th);
            if (requestWasCommitted) {
              // The response was already committed before Jetty handled the exception.
              // In order to preserve Jetty 6 behavior we clear out the
              // attribute that holds the exception to avoid rethrowing it further.
              getRequest().removeAttribute(RequestDispatcher.ERROR_EXCEPTION);
            }
            blockEndRequest.countDown();
          }

          @Override
          protected String formatAddrOrHost(String addr) {
            return NORMALIZE_INET_ADDR ? super.formatAddrOrHost(addr) : addr;
          }

          @Override
          public void onCompleted() {
            super.onCompleted();
            blockEndRequest.countDown();
          }
        };

    Request request = channel.getRequest();

    // Enable async via a property
    request.setAsyncSupported(Boolean.getBoolean(ASYNC_ENABLE_PPROPERTY), null);

    // pretend to parse the request line

    // LEGACY_MODE is case insensitive for known methods
    HttpMethod method = RpcConnector.LEGACY_MODE
            ? HttpMethod.INSENSITIVE_CACHE.get(rpc.getProtocol())
            : HttpMethod.CACHE.get(rpc.getProtocol());
    String methodS = method != null ? method.asString() : rpc.getProtocol();

    try {
      String url = rpc.getUrl();
      HttpURI uri = new HttpURI(url);

      HttpVersion version = HttpVersion.CACHE.getBest(rpc.getHttpVersion());
      MetaData.Request requestData =
          new MetaData.Request(
              methodS, uri, version, new HttpFields(), postdata == null ? -1 : postdata.length);

      // pretend to parse the header fields
      boolean contentLength = false;

      for (ParsedHttpHeader header : rpc.getHeadersList()) {
        HttpField field = getField(header);

        // Handle LegacyMode Headers
        if (RpcConnector.LEGACY_MODE && field.getHeader() != null) {
          switch (field.getHeader()) {
            case CONTENT_ENCODING:
              continue;
            case CONTENT_LENGTH:
              if (contentLength) {
                throw new BadMessageException("Duplicate Content-Length");
              }
              contentLength = true;
              break;
            default:
              break;
          }
        }

        requestData.getFields().add(field);
      }
      // end of headers. This should return true to indicate that we are good to continue handling
      channel.onRequest(requestData);
      // is this SSL
      if (rpc.getIsHttps()) {
        // the following code has to be done after the channel.onRequest(requestData) call
        // to avoid NPE.
        request.setScheme(HttpScheme.HTTPS.asString());
        request.setSecure(true);
      }

      // signal the end of the request
      channel.onRequestComplete();

      // Give the input any post content.
      if (postdata != null) {
        channel.getRequest().getHttpInput().addContent(new Content(BufferUtil.toBuffer(postdata)));
      }
    } catch (Exception t) {
      // Any exception at this stage is most likely due to a bad message
      // We cannot use response.sendError as it needs a validly initiated channel to work.
      upResponse.setHttpResponseCodeAndResponse(400, "");
      channel.getResponse().setStatus(400);
      return;
    }

    // Tell AppVersionHandlerMap which app version should handle this
    // request.
    request.setAttribute(JettyConstants.APP_VERSION_KEY_REQUEST_ATTR, appVersionKey);

    final boolean skipAdmin = hasSkipAdminCheck(endPoint.getUpRequest());
    // Translate the X-Google-Internal-SkipAdminCheck to a servlet attribute.
    if (skipAdmin) {
      request.setAttribute(SKIP_ADMIN_CHECK_ATTR, true);

      // N.B.: If SkipAdminCheck is set, we're actually lying
      // to Jetty here to tell it that HTTPS is in use when it may not
      // be.  This is useful because we want to bypass Jetty's
      // transport-guarantee checks (to match Python, which bypasses
      // handler_security: for these requests), but unlike
      // authentication SecurityHandler does not provide an easy way to
      // plug in custom logic here.  I do not believe that our lie is
      // user-visible (ServletRequest.getProtocol() is unchanged).
      request.setSecure(true);
    }

    Throwable exception = null;
    try {
      // This will invoke a servlet and mutate upResponse before returning.
      channel.handle();
      waitforAsyncDone(blockEndRequest);

      // If an exception occurred while running GenericServlet.service,
      // this attribute will either be thrown or set as an attribute for the WebAppContext's
      // ErrorHandler to be invoked.
      exception = (Throwable) request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
    } catch (Exception ex) {
      exception = ex;
    }

    // TODO(b/263341977) this is a correct behavior, but customers depend on this bug, so we
    // enable it only for non java8 runtimes.
    if ((exception == null)
        && (abortedError != null)
        && !"java8".equals(System.getenv("GAE_RUNTIME"))) {
        exception = abortedError;
      }


    if (exception != null) {
      Throwable cause = unwrap(exception);
      if (cause instanceof BadMessageException) {
        // Jetty bad messages exceptions are handled here to prevent
        // 4xx client issues being signalled as 5xx server issues
        BadMessageException bme = (BadMessageException) cause;
        upResponse.clearHttpResponse();
        upResponse.setError(UPResponse.ERROR.OK_VALUE);
        upResponse.setHttpResponseCode(bme.getCode());
        upResponse.setErrorMessage(bme.getReason());
      } else if (!hasExceptionHandledByErrorPage(request)) {
        // We will most likely have set something here, but the
        // AppServer will only do the right thing (print stack traces
        // for admins, generic Prometheus error message others) if this
        // is completely unset.
        upResponse.clearHttpResponse();

        if (exception instanceof ServletException) {
          throw (ServletException) exception;
        } else {
          throw new ServletException(exception);
        }
      }
    }
  }

  private static Throwable unwrap(Throwable th) {
    while (th instanceof ServletException && th.getCause() != null) {
      th = th.getCause();
    }
    return th;
  }

  private static void waitforAsyncDone(CountDownLatch blockEndRequest) {
    if (Boolean.getBoolean(ASYNC_ENABLE_PPROPERTY)) {
      try {
        /**
         * Note: such a wait means that the container is not really running in asynchronous mode as
         * a thread will be held while waiting.
         *
         * <p>If the application is using the async APIs for async IO, this will not be a big issue
         * as the request is fully available before handle and the response is entirely buffered.
         * Thus an application will never actually block on IO as data will always be available and
         * writes will always be possible.
         *
         * <p>However, if the application is going async to wait for a backend
         * server/database/webservice/etc. then the intention of using async is so that the thread
         * assigned can be used for other useful work. This wait will prevent this. Thus an feature
         * request could be opened on the RPC layer to provide a real async invocation mode, so that
         * this wait is not necessary.
         */
        blockEndRequest.await();
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
  }

  /**
   * Returns true if the X-Google-Internal-SkipAdminCheck header is
   * present.  This header is passed via the set of protected headers
   * that is made available to the runtime but not to user code.  Note
   * that like the AppServer code, we only check if the header is
   * present and not the value itself.
   */
  private static boolean hasSkipAdminCheck(UPRequest upRequest) {
    for (ParsedHttpHeader header : upRequest.getRuntimeHeadersList()) {
      if (Ascii.equalsIgnoreCase(X_GOOGLE_INTERNAL_SKIPADMINCHECK, header.getKey())) {
        return true;
      }
    }
    return false;
  }

  // Transform ParsedHttpHeader to HttpField.
  @VisibleForTesting
  static HttpField getField(ParsedHttpHeader header) {
    return new HttpField(header.getKey(), header.getValue());
  }

  /**
   * Check if the exception has been explicitly handled by an "error" page of the webapp.
   *
   * @return true iff the exception has already been handled by the "current" error page.
   */
  private static boolean hasExceptionHandledByErrorPage(Request servletRequest) {
    Object errorPage = servletRequest.getAttribute(WebAppContext.ERROR_PAGE);
    Object errorPageHandled =
        servletRequest.getAttribute(AppVersionHandlerFactory.ERROR_PAGE_HANDLED);
    return errorPage != null && errorPage.equals(errorPageHandled);
  }

  @Override
  public void send(
      MetaData.Response info,
      boolean head,
      ByteBuffer content,
      boolean lastContent,
      Callback callback) {

    if (info != null) {
      upResponse.setHttpResponseCode(info.getStatus());
      for (HttpField field : info.getFields()) {
        upResponse.addHttpOutputHeaders(ParsedHttpHeader.newBuilder()
            .setKey(field.getName())
            .setValue(field.getValue()));
      }
    }

    if (BufferUtil.hasContent(content)) {
      aggregate = aggregate.concat(ByteString.copyFrom(content));
    }
    callback.succeeded();
  }

  @Override
  public void onCompleted() {
    upResponse.setHttpResponseResponse(aggregate);
    aggregate = ByteString.EMPTY;
  }

  @Override
  public void abort(Throwable t) {
    abortedError = t;
    endPoint.close();
  }

  @Override
  public boolean isPushSupported() {
    return false;
  }

  @Override
  public void push(MetaData.Request rqst) {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public boolean isOptimizedForDirectBuffers() {
    return false;
  }

  @Override
  public boolean onIdleExpired() {
    return false;
  }

  @Override
  public void removeListener(Listener ll) {}
}
