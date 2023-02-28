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

import static com.google.common.io.BaseEncoding.base64Url;

import com.google.common.base.CharMatcher;
import com.google.storage.onestore.v3.OnestoreEntity.Reference;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * This class enables direct creation of {@code Key} objects, both in the root entity group (no
 * parent) and as the child of a given parent. Keys can also be created indirectly by putting named
 * Entities into the datastore, which will allocate a new key. See {@link Entity#Entity(String,
 * String)} for details.
 *
 * <p>This class also has methods for encoding and decoding {@code Key} objects to and from strings.
 * Clients should not make any assumptions about the encoding format, except that it is a websafe
 * string that does not need to be quoted when used in HTML or in URLs.
 *
 * @see Entity
 */
public class KeyFactory {

  /**
   * Creates a new {@code Key} having no parent from its kind and ID.
   *
   * @param kind the kind of the key to create
   * @param id the numeric identifier of the key in {@code kind}, unique across all root entities of
   *     this kind, must not be zero
   */
  public static Key createKey(String kind, long id) {
    return createKey(null, kind, id);
  }

  /**
   * Creates a new {@code Key} with the provided parent from its kind and ID.
   *
   * @param parent the parent of the key to create, can be {@code null}
   * @param kind the kind of the key to create
   * @param id the numeric identifier of the key in {@code kind}, unique across all entities of this
   *     kind with the same parent, must not be zero
   */
  public static Key createKey(@Nullable Key parent, String kind, long id) {
    return createKey(parent, kind, id, null);
  }

  static Key createKey(
      @Nullable Key parent, String kind, long id, @Nullable AppIdNamespace appIdNamespace) {
    if (id == 0L) {
      throw new IllegalArgumentException("id cannot be zero");
    }
    return new Key(kind, parent, id, appIdNamespace);
  }

  /**
   * Creates a new {@code Key} having no parent from its kind and name.
   *
   * @param kind the kind of the key to create
   * @param name the name of the key in {@code kind}, as an arbitrary string unique across all root
   *     entities of this {@code kind}
   */
  public static Key createKey(String kind, String name) {
    return createKey(null, kind, name);
  }

  /**
   * Creates a new {@code Key} with the provided parent from its kind and name.
   *
   * @param parent the parent of the key to create, can be {@code null}
   * @param kind the kind of the key to create
   * @param name the name of the key in {@code kind}, as an arbitrary string unique across all
   *     entities of this {@code kind} with the same parent
   */
  public static Key createKey(@Nullable Key parent, String kind, String name) {
    return createKey(parent, kind, name, null);
  }

  static Key createKey(
      @Nullable Key parent, String kind, String name, @Nullable AppIdNamespace appIdNamespace) {
    if (name == null || name.length() == 0) {
      throw new IllegalArgumentException("name cannot be null or empty");
    }
    return new Key(kind, parent, name, appIdNamespace);
  }

  /**
   * Shorthand for invoking {@link #keyToString(Key)} on the result of {@link #createKey(String,
   * long)}
   *
   * @param kind the kind of the key to create
   * @param id the numeric identifier of the key in {@code kind}, unique across all root entities of
   *     this kind, must not be zero
   * @return A websafe {@code String} representation of the {@link Key} that was created
   */
  public static String createKeyString(String kind, long id) {
    return keyToString(createKey(kind, id));
  }

  /**
   * Shorthand for invoking {@link #keyToString(Key)} on the result of {@link #createKey(Key,
   * String, long)}
   *
   * @param parent the parent of the key to create, can be {@code null}.
   * @param kind the kind of the key to create
   * @param id the numeric identifier of the key in {@code kind}, unique across entities of this
   *     kind with the same parent, must not be zero
   * @return A websafe {@code String} representation of the {@link Key} that was created
   */
  public static String createKeyString(@Nullable Key parent, String kind, long id) {
    return keyToString(createKey(parent, kind, id));
  }

  /**
   * Shorthand for invoking {@link #keyToString(Key)} on the result of {@link #createKey(String,
   * String)}
   *
   * @param kind the kind of the key to create
   * @param name the name of the key in {@code kind}, as an arbitrary string unique across root
   *     entities of this {@code kind}
   * @return A websafe {@code String} representation of the {@link Key} that was created
   */
  public static String createKeyString(String kind, String name) {
    return keyToString(createKey(kind, name));
  }

  /**
   * Shorthand for invoking {@link #keyToString(Key)} on the result of {@link #createKey(Key,
   * String, String)}
   *
   * @param parent the parent of the key to create, can be {@code null}.
   * @param kind the kind of the key to create
   * @param name the name of the key in {@code kind}, as an arbitrary string unique across entities
   *     of this {@code kind} with the same parent
   * @return A websafe {@code String} representation of the {@link Key} that was created
   */
  public static String createKeyString(@Nullable Key parent, String kind, String name) {
    return keyToString(createKey(parent, kind, name));
  }

  /**
   * Converts a {@code Key} into a websafe string. For example, this string can safely be used as an
   * URL parameter embedded in a HTML document. Note that <code>
   * key.equals(KeyFactory.stringToKey(KeyFactory.keyToString(key))
   * </code> should evaluate to {@code true}.
   *
   * @param key The {@code Key} to convert to a {@code String}.
   * @return A websafe {@code String} representation of the provided {@code String}.
   * @throws IllegalArgumentException If the specified {@code Key} is incomplete.
   */
  public static String keyToString(Key key) {
    // Note: This must be consistent across languages.  If it changes,
    // make sure that the similar logic in Python changes as well.
    // There are also unit tests which need to be changed.
    if (!key.isComplete()) {
      throw new IllegalArgumentException("Key is incomplete.");
    } else {
      Reference reference = KeyTranslator.convertToPb(key);
      return base64Url().omitPadding().encode(reference.toByteArray());
    }
  }

  /**
   * Converts a {@code String}-representation of a {@code Key} into the {@code Key} instance it
   * represents. Note that <code>
   * str.equals(KeyFactory.keyToString(KeyFactory.stringToKey(str))
   * </code> should evaluate to {@code true} for all strings returned by {@link
   * KeyFactory#keyToString}.
   *
   * @param encoded The {@code String} representation of a {@code Key}.
   * @return The {@code Key} that the given {@code String} represents.
   * @throws IllegalArgumentException If the string cannot be parsed.
   */
  public static Key stringToKey(String encoded) {
    // Note: This must be consistent across languages.  If it changes,
    // make sure that the similar logic in Python changes as well.
    // There are unit tests which also need to be changed.
    int modulo = encoded.length() % 4;
    if (modulo != 0) {
      // Pad the string to a 4-byte boundary.
      encoded += "====".substring(modulo);
    }

    byte[] decodedBytes;
    try {
      decodedBytes = base64Url().decode(CharMatcher.whitespace().removeFrom(encoded));
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("Cannot parse: " + encoded, ex);
    }

    Reference reference = new Reference();
    boolean parsed = reference.parseFrom(decodedBytes);
    if (!parsed) {
      throw new IllegalArgumentException("Could not parse Reference");
    }
    // The validation in createFromPb should cover missing required fields, so no need for
    // an isInitialized check.
    return KeyTranslator.createFromPb(reference);
  }

  /**
   * Helper class that aids in the construction of {@link Key Keys} with ancestors. Initialize the
   * {@code Builder} with the topmost ancestor in your key path and then add children using the
   * {@link #addChild} overload that best suits your needs. When finished adding children, call
   * {@link #getKey()} to retrieve your {@link Key} or {@link #getString()} to retrieve your {@link
   * Key} encoded as a websafe {@link String}.
   *
   * <p>Examples:<br>
   *
   * <pre>{@code
   * import com.google.appengine.api.datastore.KeyFactory.Builder;
   *
   * ...
   *
   * Key key = new Builder("Person", 88).addChild("Address", 24).getKey();
   * String keyStr = new Builder("Photo Album", "Vacation").addChild("Photo", 1424).getString();
   * }</pre>
   */
  public static final class Builder {
    private Key current;

    /**
     * Create a {@code Builder}, establishing a {@link Key} constructed from the provided kind and
     * name as the topmost ancestor.
     *
     * @param kind the kind of the topmost ancestor
     * @param name the name of the topmost ancestor in {@code kind}, as an arbitrary string unique
     *     across root entities of this {@code kind}
     */
    public Builder(String kind, String name) {
      current = createKey(null, kind, name);
    }

    /**
     * Create a {@code Builder}, establishing a {@link Key} constructed from the provided kind and
     * id as the topmost ancestor.
     *
     * @param kind the kind of the topmost ancestor
     * @param id the numeric identifier of the topmost ancestor in {@code kind}, unique across root
     *     entities of this kind, must not be zero
     */
    public Builder(String kind, long id) {
      current = createKey(null, kind, id);
    }

    /**
     * Create a {@code Builder}, establishing the provided {@link Key} as the topmost ancestor.
     *
     * @param key the topmost ancestor
     */
    public Builder(Key key) {
      current = key;
    }

    /**
     * Add a {@link Key} constructed from the provided kind and name as the child of the {@link Key}
     * most recently added to the {@code Builder}.
     *
     * @param kind the kind of the child
     * @param name the name of the child in {@code kind}, as an arbitrary string unique across
     *     entities of this {@code kind} with the same parent
     * @return {@code this}
     */
    public Builder addChild(String kind, String name) {
      current = createKey(current, kind, name);
      return this;
    }

    /**
     * Add a {@link Key} constructed from the provided kind and id as the child of the {@link Key}
     * most recently added to the {@code Builder}.
     *
     * @param kind the kind of the child
     * @param id the numeric identifier of the child in {@code kind}, unique across entities of this
     *     kind with the same parent, must not be zero
     * @return {@code this}
     */
    public Builder addChild(String kind, long id) {
      current = createKey(current, kind, id);
      return this;
    }

    /** Returns the most recently added {@link Key}. */
    public Key getKey() {
      return current;
    }

    /** Returns the most recently added {@link Key}, encoded as a websafe {@link String}. */
    public String getString() {
      return keyToString(current);
    }
  }

  // All methods are static.  Do not instantiate.
  private KeyFactory() {}
}
