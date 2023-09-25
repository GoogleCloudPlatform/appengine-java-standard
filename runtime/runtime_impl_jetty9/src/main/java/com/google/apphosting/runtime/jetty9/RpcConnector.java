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

package com.google.apphosting.runtime.jetty9;

import com.google.apphosting.base.AppVersionKey;
import com.google.apphosting.base.protos.RuntimePb.UPRequest;
import com.google.apphosting.runtime.MutableUpResponse;
import com.google.apphosting.runtime.jetty9.RpcEndPoint;
import java.io.IOException;
import javax.servlet.ServletException;
import org.eclipse.jetty.http.CookieCompliance;
import org.eclipse.jetty.http.HttpCompliance;
import org.eclipse.jetty.server.AbstractConnector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.MultiPartFormDataCompliance;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;

/**
 * {@code RpcConnector} is an {@link AbstractConnector} that essentially does nothing.  In
 * particular, it does not open a local socket and does not start any background threads for
 * accepting new connections.
 *
 * <p>It exists primarily to satisfy various low-level Jetty components that expect each {@code
 * Connection} to have a {@code Connector} and for them to share an {@code EndPoint}.
 *
 * <p>This {@link AbstractConnector} has no intrinsic transport guarantees.  Instead, it checks the
 * scheme of each {@link Request} to determine whether HTTPS was used, and if so, indicates that
 * both integrity and confidentiality are guaranteed.
 *
 * <p>This class is loosely based on {@link org.mortbay.jetty.LocalConnector}, but we don't extend
 * it because it still does some things that we don't want (e.g. accepting connections).
 *
 */
public class RpcConnector extends AbstractConnector {

  private final HttpConfiguration httpConfiguration = new HttpConfiguration();

  /**
   * If Legacy Mode is tunred on, then Jetty is configured to be more forgiving of bad requests
   * and to act more in the style of Jetty-9.3
   */
  // Keep this public property name, do not change to jetty9 as it is public contract.
  static final boolean LEGACY_MODE =
      Boolean.getBoolean("com.google.apphosting.runtime.jetty94.LEGACY_MODE"); // Keep 94 name.

  public RpcConnector(Server server) {
    super(server, null, null, null, 0, new RpcConnectionFactory());

    addBean(HttpCompliance.RFC7230);
    if (LEGACY_MODE) {
      httpConfiguration.setRequestCookieCompliance(CookieCompliance.RFC2965);
      httpConfiguration.setResponseCookieCompliance(CookieCompliance.RFC2965);
      httpConfiguration.setMultiPartFormDataCompliance(MultiPartFormDataCompliance.LEGACY);
    }
  }

  public HttpConfiguration getHttpConfiguration() {
    return httpConfiguration;
  }

  /**
   * @throws UnsupportedOperationException
   */
  @Override
  protected void accept(int acceptorID) throws IOException, InterruptedException {
    // Because we don't call AbstractConnector.doStart(), we won't spawn
    // acceptor threads and thus this method won't ever be called.
    throw new UnsupportedOperationException();
  }

  @Override
  public Object getTransport() {
    return null;
  }

  public void serviceRequest(
      AppVersionKey appVersionKey,
      UPRequest upRequest,
      MutableUpResponse upResponse)
      throws ServletException, IOException {
    RpcEndPoint endPoint = new RpcEndPoint(upRequest, upResponse);
    RpcConnection connection =
        (RpcConnection) getDefaultConnectionFactory().newConnection(this, endPoint);
    endPoint.setConnection(connection);
    connection.handle(appVersionKey);
  }
}
