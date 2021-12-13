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
 * The {@code QuotaService} provides measurement of API and CPU usage
 * during requests.
 *
 */
public interface QuotaService {

  /**
   * Represents all types of data that a QuotaService might be able to provide.
   */
  enum DataType {
    API_TIME_IN_MEGACYCLES,
    CPU_TIME_IN_MEGACYCLES;
  }

  /**
   * Tests if the QuotaService can provide a certain kind of data at this
   * point in time. Depending on the underlying app server implementation and
   * what state it is in, a QuotaService might not always have access to all
   * categories of data. For example, the dev-appserver might not be able to
   * measure the megacycles of api calls. Trying to access that data would lead
   * to an IllegalStateException, which would then need to be handled. To make
   * it easier to prevent this, it is possible to ask the service if a
   * particular kind of data is supported.
   *
   * @param type the type of data in question.
   * @return true if the QuotaService can provide such data at this time.
   * @exception NullPointerException if a null argument is passed into
   *   the method.
   */
  boolean supports(DataType type);

  /**
   * @deprecated This value is no longer meaningful.
   * @return the overall amount spent in API cycles, as returned by the system.
   *   Returns 0 if the feature is not supported.
   */
  @Deprecated
  long getApiTimeInMegaCycles();

  /**
   * Measures the duration that the current request has spent so far processing
   * the request within the App Engine sandbox. Note that time spent in API
   * calls will not be added to this value.
   * <p>
   * The unit the duration is measured is Megacycles. If all instructions
   * were to be executed sequentially on a standard 1.2 GHz 64-bit x86 CPU,
   * 1200 megacycles would equate to one second physical time elapsed.
   *
   * @return the overall amount spent in CPU cycles, as returned by the system.
   *   Returns 0 if the feature is not supported.
   */
  long getCpuTimeInMegaCycles();

  /**
   * Expresses a value in megaCycles as its approximate equivalent of CPU
   * seconds on a theoretical 1.2 GHz CPU.
   *
   * @param megaCycles the value, in megacycles, to convert.
   * @return a double representing the CPU-seconds the input megacycle value
   *    converts to.
   */
  double convertMegacyclesToCpuSeconds(long megaCycles);

  /**
   * Expresses a value in megaCycles as its approximate equivalent of CPU
   * seconds on a theoretical 1.2 GHz CPU.
   *
   * @param cpuSeconds the value, in cpu seconds, to convert.
   * @return a long representing the megacycles the input CPU-seconds value
   *    converts to.
   */
  long convertCpuSecondsToMegacycles(double cpuSeconds);

}
