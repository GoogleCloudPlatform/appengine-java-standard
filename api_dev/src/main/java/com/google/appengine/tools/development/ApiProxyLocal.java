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

package com.google.appengine.tools.development;

import com.google.apphosting.api.ApiProxy.Delegate;
import com.google.apphosting.api.ApiProxy.Environment;
import java.util.Map;

/**
 * A local service object, suitable for testing of service-client code as
 * well as for local runtime use in {@code dev_appserver}.
 *
 */
public interface ApiProxyLocal extends Delegate<Environment> {
  /**
   * Sets an individual service property.
   * @param property name of the property to set
   * @param value new value of the property
   */
  void setProperty(String property, String value);

  /**
   * Resets the service properties to {@code properties}.
   *
   * @param properties a maybe {@code null} set of properties for
   * local services.
   */
  void setProperties(Map<String,String> properties);

  /**
   * Appends the given service properties to {@code properties}.
   *
   * @param properties a set of properties to append for local services.
   */
  void appendProperties(Map<String,String> properties);

  // TODO When we fix DevAppServer to support hot redeployment,
  // it <b>MUST</b> call into {@code stop} when it is attempting to
  // GC a webapp (otherwise background threads won't be stopped, etc...)
  /**
   * Stops all services started by this ApiProxy and releases
   * all of its resources.
   */
  void stop();

  /**
   * Get the {@code LocalRpcService} identified by the given package.
   * This method should really only be used by tests.
   *
   * @param pkg The package identifying the service we want.
   * @return The requested service, or {@code null} if no service
   * identified by the given package is available.
   */
  LocalRpcService getService(String pkg);

  /**
   * @return The clock with which all local services are initialized.  Local
   * services use the clock to determine the current time.
   */
  Clock getClock();

  /**
   * Sets the clock with which all local services are initialized.
   * Local services use the clock to determine the current time.
   * Note that calling this method after a local service has already
   * initialized will have no effect.
   *
   * @param clock
   */
  void setClock(Clock clock);
}
