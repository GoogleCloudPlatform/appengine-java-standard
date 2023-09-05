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

package com.google.apphosting.utils.config;

import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.esotericsoftware.yamlbeans.YamlException;
import com.esotericsoftware.yamlbeans.YamlReader;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.Reader;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Helper methods for parsing YAML files.
 *
 */
public class YamlUtils {


  // From http://yaml.org/type/bool.html
  static final Pattern TRUE_PATTERN =
      Pattern.compile("y|Y|yes|Yes|YES|true|True|TRUE|on|On|ON");
  static final Pattern FALSE_PATTERN =
      Pattern.compile("n|N|no|No|NO|false|False|FALSE|off|Off|OFF");

  private static final String RESERVED_URL =
    "The URL '%s' is reserved and cannot be used.";

  private YamlUtils() { }

  /**
   * Parse a YAML !!bool type. YamlBeans only supports "true" or "false".
   *
   * @throws AppEngineConfigException
   */
  static boolean parseBoolean(String value) {
    if (TRUE_PATTERN.matcher(value).matches()) {
      return true;
    } else if (FALSE_PATTERN.matcher(value).matches()) {
      return false;
    }
    throw new AppEngineConfigException("Invalid boolean value '" + value + "'.");
  }

  /**
   * Check that a URL is not one of the reserved URLs according to
   * http://code.google.com/appengine/docs/java/configyaml/appconfig_yaml.html#Reserved_URLs
   *
   * @throws AppEngineConfigException
   */
  static void validateUrl(String url) {
    if (url.equals("/form")) {
      throw new AppEngineConfigException(String.format(RESERVED_URL, url));
    }
  }

  /**
   * Parses YAML from the given {@link Reader} to produce an object of the given class.
   *
   * <p>This is a wrapper for YamlBeans that replaces its use of reflection. Recent versions require
   * every property to have a field of the same name, which doesn't correspond to the way we use it
   * here. So instead we build only on its basic YAML parsing support to get a Map of the YAML
   * properties and their values (possibly consisting of nested Map and List values), and use
   * reflection to call the appropriate setters ourselves.
   *
   * <p>This is not intended to be a general-purpose solution, but suffices for the types we need to
   * build in this package.
   *
   * @return the parsed object, or null if there was nothing in {@code reader}. (Throwing an
   *     exception might be better, but null is consistent with existing code.)
   */
  static <T> T parse(Reader reader, Class<T> targetClass) throws YamlException {
    YamlReader yamlReader = new YamlReader(reader);
    @SuppressWarnings("unchecked")
    Map<String, ?> map = (Map<String, ?>) yamlReader.read(Map.class);
    if (map == null) {
      return null;
    }
    try {
      return targetClass.cast(decode(map, targetClass));
    } catch (NumberFormatException | ClassCastException e) {
      throw new YamlException(e.getMessage(), e);
    }
  }

  /**
   * Decode part of the result we got back from {@link YamlReader#read()} into an object of the
   * given type.
   *
   * <p>At the top level, we are trying to construct some Java object like {@link AppYaml} from
   * YAML. We'll have got back a {@link Map} in which the keys are the top-level YAML fields and the
   * corresponding values are what goes in those fields. We need to convert those values into the
   * appropriate Java objects that can be stored in the fields of the target object ({@link
   * AppYaml}).
   *
   * <p>Suppose the YAML looks like this:
   *
   * <pre>
   * application: foo
   * basic_scaling:
   *   idle_timeout: 3
   *   max_instances: 314
   * </pre>
   *
   * <p>Then our goal is to construct an {@link AppYaml} and call its {@code link
   * AppYaml#setApplication} and {@link AppYaml#setBasic_scaling} methods with the correct
   * arguments. We know the types of those arguments because they are the parameter types of the
   * setter methods. For {@code setApplication}, the parameter type is {@code String}. We already
   * have a string from our {@code Map}, and we can simply pass that string to the method. For
   * {@code setBasicScaling}, the parameter type is is {@link AppYaml.BasicScaling}. We'll need to
   * construct a {@code BasicScaling} object recursively, and fill in its {@code idle_timeout} and
   * {@code max_instances} fields.
   *
   * <p>Meanwhile the {@code Map} we get from {@link YamlReader#read()} looks essentially like this:
   *
   * <pre>
   * Map.of(
   *     "application", "foo",
   *     "basic_scaling", Map.of(
   *         "idle_timeout", "3",
   *         "max_instances", "314"))
   * </pre>
   *
   * <p>If the target type is a JavaBean, like {@link AppYaml} or {@link AppYaml.BasicScaling}, then
   * the object we are decoding will be a map like the above. We need to construct an instance of
   * the target type and fill in its properties using its setter methods. The values to be filled in
   * are recursively decoded using this method.
   *
   * <p>If the target type is {@code String}, then we will already have a {@code String} in the map
   * and we can just use it as-is.
   *
   * <p>If the target type is {@code int} or {@code double} or {@code boolean}, or their boxed
   * wrapper types, then we will have a {@code String} which we will need to parse in the standard
   * way.
   *
   * <p>If the target type is {@code List<T>} then we will have a {@code List} where each element
   * needs to be recursively decoded into a {@code T}.
   *
   * <p>If the target type is {@code Map<K, V>} then we will have a {@code Map} where again the keys
   * and values need to be recursively decoded into {@code K} and {@code V}.
   */
  private static Object decode(Object encoded, Type targetType) throws YamlException {
    if (targetType instanceof Class<?>) {
      Class<?> targetClass = (Class<?>) targetType;
      if (encoded instanceof Map) {
        // This must be a JavaBean. If the target type is {@code Map<K, V>} then it is a
        // ParameterizedType, not a Class.
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) encoded;
        return decodeBean(map, targetClass);
      }
      if (targetClass.isEnum()) {
        try {
          @SuppressWarnings("unchecked")
          Object result = Enum.valueOf((Class<Enum>) targetClass, (String) encoded);
          return result;
        } catch (IllegalArgumentException e) {
          throw new YamlException(encoded + " is not a legal value of " + targetClass.getName(), e);
        }
      }
      Function<String, Object> decoder = SIMPLE_DECODERS.get(targetClass);
      if (decoder == null) {
        if (encoded == null || encoded.equals("")) {
          // We have something like this:
          // foo:
          // bar:
          // This shows up as a map entry from "foo" to null or to "", depending on the YamlBeans
          // version.
          return decodeBean(ImmutableMap.of(), targetClass);
        }
        throw new YamlException(
            "Don't know how to decode " + targetClass.getName() + " from <" + encoded + ">");
      }
      return decoder.apply((String) encoded);
    } else if (targetType instanceof ParameterizedType) {
      return decodeParameterized(encoded, (ParameterizedType) targetType);
    } else {
      throw new YamlException(
          "Don't know how to decode " + targetType + " from " + encoded);
    }
  }

  private static final ImmutableMap<Class<?>, Function<String, Object>> SIMPLE_DECODERS =
      ImmutableMap.of(
          Integer.class, Integer::decode,
          int.class, Integer::decode,
          Double.class, Double::parseDouble,
          double.class, Double::parseDouble,
          Boolean.class, YamlUtils::parseBoolean,
          boolean.class, YamlUtils::parseBoolean,
          String.class, x -> x);

  private static <T> T decodeBean(Map<String, Object> map, Class<T> targetClass)
      throws YamlException {
    T object;
    try {
      object = targetClass.getConstructor().newInstance();
    } catch (ReflectiveOperationException e) {
      throw new YamlException("Can't construct " + targetClass.getName(), e);
    }
    // We have to find all the setters first, then invoke them in the order that the properties
    // appear in `map`. Some things depend on earlier YAML entries being set before later ones.
    ImmutableMap<String, Method> propertyToSetter =
        Arrays.stream(targetClass.getMethods())
            .filter(
                m ->
                    m.getName().startsWith("set")
                        && m.getName().length() > 3
                        && m.getParameterCount() == 1)
            .collect(
                toImmutableMap(
                    m -> {
                      String name = m.getName().substring(3);
                      return Ascii.toLowerCase(name.charAt(0)) + name.substring(1);
                    },
                    m -> m));
    for (Map.Entry<String, Object> entry : map.entrySet()) {
      Method setter = propertyToSetter.get(entry.getKey());
      if (setter == null) {
        throw new YamlException(
            "Unable to find property '" + entry.getKey() + "' in " + targetClass.getName());
      }
      Type parameterType = setter.getGenericParameterTypes()[0];
      Object decodedArg;
      try {
        decodedArg = decode(entry.getValue(), parameterType);
        setter.invoke(object, decodedArg);
      } catch (ReflectiveOperationException | ClassCastException e) {
        throw new YamlException(
            "Error setting property '" + entry.getKey() + "' in " + targetClass.getName(), e);
      }
    }
    return object;
  }

  private static Object decodeParameterized(Object encoded, ParameterizedType targetType)
      throws YamlException {
    if (targetType.getRawType().equals(List.class)) {
      Type elementType = targetType.getActualTypeArguments()[0];
      if (encoded.equals("")) {
        encoded = ImmutableList.of();
      }
      return decodeList((Iterable<?>) encoded, elementType);
    } else if (targetType.getRawType().equals(Map.class)) {
      Type keyType = targetType.getActualTypeArguments()[0];
      Type valueType = targetType.getActualTypeArguments()[1];
      return decodeMap((Map<?, ?>) encoded, keyType, valueType);
    } else {
      throw new YamlException("Don't know how to decode " + targetType);
    }
  }

  private static ImmutableList<?> decodeList(Iterable<?> encoded, Type elementType)
      throws YamlException {
    if (encoded == null) {
      return ImmutableList.of();
    }
    ImmutableList.Builder<Object> list = ImmutableList.builder();
    for (Object element : encoded) {
      list.add(decode(element, elementType));
    }
    return list.build();
  }

  private static ImmutableMap<?, ?> decodeMap(Map<?, ?> encoded, Type keyType, Type valueType)
      throws YamlException {
    if (encoded == null) {
      return ImmutableMap.of();
    }
    Map<Object, Object> map = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : encoded.entrySet()) {
      Object key = decode(entry.getKey(), keyType);
      Object value = decode(entry.getValue(), valueType);
      map.put(key, value);
    }
    return ImmutableMap.copyOf(map);
  }
}
