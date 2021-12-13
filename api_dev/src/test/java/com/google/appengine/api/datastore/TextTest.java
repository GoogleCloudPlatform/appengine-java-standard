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

import com.google.common.testing.EqualsTester;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for the Text class.
 *
 */
@RunWith(JUnit4.class)
public class TextTest {
  @Test
  public void testEquals() throws Exception {
    Text text1 = new Text("This is a test.");
    Text text2 = new Text("This is a test.");
    Text text3 = new Text("This is a test.\n");
    Text text4 = new Text("This is a test.\r\n");
    Text nullText = new Text(null);

    new EqualsTester()
        .addEqualityGroup(text1, text2)
        .addEqualityGroup(text3)
        .addEqualityGroup(text4)
        .addEqualityGroup(nullText, new Text(null))
        .testEquals();
  }

  @Test
  public void testHashCode() throws Exception {
    Text text1 = new Text("This is a test.");
    Text text2 = new Text("This is a test.");
    assertThat(text2.hashCode()).isEqualTo(text1.hashCode());

    Text null1 = new Text(null);
    Text null2 = new Text(null);
    assertThat(null2.hashCode()).isEqualTo(null1.hashCode());
  }

  @Test
  public void testToString() throws Exception {
    String testString = "This is a test.";
    Text text = new Text(testString);
    assertThat(text.getValue()).isEqualTo(testString);
    assertThat(text.toString()).isEqualTo("<Text: " + testString + ">");

    Text nullText = new Text(null);
    assertThat(nullText.toString()).isEqualTo("<Text: null>");
  }

  @Test
  public void testSerialization() throws Exception {
    Text text = new Text("This is a test.\r\nIt is only a test.\r\n");

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream(baos);
    oos.writeObject(text);

    byte[] bytes = baos.toByteArray();

    ObjectInputStream iis = new ObjectInputStream(new ByteArrayInputStream(bytes));
    Text readText = (Text) iis.readObject();

    assertThat(readText).isNotSameInstanceAs(text);
    assertThat(readText).isEqualTo(text);
  }
}
