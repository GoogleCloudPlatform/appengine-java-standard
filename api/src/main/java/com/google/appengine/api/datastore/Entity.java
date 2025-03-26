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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Objects;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * {@code Entity} is the fundamental unit of data storage. It has an immutable identifier (contained
 * in the {@link Key}) object, a reference to an optional parent {@code Entity}, a kind (represented
 * as an arbitrary string), and a set of zero or more typed properties.
 *
 */
public final class Entity extends PropertyContainer implements Cloneable {

  @SuppressWarnings("hiding")
  static final long serialVersionUID = -836647825120453511L;

  /**
   * A reserved property name used to refer to the key of the entity. This string can be used for
   * filtering and sorting by the entity key itself.
   */
  public static final String KEY_RESERVED_PROPERTY = "__key__";

  /**
   * A reserved property name used to refer to the scatter property of the entity. Used for finding
   * split points (e.g. for mapping over a kind).
   */
  public static final String SCATTER_RESERVED_PROPERTY = "__scatter__";

  /** A reserved property name used to report an entity group's version. */
  public static final String VERSION_RESERVED_PROPERTY = "__version__";

  private final Key key;
  // NOTE: Cannot move this.
  // http://code.google.com/p/datanucleus-appengine/source/search?q=getdeclaredfield&origq=getdeclaredfield&btnG=Search+Trunk
  final Map<String, @Nullable Object> propertyMap;

  static interface WrappedValue {
    public @Nullable Object getValue();

    public boolean isIndexed();

    public boolean getForceIndexedEmbeddedEntity();
  }

  static final class WrappedValueImpl implements Serializable, WrappedValue {
    private static final long serialVersionUID = 47618472263544528L;

    private final @Nullable Object value;
    private final boolean indexed;
    private final boolean forceIndexedEmbeddedEntity;

    WrappedValueImpl(@Nullable Object value, boolean indexed, boolean forceIndexedEmbeddedEntity) {
      checkArgument(indexed || !forceIndexedEmbeddedEntity);
      this.value = value;
      this.indexed = indexed;
      this.forceIndexedEmbeddedEntity = forceIndexedEmbeddedEntity;
    }

    @Override
    public @Nullable Object getValue() {
      return value;
    }

    @Override
    public boolean isIndexed() {
      return indexed;
    }

    @Override
    public boolean getForceIndexedEmbeddedEntity() {
      return forceIndexedEmbeddedEntity;
    }

    @Override
    public boolean equals(@Nullable Object that) {
      if (that instanceof WrappedValueImpl) {
        WrappedValueImpl wv = (WrappedValueImpl) that;
        return ((value == null) ? wv.value == null : value.equals(wv.value))
            && indexed == wv.indexed
            && forceIndexedEmbeddedEntity == wv.forceIndexedEmbeddedEntity;
      }
      return false;
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(value, indexed, forceIndexedEmbeddedEntity);
    }

    @Override
    public String toString() {
      return String.valueOf(value) + "(wrapped:" + indexed + ":" + forceIndexedEmbeddedEntity + ")";
    }
  }

  /* Wraps a property value that should be stored but not indexed. */
  static final class UnindexedValue implements Serializable, WrappedValue {
    private static final long serialVersionUID = 293279595114451718L;
    private final @Nullable Object value;

    /** @param value may be null */
    UnindexedValue(@Nullable Object value) {
      this.value = value;
    }

    @Override
    public @Nullable Object getValue() {
      return value;
    }

    @Override
    public boolean isIndexed() {
      return false;
    }

    @Override
    public boolean getForceIndexedEmbeddedEntity() {
      return false;
    }

    @Override
    public boolean equals(@Nullable Object that) {
      if (that instanceof UnindexedValue) {
        UnindexedValue uv = (UnindexedValue) that;
        return (value == null) ? uv.value == null : value.equals(uv.value);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return (value == null) ? 0 : value.hashCode();
    }

    @Override
    public String toString() {
      return value + " (unindexed)";
    }
  }

  // TODO
  // Consider adding more validation here. Things like:
  // max lengths for kinds, property names, and paths,
  // see CL 6301439

  /**
   * Create a new {@code Entity} with the specified kind and no parent {@code Entity}. The
   * instantiated {@code Entity} will have an incomplete {@link Key} when this constructor returns.
   * The {@link Key} will remain incomplete until you put the {@code Entity}, after which time the
   * {@link Key} will have its {@code id} set.
   */
  public Entity(String kind) {
    this(kind, (Key) null);
  }

  /**
   * Create a new {@code Entity} with the specified kind and parent {@code Entity}. The instantiated
   * {@code Entity} will have an incomplete {@link Key} when this constructor returns. The {@link
   * Key} will remain incomplete until you put the {@code Entity}, after which time the {@link Key}
   * will have its {@code id} set.
   */
  public Entity(String kind, @Nullable Key parent) {

    // TODO - A note from ryanb on transaction support:
    //
    // Set the entity group immediately, as opposed to on Put.
    // In the python API, we check that all entities inside a tx are in
    // the same entity group, but until recently, we only set entity
    // group on Put. This meant that Putting new entities in txes
    // caused failures. see http://b/1019358
    this(new Key(kind, parent));
  }

  /**
   * Create a new {@code Entity} with the specified kind and key name and no parent {@code Entity}.
   * The instantiated {@code Entity} will have a complete {@link Key} when this constructor returns.
   * The {@link Key Key's} {@code name} field will be set to the value of {@code keyName}.
   *
   * <p>This constructor is syntactic sugar for {@code new Entity(KeyFactory.createKey(kind,
   * keyName))}.
   */
  public Entity(String kind, String keyName) {
    this(KeyFactory.createKey(kind, keyName));
  }

  /**
   * Create a new {@code Entity} with the specified kind and ID and no parent {@code Entity}. The
   * instantiated {@code Entity} will have a complete {@link Key} when this constructor returns. The
   * {@link Key Key's} {@code id} field will be set to the value of {@code id}.
   *
   * <p>Creating an entity for the purpose of insertion (as opposed to update) with this constructor
   * is discouraged unless the id was obtained from a key returned by a {@link KeyRange} obtained
   * from {@link AsyncDatastoreService#allocateIds(String, long)} or {@link
   * DatastoreService#allocateIds(String, long)} for the same kind.
   *
   * <p>This constructor is syntactic sugar for {@code new Entity(KeyFactory.createKey(kind, id))}.
   */
  public Entity(String kind, long id) {
    this(KeyFactory.createKey(kind, id));
  }

  /**
   * Create a new {@code Entity} with the specified kind, key name, and parent {@code Entity}. The
   * instantiated {@code Entity} will have a complete {@link Key} when this constructor returns. The
   * {@link Key Key's} {@code name} field will be set to the value of {@code keyName}.
   *
   * <p>This constructor is syntactic sugar for {@code new Entity(KeyFactory.createKey(parent, kind,
   * keyName))}.
   */
  public Entity(String kind, String keyName, Key parent) {
    this(KeyFactory.createKey(parent, kind, keyName));
  }

  /**
   * Create a new {@code Entity} with the specified kind and ID and parent {@code Entity}. The
   * instantiated {@code Entity} will have a complete {@link Key} when this constructor returns. The
   * {@link Key Key's} {@code id} field will be set to the value of {@code id}.
   *
   * <p>Creating an entity for the purpose of insertion (as opposed to update) with this constructor
   * is discouraged unless the id was obtained from a key returned by a {@link KeyRange} obtained
   * from {@link AsyncDatastoreService#allocateIds(Key, String, long)} or {@link
   * DatastoreService#allocateIds(Key, String, long)} for the same parent and kind.
   *
   * <p>This constructor is syntactic sugar for {@code new Entity(KeyFactory.createKey(parent, kind,
   * id))}.
   */
  public Entity(String kind, long id, Key parent) {
    this(KeyFactory.createKey(parent, kind, id));
  }

  /**
   * Create a new {@code Entity} uniquely identified by the provided {@link Key}. Creating an entity
   * for the purpose of insertion (as opposed to update) with a key that has its {@code id} field
   * set is strongly discouraged unless the key was returned by a {@link KeyRange}.
   *
   * @see KeyRange
   */
  public Entity(Key key) {
    this.key = key;
    this.propertyMap = new HashMap<>();
  }

  /**
   * Two {@code Entity} objects are considered equal if they refer to the same entity (i.e. their
   * {@code Key} objects match).
   */
  @Override
  public boolean equals(@Nullable Object object) {
    if (object instanceof Entity) {
      Entity otherEntity = (Entity) object;
      return key.equals(otherEntity.key);
    }
    return false;
  }

  /**
   * Returns the {@code Key} that represents this {@code Entity}. If the entity has not yet been
   * saved (e.g. via {@code DatastoreService.put}), this {@code Key} will not be fully specified and
   * cannot be used for certain operations (like {@code DatastoreService.get}). Once the {@code
   * Entity} has been saved, its {@code Key} will be updated to be fully specified.
   */
  public Key getKey() {
    return key;
  }

  /**
   * Returns a logical type that is associated with this {@code Entity}. This is simply a
   * convenience method that forwards to the {@code Key} for this {@code Entity}.
   */
  public String getKind() {
    return key.getKind();
  }

  /**
   * Get a {@code Key} that corresponds to this the parent {@code Entity} of this {@code Entity}.
   * This is simply a convenience method that forwards to the {@code Key} for this {@code Entity}.
   */
  public @Nullable Key getParent() {
    return key.getParent();
  }

  @Override
  public int hashCode() {
    return key.hashCode();
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder("<Entity [");
    builder.append(key).append("]:\n");
    appendPropertiesTo(builder);
    builder.append(">\n");
    return builder.toString();
  }

  /**
   * Returns the identifier of the application that owns this {@code Entity}. This is simply a
   * convenience method that forwards to the {@code Key} for this {@code Entity}.
   */
  public String getAppId() {
    return key.getAppId();
  }

  /**
   * Returns the AppIdNamespace of the application/namespace that owns this {@code Entity}. This is
   * simply a convenience method that forwards to the {@code Key} for this {@code Entity}.
   */
  AppIdNamespace getAppIdNamespace() {
    return key.getAppIdNamespace();
  }

  /**
   * Returns the namespace of the application/namespace that owns this {@code Entity}. This is
   * simply a convenience method that forwards to the {@code Key} for this {@code Entity}.
   */
  public String getNamespace() {
    return key.getNamespace();
  }

  /**
   * Returns a shallow copy of this {@code Entity} instance. {@code Collection} properties are
   * cloned as an {@code ArrayList}, the type returned from the datastore. Instances of mutable
   * datastore types are cloned as well. Instances of all other types are reused.
   *
   * @return a shallow copy of this {@code Entity}
   */
  @Override
  public Entity clone() {
    Entity entity = new Entity(key);
    entity.setPropertiesFrom(this);
    return entity;
  }

  // NOTE: This is here for backwards compatibility
  public void setPropertiesFrom(Entity src) {
    super.setPropertiesFrom(src);
  }

  @Override
  Map<String, @Nullable Object> getPropertyMap() {
    return propertyMap;
  }
}
