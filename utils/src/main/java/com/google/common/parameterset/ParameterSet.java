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

package com.google.common.parameterset;

import java.util.Iterator;

/**
 * Interface for representing parameters. Can be used to represent parameters from an HTTP request,
 * a config file or whatever. It isn't a plain map, because it conveniently handles both singleton
 * values, as well as list-values.
 */
public interface ParameterSet {
  /**
   * Returns an Iterator of String objects containing the names of the parameters contained in this
   * ParameterSet.
   *
   * @return Iterator of the names of parameters that are set.
   */
  Iterator<String> getParameterNames();

  /**
   * Returns the value of a parameter as a String, or null if the parameter does not exist. If the
   * parameter has multiple values, only the first value will be returned.
   *
   * @param name the name of the parameter to retrieve.
   * @return single parameter value.
   */
  String getParameter(String name);

  /**
   * Returns an array of String objects containing all of the values the given parameter has, or
   * null if the parameter does not exist.
   *
   * @param name the name of the parameter to retrieve.
   * @return String array of parameter values.
   */
  String[] getParameterValues(String name);
}
