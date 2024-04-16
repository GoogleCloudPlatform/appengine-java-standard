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

import java.util.stream.Stream;

/**
 * This interface defines a set of operations required for a Request to be used by the Java Runtime.
 */
public interface RequestAPIData {
  String getObfuscatedGaiaId();

  String getUserOrganization();

  boolean getIsTrustedApp();

  boolean getTrusted();

  String getPeerUsername();

  String getSecurityLevel();

  Stream<HttpPb.ParsedHttpHeader> getHeadersList();

  boolean getIsOffline();

  long getGaiaId();

  String getAuthuser();

  String getGaiaSession();

  String getAppserverDatacenter();

  String getAppserverTaskBns();

  boolean hasEventIdHash();

  String getEventIdHash();

  boolean hasRequestLogId();

  String getRequestLogId();

  boolean hasDefaultVersionHostname();

  String getDefaultVersionHostname();

  String getAppId();

  String getModuleId();

  String getModuleVersionId();

  boolean getIsAdmin();

  String getEmail();

  String getAuthDomain();

  String getSecurityTicket();

  boolean hasTraceContext();

  TracePb.TraceContextProto getTraceContext();

  String getUrl();

  RuntimePb.UPRequest.RequestType getRequestType();
}
