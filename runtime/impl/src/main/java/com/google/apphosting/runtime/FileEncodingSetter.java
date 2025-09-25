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

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.GoogleLogger;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.util.Map;

/**
 * Set the default character encoding.
 *
 * <p>We get the default encoding from appengine-web.xml. Changing the default after the JVM has
 * started requires reflection.
 */
public final class FileEncodingSetter {
  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();

  private static final ImmutableMap<String, Charset> KNOWN_CHARSETS =
      ImmutableMap.of(
          "UTF-8", UTF_8,
          "US-ASCII", US_ASCII,
          "ANSI_X3.4-1968", US_ASCII);

  public static void set(Map<String, String> sysProps) {
    String fileEncoding = sysProps.get("appengine.file.encoding");
    Charset charset = KNOWN_CHARSETS.get(fileEncoding);
    if (charset != null) {
      sysProps.put("file.encoding", fileEncoding);
      overwriteDefaultCharset(charset);
    } else if (fileEncoding != null) {
      logger.atWarning().log("Unknown appengine.file.encoding %s", fileEncoding);
    }
  }

  static void overwriteDefaultCharset(Charset charset) {
    try {
      Field defaultCharset = Charset.class.getDeclaredField("defaultCharset");
      defaultCharset.setAccessible(true);
      defaultCharset.set(null, charset);
    } catch (ReflectiveOperationException e) {
      if (Runtime.version().feature() >= 26) {
        logger.atWarning().withCause(e).log(
            """
            Could not overwrite default charset using system property named \
            'appengine.file.encoding', reflective access is blocked in this Java version.\
            Instead, use a environment variable named 'JAVA_USER_OPTS' to set the file encoding 'file.encoding'property in the JVM command line.\
            Example in appengine-web.xml:
               <env-variables>
                 <env-var name="JAVA_USER_OPTS" value="-Dfile.encoding=UTF-8" />
               </env-variables>
            """);
      } else {
        throw new LinkageError(e.getMessage(), e);
      }
    }
  }

  private FileEncodingSetter() {}
}
