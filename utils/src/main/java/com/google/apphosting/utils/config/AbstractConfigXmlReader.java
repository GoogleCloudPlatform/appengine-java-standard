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

package com.google.apphosting.utils.config;

import com.google.common.base.CharMatcher;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.w3c.dom.Element;

/**
 * Abstract class for reading the XML files to configure an application.
 *
 * @param <T> the type of the configuration object returned
 *
 *
 */
abstract class AbstractConfigXmlReader<T> {
  /** The path to the top level directory of the application. */
  final String appDir;

  /** Whether the config file must exist in a correct application. */
  final boolean required;

  /** A logger for messages. */
  private static final Logger logger = Logger.getLogger(AbstractConfigXmlReader.class.getName());


  /**
   * Initializes the generic attributes of all our configuration XML readers.
   *
   * @param appDir pathname to the application directory
   * @param required {@code true} if is an error for the config file not to exist.
   */
  AbstractConfigXmlReader(String appDir, boolean required) {
    // Be friendly, make sure we end with a slash.
    if (appDir.length() > 0 && appDir.charAt(appDir.length() - 1) != File.separatorChar) {
      appDir += File.separatorChar;
    }
    this.appDir = appDir;
    this.required = required;
  }

  /**
   * Gets the absolute filename for the configuration file.
   *
   * @return concatenation of {@link #appDir} and {@link #getRelativeFilename()}
   */
  public String getFilename() {
    return appDir + getRelativeFilename();
  }

  /**
   * Fetches the name of the configuration file processed by this instance,
   * relative to the application directory.
   *
   * @return relative pathname for a configuration file
   */
  abstract String getRelativeFilename();

  /**
   * Parses the input stream to compute an instance of {@code T}.
   *
   * @return the parsed config file
   * @throws AppEngineConfigException if there is an error.
   */
  abstract T processXml(InputStream is);

  /**
   * Does the work of reading the XML file, processing it, and either returning
   * an object representing the result or throwing error information.
   *
   * @return A {@link AppEngineWebXml} config object derived from the
   * contents of the config xml, or {@code null} if no such file is defined and
   * the config file is optional.
   *
   * @throws AppEngineConfigException If the file cannot be parsed properly
   */
  T readConfigXml() {
    T configXml;
    if (!required && !fileExists()) {
      return null;  // non-required files can be missing
    }
    try (InputStream is = getInputStream()) {
      configXml = processXml(is);
      logger.fine("Successfully processed " + getFilename());
    } catch (Exception e) {
      String msg = "Received exception processing " + getFilename();
      logger.log(Level.SEVERE, msg, e);
      // Guarantee that the only exceptions thrown from this method are of
      // type AppEngineConfigException.
      if (e instanceof AppEngineConfigException) {
        throw (AppEngineConfigException) e;
      }
      throw new AppEngineConfigException(msg, e);
    }
    return configXml;
  }

  /**
   * Tests for file existence.  Test clases will often override this, to lie
   * (and thus stay small by avoiding needing the real filesystem).
   */
  boolean fileExists() {
    return (new File(getFilename())).exists();
  }

  /**
   * Opens an input stream, or fails with an AppEngineConfigException
   * containing helpful information.  Test classes will often override this.
   *
   * @return an open {@link InputStream}
   * @throws AppEngineConfigException
   */
  protected InputStream getInputStream() {
    try {
      return new FileInputStream(getFilename());
    } catch (FileNotFoundException fnfe) {
      // Having the full path in the exception is helpful for debugging.
      throw new AppEngineConfigException(
          "Could not locate " + new File(getFilename()).getAbsolutePath(), fnfe);
    }
  }

  String stringContents(Element element) {
    String text = XmlUtils.getText(element);
    if (text.isEmpty()) {
      throw new AppEngineConfigException(
          getFilename() + " has bad contents in <" + sanitizeTag(element.getTagName()) + ">");
    }
    return CharMatcher.whitespace().trimFrom(text);
  }

  private static final CharMatcher INVALID_TAG_CHARS =
      CharMatcher.inRange('a', 'z')
          .or(CharMatcher.inRange('A', 'Z'))
          .or(CharMatcher.anyOf("_.-"))
          .negate();

  static String sanitizeTag(String tag) {
    return INVALID_TAG_CHARS.replaceFrom(tag, '?');
  }
}
