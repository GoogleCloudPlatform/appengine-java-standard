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

import com.google.apphosting.datastore_bytes.proto2api.DatastoreV3Pb.CompiledCursor;
import com.google.apphosting.datastore_bytes.proto2api.DatastoreV3Pb.Query;
import com.google.protobuf.ByteString;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.storage.onestore.v3_bytes.proto2api.OnestoreEntity.IndexPosition;
import com.google.storage.onestore.v3_bytes.proto2api.OnestoreEntity.IndexPostfix;
import com.google.storage.onestore.v3_bytes.proto2api.OnestoreEntity.Reference;
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
    CompiledCursor.Builder compiledCursorBuilder = CompiledCursor.newBuilder();
    CompiledCursor.Position.Builder position = compiledCursorBuilder.getPositionBuilder();
    position.setStartKey(ByteString.copyFromUtf8("Hello World"));
    position.setStartInclusive(true);
    compiledCursor = compiledCursorBuilder.build();
    cursor = toCursor(compiledCursor);
  }

  @Test
  public void testCursorLifeCycle() {
    Cursor reconstituted = Cursor.fromWebSafeString(cursor.toWebSafeString());
    assertThat(reconstituted).isEqualTo(cursor);

    Query.Builder query = Query.newBuilder().setOffset(3).setCompiledCursor(toPb(reconstituted));
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
    IndexPostfix postfixPosition = IndexPostfix.newBuilder().setKey(Reference.getDefaultInstance()).setBefore(true).buildPartial();
    Cursor pfCursor = toCursor(CompiledCursor.newBuilder().setPostfixPosition(postfixPosition).buildPartial());

    // reverse() is a no-op.
    Cursor pfReverse = pfCursor.reverse();
    assertThat(pfReverse).isEqualTo(pfCursor);
    assertThat(pfCursor).isEqualTo(pfReverse.reverse());
  }

  @SuppressWarnings("deprecation")
  @Test
  public void testReverseCursorAbsolutePosition() {
    IndexPosition absolutePosition =
        IndexPosition.newBuilder()
            .setKey(ByteString.copyFromUtf8("Goodnight moon"))
            .setBefore(true)
            .build();
    Cursor absCursor = toCursor(CompiledCursor.newBuilder().setAbsolutePosition(absolutePosition).build());

    // reverse() is a no-op.
    Cursor absReverse = absCursor.reverse();
    assertThat(absCursor).isEqualTo(absReverse);
    assertThat(absCursor).isEqualTo(absReverse.reverse());
  }

  @Test
  public void testSerialization() throws Exception {
    CompiledCursor.Builder compiledCursor = CompiledCursor.newBuilder();
    compiledCursor
        .getPositionBuilder()
        .setStartKey(ByteString.copyFromUtf8("Hello World"))
        .setStartInclusive(true);

    Cursor original = toCursor(compiledCursor.build());

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream(baos);
    oos.writeObject(original);

    byte[] bytes = baos.toByteArray();

    ObjectInputStream iis = new ObjectInputStream(new ByteArrayInputStream(bytes));
    Cursor readCursor = (Cursor) iis.readObject();

    assertThat(readCursor).isNotSameInstanceAs(original);
    assertThat(readCursor).isEqualTo(original);
    Query.Builder query = Query.newBuilder().setOffset(3).setCompiledCursor(toPb(readCursor));
    assertThat(query.getCompiledCursor()).isEqualTo(compiledCursor.build());
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
    CompiledCursor.Builder pb = CompiledCursor.newBuilder();
    boolean parse = true;
    try{
      pb.mergeFrom(cursor.toByteString(), ExtensionRegistry.getEmptyRegistry());
    } catch (InvalidProtocolBufferException e){
      parse = false;
    }
    assertThat(parse).isTrue();
    return pb.build();
  }
}
