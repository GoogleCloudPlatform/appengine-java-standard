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

package com.google.appengine.tools;

import java.nio.charset.Charset;

/**
 * Prints the default character set. This is the value reported by {@link Charset#defaultCharset}
 * and by the {@code file.encoding} system property.
 */
public class PrintDefaultCharset {
  public static void main(String[] args) {
    System.out.println(Charset.defaultCharset());
  }
}
