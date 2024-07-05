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

package com.google.apphosting.runtime;

import com.google.apphosting.base.protos.HttpPb;
import com.google.apphosting.base.protos.RuntimePb;
import com.google.apphosting.base.protos.TracePb;
import com.google.common.base.Ascii;
import java.util.stream.Stream;
import javax.annotation.Nullable;

public class UpRequestAPIData implements RequestAPIData {

  private final RuntimePb.UPRequest request;

  public UpRequestAPIData(RuntimePb.UPRequest request) {
    this.request = request;
  }

  @Override
  public String getObfuscatedGaiaId() {
    return request.getObfuscatedGaiaId();
  }

  @Override
  public String getUserOrganization() {
    return request.getUserOrganization();
  }

  @Override
  public boolean getIsTrustedApp() {
    return request.getIsTrustedApp();
  }

  @Override
  public String getPeerUsername() {
    return request.getPeerUsername();
  }

  @Override
  public String getSecurityLevel() {
    return request.getSecurityLevel();
  }

  @Override
  public Stream<HttpPb.ParsedHttpHeader> getHeadersList() {
    return request.getRequest().getHeadersList().stream();
  }

  @Override
  public boolean getTrusted() {
    return request.getRequest().getTrusted();
  }

  @Override
  public boolean getIsOffline() {
    return request.getRequest().getIsOffline();
  }

  @Override
  public long getGaiaId() {
    return request.getGaiaId();
  }

  @Override
  public String getAuthuser() {
    return request.getAuthuser();
  }

  @Override
  public String getGaiaSession() {
    return request.getGaiaSession();
  }

  @Override
  public String getAppserverDatacenter() {
    return request.getAppserverDatacenter();
  }

  @Override
  public String getAppserverTaskBns() {
    return request.getAppserverTaskBns();
  }

  @Override
  public boolean hasEventIdHash() {
    return request.hasEventIdHash();
  }

  @Override
  public String getEventIdHash() {
    return request.getEventIdHash();
  }

  @Override
  public boolean hasRequestLogId() {
    return request.hasRequestLogId();
  }

  @Override
  public String getRequestLogId() {
    return request.getRequestLogId();
  }

  @Override
  public boolean hasDefaultVersionHostname() {
    return request.hasDefaultVersionHostname();
  }

  @Override
  public String getDefaultVersionHostname() {
    return request.getDefaultVersionHostname();
  }

  @Override
  public String getAppId() {
    return request.getAppId();
  }

  @Override
  public String getModuleId() {
    return request.getModuleId();
  }

  @Override
  public String getModuleVersionId() {
    return request.getModuleVersionId();
  }

  @Override
  public boolean getIsAdmin() {
    return request.getIsAdmin();
  }

  @Override
  public String getEmail() {
    return request.getEmail();
  }

  @Override
  public String getAuthDomain() {
    return request.getAuthDomain();
  }

  @Override
  public String getSecurityTicket() {
    return request.getSecurityTicket();
  }

  @Override
  public boolean hasTraceContext() {
    return request.hasTraceContext();
  }

  @Override
  public TracePb.TraceContextProto getTraceContext() {
    return request.getTraceContext();
  }

  @Override
  public String getUrl() {
    return request.getRequest().getUrl();
  }

  @Override
  public RuntimePb.UPRequest.RequestType getRequestType() {
    return request.getRequestType();
  }

  @Override
  @Nullable
  public String getBackgroundRequestId() {
    for (HttpPb.ParsedHttpHeader header : request.getRequest().getHeadersList()) {
      if (Ascii.equalsIgnoreCase(
          header.getKey(), AppEngineConstants.X_APPENGINE_BACKGROUNDREQUEST)) {
        return header.getValue();
      }
    }
    return null;
  }
}
