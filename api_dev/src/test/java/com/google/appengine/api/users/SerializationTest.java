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

package com.google.appengine.api.users;

import com.google.appengine.api.testing.SerializationTestBase;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.io.Serializable;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 */
@RunWith(JUnit4.class)
public class SerializationTest extends SerializationTestBase {

  @Override
  protected Iterable<Serializable> getCanonicalObjects() {
    return ImmutableList.of(
        new UserServiceFailureException("boom"),
        new User("maxr@google.com", "google.com")
    );
  }

  @Override
  protected Class<?> getClassInApiJar() {
    return UserService.class;
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
