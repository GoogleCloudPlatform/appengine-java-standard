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

package com.google.appengine.api.internal;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static org.junit.Assert.fail;

import com.google.appengine.api.ApiJarPath;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.ClassPath;
import com.google.common.reflect.ClassPath.ClassInfo;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Checks that instances of the repackaged {@code ImmutableList} class are recognized by {@link
 * Repackaged}, and that the other methods of {@code Repackaged} behave as expected.
 */
@RunWith(JUnit4.class)
public class RepackagedTest {
  @Test
  public void recognizeRepackagedSdk() throws Exception {
    checkRecognized(ApiJarPath.getFile());
  }

  @Test
  public void recognizeNotRepackaged() {
    List<List<String>> lists =
        Arrays.asList(
            ImmutableList.of(),
            ImmutableList.of("foo"),
            Arrays.asList("foo", "bar", "baz"),
            new ArrayList<>(Arrays.asList("buh", "blim")));
    for (List<String> list : lists) {
      assertThat(Repackaged.isRepackaged(list)).isFalse();
      assertThat(Repackaged.copyIfRepackagedElseOriginal(list)).isSameInstanceAs(list);
    }
  }

  @Test
  public void notRepackagedUnmodifiable() {
    List<List<String>> lists =
        Arrays.asList(
            ImmutableList.of(),
            ImmutableList.of("foo"),
            Arrays.asList("foo", "bar", "baz"),
            new ArrayList<>(Arrays.asList("buh", "blim")));
    for (List<String> list : lists) {
      assertUnmodifiable(Repackaged.copyIfRepackagedElseUnmodifiable(list));
    }
  }

  private static void checkRecognized(File apiJarFile)
      throws IOException, ReflectiveOperationException {
    assertWithMessage(apiJarFile.toString()).that(apiJarFile.exists()).isTrue();
    ClassLoader loader = new URLClassLoader(new URL[] {apiJarFile.toURI().toURL()}, null);
    ImmutableSet<ClassInfo> classInfos = ClassPath.from(loader).getAllClasses();
    List<ClassInfo> immutableListClasses = new ArrayList<>();
    for (ClassInfo classInfo : classInfos) {
      if (IMMUTABLE_LIST_PATTERN.matcher(classInfo.getName()).find()) {
        immutableListClasses.add(classInfo);
      }
    }
    assertThat(immutableListClasses).hasSize(1);
    Class<?> immutableListClass = immutableListClasses.get(0).load();
    assertThat(immutableListClass).isAssignableTo(List.class);
    check(immutableListClass, new Object[] {}, "of");
    check(immutableListClass, new Object[] {"foo"}, "of", Object.class);
    check(immutableListClass, new Object[] {Arrays.asList("foo")}, "copyOf", Collection.class);
  }

  private static final Pattern IMMUTABLE_LIST_PATTERN = Pattern.compile("\\.\\$?ImmutableList$");

  /**
   * Invokes the given static factory method from the repackaged {@code ImmutableList} class, via
   * reflection, to obtain a repackaged {@code ImmutableList} instance, then checks that {@link
   * Repackaged} identifies that instance as being of a repackaged class.
   */
  private static void check(
      Class<?> immutableListClass,
      Object[] actualParams,
      String methodName,
      Class<?>... formalParams)
      throws ReflectiveOperationException {
    Method factory = immutableListClass.getMethod(methodName, formalParams);
    @SuppressWarnings("unchecked")
    List<String> list = (List<String>) factory.invoke(null, actualParams);
    assertThat(Repackaged.isRepackaged(list)).isTrue();

    // Check that the copies have a null ClassLoader, meaning they're using a JRE core class,
    // and that they are unmodifiable.

    List<String> copy1 = Repackaged.copyIfRepackagedElseOriginal(list);
    assertThat(copy1.getClass().getClassLoader()).isNull();
    assertUnmodifiable(copy1);

    List<String> copy2 = Repackaged.copyIfRepackagedElseUnmodifiable(list);
    assertThat(copy2.getClass().getClassLoader()).isNull();
    assertUnmodifiable(copy2);
  }

  private static void assertUnmodifiable(List<String> list) {
    try {
      list.add("foo");
      fail("List is modifiable");
    } catch (UnsupportedOperationException expected) {
    }
  }
}
