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

package com.google.appengine.api.memcache;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;

/**
 * The Java API for the App Engine Memcache service.  This offers a fast
 * distributed cache for commonly-used data.  The cache is limited both in
 * duration and also in total space, so objects stored in it may be discarded
 * at any time.
 *
 * <p>Note that {@code null} is a legal value to store in the cache, or to use
 * as a cache key.  Although the API is written for {@link Object}s, both
 * keys and values should be {@link Serializable}, although future versions
 * may someday accept specific types of non-{@code Serializable}
 * {@code Objects}.
 *
 * <p>The values returned from this API are mutable copies from the cache;
 * altering them has no effect upon the cached value itself until assigned with
 * one of the {@link #put(Object, Object) put} methods.  Likewise, the methods
 * returning collections return mutable collections, but changes do not affect
 * the cache.
 *
 * <p>Methods that operate on single entries, including {@link #increment}, are
 * atomic, while batch methods such as {@link #getAll}, {@link #putAll}, and
 * {@link #deleteAll} do not provide atomicity.  Arbitrary operations on single
 * entries can be performed atomically by using {@link #putIfUntouched} in
 * combination with {@link #getIdentifiable}.
 *
 * <p>{@link #increment Increment} has a number of caveats to its use; please
 * consult the method documentation.
 *
 * <p>An {@link ErrorHandler} configures how errors are treated. The default
 * error handler is an instance of {@link LogAndContinueErrorHandler}. In most
 * cases this will log the underlying error condition and emulate cache-miss
 * behavior instead of throwing an error to the calling code. For example, it
 * returns {@code null} from {@link #get(Object)}.
 *
 * <p>A less permissive alternative is {@link StrictErrorHandler}, which will
 * instead throw a {@link MemcacheServiceException} to expose any errors for
 * application code to resolve.
 *
 * <p>To guarantee that all {@link MemcacheServiceException} are directed to the
 * error handler use a {@link ConsistentErrorHandler} such as
 * {@link ErrorHandlers#getConsistentLogAndContinue(Level)} or
 * {@link ErrorHandlers#getStrict()}.
 *
 */
public interface MemcacheService extends BaseMemcacheService {

  /**
   * Cache replacement strategies for {@link MemcacheService#put} operations,
   * indicating how to handle putting a value that already exists.
   */
  // N.B.(fabbott): the order of this enum must match the order of
  // MemcacheSetRequest.{SET,ADD,REPLACE}.
  enum SetPolicy {
    /**
     * Always stores the new value.  If an existing value was stored with the
     * given key, it will be discarded and replaced.
     */
    SET_ALWAYS,

    /**
     * An additive-only strategy, useful to avoid race conditions.
     */
    ADD_ONLY_IF_NOT_PRESENT,

    /**
     * A replace-only strategy.
     */
    REPLACE_ONLY_IF_PRESENT
  }

  /**
   * Encapsulates an Object that is returned by {@link #getIdentifiable}.
   * An {@code IdentifiableValue} can later be used in a {@link #putIfUntouched}
   * operation.
   */
  interface IdentifiableValue {
    /**
     * @return the encapsulated value object.
     */
    Object getValue();
  }

  /**
   * A holder for compare and set values.
   * {@link Expiration expiration} and {@code newValue} can be null.
   */
  final class CasValues {

    private final IdentifiableValue oldValue;
    private final Object newValue;
    private final Expiration expiration;

    public CasValues(IdentifiableValue oldValue, Object newValue) {
      this(oldValue, newValue, null);
    }

    public CasValues(IdentifiableValue oldValue, Object newValue, Expiration expiration) {
      if (oldValue == null) {
        throw new IllegalArgumentException("oldValue can not be null");
      }
      this.oldValue = oldValue;
      this.newValue = newValue;
      this.expiration = expiration;
    }

    public IdentifiableValue getOldValue() {
      return oldValue;
    }

    public Object getNewValue() {
      return newValue;
    }

    public Expiration getExipration() {
      return expiration;
    }

    @Override
    public boolean equals(Object otherObj) {
      if (this == otherObj) {
        return true;
      }
      if ((otherObj == null) || (getClass() != otherObj.getClass())) {
        return false;
      }
      CasValues otherCasValues = (CasValues) otherObj;
      return Objects.equals(oldValue, otherCasValues.oldValue) &&
          Objects.equals(newValue, otherCasValues.newValue) &&
          Objects.equals(expiration, otherCasValues.expiration);
    }

    @Override
    public int hashCode() {
      return Objects.hash(oldValue, newValue, expiration);
    }
  }

  /**
   * @deprecated use {@link MemcacheServiceFactory#getMemcacheService(String)}
   * instead.
   */
  @Deprecated
  void setNamespace(String newNamespace);

  /**
   * Fetches a previously-stored value, or {@code null} if unset.  To
   * distinguish a {@code null} value from unset use
   * {@link MemcacheService#contains(Object)}.
   *
   * <p>If an error deserializing the value occurs, this passes
   * an {@link InvalidValueException} to the service's {@link ErrorHandler}.
   * If a service error occurs, this passes a {@link MemcacheServiceException}.
   * See {@link BaseMemcacheService#setErrorHandler(ErrorHandler)}.
   *
   * @param key the key object used to store the cache entry
   * @return the value object previously stored, or {@code null}
   * @throws IllegalArgumentException if {@code key} is not
   *    {@link Serializable} and is not {@code null}
   */
  Object get(Object key);

  /**
   * Similar to {@link #get}, but returns an object that can later be used
   * to perform a {@link #putIfUntouched} operation.
   *
   * <p>If an error deserializing the value occurs, this passes
   * an {@link InvalidValueException} to the service's {@link ErrorHandler}.
   * If a service error occurs, this passes a {@link MemcacheServiceException}.
   * See {@link BaseMemcacheService#setErrorHandler(ErrorHandler)}.
   *
   * @param key the key object used to store the cache entry
   * @return an {@link IdentifiableValue} object that wraps the
   * value object previously stored.  {@code null} is returned if {@code key}
   * is not present in the cache.
   * @throws IllegalArgumentException if {@code key} is not
   *    {@link Serializable} and is not {@code null}
   */
  IdentifiableValue getIdentifiable(Object key);

  /**
   * Performs a getIdentifiable for multiple keys at once.
   * This is more efficient than multiple separate calls to
   * {@link #getIdentifiable(Object)}.
   *
   * <p>If an error deserializing the value occurs, this passes
   * an {@link InvalidValueException} to the service's {@link ErrorHandler}.
   * If a service error occurs, this passes a {@link MemcacheServiceException}.
   * See {@link BaseMemcacheService#setErrorHandler(ErrorHandler)}.
   *
   * @param keys a collection of keys for which values should be retrieved
   * @return a mapping from keys to values of any entries found. If a requested
   *     key is not found in the cache, the key will not be in the returned Map.
   * @throws IllegalArgumentException if any element of {@code keys} is not
   *    {@link Serializable} and is not {@code null}
   */
  <T> Map<T, IdentifiableValue> getIdentifiables(Collection<T> keys);

  /**
   * Tests whether a given value is in cache, even if its value is {@code null}.
   *
   * <p>Note that, because an object may be removed from cache at any time, the
   * following is not sound code:
   * <pre>{@code
   *   if (memcache.contains("key")) {
   *     foo = memcache.get("key");
   *     if (foo == null) {
   *       // continue, assuming foo had the real value null
   *     }
   *   }
   *  }</pre>
   *  The problem is that the cache could have dropped the entry between the
   *  call to {@link #contains} and {@link #get(Object)}.  This is
   *  a sounder pattern:
   * <pre>{@code
   *   foo = memcache.get("key");
   *   if (foo == null) {
   *     if (memcache.contains("key")) {
   *       // continue, assuming foo had the real value null
   *     } else {
   *       // continue; foo may have had a real null, but has been dropped now
   *     }
   *   }
   *  }</pre>
   *  Another alternative is to prefer {@link #getAll(Collection)}, although
   *  it requires making an otherwise-unneeded {@code Collection} of some sort.
   *
   * @param key the key object used to store the cache entry
   * @return {@code true} if the cache contains an entry for the key
   * @throws IllegalArgumentException if {@code key} is not
   *    {@link Serializable} and is not {@code null}
   */
  boolean contains(Object key);

  /**
   * Performs a get of multiple keys at once.  This is more efficient than
   * multiple separate calls to {@link #get(Object)}, and allows a single
   * call to both test for {@link #contains(Object)} and also fetch the value,
   * because the return will not include mappings for keys not found.
   *
   * <p>If an error deserializing the value occurs, this passes
   * an {@link InvalidValueException} to the service's {@link ErrorHandler}.
   * If a service error occurs, this passes a {@link MemcacheServiceException}.
   * See {@link BaseMemcacheService#setErrorHandler(ErrorHandler)}.
   *
   * @param keys a collection of keys for which values should be retrieved
   * @return a mapping from keys to values of any entries found.
   *    If a requested key is not found in the cache, the key will not be in
   *    the returned Map.
   * @throws IllegalArgumentException if any element of {@code keys} is not
   *    {@link Serializable} and is not {@code null}
   * @throws InvalidValueException for any error in deserializing the cache
   *    value
   */
  <T> Map<T, Object> getAll(Collection<T> keys);

  /**
   * Store a new value into the cache, using {@code key}, but subject to the
   * {@code policy} regarding existing entries.
   *
   * @param key the key for the new cache entry
   * @param value the value to be stored
   * @param expires an {@link Expiration} object to set time-based expiration.
   *    {@code null} may be used indicate no specific expiration.
   * @param policy Requests particular handling regarding pre-existing entries
   *    under the same key.  This parameter must not be {@code null}.
   * @return {@code true} if a new entry was created, {@code false} if not
   *    because of the {@code policy}
   * @throws IllegalArgumentException if {@code key} or {@code value} is not
   *    {@link Serializable} and is not {@code null}
   * @throws MemcacheServiceException if server responds with an error and a
   *    {@link ConsistentErrorHandler} is not configured
   */
  boolean put(Object key, Object value, Expiration expires, SetPolicy policy);

  /**
   * Convenience put, equivalent to {@link #put(Object,Object,Expiration,
   * SetPolicy) put(key, value, expiration, SetPolicy.SET_ALWAYS)}.
   *
   * @param key key of the new entry
   * @param value value for the new entry
   * @param expires time-based {@link Expiration}, or {@code null} for none
   * @throws IllegalArgumentException if {@code key} or {@code value} is not
   *    {@link Serializable} and is not {@code null}
   * @throws MemcacheServiceException if server responds with an error and a
   *    {@link ConsistentErrorHandler} is not configured
   */
  void put(Object key, Object value, Expiration expires);

  /**
   * A convenience shortcut, equivalent to {@link #put(Object,Object,
   * Expiration,SetPolicy) put(key, value, null, SetPolicy.SET_ALWAYS)}.
   *
   * @param key key of the new entry
   * @param value value for the new entry
   * @throws IllegalArgumentException if {@code key} or {@code value} is not
   *    {@link Serializable} and is not {@code null}
   * @throws MemcacheServiceException if server responds with an error and a
   *    {@link ConsistentErrorHandler} is not configured
   */
  void put(Object key, Object value);

  /**
   * A batch-processing variant of {@link #put}.  This is more efficiently
   * implemented by the service than multiple calls.
   *
   * @param values the key/value mappings to add to the cache
   * @param expires the expiration time for all {@code values}, or
   *    {@code null} for no time-based expiration.
   * @param policy what to do if the entry is or is not already present
   * @return the set of keys for which entries were created.  Keys in
   *    {@code values} may not be in the returned set because of the
   *    {@code policy} regarding pre-existing entries.
   * @throws IllegalArgumentException if any of the keys or values are not
   *    {@link Serializable} and are not {@code null}
   * @throws MemcacheServiceException if server responds with an error for any
   *    of the given values and a {@link ConsistentErrorHandler} is not
   *    configured
   */
  <T> Set<T> putAll(Map<T, ?> values, Expiration expires, SetPolicy policy);

  /**
   * Convenience multi-put, equivalent to {@link #putAll(Map,Expiration,
   * SetPolicy) putAll(values, expires, SetPolicy.SET_ALWAYS)}.
   *
   * @param values key/value mappings to add to the cache
   * @param expires expiration time for the new values, or {@code null} for no
   *    time-based expiration
   * @throws IllegalArgumentException if any of the keys or values are not
   *    {@link Serializable} and are not {@code null}
   * @throws MemcacheServiceException if server responds with an error for any
   *    of the given values and a {@link ConsistentErrorHandler} is not
   *    configured
   */
  void putAll(Map<?, ?> values, Expiration expires);

  /**
   * Convenience multi-put, equivalent to {@link #putAll(Map,Expiration,
   * SetPolicy) putAll(values, expires, SetPolicy.SET_ALWAYS)}.
   *
   * @param values key/value mappings for new entries to add to the cache
   * @throws IllegalArgumentException if any of the keys or values are not
   *    {@link Serializable} and are not {@code null}
   * @throws MemcacheServiceException if server responds with an error for any
   *    of the given values and a {@link ConsistentErrorHandler} is not
   *    configured
   */
  void putAll(Map<?, ?> values);

  /**
   * Atomically, store {@code newValue} only if no other value has been stored
   * since {@code oldValue} was retrieved. {@code oldValue} is an
   * {@link IdentifiableValue} that was returned from a previous call to
   * {@link #getIdentifiable}.
   *
   * <p>If another value in the cache for {@code key} has been stored, or if
   * this cache entry has been evicted, then nothing is stored by this call and
   * {@code false} is returned.
   *
   * <p>Note that storing the same value again <i>does</i> count as a "touch"
   * for this purpose.
   *
   * <p>Using {@link #getIdentifiable} and {@link #putIfUntouched} together
   * constitutes an operation that either succeeds atomically or fails due to
   * concurrency (or eviction), in which case the entire operation can be
   * retried by the application.
   *
   * @param key key of the entry
   * @param oldValue identifier for the value to compare against newValue
   * @param newValue new value to store if oldValue is still there
   * @param expires an {@link Expiration} object to set time-based expiration.
   *    {@code null} may be used to indicate no specific expiration.
   * @return {@code true} if {@code newValue} was stored,
   *    {@code false} otherwise
   * @throws IllegalArgumentException if {@code key} or {@code newValue} is
   *    not {@link Serializable} and is not {@code null}. Also throws
   *    IllegalArgumentException if {@code oldValue} is {@code null}.
   * @throws MemcacheServiceException if server responds with an error and a
   *    {@link ConsistentErrorHandler} is not configured
   */
  boolean putIfUntouched(Object key, IdentifiableValue oldValue,
                         Object newValue, Expiration expires);

  /**
   * Convenience shortcut, equivalent to {@link #putIfUntouched(Object,
   * IdentifiableValue,Object,Expiration) put(key, oldValue, newValue, null)}.
   *
   * @param key key of the entry
   * @param oldValue identifier for the value to compare against newValue
   * @param newValue new value to store if oldValue is still there
   * @return {@code true} if {@code newValue} was stored,
   *    {@code false} otherwise.
   * @throws IllegalArgumentException if {@code key} or {@code newValue} is
   *    not {@link Serializable} and is not {@code null}. Also throws
   *    IllegalArgumentException if {@code oldValue} is {@code null}.
   * @throws MemcacheServiceException if server responds with an error and a
   *    {@link ConsistentErrorHandler} is not configured
   */
  boolean putIfUntouched(Object key, IdentifiableValue oldValue, Object newValue);

  /**
   * Convenience shortcut, equivalent to
   * {@link #putIfUntouched(Map, Expiration) putIfUntouched(values, null)}.
   *
   * @param values the key/values mappings to compare and swap
   * @return the set of keys for which the new value was stored.
   * @throws IllegalArgumentException if any of the keys are not
   *    {@link Serializable} or any of the values are not {@link Serializable}
   *    or {@code null}
   * @throws IllegalArgumentException If any of the keys or newValues are not
   *    {@link Serializable} and are not {@code null}. Also throws
   *    IllegalArgumentException if {@code values} has any nulls.
   * @throws MemcacheServiceException if server responds with an error for any
   *    of the given {@code values} and a {@link ConsistentErrorHandler} is not
   *    configured
   */
  <T> Set<T> putIfUntouched(Map<T, CasValues> values);

  /**
   * A batch-processing variant of {@link #putIfUntouched(Object,
   * IdentifiableValue,Object,Expiration)}. This is more efficient than
   * multiple single value calls.
   *
   * @param values the key/values mappings to compare and swap
   * @param expiration an {@link Expiration} object to set time-based
   *     expiration for a {@link CasValues value} with a {@code null}
   *     {@link Expiration expiration} value.
   *     {@code null} may be used to indicate no specific expiration.
   * @return the set of keys for which the new value was stored.
   * @throws IllegalArgumentException If any of the keys or newValues are not
   *    {@link Serializable} and are not {@code null}. Also throws
   *    IllegalArgumentException if {@code values} has any nulls.
   * @throws MemcacheServiceException if server responds with an error for any
   *    of the given {@code values} and a {@link ConsistentErrorHandler} is not
   *    configured
   */
  <T> Set<T> putIfUntouched(Map<T, CasValues> values, Expiration expiration);

  /**
   * Removes {@code key} from the cache.
   *
   * @param key the key of the entry to delete.
   * @return {@code true} if an entry existed, but was discarded
   * @throws IllegalArgumentException if {@code key} is not
   *    {@link Serializable} and is not {@code null}
   */
  boolean delete(Object key);

  /**
   * Removes the given key from the cache, and prevents it from being added
   * under the {@link SetPolicy#ADD_ONLY_IF_NOT_PRESENT} policy for
   * {@code millisNoReAdd} milliseconds thereafter.  Calls to a {@link #put}
   * method using {@link SetPolicy#SET_ALWAYS} are not blocked, however.
   *
   * @param key key to delete
   * @param millisNoReAdd time during which calls to put using
   *    ADD_IF_NOT_PRESENT should be denied.
   * @return {@code true} if an entry existed to delete
   * @throws IllegalArgumentException if {@code key} is not
   *    {@link Serializable} and is not {@code null}
   */
  boolean delete(Object key, long millisNoReAdd);

  /**
   * Batch version of {@link #delete(Object)}.
   *
   * @param keys a collection of keys for entries to delete
   * @return the Set of keys deleted.  Any keys in {@code keys} but not in the
   *    returned set were not found in the cache. The iteration order of the
   *    returned set matches the iteration order of the provided {@code keys}.
   * @throws IllegalArgumentException if any element of {@code keys} is not
   *    {@link Serializable} and is not {@code null}
   */
  <T> Set<T> deleteAll(Collection<T> keys);

  /**
   * Batch version of {@link #delete(Object, long)}.
   *
   * @param keys a collection of keys for entries to delete
   * @param millisNoReAdd time during which calls to put using
   *    {@link SetPolicy#ADD_ONLY_IF_NOT_PRESENT} should be denied.
   * @return the Set of keys deleted.  Any keys in {@code keys} but not in the
   *    returned set were not found in the cache. The iteration order of the
   *    returned set matches the iteration order of the provided {@code keys}.
   * @throws IllegalArgumentException if any element of {@code keys} is not
   *    {@link Serializable} and is not {@code null}
   */
  <T> Set<T> deleteAll(Collection<T> keys, long millisNoReAdd);

  /**
   * Atomically fetches, increments, and stores a given integral value.
   * "Integral" types are {@link Byte}, {@link Short}, {@link Integer},
   * {@link Long}, and in some cases {@link String}. The entry must already
   * exist, and have a non-negative value.
   *
   * <p>Incrementing by positive amounts will reach signed 64-bit max (
   * {@code 2^63 - 1}) and then wrap-around to signed 64-bit min ({@code -2^63}
   * ), continuing increments from that point.
   *
   * <p>To facilitate use as an atomic countdown, incrementing by a negative
   * value (i.e. decrementing) will not go below zero: incrementing {@code 2} by
   * {@code -5} will return {@code 0}, not {@code -3}.
   *
   * <p>Note: The actual representation of all numbers in Memcache is a string.
   * This means if you initially stored a number as a string (e.g., "10") and
   * then increment it, everything will work properly.
   *
   * <p>When you {@link #get(Object)} a key for a string value, wrapping occurs
   * after exceeding the max value of an unsigned 64-bit number
   * ({@code 2^64 - 1}). When you {@link #get(Object)} a key
   * for a numerical type, wrapping occurs after exceeding the type max value.
   *
   * <p>If a service error occurs, this passes a {@link
   * MemcacheServiceException} to the service's {@link ErrorHandler}. See
   * {@link BaseMemcacheService#setErrorHandler(ErrorHandler)}.
   *
   * @param key the key of the entry to manipulate
   * @param delta the size of the increment, positive or negative
   * @return the post-increment value, as a long. However, a
   *    {@link #get(Object)} of the key will still have the original type (
   *    {@link Byte}, {@link Short}, etc.). If there is no entry for
   *    {@code key}, returns {@code null}.
   * @throws IllegalArgumentException if {@code key} is not
   *    {@link Serializable} and is not {@code null}
   * @throws InvalidValueException if the object incremented is not of a
   *    integral type or holding a negative value
   */
  Long increment(Object key, long delta);

  /**
   * Like normal increment, but allows for an optional initial value for the
   * key to take on if not already present in the cache.
   *
   * <p>Note, the provided initial value can be negative, which allows
   * incrementing negative numbers.  This is in contrast to the base version of
   * this method, which requires a pre-existing value (e.g. one stored with
   * {@link #put}) to be non-negative prior to being incremented.
   *
   * @param initialValue value to insert into the cache if the key is not
   *    present
   * @see #increment(Object, long)
   */
  Long increment(Object key, long delta, Long initialValue);

  /**
   * Like normal increment, but increments a batch of separate keys in
   * parallel by the same delta.
   *
   * @return mapping keys to their new values; values will be null if they
   *    could not be incremented or were not present in the cache
   * @see #increment(Object, long)
   */
  <T> Map<T, Long> incrementAll(Collection<T> keys, long delta);

  /**
   * Like normal increment, but increments a batch of separate keys in
   * parallel by the same delta and potentially sets a starting value.
   *
   * @param initialValue value to insert into the cache if the key is not
   *    present
   * @return mapping keys to their new values; values will be null if they
   *    could not be incremented for whatever reason
   * @see #increment(Object, long)
   */
  <T> Map<T, Long> incrementAll(Collection<T> keys, long delta, Long initialValue);

  /**
   * Like normal increment, but accepts a mapping of separate controllable
   * offsets for each key individually. Good for incrementing by a sum and
   * a count in parallel.
   *
   * @return mapping keys to their new values; values will be null if they
   *    could not be incremented for whatever reason
   * @see #increment(Object, long)
   */
  <T> Map<T, Long> incrementAll(Map<T, Long> offsets);

  /**
   * Like normal increment, but accepts a mapping of separate controllable
   * offsets for each key individually. Good for incrementing by a sum and
   * a count in parallel. Callers may also pass an initial value for the keys
   * to take on if they are not already present in the cache.
   *
   * @return mapping keys to their new values; values will be null if they
   *    could not be incremented for whatever reason
   * @see #increment(Object, long)
   */
  <T> Map<T, Long> incrementAll(Map<T, Long> offsets, Long initialValue);

  /**
   * Empties the cache of all values across all namespaces.  Statistics are not affected.
   */
  void clearAll();

  /**
   * Fetches some statistics about the cache and its usage.
   *
   * @return statistics for the cache. Note that this method returns
   * aggregated {@link Stats} for all namespaces. Response will never be
   * {@code null}.
   */
  Stats getStatistics();

}
