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

package com.google.apphosting.runtime;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.net.URL;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests serial compatibility of critical runtime classes.
 */
@RunWith(JUnit4.class)
public class SerializationTest {
  private static final long FAKE_EXPIRATION_TIME = 123_456_789_012_345L;
  private static final ImmutableMap<String, Object> FAKE_VALUE_MAP = ImmutableMap.of(
      "foo", 23,
      "bar", BigInteger.valueOf(23));

  @Test
  public void sessionData() throws IOException, ClassNotFoundException {
    URL sessionDataResource = getClass().getResource("sessiondata.ser");
    assertThat(sessionDataResource).isNotNull();
    SessionData sessionData;
    try (
        InputStream inputStream = sessionDataResource.openStream();
        ObjectInputStream objectInputStream = new ObjectInputStream(inputStream)) {
      sessionData = (SessionData) objectInputStream.readObject();
    }
    assertThat(sessionData.getExpirationTime()).isEqualTo(FAKE_EXPIRATION_TIME);
    assertThat(sessionData.getValueMap()).containsExactlyEntriesIn(FAKE_VALUE_MAP);
  }

  /**
   * Generate the serial reference file. Use with the following commands from the google3/
   * directory:
   *
   * <pre>{@code
   * blaze build javatests/com/google/apphosting/runtime:SerializationTest_deploy.jar
   * java -cp blaze-bin/javatests/com/google/apphosting/runtime/SerializationTest_deploy.jar \
   *     com.google.apphosting.runtime.SerializationTest \
   *     >javatests/com/google/apphosting/runtime/sessiondata.ser
   * }</pre>
   */
  public static void main(String[] args) throws Exception {
    SessionData sessionData = new SessionData();
    sessionData.setExpirationTime(FAKE_EXPIRATION_TIME);
    Map<String, Object> valueMap = sessionData.getValueMap();
    valueMap.putAll(FAKE_VALUE_MAP);
    try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(System.out)) {
      objectOutputStream.writeObject(sessionData);
    }
  }
}
