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

import com.google.appengine.api.datastore.FutureHelper.MultiFuture;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.io.protocol.Protocol;
import com.google.protobuf.MessageLite;
import com.google.protobuf.MessageLiteOrBuilder;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A class that splits groups of values into multiple batches based on size, count and group
 * affiliation.
 *
 * <p>This class purposefully delays conversion to protocol message format to reduce memory usage.
 *
 * @param <R> the batch message type, usually the request
 * @param <F> the java native value type to batch
 * @param <T> the proto value type to batch
 */
abstract class Batcher<R extends MessageLiteOrBuilder, F, T extends MessageLite> {
  /** @return the group affiliation for the given value. */
  abstract Object getGroup(F value);

  /**
   * Adds the given proto value to the given batch.
   *
   * @param pb the proto to add
   * @param batch the batch to add to
   */
  abstract void addToBatch(T pb, R batch);

  /** @return a new empty batch based on the base batch template. */
  abstract R newBatch(R baseBatch);

  /** @return the maximum message size in bytes for a single batch */
  abstract int getMaxSize();

  /** @return the maximum number of values to add to a single batch */
  abstract int getMaxCount();

  /** @return the maximum number of groups to include in a single batch (if grouping is enabled) */
  abstract int getMaxGroups();

  /** @return the protocol message version of the value */
  abstract T toPb(F value);

  /**
   * Models an item and its associated index in some ordered collection.
   *
   * @param <F> The type of the item.
   */
  static class IndexedItem<F> {
    final int index;
    final F item;

    IndexedItem(int index, F item) {
      this.index = index;
      this.item = item;
    }

    @Override
    public String toString() {
      return String.format("IndexedItem(%d,  %s)", index, item);
    }
  }

  /**
   * * A future that re-orders the results of a batch operation given the order returned by {@link
   * Batcher#getBatches(Collection, MessageLiteOrBuilder, int, boolean, List)}.
   *
   * @param <K> batch type
   * @param <V> aggregated result type
   */
  abstract static class ReorderingMultiFuture<K, V> extends MultiFuture<K, V> {
    private final Collection<Integer> order;

    /**
     * @param futures the batched futures
     * @param order a collection containing the index at which the associated value should appear.
     */
    public ReorderingMultiFuture(Iterable<Future<K>> futures, Collection<Integer> order) {
      super(futures);
      this.order = order;
    }

    /**
     * Returns the populated aggregate result.
     *
     * @param batch a batch result
     * @param indexItr an iterator that produces the associated index for each batch result. {@code
     *     next()} will be called exactly once for each value in batch.
     * @param result the aggregated result instance to populate.
     */
    protected abstract V aggregate(K batch, Iterator<Integer> indexItr, V result);

    /** Returns the object that should be populated with the re-ordered results. */
    protected abstract V initResult();

    @Override
    public final V get() throws InterruptedException, ExecutionException {
      Iterator<Integer> indexItr = order.iterator();
      V result = initResult();
      for (Future<K> future : futures) {
        result = aggregate(future.get(), indexItr, result);
      }
      return result;
    }

    @Override
    public final V get(long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
      Iterator<Integer> indexItr = order.iterator();
      V result = initResult();
      for (Future<K> future : futures) {
        // TODO: the max time this can take is actually N * timeout. Consider fixing this.
        result = aggregate(future.get(timeout, unit), indexItr, result);
      }
      return result;
    }
  }

  /**
   * An iterator that builds batches lazily.
   *
   * @param <V> the intermediate value type
   */
  private abstract class BatchIterator<V> implements Iterator<R> {
    /**
     * Must be called only once per value and in the order in which the values are added to batches.
     *
     * @return the original value
     */
    protected abstract F getValue(V value);

    final R baseBatch;
    // TODO: See if we can make these values immutable and store them on the batcher.
    final int baseSize;
    final int maxSize = getMaxSize();
    final int maxCount = getMaxCount();
    final int maxGroups;
    final Iterator<? extends Iterable<V>> groupItr;
    Iterator<V> valueItr;
    T nextValue;

    /**
     * @param baseBatch the base batch template
     * @param groupedValues an iterator the returns groups of values, must not be empty or contain
     *     any empty group.
     */
    BatchIterator(R baseBatch, int baseBatchSize, Iterator<? extends Iterable<V>> groupedValues) {
      this.baseBatch = baseBatch;
      this.baseSize = baseBatchSize;
      this.groupItr = groupedValues;
      this.valueItr = groupItr.next().iterator();
      this.nextValue = toPb(getValue(valueItr.next()));
      this.maxGroups = getMaxGroups();
    }

    @Override
    public boolean hasNext() {
      return nextValue != null;
    }

    @Override
    public R next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      R batch = newBatch(baseBatch);
      int size = baseSize;
      int numGroups = 1;
      for (int i = 0; i < maxCount && numGroups <= maxGroups; ++i) {
        // See if adding the next value will overflow our size limit.
        int valueSize = getEmbeddedSize(nextValue);
        if (i > 0
            && // Always return at least one result.
            size + valueSize > maxSize) {
          break;
        }
        // Add the value to the batch.
        size += valueSize;
        addToBatch(nextValue, batch);

        // Find the next value.
        if (!valueItr.hasNext()) {
          if (!groupItr.hasNext()) {
            nextValue = null; // No more values.
            break;
          }
          // Moving on to the next group.
          valueItr = groupItr.next().iterator();
          ++numGroups;
        }
        // Populate the next value.
        nextValue = toPb(getValue(valueItr.next()));
      }
      return batch;
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }

  /** @return the embedded size of the given message */
  private static int getEmbeddedSize(MessageLite pb) {
    return Protocol.stringSize(pb.getSerializedSize()) + 1;
  }

  /**
   * Gets or create the collection for the given key and adds the given value.
   *
   * <p>Creates collections that allow duplicates and preserves order (ArrayList).
   *
   * @param map the map from which get or create the collection
   * @param key the key of the collection to add value to
   * @param value the value to add
   */
  private <T2> void put(Map<Object, Collection<T2>> map, Object key, T2 value) {
    Collection<T2> col = map.get(key);
    if (col == null) {
      col = Lists.newArrayList();
      map.put(key, col);
    }
    col.add(value);
  }

  /**
   * Lazily compute batches.
   *
   * @param values the values to batch
   * @param baseBatch the batch template to use
   * @param baseBatchSize serialized size of baseBatch
   * @param group if the values should be grouped using {@link #getGroup}
   * @return an iterator that lazily computes batches.
   */
  Iterator<R> getBatches(Collection<F> values, R baseBatch, int baseBatchSize, boolean group) {
    if (values.isEmpty()) {
      return Collections.emptyIterator();
    }

    Iterator<? extends Iterable<F>> groupItr;
    // NOTE: We want to group even if the number of values is < maxGroups so that
    // if we split based on other metrics entities in the same group tend to be in the same
    // batch.
    if (group) {
      // NOTE: We are going to some length to provide a consistent ordering. This
      // is merely for tests and can be relaxed if the tests are improved.
      Map<Object, Collection<F>> groupedValues = Maps.newLinkedHashMap();
      for (F value : values) {
        put(groupedValues, getGroup(value), value);
      }
      groupItr = groupedValues.values().iterator();
    } else {
      groupItr = Iterators.singletonIterator(values);
    }

    return new BatchIterator<F>(baseBatch, baseBatchSize, groupItr) {
      @Override
      protected F getValue(F value) {
        return value;
      }
    };
  }

  /**
   * Lazily compute batches and populate order with indexes of the value as they are added to each
   * batch.
   *
   * @param values the values to batch
   * @param baseBatch the batch template to use
   * @param baseBatchSize size of baseBatch
   * @param group if the values should be grouped using {@link #getGroup}
   * @param order the list to populate with the indexes of the values as they are added to batches
   * @return an iterator that lazily computes batches.
   */
  Iterator<R> getBatches(
      Collection<F> values,
      R baseBatch,
      int baseBatchSize,
      boolean group,
      final List<Integer> order) {
    if (values.isEmpty()) {
      return Collections.emptyIterator();
    }

    Iterator<? extends Iterable<IndexedItem<F>>> groupItr;
    // NOTE: We want to group even if the number of values is < maxGroups so that
    // if we split based on other metrics entities in the same group tend to be in the same
    // batch.
    if (group) {
      // NOTE: We are going to some length to provide a consistent ordering. This
      // is merely for tests and can be relaxed if the tests are improved.
      Map<Object, Collection<IndexedItem<F>>> groupedValues = Maps.newLinkedHashMap();
      int index = 0;
      for (F value : values) {
        put(groupedValues, getGroup(value), new IndexedItem<F>(index++, value));
      }
      groupItr = groupedValues.values().iterator();
    } else {
      List<IndexedItem<F>> indexedValue = Lists.newArrayList();
      int index = 0;
      for (F value : values) {
        indexedValue.add(new IndexedItem<F>(index++, value));
      }
      groupItr = Iterators.<Iterable<IndexedItem<F>>>singletonIterator(indexedValue);
    }

    return new BatchIterator<IndexedItem<F>>(baseBatch, baseBatchSize, groupItr) {
      @Override
      protected F getValue(IndexedItem<F> value) {
        order.add(value.index); // Add the index to the order list.
        return value.item;
      }
    };
  }
}
