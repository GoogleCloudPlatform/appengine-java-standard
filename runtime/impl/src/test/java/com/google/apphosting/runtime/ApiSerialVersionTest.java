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

import com.google.apphosting.testing.SerialVersionVerifier;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Scans the API jar for classes meeting the following conditions:
 * <ul>
 * <li>its name starts with {@code com.google.appengine.api} or {@code com.google.apphosting.api};
 * <li>it is public;
 * <li>it is {@link java.io.Serializable Serializable};
 * <li>it is not an enum;
 * <li>it is not abstract.
 * </ul>
 * For each such class, the test checks that it defines a <a
 * href="http://docs.oracle.com/javase/8/docs/platform/serialization/spec/class.html#a4100">
 * {@code serialVersionUID}</a>.
 *
 * <p>The API jar is scanned because it is in the {@code runtime_deps} of this test. Other
 * dependencies of the test will be scanned too but they won't have any effect since they don't
 * define classes matching the name pattern.
 */
@RunWith(JUnit4.class)
public class ApiSerialVersionTest {
  @Test
  public void serialApiClassesHaveSerialVersionUID() {
    Predicate<CharSequence> apiClass = s -> API_PATTERN.matcher(s).find();
    ClassLoader myLoader = getClass().getClassLoader();
    SerialVersionVerifier.verify(myLoader, apiClass);
  }

  private static final Pattern API_PATTERN =
      Pattern.compile("^com\\.google\\.app(engine|hosting)\\.api\\.");
}
