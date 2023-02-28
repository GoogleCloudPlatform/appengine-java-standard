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

import static com.google.appengine.api.datastore.FetchOptions.Builder.withStartCursor;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.io.BaseEncoding.base64Url;

import com.google.apphosting.datastore.DatastoreV3Pb.Query;
import com.google.common.base.CharMatcher;
import com.google.common.io.ByteStreams;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.io.Serializable;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * A cursor that represents a position in a query.
 *
 * <p>To resume a {@link Query} at the position defined by a {@link Cursor}, the {@link Cursor} must
 * be present in the {@link FetchOptions} passed to a {@link PreparedQuery} identical to the one it
 * was created from.
 *
 * <p>Cursors can be retrieved from {@code PreparedQuery.asQueryResult*} functions. A typical use
 * case would be:
 *
 * <blockquote>
 *
 * <pre>{@code
 * Cursor originalCursor = preparedQuery.asQueryResultList(withLimit(20)).getCursor();
 * String encodedCursor = original.toWebSafeString();
 * }</pre>
 *
 * </blockquote>
 *
 * The encoded cursor can then be passed safely in a get or post arg of a web request and on another
 * request the next batch of results can be retrieved with:
 *
 * <blockquote>
 *
 * <pre>{@code
 * Cursor decodedCursor = Cursor.fromWebSafeString(encodedCursor);
 * List<Entity> nextBatch = preparedQuery.asQueryResultList(withLimit(20).cursor(decoded));
 * }</pre>
 *
 * </blockquote>
 *
 */
public final class Cursor implements Serializable {
  static final long serialVersionUID = 3515556366838971499L;
  // NOTE: Ideally this variable should be final but we need to
  // support custom serialization as we have no control over the
  // serialization of CompiledQuery and have already been broken by a change
  // to its base class.
  // Since we don't use default serialization, we can mark the field transient, to signal
  // to class scanners that it is not part of the serial form.
  private transient ByteString cursorBytes;

  // Constructor is package private
  Cursor() {
    cursorBytes = ByteString.EMPTY;
  }

  Cursor(Cursor previousCursor) {
    this(previousCursor.cursorBytes);
  }

  Cursor(ByteString cursorBytes) {
    checkNotNull(cursorBytes);
    this.cursorBytes = cursorBytes;
  }

  // Serialization functions
  private void writeObject(java.io.ObjectOutputStream out) throws IOException {
    out.write(cursorBytes.toByteArray());
  }

  private void readObject(java.io.ObjectInputStream in) throws IOException {
    cursorBytes = ByteString.copyFrom(ByteStreams.toByteArray(in));
  }

  // TODO: consider exposing to users
  Cursor advance(final int n, PreparedQuery query) {
    if (n == 0) {
      return this;
    } else if (n > 0) {
      return query.asQueryResultIterator(withStartCursor(this).offset(n).limit(0)).getCursor();
    }
    throw new IllegalArgumentException("Unable to offset cursor by " + n + " results.");
  }

  /**
   * Returns a cursor identical to {@code this}.
   *
   * @deprecated It is no longer necessary to call {@link #reverse()} on cursors.
   *     <p>A cursor returned by a query may also be used in the query returned by {@link
   *     com.google.appengine.api.datastore.Query#reverse()}.
   */
  @Deprecated
  public Cursor reverse() {
    return this;
  }

  /**
   * Encodes the current cursor as a web safe string that can later be decoded by {@link
   * #fromWebSafeString(String)}
   */
  public String toWebSafeString() {
    return base64Url().omitPadding().encode(cursorBytes.toByteArray());
  }

  /**
   * Decodes the given encoded cursor
   *
   * @return the decoded cursor
   * @throws IllegalArgumentException if the provided string is not a valid encoded cursor
   */
  public static Cursor fromWebSafeString(String encodedCursor) {
    checkNotNull(encodedCursor, "encodedCursor must not be null");

    try {
      return new Cursor(
          ByteString.copyFrom(
              base64Url().decode(CharMatcher.whitespace().removeFrom(encodedCursor))));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Unable to decode provided cursor.", e);
    }
  }

  ByteString toByteString() {
    return ByteString.copyFrom(cursorBytes.toByteArray());
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (obj == null) {
      return false;
    }

    if (obj.getClass() != this.getClass()) {
      return false;
    }

    return cursorBytes.equals(((Cursor) obj).cursorBytes);
  }

  @Override
  public int hashCode() {
    return cursorBytes.hashCode();
  }

  @Override
  public String toString() {
    return cursorBytes.toString();
  }
}
