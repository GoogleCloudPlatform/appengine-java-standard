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

package com.google.appengine.tools.compilation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Sets;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Properties;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DatastoreCallbacksConfigWriterTest {

  static <E extends Object> LinkedHashSet<E> newLinkedHashSet(E... elements) {
    LinkedHashSet<E> set = new LinkedHashSet<E>(elements.length + 1);
    Collections.addAll(set, elements);
    return set;
  }

  @Test
  public void testAddCallback() throws IOException {
    DatastoreCallbacksConfigWriter writer = new DatastoreCallbacksConfigWriter(null);

    writer.addCallback(newLinkedHashSet("yar1", "yar2"), "bla", "c1", "jam1");
    assertEquals(2, writer.callbacks.size());
    assertEquals(1, writer.callbacks.get("yar1").size());
    assertEquals(1, writer.callbacks.get("yar1").get("bla").size());
    assertEquals(Sets.newHashSet("c1:jam1"), writer.callbacks.get("yar1").get("bla"));
    assertEquals(1, writer.callbacks.get("yar2").size());
    assertEquals(1, writer.callbacks.get("yar2").get("bla").size());
    assertEquals(Sets.newHashSet("c1:jam1"), writer.callbacks.get("yar2").get("bla"));
    assertEquals(LinkedHashMultimap.create(ImmutableMultimap.of("c1", "jam1")),
        writer.methodsWithCallbacks);

    writer.addCallback(newLinkedHashSet("yar1", "yar2"), "bla", "c1", "jam2");
    assertEquals(2, writer.callbacks.size());
    assertEquals(2, writer.callbacks.get("yar1").size());
    assertEquals(2, writer.callbacks.get("yar1").get("bla").size());
    assertEquals(Sets.newHashSet("c1:jam1", "c1:jam2"), writer.callbacks.get("yar1").get("bla"));
    assertEquals(2, writer.callbacks.get("yar2").size());
    assertEquals(2, writer.callbacks.get("yar2").get("bla").size());
    assertEquals(Sets.newHashSet("c1:jam1", "c1:jam2"), writer.callbacks.get("yar2").get("bla"));
    assertEquals(LinkedHashMultimap.create(ImmutableMultimap.of("c1", "jam1", "c1", "jam2")),
        writer.methodsWithCallbacks);

    writer.addCallback(newLinkedHashSet("yar1", "yar2"), "blap", "c2", "a");
    assertEquals(2, writer.callbacks.size());
    assertEquals(3, writer.callbacks.get("yar1").size());
    assertEquals(1, writer.callbacks.get("yar1").get("blap").size());
    assertEquals(Sets.newHashSet("c2:a"), writer.callbacks.get("yar1").get("blap"));
    assertEquals(3, writer.callbacks.get("yar2").size());
    assertEquals(1, writer.callbacks.get("yar2").get("blap").size());
    assertEquals(Sets.newHashSet("c2:a"), writer.callbacks.get("yar2").get("blap"));
    assertEquals(LinkedHashMultimap.create(
        ImmutableMultimap.of("c1", "jam1", "c1", "jam2", "c2", "a")), writer.methodsWithCallbacks);

    writer.addCallback(newLinkedHashSet("yam1", "yam2"), "bla", "c2", "b");
    assertEquals(4, writer.callbacks.size());
    assertEquals(1, writer.callbacks.get("yam1").size());
    assertEquals(1, writer.callbacks.get("yam1").get("bla").size());
    assertEquals(Sets.newHashSet("c2:b"), writer.callbacks.get("yam1").get("bla"));
    assertEquals(1, writer.callbacks.get("yam2").size());
    assertEquals(1, writer.callbacks.get("yam2").get("bla").size());
    assertEquals(Sets.newHashSet("c2:b"), writer.callbacks.get("yam2").get("bla"));
    assertEquals(LinkedHashMultimap.create(
        ImmutableMultimap.of("c1", "jam1", "c1", "jam2", "c2", "a", "c2", "b")),
                 writer.methodsWithCallbacks);
  }

  @Test
  public void testStore() throws IOException {
    DatastoreCallbacksConfigWriter writer = new DatastoreCallbacksConfigWriter(null);
    writer.addCallback(newLinkedHashSet("kind1", "kind2"), "callback1", "c1", "method1");
    writer.addCallback(newLinkedHashSet("kind1"), "callback1", "c1", "method2");
    writer.addCallback(newLinkedHashSet("kind1", "kind2"), "callback2", "c1", "method3");
    writer.addCallback(newLinkedHashSet(""), "callback1", "c1", "method4");
    writer.addCallback(newLinkedHashSet(""), "callback1", "c1", "method5");
    writer.addCallback(newLinkedHashSet(""), "callback2", "c1", "method6");

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    writer.store(baos);
    Properties props = new Properties();
    props.loadFromXML(new ByteArrayInputStream(baos.toByteArray()));

    Properties expected = new Properties();
    expected.setProperty(DatastoreCallbacksConfigWriter.FORMAT_VERSION_PROPERTY, "1");
    expected.setProperty("kind1.callback1", "c1:method1,c1:method2");
    expected.setProperty("kind1.callback2", "c1:method3");
    expected.setProperty(".callback1", "c1:method4,c1:method5");
    expected.setProperty(".callback2", "c1:method6");
    expected.setProperty("kind2.callback1", "c1:method1");
    expected.setProperty("kind1.callback2", "c1:method3");
    expected.setProperty("kind2.callback2", "c1:method3");
    assertEquals(expected, props);
  }

  @Test
  public void testOverride_RemoveRefs() throws IOException {
    DatastoreCallbacksConfigWriter writer = new DatastoreCallbacksConfigWriter(null);
    writer.addCallback(Sets.newHashSet("kind1"), "callback1", "java.lang.String", "method1");
    writer.addCallback(Sets.newHashSet("kind1"), "callback1", "java.lang.String", "method2");
    writer.addCallback(Sets.newHashSet("kind1"), "callback2", "java.lang.String", "method3");
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    writer.store(baos);
    baos.close();

    // "remove" the references to method2 and method3
    DatastoreCallbacksConfigWriter writer2 = new DatastoreCallbacksConfigWriter(
        new ByteArrayInputStream(baos.toByteArray()));
    writer2.addCallback(newLinkedHashSet("kind1"), "callback1", "java.lang.String", "method1");
    writer2.store(new ByteArrayOutputStream());

    Properties expected = new Properties();
    expected.setProperty(DatastoreCallbacksConfigWriter.FORMAT_VERSION_PROPERTY, "1");
    expected.setProperty("kind1.callback1", "java.lang.String:method1");
    assertEquals(expected, writer2.props);
  }

  @Test
  public void testOverride_RemoveClass() throws IOException {
    DatastoreCallbacksConfigWriter writer = new DatastoreCallbacksConfigWriter(null);
    writer.addCallback(Sets.newHashSet("kind1"), "callback1", "java.lang.String.String", "method1");
    writer.addCallback(Sets.newHashSet("kind1"), "callback1", "doesnotexist", "method2");
    writer.addCallback(Sets.newHashSet("kind1"), "callback2", "doesnotexist", "method3");
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    writer.store(baos);
    baos.close();

    // "remove" the references to method2 and method3
    DatastoreCallbacksConfigWriter writer2 = new DatastoreCallbacksConfigWriter(
        new ByteArrayInputStream(baos.toByteArray()));
    writer2.addCallback(newLinkedHashSet("kind1"), "callback1", "java.lang.String", "method1");
    writer2.store(new ByteArrayOutputStream());

    Properties expected = new Properties();
    expected.setProperty(DatastoreCallbacksConfigWriter.FORMAT_VERSION_PROPERTY, "1");
    expected.setProperty("kind1.callback1", "java.lang.String:method1");
    assertEquals(expected, writer2.props);
  }

  @Test
  public void testOverride_AddMethod() throws IOException {
    DatastoreCallbacksConfigWriter writer = new DatastoreCallbacksConfigWriter(null);
    writer.addCallback(Sets.newHashSet("kind1"), "callback1", "java.lang.String", "method1");
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    writer.store(baos);
    baos.close();

    // now add a callback on a different class for the same kind and callback type
    DatastoreCallbacksConfigWriter writer2 = new DatastoreCallbacksConfigWriter(
        new ByteArrayInputStream(baos.toByteArray()));
    writer2.addCallback(newLinkedHashSet("kind1"), "callback1", "java.lang.Integer", "method2");
    writer2.store(new ByteArrayOutputStream());

    Properties expected = new Properties();
    expected.setProperty(DatastoreCallbacksConfigWriter.FORMAT_VERSION_PROPERTY, "1");
    expected.setProperty("kind1.callback1", "java.lang.String:method1,java.lang.Integer:method2");
    assertEquals(expected, writer2.props);
  }

  @Test
  public void testOverwriteWithWrongVersion() throws IOException {
    Properties wrongVersion = new Properties();
    wrongVersion.setProperty(DatastoreCallbacksConfigWriter.FORMAT_VERSION_PROPERTY, "yar");
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    wrongVersion.storeToXML(baos, "");
    try {
      new DatastoreCallbacksConfigWriter(new ByteArrayInputStream(baos.toByteArray()));
      fail("expected exception");
    } catch (IllegalStateException ise) {
      assertEquals(DatastoreCallbacksConfigWriter.INCORRECT_FORMAT_MESSAGE, ise.getMessage());
    }
  }

  @Test
  public void testStoreTrickyKinds() throws IOException {
    DatastoreCallbacksConfigWriter writer = new DatastoreCallbacksConfigWriter(null);
    writer.addCallback(newLinkedHashSet("="), "callback1", "c1", "method1");
    writer.addCallback(newLinkedHashSet("=="), "callback1", "c1", "method2");
    writer.addCallback(newLinkedHashSet("=,="), "callback1", "c1", "method3");
    writer.addCallback(newLinkedHashSet("=.="), "callback1", "c1", "method4");

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    writer.store(baos);
    Properties expected = new Properties();
    expected.setProperty(DatastoreCallbacksConfigWriter.FORMAT_VERSION_PROPERTY, "1");
    expected.setProperty("=.callback1", "c1:method1");
    expected.setProperty("==.callback1", "c1:method2");
    expected.setProperty("=,=.callback1", "c1:method3");
    expected.setProperty("=.=.callback1", "c1:method4");
    assertEquals(expected, writer.props);
  }

  @Test
  public void testStoreEmpty() throws IOException {
    DatastoreCallbacksConfigWriter writer = new DatastoreCallbacksConfigWriter(null);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    writer.store(baos);
    Properties expected = new Properties();
    expected.setProperty(DatastoreCallbacksConfigWriter.FORMAT_VERSION_PROPERTY, "1");
    assertEquals(expected, writer.props);
  }
}
