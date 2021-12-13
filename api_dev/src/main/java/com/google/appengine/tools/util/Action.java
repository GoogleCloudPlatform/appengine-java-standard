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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import java.util.List;

/**
 * A command line action.
*
*/
public abstract class Action {

  private final String[] names;
  private List<String> args;

  // Options that are applicable only to this Action.
  protected List<Option> extraOptions;

  // Short description for a help string
  protected String shortDescription;

  public Action(String... names) {
    this.names = names;
  }

  /**
   * Constructor
   *
   * @param options The list of {@link Option Options} that are specially applicable to this
   *        {@code Action} beyond the {@link Option Options} that are generally applicable.
   *        Currently this list is used only for generating help text.
   * @param names The names for this {@code Action}
   */
  public Action(List<Option> options, String... names) {
    this.names = names;
    this.extraOptions = options;
  }


  public String[] getNames() {
    return names;
  }

  public String getNameString() {
    return Joiner.on(" ").join(names);
  }

  public List<Option> getOptions() {
    return extraOptions;
  }

  public void setOptions(List<Option> extraOptions) {
    this.extraOptions = extraOptions;
  }

  public void setShortDescription(String description) {
    this.shortDescription = description;
  }

  public String getShortDescription() {
    return this.shortDescription;
  }

  protected void setArgs(List<String> args) {
    this.args = args;
  }

  public List<String> getArgs() {
    return args;
  }

  /**
   * Reads the value parsed for the {@code Action} and applies the
   * appropriate logic.
   *
   * @throws IllegalArgumentException if the parsed value is invalid.
   */
  public abstract void apply();

  private String helpString = null;

  public String getHelpString() {
    if (helpString == null) {
      helpString = Joiner.on('\n').join(getHelpLines());
    }
    return helpString;
  }

  /**
   * Returns a list of Strings to be displayed as the lines of
   * a help text. Subclasses should override this method.
   */
  protected List<String> getHelpLines() {
    return ImmutableList.of();
  }

}
