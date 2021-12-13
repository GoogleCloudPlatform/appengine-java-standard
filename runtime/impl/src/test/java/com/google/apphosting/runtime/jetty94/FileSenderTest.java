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

package com.google.apphosting.runtime.jetty94;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.apphosting.utils.config.AppYaml;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.util.resource.Resource;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class FileSenderTest {

  private static final String FAKE_URL_PATH = "/fake_url";

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Mock private Resource mockResource;
  @Mock private ServletContext mockServletContext;

  // These mock objects are a bit fragile. It would be better to use fake HttpServletRequest and
  // HttpServletResponse objects. That would allow for setting headers with setHeader or addHeader
  // or retrieving them with getHeader or getDateHeader, for example.
  @Mock private HttpServletRequest mockRequest;
  @Mock private HttpServletResponse mockResponse;
  private AppYaml appYaml;
  private FileSender testInstance;

  @Before
  public void setUp() {
    appYaml = new AppYaml();
    testInstance = new FileSender(appYaml);
  }

  @Test
  public void shouldAddBasicHeaders_noAppYaml() throws Exception {
    when(mockResource.length()).thenReturn(1L);
    when(mockServletContext.getMimeType(any())).thenReturn("fake_content_type");
    testInstance = new FileSender(/* appYaml= */ null);

    testInstance.sendData(
        mockServletContext, mockResponse, /* include= */ false, mockResource, FAKE_URL_PATH);

    verify(mockResponse).setContentType("fake_content_type");
    verify(mockResponse).setContentLength(1);
    verify(mockResponse).setHeader(HttpHeader.CACHE_CONTROL.asString(), "public, max-age=600");
    verify(mockResource, times(1)).writeTo(any(), eq(0L), eq(1L));
  }

  @Test
  public void shouldAddBasicHeaders_appYamlIncluded() throws Exception {
    AppYaml.Handler handler = new AppYaml.Handler();
    handler.setStatic_files("fake_static_files");
    handler.setUrl(FAKE_URL_PATH);
    handler.setExpiration("1d 2h 3m");
    Map<String, String> fakeHeaders = new HashMap<>();
    fakeHeaders.put("fake_name", "fake_value");
    handler.setHttp_headers(fakeHeaders);
    appYaml.setHandlers(Arrays.asList(handler));
    when(mockResource.length()).thenReturn(1L);

    testInstance.sendData(
        mockServletContext, mockResponse, /* include= */ false, mockResource, FAKE_URL_PATH);

    verify(mockResponse).setHeader(HttpHeader.CACHE_CONTROL.asString(), "public, max-age=93780");
    verify(mockResponse).addHeader("fake_name", "fake_value");
    verify(mockResource, times(1)).writeTo(any(), eq(0L), eq(1L));
  }

  @Test
  public void shouldNotAddBasicHeaders_appYamlIncluded() throws Exception {
    AppYaml.Handler handler = new AppYaml.Handler();
    handler.setStatic_files("fake_static_files");
    handler.setUrl(FAKE_URL_PATH);
    handler.setExpiration("1d 2h 3m");
    Map<String, String> fakeHeaders = new HashMap<>();
    fakeHeaders.put("fake_name", "fake_value");
    handler.setHttp_headers(fakeHeaders);
    appYaml.setHandlers(Arrays.asList(handler));
    when(mockResource.length()).thenReturn(1L);

    testInstance.sendData(
        mockServletContext,
        mockResponse,
        /* include= */ false,
        mockResource,
        "/different_url_path");

    verify(mockResponse, never())
        .setHeader(HttpHeader.CACHE_CONTROL.asString(), "public, max-age=93780");
    verify(mockResponse, never()).addHeader("fake_name", "fake_value");
    verify(mockResource, times(1)).writeTo(any(), eq(0L), eq(1L));
  }

  @Test
  public void checkIfUnmodified_requestMethodHead() throws Exception {
    when(mockRequest.getMethod()).thenReturn(HttpMethod.HEAD.asString());

    assertThat(testInstance.checkIfUnmodified(mockRequest, mockResponse, mockResource)).isFalse();
  }

  @Test
  public void checkIfUnmodified_validHeaders() throws Exception {
    when(mockRequest.getMethod()).thenReturn(HttpMethod.GET.asString());
    when(mockRequest.getHeader(HttpHeader.IF_MODIFIED_SINCE.asString()))
        .thenReturn("Thu, 1 Jan 1970 00:00:00 GMT");
    when(mockRequest.getDateHeader(HttpHeader.IF_MODIFIED_SINCE.asString())).thenReturn(0L);
    when(mockRequest.getHeader(HttpHeader.IF_UNMODIFIED_SINCE.asString()))
        .thenReturn("Thu, 1 Jan 1970 00:00:01 GMT");
    when(mockRequest.getDateHeader(HttpHeader.IF_UNMODIFIED_SINCE.asString())).thenReturn(1000L);
    when(mockResource.lastModified()).thenReturn(100L);

    assertThat(testInstance.checkIfUnmodified(mockRequest, mockResponse, mockResource)).isFalse();
  }

  @Test
  public void checkIfUnmodified_headerModifedGreaterThanResource() throws Exception {
    when(mockRequest.getMethod()).thenReturn(HttpMethod.GET.asString());
    when(mockRequest.getHeader(HttpHeader.IF_MODIFIED_SINCE.asString()))
        .thenReturn("Thu, 1 Jan 1970 00:00:01 GMT");
    when(mockRequest.getDateHeader(HttpHeader.IF_MODIFIED_SINCE.asString())).thenReturn(1000L);
    when(mockResource.lastModified()).thenReturn(100L);

    assertThat(testInstance.checkIfUnmodified(mockRequest, mockResponse, mockResource)).isTrue();
  }

  @Test
  public void checkIfUnmodified_headerUnmodifedLessThanResource() throws Exception {
    when(mockRequest.getMethod()).thenReturn(HttpMethod.GET.asString());
    when(mockRequest.getHeader(HttpHeader.IF_MODIFIED_SINCE.asString()))
        .thenReturn("Thu, 1 Jan 1970 00:00:00 GMT");
    when(mockRequest.getDateHeader(HttpHeader.IF_MODIFIED_SINCE.asString())).thenReturn(0L);
    when(mockRequest.getHeader(HttpHeader.IF_UNMODIFIED_SINCE.asString()))
        .thenReturn("Thu, 1 Jan 1970 00:00:00 GMT");
    when(mockRequest.getDateHeader(HttpHeader.IF_UNMODIFIED_SINCE.asString())).thenReturn(0L);
    when(mockResource.lastModified()).thenReturn(100L);

    assertThat(testInstance.checkIfUnmodified(mockRequest, mockResponse, mockResource)).isTrue();
  }
}
