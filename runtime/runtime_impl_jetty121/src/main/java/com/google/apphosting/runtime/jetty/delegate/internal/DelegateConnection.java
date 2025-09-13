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

package com.google.apphosting.runtime.jetty.delegate.internal;

import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.runtime.jetty.delegate.DelegateConnector;
import com.google.apphosting.runtime.jetty.delegate.api.DelegateExchange;
import java.io.IOException;
import java.util.EventListener;
import java.util.concurrent.TimeoutException;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpScheme;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.server.ConnectionMetaData;
import org.eclipse.jetty.server.internal.HttpChannelState;
import org.eclipse.jetty.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DelegateConnection implements Connection {
  private static final Logger LOG = LoggerFactory.getLogger(DelegateConnection.class);

  private final DelegateConnector _connector;
  private final DelegateEndpoint _endpoint;
  private final String _connectionId;

  public DelegateConnection(DelegateConnector connector, DelegateEndpoint endpoint) {
    _connector = connector;
    _endpoint = endpoint;
    _connectionId = StringUtil.randomAlphaNumeric(16);
  }

  public String getId() {
    return _connectionId;
  }

  @Override
  public void addEventListener(EventListener listener) {}

  @Override
  public void removeEventListener(EventListener listener) {}

  @Override
  public void onOpen() {
    _endpoint.onOpen();
  }

  @Override
  public void onClose(Throwable cause) {}

  @Override
  public EndPoint getEndPoint() {
    return _endpoint;
  }

  @Override
  public void close() {
    _endpoint.close();
  }

  @Override
  public boolean onIdleExpired(TimeoutException timeoutException) {
    return false;
  }

  @Override
  public long getMessagesIn() {
    return 0;
  }

  @Override
  public long getMessagesOut() {
    return 0;
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
    return _endpoint.getCreatedTimeStamp();
  }

  public void handle() throws IOException {
    DelegateExchange delegateExchange = _endpoint.getDelegateExchange();
    if (LOG.isDebugEnabled()) LOG.debug("handling request {}", delegateExchange);

    try {
      // TODO: We want to recycle the channel instead of creating a new one every time.
      // TODO: Implement the NestedChannel with the top layers HttpChannel.
      ConnectionMetaData connectionMetaData =
          new DelegateConnectionMetadata(_endpoint, this, _connector);
      HttpChannelState httpChannel = new HttpChannelState(connectionMetaData);
      httpChannel.setHttpStream(new DelegateHttpStream(_endpoint, this, httpChannel));
      httpChannel.initialize();

      // Generate the Request MetaData.
      String method = delegateExchange.getMethod();
      HttpURI httpURI =
          HttpURI.build(delegateExchange.getRequestURI())
              .scheme(delegateExchange.isSecure() ? HttpScheme.HTTPS : HttpScheme.HTTP);
      HttpVersion httpVersion = HttpVersion.fromString(delegateExchange.getProtocol());
      HttpFields httpFields = delegateExchange.getHeaders();
      long contentLength =
          (httpFields == null) ? -1 : httpFields.getLongField(HttpHeader.CONTENT_LENGTH);
      MetaData.Request requestMetadata =
          new MetaData.Request(method, httpURI, httpVersion, httpFields, contentLength);

      // Invoke the HttpChannel.
      Runnable runnable = httpChannel.onRequest(requestMetadata);
      for (String name : delegateExchange.getAttributeNameSet()) {
        httpChannel.getRequest().setAttribute(name, delegateExchange.getAttribute(name));
      }
      if (LOG.isDebugEnabled()) LOG.debug("executing channel {}", httpChannel);

      ApiProxy.Environment currentEnvironment = ApiProxy.getCurrentEnvironment();
      _connector.run(
          () -> {
            try {
              ApiProxy.setEnvironmentForCurrentThread(currentEnvironment);
              runnable.run();
            } finally {
              ApiProxy.clearEnvironmentForCurrentThread();
            }
          });
    } catch (Throwable t) {
      _endpoint.getDelegateExchange().failed(t);
    }
  }
}
