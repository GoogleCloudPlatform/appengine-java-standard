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

import com.google.appengine.tools.development.InstanceStateHolder.InstanceState;
import junit.framework.TestCase;

/**
 * Unit test for {@link InstanceStateHolder}.
 */
public class InstanceStateHolderTest extends TestCase {
  private static final String SERVER_NAME = "server1";
  private static final int INSTANCE = 123;
  private InstanceStateHolder instanceStateHolder;

  @Override
  public void setUp() {
    instanceStateHolder = new InstanceStateHolder(SERVER_NAME, INSTANCE);
  }

  public void testInitial() {
    assertCurrentState(InstanceState.SHUTDOWN);
  }

  public void testTestAndSet() {
    instanceStateHolder.testAndSet(InstanceState.RUNNING,
        InstanceState.STOPPED, InstanceState.SHUTDOWN);
    assertCurrentState(InstanceState.RUNNING);
  }

  public void testTestAndSet_fail() {
    try {
      instanceStateHolder.testAndSet(InstanceState.RUNNING,
          InstanceState.STOPPED, InstanceState.RUNNING_START_REQUEST);
      fail();
    } catch (IllegalStateException ise) {
      assertTrue("Unexpected message: " + ise.getMessage(),
          ise.getMessage().startsWith("Tried to change state to"));
    }
    assertCurrentState(InstanceState.SHUTDOWN);
  }

  public void testAcceptsConnections_shutdown() {
    assertFalse(instanceStateHolder.acceptsConnections());
  }

  public void testAcceptsConnections_running() {
    instanceStateHolder.set(InstanceState.RUNNING);
    assertCurrentState(InstanceState.RUNNING);
    assertTrue(instanceStateHolder.acceptsConnections());
  }

  public void testAcceptsConnections_runningStartRequest() {
    instanceStateHolder.set(InstanceState.RUNNING_START_REQUEST);
    assertCurrentState(InstanceState.RUNNING_START_REQUEST);
    assertTrue(instanceStateHolder.acceptsConnections());
  }

  public void testAcceptsConnections_sleeping() {
    instanceStateHolder.set(InstanceState.SLEEPING);
    assertCurrentState(InstanceState.SLEEPING);
    assertTrue(instanceStateHolder.acceptsConnections());
  }

  private void assertCurrentState(InstanceState expect) {
    assertTrue(instanceStateHolder.test(expect));
    assertEquals(expect.name().toLowerCase(), instanceStateHolder.getDisplayName());
  }
}
