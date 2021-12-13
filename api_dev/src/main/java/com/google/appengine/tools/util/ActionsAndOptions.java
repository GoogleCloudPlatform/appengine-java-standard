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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A container for the collection of {@link Action Actions} and {@link Option Options} that may be
 * passed to the {@code AppCfg} command-line tool.
 * <p>
 * This class is not thread-safe. An instance should only be accessed by one thread at-a-time.
 *
 */
public class ActionsAndOptions {

  /**
   * All {@link Action Actions} in processing order
   */
  public List<Action> actions;

  /**
   */
  public List<Option> options;

  /**
   * Action names in help-string order
   */
  public List<String> actionNames;


  /**
   * Option names in help-string order
   */
  public List<String> optionNames;


  /**
   * General option names in help-string order
   */
  public List<String> generalOptionNames;

  /**
   * Name to object caches
   */
  private Map<String, Option> nameToOptionMap;
  private Map<String, Action> nameToActionMap;

  /**
   * After {@link #options} has been set, this method may be invoked to lookup an option by its
   * long name.
   */
  public Option getOption(String name) {
    if (options == null) {
      throw new IllegalStateException("options must be set first");
    }
    if (nameToOptionMap == null) {
      nameToOptionMap = new HashMap<String, Option>(options.size() * 2);
      for (Option option : options) {
        nameToOptionMap.put(option.getLongName(), option);
      }
    }
    return nameToOptionMap.get(name);

  }

  /**
   * After {@link #actions} has been set, this method may be invoked to lookup an option by its
   * name string.
   */
  public Action getAction(String name) {
    if (actions == null) {
      throw new IllegalStateException("actions must be set first");
    }
    if (nameToActionMap == null) {
      nameToActionMap = new HashMap<String, Action>(actions.size() * 2);
      for (Action action : actions) {
        nameToActionMap.put(action.getNameString(), action);
      }
    }
    return nameToActionMap.get(name);

  }
}
