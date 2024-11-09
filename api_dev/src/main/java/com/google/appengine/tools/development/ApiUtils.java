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
  public static <T> T convertBytesToPb(byte[] bytes, Class<T> messageClass)
      throws IllegalAccessException, InstantiationException, InvocationTargetException,
          NoSuchMethodException {
    if (Message.class.isAssignableFrom(messageClass)) {
      Method newBuilderMethod = messageClass.getMethod("newBuilder");
      Message.Builder proto = (Message.Builder) newBuilderMethod.invoke(null);
      boolean parsed = true;
      try{
        proto.mergeFrom(bytes);
      }
      catch (InvalidProtocolBufferException e){
        parsed = false;
      }
      if (!parsed || !proto.isInitialized()) {
        throw new RuntimeException(
            "Could not parse request bytes into " + classDescription(messageClass));
      }
      return messageClass.cast(proto.build());
    }
    if (Message.class.isAssignableFrom(messageClass)) {
      Method method = messageClass.getMethod("parseFrom", byte[].class);
      return messageClass.cast(method.invoke(null, bytes));
    }
    throw new UnsupportedOperationException(
        String.format(
            "Cannot assign %s to either %s or %s",
            classDescription(messageClass), Message.class, Message.class));
  }

  /**
   * Convert the protocol buffer representation to a byte array. The object is an instance of {@link
   * Message} (the open-sourced protocol buffer implementation).
   */
  public static byte[] convertPbToBytes(Object object) {
    if (object instanceof MessageLite) {
      return ((MessageLite) object).toByteArray();
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
