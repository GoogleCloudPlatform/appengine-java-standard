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

package com.google.apphosting.base;

import com.google.apphosting.base.protos.AppinfoPb.AppInfo;
import com.google.apphosting.base.protos.RuntimePb.UPRequest;
import javax.annotation.concurrent.Immutable;

/**
 * A simple immutable data container class that identifies a single
 * version of specific application.
 *
 * <p>Static factory methods are provided to create AppVersionKey's from
 * AppInfo and UPRequests.
 *
 */
@Immutable
public record AppVersionKey(String appId, String versionId) {
  public static AppVersionKey fromAppInfo(AppInfo appInfo) {
    return of(appInfo.getAppId(), appInfo.getVersionId());
  }

  public static AppVersionKey fromUpRequest(UPRequest request) {
    return of(request.getAppId(), request.getVersionId());
  }

  public static AppVersionKey of(String appId, String versionId) {
    return new AppVersionKey(appId, versionId);
  }

  public String getAppId() {
    return appId();
  }

  public String getVersionId() {
    return versionId();
  }

  @Override
  public final String toString() {
    return appId() + "/" + versionId();
  }
}
