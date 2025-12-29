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

package com.google.appengine.tools.util;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.util.LinkedList;
import java.util.List;

/**
 * A command line option. There may be multiple command line options for a single
 * command line. Each option may be represented by a "short" name or a "long" name.
 * A short option is prefixed by a single dash ("-"), and its value follows in a separate
 * argument. For example, <pre>
 * -e foo@example.com
 * </pre>
 * A long option is prefixed by a double dash ("--"), and its value is specified with an
 * equals sign ("="). For example, <pre>
 * --email=foo@example.com
 * </pre>
 * Flag-style {@code Options} have no specified value, and imply their meaning by mere
 * presence. For example, <pre>
 * --append (Forces appending to an existing file)</pre>
 *
 */
public abstract class Option {

  public enum Style {
    Short,
    Long
  }

  private final String shortName;

  private final String longName;

  private final boolean isFlag;

  private Style style;

  private List<String> values;

  /**
   * Creates a new {@code Option}. While both {@code shortName} and
   * {@code longName} are optional, one must be supplied.
   *
   * @param shortName The short name to support. May be {@code null}.
   * @param longName The long name to support. May be {@code null}.
   * @param isFlag true to indicate that the Option represents a boolean value.
   */
  public Option(String shortName, String longName, boolean isFlag) {
    this.shortName = shortName;
    this.longName = longName;
    this.isFlag = isFlag;
    this.values = new LinkedList<String>();
  }

  /**
   * Returns true if the {@code Option} represents a boolean value.
   * Flags are always specified using one single argument, for example,
   * "-h" or "--help". They are never of the form, "-h true" or "--help=true".
   */
  public boolean isFlag() {
    return isFlag;
  }

  /**
   * Returns the {@code Style} in which the {@code Option} was supplied
   * on the command line.
   */
  public Style getArgStyle() {
    return style;
  }

  /**
   * Returns the value specified for the {@code Option}. If the option was
   * specified on the command line multiple times, the values from the last
   * option will be returned (i.e. for the command line "--option=1 --option=2"
   * this method will return "2").
   */
  public String getValue() {
    return Iterables.getLast(values, null);
  }

  /**
   * Returns all the values that were specified for the {@code Option} in the
   * order they were specified on the command line. So for the command line
   * "--option=1 --option=2" this method will return a List that has "1" at
   * index 0 and "2" at index 1.
   */
  public List<String> getValues() {
    return values;
  }

  public String getShortName() {
    return shortName;
  }  

  public String getLongName() {
    return longName;
  }

  @Override
  public String toString() {
    return "Option{" + "shortName='" + shortName + '\'' + ", longName='" + longName + '\''
        + ", isFlag=" + isFlag + ", style=" + style + ", values='" + values + '\'' + '}';
  }

  /**
   * Reads the value parsed for the {@code Option} and applies the
   * appropriate logic.
   *
   * This method will be called exactly one time (even if the option was
   * specified on the command line multiple times). If the value is singular,
   * the method can call {@link Option#getValue()} to get the appropriate
   * value; if the value is multiple, the method can call {@link
   * Option#getValues()} to get all the values.
   *
   * @throws IllegalArgumentException if the parsed value is invalid.
   */
  public abstract void apply();

  public List<String> getHelpLines() {
    return ImmutableList.<String>of();
  }

  boolean parse(String[] args, int currentArg) {
    String argVal = args[currentArg];

    if (shortName != null) {
      if (argVal.equals("-" + shortName)) {
        if (isFlag()) {
          return true;
        }
        if (currentArg + 1 == args.length) {
          throw new IllegalArgumentException(shortName + " requires an argument.\n");
        }
        values.add(args[currentArg + 1]);
        style = Style.Short;
        return true;
      }
    }

    if (longName != null) {
      if (isFlag()) {
        return argVal.equals("--" + longName);
      }
      if (!argVal.startsWith("--" + longName + "=")) {
        return false;
      }
      String[] tokens = argVal.split("=", 2);
      if (tokens.length == 1) {
        throw new IllegalArgumentException(
            longName + " requires an argument, for example, \"" + longName + "=FOO\"\n");
      }
      values.add(tokens[1]);
      style = Style.Long;
      return true;
    }

    return false;
  }
}
