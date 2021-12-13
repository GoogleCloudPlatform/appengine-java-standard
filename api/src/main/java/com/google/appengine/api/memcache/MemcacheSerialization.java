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

import static com.google.common.io.BaseEncoding.base64;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.primitives.Bytes;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Static serialization helpers used by {@link MemcacheServiceImpl}
 *
 * This class is thread-safe.
 *
 *
 */
public class MemcacheSerialization {

  /**
   * Values used as flags on the MemcacheService's values.
   */
  public enum Flag {
    // For a MemcacheViewer to make sense, we need to have commonality between
    // these constants and those in <internal>
    BYTES,   // python TYPE_STR
    UTF8,    // python TYPE_UNICODE
    OBJECT,  // python TYPE_PICKLED (but our serialization is different!)
    INTEGER, // python TYPE_INT
    LONG,    // python TYPE_LONG
    BOOLEAN,  // python TYPE_BOOL
    BYTE,
    SHORT;

    private static final Flag[] VALUES = Flag.values();

    /**
     * While the enum is convenient, the implementation wants {@code int}s...
     * this factory converts {@code int} value to Flag value.
     */
    public static Flag fromInt(int i) {
      if (i < 0 || i >= VALUES.length) {
        throw new IllegalArgumentException();
      }
      return VALUES[i];
    }
  }

  /**
   * Tuple of a serialized byte array value and associated flags to interpret
   * that value.  Used as the return from {@link MemcacheSerialization#serialize}.
   */
  public static class ValueAndFlags {
    public final byte[] value;
    public final Flag flags;

    private ValueAndFlags(byte[] value, Flag flags) {
      this.value = value;
      this.flags = flags;
    }
  }

  /** Limit determined by memcache backend. */
  static final int MAX_KEY_BYTE_COUNT = 250;

  private static final byte FALSE_VALUE = '0'; // cache value for Boolean(false)
  private static final byte TRUE_VALUE = '1'; // cache value for Boolean(true)
  private static final String MYCLASSNAME = MemcacheSerialization.class.getName();

  /**
   * The SHA1 checksum engine, cloned for reuse.  We did test the hashing time
   * was negligible (17us/kb, linear); we don't "need" crypto-secure, but it's
   * a good way to minimize collisions.
   */
  private static final MessageDigest sha1Prototype;

  static {
    try {
      sha1Prototype = MessageDigest.getInstance("SHA-1");
    } catch (NoSuchAlgorithmException ex) {
      Logger.getLogger(MYCLASSNAME).log(Level.SEVERE,
          "Can't load SHA-1 MessageDigest!", ex);
      throw new MemcacheServiceException("No SHA-1 algorithm, cannot hash keys for memcache", ex);
    }
  }

  // The name of the system property that controls the use of the thread context
  // classloader for deserialization.
  public static final String USE_THREAD_CONTEXT_CLASSLOADER_PROPERTY =
      "appengine.api.memcache.useThreadContextClassLoader";

  private static class UseThreadContextClassLoaderHolder {
    static final boolean INSTANCE =
        Boolean.getBoolean(USE_THREAD_CONTEXT_CLASSLOADER_PROPERTY);
  }

  private MemcacheSerialization() {
    // non-instantiable
  }

  /**
   * Deserialize the object, according to its flags.  This would have private
   * visibility, but is also used by LocalMemcacheService for the increment
   * operation.
   *
   * @param value
   * @param flags
   * @return the Object originally stored
   * @throws ClassNotFoundException if the object can't be re-instantiated due
   *    to being an unlocatable type
   * @throws IOException if the object can't be re-instantiated for some other
   *    reason
   */
  public static Object deserialize(byte[] value, int flags)
      throws ClassNotFoundException, IOException {
    Flag flagval = Flag.fromInt(flags);

    switch (flagval) {
      case BYTES:
        return value;

      case BOOLEAN:
        if (value.length != 1) {
          throw new InvalidValueException("Cannot deserialize Boolean: bad length", null);
        }
        switch (value[0]) {
          case TRUE_VALUE:
            return Boolean.TRUE;
          case FALSE_VALUE:
            return Boolean.FALSE;
        }
        throw new InvalidValueException("Cannot deserialize Boolean: bad contents", null);

      case BYTE:
      case SHORT:
      case INTEGER:
      case LONG:
        long val = new BigInteger(new String(value, US_ASCII)).longValue();
        switch (flagval) {
          case BYTE:
            return (byte) val;
          case SHORT:
            return (short) val;
          case INTEGER:
            return (int) val;
          case LONG:
            return val;
          default:
            throw new InvalidValueException("Cannot deserialize number: bad contents", null);
        }

      case UTF8:
        return new String(value, UTF_8);

      case OBJECT:
        if (value.length == 0) {
          return null;
        }
        ByteArrayInputStream baos = new ByteArrayInputStream(value);

        ObjectInputStream objIn = null;
        if (UseThreadContextClassLoaderHolder.INSTANCE) {
          objIn = new ObjectInputStream(baos) {
            // If there are more user-defined class loaders, the default class loader by
            // ObjectInputStream might not be enough. For example, in Jetty, there are two
            // user-defined class loaders, i.e. startJarLoader (the parent) and WebAppClassLoader
            // (the child). startJarLoader normally loads this class, and WebAppClassLoader
            // normally loads application classes. When an application object is serialized,
            // startJarLoader will load this class and WebAppClassLoader will the application
            // class. However, when the application object is deserialized, startJarLoader will
            // be unfortunately used according to the logic of ObjectInputStream. This will cause
            // ClassNotFoundException because startJarLoader could not find the application class.
            @Override
            protected Class<?> resolveClass(ObjectStreamClass objectStreamClass)
                throws ClassNotFoundException, IOException {
              ClassLoader threadClassLoader = Thread.currentThread().getContextClassLoader();
              if (threadClassLoader == null) {
                return super.resolveClass(objectStreamClass);
              }

              try {
                return Class.forName(objectStreamClass.getName(), false, threadClassLoader);
              } catch (ClassNotFoundException ex) {
                return super.resolveClass(objectStreamClass);
              }
            }
          };
        } else {
          objIn = new ObjectInputStream(baos);
        }

        Object response = objIn.readObject();
        objIn.close();
        return response;

      default:
        assert false;
    }
    return null;
  }

  private static boolean noEmbeddedNulls(byte[] key) {
    return !Bytes.contains(key, (byte) 0);
  }

  /**
   * Converts the user's key Object into a byte[] for the MemcacheGetRequest.
   * Because the underlying service has a length limit, we actually use the
   * SHA1 hash of the serialized object as its key if it's not a basic type.
   * For the basic types (that is, {@code String}, {@code Boolean}, and the
   * fixed-point numbers), we use a human-readable representation.
   *
   * @param key
   * @return hash result.  For the key {@code null}, the hash is also
   *   {@code null}.
   */
  public static byte[] makePbKey(Object key) throws IOException {
    if (key == null) {
      return new byte[0];
    }

    // Subtracting 2 from length to account for added quotes.
    if (key instanceof String && ((String) key).length() <= MAX_KEY_BYTE_COUNT - 2) {
      byte[] bytes = ("\"" + key + "\"").getBytes(UTF_8);

      // Check for rare case when multi-bytes unicode chars pushes key over size limit
      if (bytes.length <= MAX_KEY_BYTE_COUNT) {
        return bytes; // normal case
      }

    } else if (key instanceof byte[]) {
      // In most cases a byte-array key given to the Java API is passed unchanged to the backend.
      // This allows for interoperability with Python ASCII strings and PHP strings, so that for
      // example a Java key "foo".getBytes() will store in the same memcache location as a Python or
      // PHP key 'foo'. Note, that in the case of an embedded null byte we do not throw an exception
      // like we do in other language runtimes but instead, for backward compatibility, we leave
      // that case to be handled later in this method by SHA1 hashing.
      byte[] bytes = (byte[]) key;

      if (bytes.length <= MAX_KEY_BYTE_COUNT && noEmbeddedNulls(bytes)) {
         // This byte-array key meets all the conditions (no null bytes and not too long) to be
         // passed through directly to the backend.
         return bytes;
      }

    } else if (key instanceof Long || key instanceof Integer
               || key instanceof Short || key instanceof Byte) {
      return (key.getClass().getName() + ":" + key).getBytes(UTF_8);

    } else if (key instanceof Boolean) {
      return (((Boolean) key) ? "true" : "false").getBytes(UTF_8);

    }

    // Fallback case for types other than strings, bytearrays, base types, or for when they
    // would be too long or contain null
    return hash(key);
  }

  private static final byte[] hash(Object key) throws IOException {
    ValueAndFlags vaf = serialize(key);
    MessageDigest md;
    try {
      md = (MessageDigest) sha1Prototype.clone();
    } catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
    md.update(vaf.value);
    byte[] sha1hash = md.digest();
    // Memcache doesn't allow keys to contain null bytes, so we need
    // to Base64 encode the results.
    return base64().encode(sha1hash).getBytes(UTF_8);
  }

  /**
   * @param value
   * @return the ValueAndFlags containing a serialized representation of the
   *    Object and the flags to hint deserialization.
   * @throws IOException for serialization errors, normally due to a
   *    non-serializable object type
   */
  public static ValueAndFlags serialize(Object value)
      throws IOException {
    Flag flags;
    byte[] bytes;

    // TODO: as all types except Serialization are final we could
    // avoid the instance of by checking class equality
    if (value == null) {
      bytes = new byte[0];
      flags = Flag.OBJECT;

    } else if (value instanceof byte[]) {
      flags = Flag.BYTES;
      bytes = (byte[]) value;

    } else if (value instanceof Boolean) {
      flags = Flag.BOOLEAN;
      bytes = new byte[1];
      bytes[0] = ((Boolean) value) ? TRUE_VALUE : FALSE_VALUE;

    } else if (value instanceof Integer || value instanceof Long
        || value instanceof Byte || value instanceof Short) {
      bytes = value.toString().getBytes(US_ASCII);
      if (value instanceof Integer) {
        flags = Flag.INTEGER;
      } else if (value instanceof Long) {
        flags = Flag.LONG;
      } else if (value instanceof Byte) {
        flags = Flag.BYTE;
      } else {
        flags = Flag.SHORT;
      }

    } else if (value instanceof String) {
      flags = Flag.UTF8;
      bytes = ((String) value).getBytes(UTF_8);

    } else if (value instanceof Serializable) {
      flags = Flag.OBJECT;
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream objOut = new ObjectOutputStream(baos);
      objOut.writeObject(value);
      objOut.close();
      bytes = baos.toByteArray();

    } else {
      throw new IllegalArgumentException("can't accept " + value.getClass()
          + " as a memcache entity");
    }
    return new ValueAndFlags(bytes, flags);
  }
}
