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

package com.google.appengine.tools.admin;

import static com.google.common.truth.Truth.assertThat;

import junit.framework.TestCase;

public class UtilityTest extends TestCase {
  public void testJsonEscaping() {
    verifyJsonEscaping("", "");
    verifyJsonEscaping("a", "a");
    verifyJsonEscaping("abc-def.xy_z", "abc-def.xy_z");
    verifyJsonEscaping("'q'b'", "'q'b'");
    verifyJsonEscaping("\"q\"b\"", "\\\"q\\\"b\\\"");
    verifyJsonEscaping("\\,\b,\f,\n,\r,\t,/", "\\\\,\\b,\\f,\\n,\\r,\\t,\\/");
    verifyJsonEscaping(Character.toString((char) 0), "\\u0000");
    verifyJsonEscaping(Character.toString((char) 1), "\\u0001");
    verifyJsonEscaping(Character.toString((char) 0xe), "\\u000e");
    verifyJsonEscaping(Character.toString((char) 0xfe), "\\u00fe");
    verifyJsonEscaping(Character.toString((char) 0x100), "\\u0100");
    verifyJsonEscaping(Character.toString((char) 0xfff), "\\u0fff");
    verifyJsonEscaping(Character.toString((char) 0x1000), "\\u1000");
    verifyJsonEscaping(Character.toString((char) 0x4fa3), "\\u4fa3");
    verifyJsonEscaping(Character.toString((char) 0x8214), "\\u8214");
    verifyJsonEscaping(Character.toString((char) 0xffff), "\\uffff");
  }

  private void verifyJsonEscaping(String input, String expectedOutput) {
    assertThat(Utility.jsonEscape(input)).isEqualTo(expectedOutput);
  }
}
