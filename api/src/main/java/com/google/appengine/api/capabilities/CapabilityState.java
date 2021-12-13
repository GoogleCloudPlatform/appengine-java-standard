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

package com.google.appengine.api.capabilities;

import java.util.Date;

/**
 * Represents the state of a {@link Capability}.
 * 
 * <p>
 * The state of a capability is valid at a particular point in time.
 * 
 * If a particular capability is enabled at time T, there is no guarantee as to
 * if it will be available at time T+1. When a maintenance period is scheduled,
 * there will be usually advance notice as to when the capability is disabled.
 * 
 * 
 */

public class CapabilityState {

  private final Capability capability;
  private final CapabilityStatus status;

  CapabilityState(Capability capability, CapabilityStatus status) {
    this.capability = capability;
    this.status = status;
  }

  /**
   * Returns the capability associated with this {@link CapabilityState}.
   * 
   * @return the capability associated with this {@link CapabilityState}.
   */
  public Capability getCapability() {
    return capability;
  }

  /**
   * Returns the status of the capability.
   * 
   * @return the status of the capability.
   */
  public CapabilityStatus getStatus() {
    return status;
  }

  /**
   * Returns the schedule date of maintenance for this activity.
   * 
   * This call will return a {@link Date} instance if and only if the status is
   * SCHEDULED_MAINTENANCE.
   * 
   * @return the schedule maintenance date for this activity or
   *         <code>null</code> if no maintenance is planned.
   */
  public Date getScheduledDate() {
    return null;
  }

}
