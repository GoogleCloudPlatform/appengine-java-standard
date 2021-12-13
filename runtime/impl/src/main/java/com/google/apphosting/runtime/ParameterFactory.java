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

package com.google.apphosting.runtime;

import com.beust.jcommander.IStringConverter;
import com.beust.jcommander.IStringConverterFactory;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.lang.reflect.Field;
import java.util.List;
import java.util.regex.Pattern;

/**
 * IStringConverterFactory that supplies custom converters.
 *
 */
public class ParameterFactory implements IStringConverterFactory {
  private static final ImmutableMap<Class<?>, Class<? extends IStringConverter<?>>> CONVERTERS =
      ImmutableMap.<Class<?>, Class<? extends IStringConverter<?>>>of(
          boolean.class, BooleanConverter.class,
          Boolean.class, BooleanConverter.class);

  @Override
  @SuppressWarnings("unchecked")
  public <T> Class<? extends IStringConverter<T>> getConverter(Class<T> type) {
    return (Class<? extends IStringConverter<T>>) CONVERTERS.get(type);
  }

  /**
   * IStringConverter that converts from string to Boolean.
   */
  public static class BooleanConverter implements IStringConverter<Boolean> {
    private static final Pattern TRUE_PATTERN =
        Pattern.compile("^(true|t|yes|y|1)$", Pattern.CASE_INSENSITIVE);

    private static final Pattern FALSE_PATTERN =
        Pattern.compile("^(false|f|no|n|0)$", Pattern.CASE_INSENSITIVE);

    @Override
    public Boolean convert(String value) {
      if (TRUE_PATTERN.matcher(value).matches()) {
        return true;
      } else if (FALSE_PATTERN.matcher(value).matches()) {
        return false;
      } else {
        throw new ParameterException("Invalid boolean value: " + value);
      }
    }
  }

  // Pattern for a simple parameter that does not contain '=' separator part.
  // I.e. boolean parameters with arity zero would match: --enable_xyz
  private static final Pattern SIMPLE_PARAM_PATTERN = Pattern.compile("^(--\\w+)$");

  public static ImmutableList<String> expandBooleanParams(
      List<String> args, Class<?> optionsClass) {
    ImmutableMap<String, String> expandedMap = expandedBooleanNamesFor(optionsClass);
    ImmutableList.Builder<String> expanded = ImmutableList.builder();
    for (String arg : args) {
      arg = arg.trim();
      if (SIMPLE_PARAM_PATTERN.matcher(arg).matches()) {
        if (expandedMap.containsKey(arg)) {
          arg = expandedMap.get(arg);
        }
      }
      expanded.add(arg);
    }
    return expanded.build();
  }

  private static ImmutableMap<String, String> expandedBooleanNamesFor(Class<?> optionsClass) {
    // Maps short-form names such as --flag or --noflag to the full form that JCommander expects.
    // If we have @Parameter(names = "--flag") then "--flag" will map to "--flag=true"
    // and "--noflag" will map to "--flag=false".
    ImmutableMap.Builder<String, String> mapBuilder = ImmutableMap.builder();
    for (Field field : optionsClass.getDeclaredFields()) {
      Parameter param = field.getAnnotation(Parameter.class);
      if (param != null) {
        Class<?> fieldType = field.getType();
        if (fieldType == boolean.class) {
          for (String name : param.names()) {
            // --flag → --flag=true
            mapBuilder.put(name, name + "=" + true);
            // --noflag → --flag=false
            mapBuilder.put("--no" + name.substring(2), name + "=" + false);
          }
        }
      }
    }
    return mapBuilder.build();
  }
}
