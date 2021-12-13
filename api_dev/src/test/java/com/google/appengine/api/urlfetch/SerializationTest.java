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

package com.google.appengine.api.urlfetch;

import com.google.appengine.api.testing.SerializationTestBase;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 */
@RunWith(JUnit4.class)
public class SerializationTest extends SerializationTestBase {

  @Override
  protected Iterable<Serializable> getCanonicalObjects() {
    try {
      return Lists.newArrayList(
          new InternalTransientException("boom"),
          new RequestPayloadTooLargeException("boom"),
          new ResponseTooLargeException("boom"),
          HTTPMethod.GET,
          new HTTPRequest(new URL("http://www.google.com"), HTTPMethod.GET),
          new HTTPResponse(33, new byte[] {10, 20}, new URL("http://www.google.com"),
              Collections.singletonList(new HTTPHeader("this", "that"))),
          new HTTPHeader("this", "that"),
          FetchOptions.Builder.withDefaults(),
          FetchOptions.CertificateValidationBehavior.VALIDATE);
    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  protected Class<?> getClassInApiJar() {
    return URLFetchService.class;
  }

  /**
   * Instructions for generating new golden files are in the BUILD file in this
   * directory.
   */
  public static void main(String[] args) throws IOException {
    SerializationTest st = new SerializationTest();
    st.writeCanonicalObjects();
  }
}
