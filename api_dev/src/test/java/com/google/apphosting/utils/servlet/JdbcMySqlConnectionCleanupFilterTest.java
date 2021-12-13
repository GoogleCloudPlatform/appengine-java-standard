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

package com.google.apphosting.utils.servlet;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.apphosting.utils.servlet.JdbcMySqlConnectionCleanupFilter.AppEngineApiWrapper;
import com.google.apphosting.utils.servlet.JdbcMySqlConnectionCleanupFilter.ConnectionsCleanupWrapper;
import com.google.common.collect.Maps;
import java.util.Map;
import javax.servlet.FilterChain;
import junit.framework.TestCase;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

/** Provides tests for {@link JdbcMySqlConnectionCleanupFilter}. */
public class JdbcMySqlConnectionCleanupFilterTest extends TestCase {

  private static final String CLOUD_SQL_JDBC_CONNECTIVITY_ENABLED_KEY =
      "com.google.appengine.runtime.new_database_connectivity";

  private final Map<String, Object> fakeAttributeMap = Maps.newConcurrentMap();

  private JdbcMySqlConnectionCleanupFilter jdbcMySqlConnectionCleanupFilter;

  @Mock private AppEngineApiWrapper appEngineApiWrapper;
  @Mock private ConnectionsCleanupWrapper connectionsCleanupWrapper;

  @Mock private FilterChain chain;

  @Override
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    fakeAttributeMap.put(
        JdbcMySqlConnectionCleanupFilter.CLOUD_SQL_JDBC_CONNECTIVITY_ENABLED_KEY, true);
    when(appEngineApiWrapper.getRequestEnvironmentAttributes()).thenReturn(fakeAttributeMap);
    jdbcMySqlConnectionCleanupFilter =
        new JdbcMySqlConnectionCleanupFilter(appEngineApiWrapper, connectionsCleanupWrapper);
  }

  @Override
  public void tearDown() {
    fakeAttributeMap.clear();
  }

  public void testProcessAbandonedMySQLConnection_abandonedConnectionClosed() throws Exception {
    jdbcMySqlConnectionCleanupFilter.doFilter(null, null, chain);

    // Environment should be queried only once for the attribute map.
    verify(appEngineApiWrapper).getRequestEnvironmentAttributes();
    verify(connectionsCleanupWrapper).cleanup();
  }

  public void testCheckDuplicatedFeatureFlagKeyString_stringInSync() {
    assertEquals(
        JdbcMySqlConnectionCleanupFilter.CLOUD_SQL_JDBC_CONNECTIVITY_ENABLED_KEY,
        CLOUD_SQL_JDBC_CONNECTIVITY_ENABLED_KEY);
  }

  public void testCheckFeatureFlagNullOrNotBooleanOrFalse_filterActsAsNoOp() throws Exception {
    // Feature on/off flag is null.
    fakeAttributeMap.clear();

    when(appEngineApiWrapper.getRequestEnvironmentAttributes()).thenReturn(fakeAttributeMap);
    jdbcMySqlConnectionCleanupFilter.doFilter(null, null, chain);
    // Doesn't invokes cleanup on the AbandonedConnections class.
    verify(connectionsCleanupWrapper, times(0)).cleanup();

    Mockito.reset(appEngineApiWrapper);

    // Feature on/off flag is set to an object that is not an instance of Boolean.
    fakeAttributeMap.put(
        JdbcMySqlConnectionCleanupFilter.CLOUD_SQL_JDBC_CONNECTIVITY_ENABLED_KEY, "foo");
    when(appEngineApiWrapper.getRequestEnvironmentAttributes()).thenReturn(fakeAttributeMap);

    jdbcMySqlConnectionCleanupFilter.doFilter(null, null, chain);
    // Doesn't invokes cleanup on the AbandonedConnections class.
    verify(connectionsCleanupWrapper, times(0)).cleanup();

    Mockito.reset(appEngineApiWrapper);

    // Feature on/off flag is false.
    fakeAttributeMap.put(
        JdbcMySqlConnectionCleanupFilter.CLOUD_SQL_JDBC_CONNECTIVITY_ENABLED_KEY, false);
    when(appEngineApiWrapper.getRequestEnvironmentAttributes()).thenReturn(fakeAttributeMap);

    jdbcMySqlConnectionCleanupFilter.doFilter(null, null, chain);
    // Doesn't invokes cleanup on the AbandonedConnections class.
    verify(connectionsCleanupWrapper, times(0)).cleanup();
  }
}
