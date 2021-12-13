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

import static com.google.common.truth.Truth.assertThat;

import com.google.appengine.api.capabilities.CapabilitiesPb.CapabilityConfig;
import com.google.appengine.api.capabilities.CapabilitiesPb.CapabilityConfig.Status;
import com.google.appengine.api.capabilities.CapabilityServicePb.IsEnabledRequest;
import com.google.appengine.api.capabilities.CapabilityServicePb.IsEnabledResponse;
import com.google.appengine.api.capabilities.CapabilityServicePb.IsEnabledResponse.SummaryStatus;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.ApiConfig;
import com.google.apphosting.api.ApiProxy.Environment;
import com.google.apphosting.api.ApiProxy.LogRecord;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Future;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.Mockito;

/**
 * Tests the {@link CapabilitiesServiceImpl} implementation. The Capabilities API no longer does
 * anything useful except for the specific case of querying whether datastore writes are enabled.
 * In every other case it reports {@code ENABLED} always. This test verifies that we don't actually
 * call the API except for the datastore-write case.
 * 
 */
@RunWith(Parameterized.class)
public class CapabilitiesServiceImplTest {
  @Before
  public void setUp() throws Exception {
    ApiProxy.Environment environment = Mockito.mock(ApiProxy.Environment.class);
    ApiProxy.setEnvironmentForCurrentThread(environment);
  }

  private final Capability capability;
  private final boolean datastoreWriteEnabled;

  public CapabilitiesServiceImplTest(Capability capability, boolean datastoreWriteEnabled) {
    this.capability = capability;
    this.datastoreWriteEnabled = datastoreWriteEnabled;
  }

  /**
   * Construct two sets of test parameters for every {@link Capability} constant, one where
   * {@link #datastoreWriteEnabled} is true and one where it is false.
   */
  @Parameters(name = "{0} datastoreWriteEnabled={1}")
  public static Collection<Object[]> parameters() throws ReflectiveOperationException {
    List<Object[]> params = new ArrayList<>();
    for (Field constant : Capability.class.getFields()) {
      if (Modifier.isStatic(constant.getModifiers())
          && constant.getType().equals(Capability.class)) {
        Capability capability = (Capability) constant.get(null);
        params.add(new Object[] {capability, false});
        params.add(new Object[] {capability, true});
      }
    }
    assertThat(params.size()).isGreaterThan(0);
    return params;
  }

  @Test
  public void testCapabilities() {
    ApiProxy.setDelegate(new FakeDelegate());
    CapabilitiesService service = CapabilitiesServiceFactory.getCapabilitiesService();
    CapabilityState state = service.getStatus(capability);
    assertThat(state.getCapability()).isEqualTo(capability);
    assertThat(state.getScheduledDate()).isNull();
    CapabilityStatus status =
        (capability.equals(Capability.DATASTORE_WRITE) && !datastoreWriteEnabled)
            ? CapabilityStatus.DISABLED
            : CapabilityStatus.ENABLED;
    assertThat(state.getStatus()).isEqualTo(status);
  }

  /**
   * Fake API implementation that only recognizes the Capabilities API, and then only if the
   * request is datastore_v3/write. In that case it returns {@code ENABLED} or {@code DISABLED}
   * depending on {@link #datastoreWriteEnabled}. In every other case it throws.
   */
  private class FakeDelegate implements ApiProxy.Delegate<ApiProxy.Environment> {
    @Override
    public byte[] makeSyncCall(
        Environment environment, String packageName, String methodName, byte[] request) {
      assertThat(packageName).isEqualTo(CapabilitiesServiceImpl.PACKAGE_NAME);
      assertThat(methodName).isEqualTo(CapabilitiesServiceImpl.METHOD_NAME);
      IsEnabledRequest isEnabledRequest;
      try {
        isEnabledRequest = IsEnabledRequest.parseFrom(request);
      } catch (Exception e) {
        throw new AssertionError(e);
      }
      // We only expect to be called for datastore_v3/write, so reject anything else.
      assertThat(isEnabledRequest.getPackage()).startsWith("datastore");
      assertThat(isEnabledRequest.getCapabilityList()).containsExactly("write");
      // Now reply ENABLED or DISABLED according as datastoreWriteEnabled is true or false.
      Status status;
      SummaryStatus summaryStatus;
      if (datastoreWriteEnabled) {
        status = Status.ENABLED;
        summaryStatus = SummaryStatus.ENABLED;
      } else {
        status = Status.DISABLED;
        summaryStatus = SummaryStatus.DISABLED;
      }
      IsEnabledResponse isEnabledResponse = IsEnabledResponse.newBuilder()
          .addConfig(
              CapabilityConfig.newBuilder()
                  .setCapability("write")
                  .setPackage(isEnabledRequest.getPackage())
                  .setStatus(status)
              .build())
          .setSummaryStatus(summaryStatus)
          .build();
      return isEnabledResponse.toByteArray();
    }

    @Override
    public Future<byte[]> makeAsyncCall(Environment environment, String packageName,
        String methodName, byte[] request, ApiConfig apiConfig) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void log(Environment environment, LogRecord record) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void flushLogs(Environment environment) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<Thread> getRequestThreads(Environment environment) {
      throw new UnsupportedOperationException();
    }
  }
}
