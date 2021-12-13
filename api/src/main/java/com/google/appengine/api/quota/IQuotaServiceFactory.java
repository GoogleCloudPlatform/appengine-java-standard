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

package com.google.appengine.api.quota;

/**
 * The factory by which users acquire a handle to the QuotaService.
 *
 */
public interface IQuotaServiceFactory {

  /**
   * Gets a handle to the quota service.  Note that the quota service
   * always exists, regardless of how many of its features are supported
   * by a particular app server. If a particular feature (like
   * {@link QuotaService#getApiTimeInMegaCycles()}) is not accessible, the
   * the instance will not be able to provide that feature and throw an
   * appropriate exception.
   *
   * @return a {@code QuotaService} instance.
   */
   QuotaService getQuotaService();

}
