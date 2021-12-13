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

package com.google.appengine.api.urlfetch;

import java.io.Serializable;

/**
 * {@code HTTPHeader} can represent either an HTTP request header, or
 * an HTTP response header.
 *
 */
public class HTTPHeader implements Serializable {
  private static final long serialVersionUID = 5630098007630337687L;

  private final String name;
  private final String value;

  /**
   * Creates a new header with the specified name and value.
   * 
   * @param name a not {@code null} name
   * @param value may be a single value or a comma-separated list of values
   * for multivalued headers such as {@code Accept} or {@code Set-Cookie}.
   */
  public HTTPHeader(String name, String value) {
    this.name = name;
    this.value = value;
  }

  public String getName() {
    return name;
  }

  public String getValue() {
    return value;
  }
}
