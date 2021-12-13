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

package com.google.appengine.api.datastore;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.Lists;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for the DatastoreService IMHandle class.
 *
 */
@RunWith(JUnit4.class)
public class IMHandleTest {
  @Test
  public void testBadConstructorInput() {
    assertThrows(NullPointerException.class, () -> new IMHandle((IMHandle.Scheme) null, "yar"));

    assertThrows(NullPointerException.class, () -> new IMHandle((URL) null, "yar"));

    assertThrows(NullPointerException.class, () -> new IMHandle(IMHandle.Scheme.sip, null));
  }

  @Test
  public void testGoodConstructorInput() throws MalformedURLException {
    IMHandle handle = new IMHandle(IMHandle.Scheme.sip, "yar");
    assertThat(handle.getProtocol()).isEqualTo("sip");
    assertThat(handle.toDatastoreString()).isEqualTo("sip yar");

    handle = new IMHandle(new URL("http://google.com"), "yar");
    assertThat(handle.getProtocol()).isEqualTo("http://google.com");
    assertThat(handle.toDatastoreString()).isEqualTo("http://google.com yar");
  }

  @Test
  public void testDatastoreRoundtrip_Scheme() {
    IMHandle handle = new IMHandle(IMHandle.Scheme.sip, "yar");
    assertThat(IMHandle.fromDatastoreString(handle.toDatastoreString())).isEqualTo(handle);
    handle = new IMHandle(IMHandle.Scheme.sip, "yar yar yar");
    assertThat(IMHandle.fromDatastoreString(handle.toDatastoreString())).isEqualTo(handle);
  }

  @Test
  public void testDatastoreRoundtrip_URL() throws MalformedURLException {
    IMHandle handle = new IMHandle(new URL("http://google.com"), "yar");
    assertThat(IMHandle.fromDatastoreString(handle.toDatastoreString())).isEqualTo(handle);
  }

  @Test
  public void testInvalidDatastoreString() {
    assertThrows(NullPointerException.class, () -> IMHandle.fromDatastoreString(null));

    assertThrows(IllegalArgumentException.class, () -> IMHandle.fromDatastoreString("nospace"));

    assertThrows(IllegalArgumentException.class, () -> IMHandle.fromDatastoreString("not valid"));
  }

  @Test
  public void testCompareTo() {
    IMHandle appSip = new IMHandle(IMHandle.Scheme.sip, "app");
    IMHandle engineXmpp = new IMHandle(IMHandle.Scheme.xmpp, "engine");
    IMHandle gaeXmpp = new IMHandle(IMHandle.Scheme.xmpp, "gae@gmail.com");
    IMHandle javaUnknown = new IMHandle(IMHandle.Scheme.unknown, "java@gmail.com");
    List<IMHandle> handleList = Lists.newArrayList(gaeXmpp, javaUnknown, engineXmpp, appSip);
    Collections.sort(handleList);
    assertThat(handleList).containsExactly(appSip, javaUnknown, engineXmpp, gaeXmpp).inOrder();
  }
}
