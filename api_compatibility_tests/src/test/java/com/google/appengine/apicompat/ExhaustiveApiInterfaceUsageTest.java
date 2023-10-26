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

package com.google.appengine.apicompat;

import static com.google.appengine.apicompat.Utils.classes;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ExhaustiveApiInterfaceUsageTest {

  interface MyApiInterface {

  }

  @Test
  public void testGetApiClass() {
    class MyApiUsage extends ExhaustiveApiInterfaceUsage<MyApiInterface> {

      @Override
      protected Set<Class<?>> useApi(MyApiInterface obj) {
        return classes();
      }
    }

    assertEquals(MyApiInterface.class, new MyApiUsage().getApiClass());
  }

  static class MyApiClass {

  }

  @Test
  public void testNotAnInterface() {
    class MyApiUsage extends ExhaustiveApiInterfaceUsage<MyApiClass> {

      @Override
      protected Set<Class<?>> useApi(MyApiClass obj) {
        return classes();
      }
    }

    try {
      new MyApiUsage();
      fail("expected exception");
    } catch (IllegalArgumentException e) {
      // good
    }
  }
}
