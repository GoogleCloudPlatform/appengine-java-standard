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

package com.google.appengine.api.urlfetch.dev;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.appengine.api.urlfetch.URLFetchServicePb.URLFetchRequest;
import com.google.appengine.api.urlfetch.URLFetchServicePb.URLFetchRequest.RequestMethod;
import com.google.appengine.api.urlfetch.URLFetchServicePb.URLFetchResponse;
import com.google.appengine.api.urlfetch.URLFetchServicePb.URLFetchServiceError.ErrorCode;
import com.google.appengine.tools.development.LocalRpcService;
import com.google.apphosting.api.ApiProxy;
import com.google.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests that the local URLFetch implementation properly handles cert validation.
 *
 */
@RunWith(JUnit4.class)
public class LocalURLFetchCertValidationTest {

  private LocalURLFetchService fetchService;

  // A URL that will be considered as invalid.
  // TODO - fix this to not rely on behavior of live certificates which
  // may change over time.
  private String urlWithInvalidCert = "https://1.1.appengine.google.com/";

  @Before
  public void setUp() throws Exception {
    fetchService = new LocalURLFetchService();
    fetchService.init(null, ImmutableMap.of());
  }

  @Test
  public void testCertNotVerifiedWithoutAsking() throws Exception {
    LocalRpcService.Status status = new LocalRpcService.Status();
    URLFetchRequest request =
        URLFetchRequest.newBuilder()
            .setMethod(RequestMethod.GET)
            .setUrl(urlWithInvalidCert)
            .setMustValidateServerCertificate(false)
            .setFollowRedirects(false)
            .build();
    URLFetchResponse response = fetchService.fetch(status, request);
    assertThat(status.getErrorCode()).isEqualTo(ErrorCode.OK.getNumber());
    assertThat(response.getStatusCode()).isEqualTo(302);
  }

  @Test
  public void testCertVerifiedWithAsking() throws Exception {
    LocalRpcService.Status status = new LocalRpcService.Status();
    URLFetchRequest request =
        URLFetchRequest.newBuilder()
            .setMethod(RequestMethod.GET)
            .setUrl(urlWithInvalidCert)
            .setMustValidateServerCertificate(true)
            .setFollowRedirects(false)
            .build();
    ApiProxy.ApplicationException ae =
        assertThrows(
            ApiProxy.ApplicationException.class, () -> fetchService.fetch(status, request));
    assertThat(ae.getApplicationError()).isEqualTo(ErrorCode.SSL_CERTIFICATE_ERROR.getNumber());
  }

  @Test
  public void testCertNotVerifiedByDefault() throws Exception {
    LocalRpcService.Status status = new LocalRpcService.Status();
    URLFetchRequest.Builder request =
        URLFetchRequest.newBuilder()
            .setMethod(RequestMethod.GET)
            .setUrl(urlWithInvalidCert)
            .setFollowRedirects(false);
    // TODO Default behavior reverted to not validating cert for 1.4.2
    // CP due to wildcard cert validation problems. Revert for 1.4.3 after we
    // fix wildcard cert validation.
    // try {
    fetchService.fetch(status, request.build());
    //  fail("Expected SSL_CERTIFICATE_ERROR");
    // } catch (ApiProxy.ApplicationException ae) {
    //  assertThat(ae.getApplicationError()).isEqualTo(ErrorCode.SSL_CERTIFICATE_ERROR.getNumber());
    // }
  }

  // Make sure that state is preserved even if we alternate between requesting
  // verification and not on the same URL.
  @Test
  public void testAlternating() throws Exception {
    testCertNotVerifiedWithoutAsking();
    testCertVerifiedWithAsking();
    testCertNotVerifiedWithoutAsking();
    testCertVerifiedWithAsking();
  }

  @Test
  public void testCertVerified() throws Exception {
    LocalRpcService.Status status = new LocalRpcService.Status();
    URLFetchRequest.Builder request =
        URLFetchRequest.newBuilder()
            .setMethod(RequestMethod.GET)
            .setUrl("https://appengine.google.com/")
            .setFollowRedirects(false);
    fetchService.fetch(status, request.build());
  }
}
