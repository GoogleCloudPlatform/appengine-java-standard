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

package com.google.appengine.tools.development;

import com.google.common.base.VerifyException;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.MessageLite;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** A class of utilities for working with API-related classes. */
public class ApiUtils {

  // This class should have no instances, all util methods are made as public static methods.
  private ApiUtils() {}

  /**
   * Convert the specified byte array to a protocol buffer representation of the specified type.
   * This type is {@link Message} (the open-sourced protocol buffer implementation).
   */
  public static Object convertBytesToPb(byte[] bytes, Class<?> messageClass)
      throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
    try {
      Class<?> protocolMessageClass = Class.forName("com.google.io.protocol.ProtocolMessage");
      if (protocolMessageClass.isAssignableFrom(messageClass)) {
        Object proto = messageClass.getDeclaredConstructor().newInstance();
        Method mergeFromMethod = messageClass.getMethod("mergeFrom", byte[].class);
        if (!(Boolean) mergeFromMethod.invoke(proto, bytes)) {
          throw new VerifyException(
              "Could not parse request bytes into " + classDescription(messageClass));
        }
        return proto;
      }
    } catch (ClassNotFoundException e) {
      // If we can't find ProtocolMessage, we don't have to worry about converting to it.
    } catch (ReflectiveOperationException e) {
      // If we found ProtocolMessage but couldn't instantiate or call merge on messageClass,
      // that's an unexpected error.
      throw new VerifyException(e);
    }
    Message.Builder proto;
    Class<?> currentClass = messageClass;
    // here messageClass is a Query.Builder, not a Query
    if (Message.Builder.class.isAssignableFrom(messageClass)) {
      currentClass = messageClass.getEnclosingClass();
    }

    if (Message.class.isAssignableFrom(currentClass)) {
      Method newBuilderMethod = currentClass.getMethod("newBuilder");
      proto = (Message.Builder) newBuilderMethod.invoke(null);
    } else {
      throw new UnsupportedOperationException(
          String.format("Cannot assign %s to %s", classDescription(currentClass), Message.class));
    }
    boolean parsed = true;
    try {
      proto.mergeFrom(bytes, ExtensionRegistry.getEmptyRegistry());
    } catch (InvalidProtocolBufferException e) {
      parsed = false;
    }
    if (!parsed) {
      throw new VerifyException(
          "Could not parse request bytes into (parse=)"
              + parsed
              + " "
              + classDescription(currentClass));
    }
    if (Message.Builder.class.isAssignableFrom(messageClass)) {
      return proto;
    } else {
      return proto.build();
    }
  }

  /**
   * Convert the protocol buffer representation to a byte array. The object is an instance of {@link
   * Message} (the open-sourced protocol buffer implementation).
   */
  public static byte[] convertPbToBytes(Object object) {
    if (object instanceof MessageLite messageLite) {
      return messageLite.toByteArray();
    }
    try {
      Class<?> protocolMessageClass = Class.forName("com.google.io.protocol.ProtocolMessage");
      if (protocolMessageClass.isInstance(object)) {
        Method toByteArrayMethod = object.getClass().getMethod("toByteArray");
        return (byte[]) toByteArrayMethod.invoke(object);
      }
    } catch (ClassNotFoundException e) {
      // If we can't find ProtocolMessage, we don't have to worry about converting it.
    } catch (ReflectiveOperationException e) {
      // If we found ProtocolMessage but couldn't call toByteArray on object,
      // that's an unexpected error.
      throw new VerifyException(e);
    }
    throw new UnsupportedOperationException(
        String.format(
            "%s is neither %s nor %s",
            classDescription(object.getClass()), Message.class, Message.class));
  }

  /**
   * Create a textual description of a class that is appropriate for troubleshooting problems with
   * {@link #convertBytesToPb(byte[], Class)} or {@link #convertPbToBytes(Object)}.
   *
   * @param klass The class to create a description for.
   * @return A string description.
   */
  private static String classDescription(Class<?> klass) {
    return String.format(
        "(%s extends %s loaded from %s)",
        klass, klass.getSuperclass(), klass.getProtectionDomain().getCodeSource().getLocation());
  }

  /* Determine if yaml configuration is preferred than xml. */
  public static boolean isPromotingYaml() {
    return Boolean.getBoolean("appengine.promoteYaml");
  }
}
