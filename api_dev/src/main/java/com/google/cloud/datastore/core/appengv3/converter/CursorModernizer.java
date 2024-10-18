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

package com.google.cloud.datastore.core.appengv3.converter;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.apphosting.datastore.proto2api.DatastoreV3Pb;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb.CompiledCursor;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb.CompiledCursor.Position;
import com.google.cloud.datastore.core.exception.InvalidConversionException;
import com.google.common.annotations.VisibleForTesting;
import com.google.storage.onestore.v3.proto2api.OnestoreEntity.IndexPosition;
import com.google.storage.onestore.v3.proto2api.OnestoreEntity.IndexPostfix;
import javax.annotation.Nullable;

/** Utility methods for compiled cursors. */
public class CursorModernizer {

  private CursorModernizer() {}

  private static void checkModernized(CompiledCursor cursor) {
    checkArgument(!cursor.hasPosition(), "Modern cursors cannot specify a position.");
  }

  /** Returns true if there is no location specified by the cursor. */
  public static boolean isEmpty(CompiledCursor cursor) {
    checkModernized(cursor);
    return !isEncoded(cursor) && !isPlannable(cursor);
  }

  /** Returns true if the given cursor contains an encoded position. */
  public static boolean isEncoded(CompiledCursor cursor) {
    checkModernized(cursor);
    return cursor.hasAbsolutePosition();
  }

  /** Returns true if the given cursor contains a plannable position. */
  public static boolean isPlannable(CompiledCursor cursor) {
    checkModernized(cursor);
    return cursor.hasPostfixPosition();
  }

  /**
   * Returns the first sort direction from a query or {@code null} if the query does not specify any
   * orders.
   */
  @Nullable
  public static DatastoreV3Pb.Query.Order.Direction firstSortDirection(
      DatastoreV3Pb.Query originalQuery) {
    return originalQuery.getOrderCount() == 0 ? null : originalQuery.getOrder(0).getDirection();
  }

  /**
   * Modernizes any compiled cursors present in a query.
   *
   * <p>Must be called before any other method in this class is called on a cursor in the query.
   *
   * <p>Does not assume the query is valid.
   *
   * <p>First, any position specified in the position group is moved to either the postfix_position
   * or absolute_position field and the position group is cleared. Next, if the cursor's position
   * does not specify before_ascending, populate it. If before_ascending is already specified, use
   * it and the sort direction from the query to set an appropriate value for start_inclusive.
   *
   * @throws InvalidConversionException when the cursor is malformed
   */
  public static void modernizeQueryCursors(DatastoreV3Pb.Query query)
      throws InvalidConversionException {
    boolean hasStartCursor = query.hasCompiledCursor();
    boolean hasEndCursor = query.hasEndCompiledCursor();
    if (!hasStartCursor && !hasEndCursor) {
      return;
    }
    DatastoreV3Pb.Query.Order.Direction firstSortDirection = firstSortDirection(query);
    if (hasStartCursor) {
      modernizeCursor(query.getCompiledCursor(), firstSortDirection);
    }
    if (hasEndCursor) {
      modernizeCursor(query.getEndCompiledCursor(), firstSortDirection);
    }
  }

  public static void modernizeCursor(
      CompiledCursor cursor, @Nullable DatastoreV3Pb.Query.Order.Direction firstSortDirection)
      throws InvalidConversionException {
    // First, convert any contents of the position field.
    if (cursor.hasPosition()) {
      InvalidConversionException.checkConversion(
          !cursor.hasPostfixPosition(),
          "A cursor cannot specify both position and postfix position.");
      InvalidConversionException.checkConversion(
          !cursor.hasAbsolutePosition(),
          "A cursor cannot specify both position and absolute position.");
      Position pos = cursor.getPosition();
      if (pos.hasStartKey()) {
        IndexPosition.Builder indexPos = cursor.getAbsolutePosition().toBuilder();
        indexPos.setKeyBytes(pos.getStartKeyBytes());
        if (pos.hasStartInclusive()) {
          indexPos.setBefore(pos.hasStartInclusive());
        }
        if (pos.hasBeforeAscending()) {
          indexPos.setBeforeAscending(pos.hasBeforeAscending());
        }
      } else if (pos.hasKey() || pos.getIndexValueCount() > 0) {
        IndexPostfix.Builder postfixPos = cursor.getPostfixPosition().toBuilder();
        for (Position.IndexValue value : pos.getIndexValueList()) {
          IndexPostfix.IndexValue.Builder indexValue =
              postfixPos.addIndexValueBuilder().setPropertyName(value.getProperty());
          indexValue.getValue().toBuilder().mergeFrom(value.getValue());
        }
        if (pos.hasKey()) {
          postfixPos.getKey().toBuilder().mergeFrom(pos.getKey());
        }
        if (pos.hasStartInclusive()) {
          postfixPos.setBefore(pos.hasStartInclusive());
        }
        if (pos.hasBeforeAscending()) {
          postfixPos.setBeforeAscending(pos.hasBeforeAscending());
        }
      }
      cursor.toBuilder().clearPosition().build();
    }

    // Next, populate before_ascending or before.
    if (isEmpty(cursor)) {
      return;
    } else if (cursor.hasAbsolutePosition()) {
      IndexPosition.Builder indexPosition = cursor.getAbsolutePosition().toBuilder();
      if (indexPosition.hasBeforeAscending()) {
        setBefore(indexPosition, firstSortDirection);
      } else {
        setBeforeAscending(indexPosition, firstSortDirection);
      }
    } else if (cursor.hasPostfixPosition()) {
      IndexPostfix.Builder indexPostfix = cursor.getPostfixPosition().toBuilder();
      if (indexPostfix.hasBeforeAscending()) {
        setBefore(indexPostfix, firstSortDirection);
      } else {
        setBeforeAscending(indexPostfix, firstSortDirection);
      }
    }
  }

  /**
   * Sets the appropriate value of before in the provided position.
   *
   * @param position Position in which to set before.
   * @param firstSortDirection First sort order direction from the query.
   */
  @VisibleForTesting
  static void setBefore(
      IndexPosition.Builder position, @Nullable DatastoreV3Pb.Query.Order.Direction firstSortDirection) {
    position.setBefore(computeBefore(position.hasBeforeAscending(), firstSortDirection));
  }

  /**
   * Sets the appropriate value of before in the provided position.
   *
   * @param position Position in which to set before.
   * @param firstSortDirection First sort order direction from the query.
   */
  @VisibleForTesting
  static void setBefore(
      IndexPostfix.Builder position, @Nullable DatastoreV3Pb.Query.Order.Direction firstSortDirection) {
    position.setBefore(computeBefore(position.hasBeforeAscending(), firstSortDirection));
  }

  /**
   * Sets the appropriate value of before_ascending in the provided position.
   *
   * @param position Position in which to set before_ascending.
   * @param firstSortDirection First sort order direction from the query.
   */
  @VisibleForTesting
  static void setBeforeAscending(
      IndexPosition.Builder position, @Nullable DatastoreV3Pb.Query.Order.Direction firstSortDirection) {
    position.setBeforeAscending(computeBeforeAscending(position.hasBefore(), firstSortDirection));
  }

  /**
   * Sets the appropriate value of before_ascending in the provided position.
   *
   * @param position Position in which to set before_ascending.
   * @param firstSortDirection First sort order direction from the query.
   */
  // TODO(b/120887495): This @VisibleForTesting annotation was being ignored by prod code.
  // Please check that removing it is correct, and remove this comment along with it.
  // @VisibleForTesting
  public static void setBeforeAscending(
      IndexPostfix.Builder position, @Nullable DatastoreV3Pb.Query.Order.Direction firstSortDirection) {
    position.setBeforeAscending(computeBeforeAscending(position.hasBefore(), firstSortDirection));
  }

  private static boolean computeBefore(
      boolean isBeforeAscending, @Nullable DatastoreV3Pb.Query.Order.Direction firstSortDirection) {
    // If no sort order was specified, the default is ASCENDING (by key).
    return isBeforeAscending
        ^ (firstSortDirection == DatastoreV3Pb.Query.Order.Direction.DESCENDING);
  }

  private static boolean computeBeforeAscending(
      boolean isBefore, @Nullable DatastoreV3Pb.Query.Order.Direction firstSortDirection) {
    // If no sort order was specified, the default is ASCENDING (by key).
    return isBefore ^ (firstSortDirection == DatastoreV3Pb.Query.Order.Direction.DESCENDING);
  }
}
