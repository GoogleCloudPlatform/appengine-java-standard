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

import com.google.apphosting.datastore.proto2api.DatastoreV3Pb.CompiledCursor;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb.Query;
import com.google.protobuf.ByteString;
import com.google.storage.onestore.v3.OnestoreEntity.IndexPosition;
import com.google.storage.onestore.v3.OnestoreEntity.IndexPostfix;
import com.google.storage.onestore.v3.OnestoreEntity.Reference;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CursorTest {
  CompiledCursor compiledCursor;
  Cursor cursor;

  @Before
  public void setUp() throws Exception {
    compiledCursor = new CompiledCursor();
    CompiledCursor.Position position = compiledCursor.getMutablePosition();
    position.setStartKey("Hello World");
    position.setStartInclusive(true);

    cursor = toCursor(compiledCursor);
  }

  @Test
  public void testCursorLifeCycle() {
    Cursor reconstituted = Cursor.fromWebSafeString(cursor.toWebSafeString());
    assertThat(reconstituted).isEqualTo(cursor);

    Query query = new Query();
    query.setOffset(3);

    query.setCompiledCursor(toPb(reconstituted));
    assertThat(query.getCompiledCursor()).isEqualTo(compiledCursor);
    assertThat(query.getOffset()).isEqualTo(3);
  }

  @SuppressWarnings("deprecation")
  @Test
  public void testReverseCursor() {
    Cursor reverse = cursor.reverse();
    assertThat(reverse).isEqualTo(cursor);

    // reverse() is a no-op.
    Cursor doubleReverse = reverse.reverse();
    assertThat(doubleReverse).isEqualTo(cursor);
  }

  @SuppressWarnings("deprecation")
  @Test
  public void testReverseCursorPostfix() {
    IndexPostfix postfixPosition = new IndexPostfix().setKey(new Reference()).setBefore(true);
    Cursor pfCursor = toCursor(new CompiledCursor().setPostfixPosition(postfixPosition));

    // reverse() is a no-op.
    Cursor pfReverse = pfCursor.reverse();
    assertThat(pfReverse).isEqualTo(pfCursor);
    assertThat(pfCursor).isEqualTo(pfReverse.reverse());
  }

  @SuppressWarnings("deprecation")
  @Test
  public void testReverseCursorAbsolutePosition() {
    IndexPosition absolutePosition = new IndexPosition().setKey("Goodnight moon").setBefore(true);
    Cursor absCursor = toCursor(new CompiledCursor().setAbsolutePosition(absolutePosition));

    // reverse() is a no-op.
    Cursor absReverse = absCursor.reverse();
    assertThat(absCursor).isEqualTo(absReverse);
    assertThat(absCursor).isEqualTo(absReverse.reverse());
  }

  @Test
  public void testSerialization() throws Exception {
    CompiledCursor compiledCursor = new CompiledCursor();
    CompiledCursor.Position position = compiledCursor.getMutablePosition();
    position.setStartKey("Hello World");
    position.setStartInclusive(true);

    Cursor original = toCursor(compiledCursor);

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream(baos);
    oos.writeObject(original);

    byte[] bytes = baos.toByteArray();

    ObjectInputStream iis = new ObjectInputStream(new ByteArrayInputStream(bytes));
    Cursor readCursor = (Cursor) iis.readObject();

    assertThat(readCursor).isNotSameInstanceAs(original);
    assertThat(readCursor).isEqualTo(original);
    Query query = new Query();
    query.setOffset(3);

    query.setCompiledCursor(toPb(readCursor));
    assertThat(query.getCompiledCursor()).isEqualTo(compiledCursor);
    assertThat(query.getOffset()).isEqualTo(3);
  }

  @Test
  public void testBadValues() {
    assertThrows(NullPointerException.class, () -> Cursor.fromWebSafeString(null));

    assertThrows(NullPointerException.class, () -> new Cursor((ByteString) null));

    assertThrows(NullPointerException.class, () -> new Cursor((Cursor) null));

    assertThrows(IllegalArgumentException.class, () -> new Cursor().advance(-1, null));

    // Cursor cannot, in general, detect bad values because it makes no
    // assumptions about the format of the bytes it contains.
    Cursor.fromWebSafeString("Bad");
  }

  private static Cursor toCursor(CompiledCursor pb) {
    return new Cursor(pb.toByteString());
  }

  private static CompiledCursor toPb(Cursor cursor) {
    CompiledCursor pb = new CompiledCursor();
    assertThat(pb.parseFrom(cursor.toByteString())).isTrue();
    return pb;
  }
}
