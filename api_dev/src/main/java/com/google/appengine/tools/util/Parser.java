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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Parses command line arguments.
 *
 */
public class Parser {

  /**
   * Contains the parsed {@link com.google.appengine.tools.util.Action} and {@link
   * com.google.appengine.tools.util.Option arguments} from a program command line.
   */
  public static class ParseResult {
    private final List<Option> parsedOptions;
    private final Action action;

    ParseResult(List<Option> parsedOptions, Action action) {
      this.parsedOptions = parsedOptions;
      this.action = action;
    }

    public List<Option> getParsedOptions() {
      return parsedOptions;
    }

    public Action getAction() {
      return action;
    }

    /**
     * Applies the arguments to all {@link com.google.appengine.tools.util.Option#apply() options}
     * and to the parsed {@link com.google.appengine.tools.util.Action#apply() action}.
     */
    public void applyArgs() {
      for (Option option : parsedOptions) {
        option.apply();
      }
      action.apply();
    }
  }

  /**
   * Finds the action for a particular name.
   *
   * @param actions the actions available
   * @return an {@link com.google.appengine.tools.util.Action} for the given
   *     name, or {@code null} if there is no match.
   */
  public static Action lookupAction(List<Action> actions, String[] args, int index) {
    for (Action action : actions) {
      if (matchesAction(action, args, index)) {
        return action;
      }
    }
    return null;
  }

  private static boolean matchesAction(Action action, String[] args, int index) {
    if ((args.length - index) < action.getNames().length) {
      // Not enough arguments.  This action cannot match.
      return false;
    }
    for (String name : action.getNames()) {
      if (!name.equals(args[index++])) {
        return false;
      }
    }
    return true;
  }

  /**
   * Parses command line arguments for the specified {@code actions} and {@code args}. This variant
   * assumes that there is only a single {@link com.google.appengine.tools.util.Action} and, as a
   * result, the user does not specify an action name on the command line.
   *
   * @param action a single not-{@code null} {@link com.google.appengine.tools.util.Action}
   * @param options a not {@code null}, optionally empty, list
   * @param cmdLineArgs a not {@code null} array of command line arguments
   * @return a not {@code null} {@code ParseResult}
   */
  public ParseResult parseArgs(Action action, List<Option> options, String[] cmdLineArgs) {
    List<Option> parsedOptions = new ArrayList<Option>();
    int currentArg = parseOptions(options, parsedOptions, cmdLineArgs);

    List<String> actionArgs = Arrays.asList(cmdLineArgs).subList(currentArg, cmdLineArgs.length);
    action.setArgs(actionArgs);
    return new ParseResult(parsedOptions, action);
  }

  /**
   * Parses command line arguments using the specified {@code actions} and
   * {@code args}.
   *
   * @param actions a not {@code null}, not empty, list
   * @param options a not {@code null}, optionally empty, list
   * @param cmdLineArgs a not {@code null} array of command line arguments
   *
   * @return a not {@code null} {@code ParseResult}
   */
  public ParseResult parseArgs(List<Action> actions, List<Option> options, String[] cmdLineArgs) {
    List<Option> parsedOptions = new ArrayList<Option>();

    int currentArg = parseOptions(options, parsedOptions, cmdLineArgs);
    if (currentArg >= cmdLineArgs.length) {
      throw new IllegalArgumentException("Expected an action: " + buildActionString(actions));
    }

    String actionString = cmdLineArgs[currentArg];
    if (actionString.startsWith("-") || actionString.startsWith("--")) {
      throw new IllegalArgumentException("Unknown option: " + actionString);
    }

    Action foundAction = lookupAction(actions, cmdLineArgs, currentArg);
    if (foundAction == null) {
      throw new IllegalArgumentException("Expected an action: " + buildActionString(actions));
    }
    currentArg += foundAction.getNames().length;

    List<String> actionArgs = Arrays.asList(cmdLineArgs).subList(currentArg, cmdLineArgs.length);
    foundAction.setArgs(actionArgs);
    return new ParseResult(parsedOptions, foundAction);
  }

  private int parseOptions(List<Option> availableOptions, List<Option> parsedOptions,
                           String[] cmdLineArgs) {
    int currentArg = -1;

    while (true) {
      ++currentArg;
      if (currentArg >= cmdLineArgs.length) {
        break;
      }
      Option option = parseArg(availableOptions, cmdLineArgs, currentArg);
      if (option != null) {
        parsedOptions.add(option);
        if (option.getArgStyle() == Option.Style.Short) {
          ++currentArg;
        }
        continue;
      }
      break;
    }

    return currentArg;
  }

  private static String buildActionString(List<Action> actions) {
    StringBuilder msg = new StringBuilder("[");

    for (int i = 0; i < actions.size(); ++i) {
      Action action = actions.get(i);
      msg.append(action.getNameString());
      if (i != actions.size() - 1) {
        msg.append(", ");
      } else {
        msg.append("]");
      }
    }

    return msg.toString();
  }

  private static Option parseArg(List<Option> optionParsers, String[] cmdLineArgs, int currentArg) {
    for (Option option : optionParsers) {
      if (option.parse(cmdLineArgs, currentArg)) {
        return option;
      }
    }
    return null;
  }
}
