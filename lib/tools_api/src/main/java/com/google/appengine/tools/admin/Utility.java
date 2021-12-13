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

import com.google.common.base.Joiner;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.logging.Logger;

/**
 * Utility methods for this package.
 *
 */
public class Utility {
  private static final Logger logger = Logger.getLogger(Utility.class.getCanonicalName());

  private static final String FORWARD_SLASH = "/";

  /** Test for Unix (to include MacOS), vice Windows. */
  public static boolean isOsUnix() {
    return File.separator.equals(FORWARD_SLASH);
  }

  /** Test for Windows, vice Unix (to include MacOS). */
  public static boolean isOsWindows() {
    return !isOsUnix();
  }

  public static String calculatePath(File f, File base) {
    int offset = base.getPath().length();
    String path = f.getPath().substring(offset);
    // On Windows the filename will include the path separator '\' instead of
    // '/'.
    // Problem is, this does not the valid filename regex. To handle this,
    // we'll replace '\'
    // characters with '/'. If '\' was actually in the filename then we'll get
    // a
    // file not found error, which is correct anyways.
    if (File.separatorChar == '\\') {
      path = path.replace('\\', '/');
    }

    // Remove leading slashes or the appserver will choke.
    for (offset = 0; path.charAt(offset) == '/'; ++offset) {
      // Do nothing. Just find first non-/
    }
    if (offset > 0) {
      path = path.substring(offset);
    }

    return path;
  }

  /**
   * Escapes the string as a JSON value.
   *
   * @param s raw string that we want to set as JSON value.
   * @return unquoted JSON escaped string
   */
  public static String jsonEscape(String s) {
    StringBuilder stringBuilder = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      char ch = s.charAt(i);

      switch (ch) {
        case '"':
          stringBuilder.append("\\\"");
          break;

        case '\\':
          stringBuilder.append("\\\\");
          break;

        case '\b':
          stringBuilder.append("\\b");
          break;

        case '\f':
          stringBuilder.append("\\f");
          break;

        case '\n':
          stringBuilder.append("\\n");
          break;

        case '\r':
          stringBuilder.append("\\r");
          break;

        case '\t':
          stringBuilder.append("\\t");
          break;

        case '/':
          stringBuilder.append("\\/");
          break;

        default:
          if ((ch >= 0x20) && (ch < 0x7f)) {
            stringBuilder.append(ch);
          } else {
            stringBuilder.append(String.format("\\u%04x", (int) ch));
          }
          break;
      }
    }

    return stringBuilder.toString();
  }

  private Utility() {
    // non-instantiable
  }

  static Process startProcess(PrintWriter detailsWriter, String... args) throws IOException {
    ProcessBuilder builder = new ProcessBuilder(args);
    Process proc = builder.redirectErrorStream(true).start();
    logger.fine(Joiner.on(" ").join(builder.command()));
    new Thread(new OutputPump(proc.getInputStream(), detailsWriter)).start();
    return proc;
  }
}
