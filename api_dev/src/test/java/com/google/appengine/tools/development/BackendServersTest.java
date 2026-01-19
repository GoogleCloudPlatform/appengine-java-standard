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

import static com.google.common.truth.Truth.assertThat;

import com.google.appengine.tools.development.BackendServersBase.ServerInstanceEntry;
import java.util.HashSet;
import junit.framework.TestCase;

/** Test @code {@link BackendServersBase} */
public class BackendServersTest extends TestCase {

  public void testServerInstanceEntryHashCode() throws Exception{
    ServerInstanceEntry server1 = new ServerInstanceEntry("server1", 17);
    ServerInstanceEntry server1copy = new ServerInstanceEntry("server1", 17);
    ServerInstanceEntry server2 = new ServerInstanceEntry("server2", 17);
    
    assertThat(server1.hashCode()).isNotEqualTo(server2.hashCode());
    assertEquals(server1.hashCode(), server1copy.hashCode());    
  }
  
  public void testServerInstanceEntryEquals() throws Exception{
    ServerInstanceEntry server1 = new ServerInstanceEntry("server1", 17);
    ServerInstanceEntry server1copy = new ServerInstanceEntry("server1", 17);
    ServerInstanceEntry server2 = new ServerInstanceEntry("server2", 17);
    ServerInstanceEntry server3 = new ServerInstanceEntry(null, 17);
    ServerInstanceEntry server3copy = new ServerInstanceEntry(null, 17);
    
    assertEquals(server1, server1copy);
    assertFalse(server1.equals(server2));
    assertFalse(server2.equals(server3));
    assertEquals(server3, server3copy);
  }
  
  
  public void testServerInstanceIsHashSetCompatible() throws Exception{
    ServerInstanceEntry server1 = new ServerInstanceEntry("server1", 17);
    ServerInstanceEntry server1copy = new ServerInstanceEntry("server1", 17);    
    ServerInstanceEntry server2 = new ServerInstanceEntry("server2", 17);
    ServerInstanceEntry server3 = new ServerInstanceEntry("server1", 18);
    
    HashSet<ServerInstanceEntry> set = new HashSet<ServerInstanceEntry>();
    set.add(server1);
    assertThat(set).contains(server1copy);
    assertFalse(set.contains(server2));
    assertFalse(set.contains(server3));
    }
}
