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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Creates an {@link AppEngineWebXml} instance from
 * <appdir>WEB-INF/appengine-web.xml.  If you want to read the configuration
 * from something that isn't a file, subclass and override
 * {@link #getInputStream()}.
 *
 */
public class AppEngineWebXmlReader {
  private static final Logger logger =
      Logger.getLogger(AppEngineWebXmlReader.class.getName());

  private static final String CONCURRENT_REQUESTS_URL =
      "http://code.google.com/appengine/docs/java/config/appconfig.html#Using_Concurrent_Requests";

  private static final String DATASTORE_AUTO_IDS_URL =
      "http://developers.google.com/appengine/docs/java/datastore/entities#Kinds_and_Identifiers";

  private static final String APPCFG_AUTO_IDS_URL =
      "http://developers.google.com/appengine/docs/java/config/appconfig#auto_id_policy";

  public static final String DEFAULT_RELATIVE_FILENAME = "WEB-INF/appengine-web.xml";

  // Absolute location of the config file
  private final String filename;

  /**
   * Creates a reader for appengine-web.xml.
   *
   * @param appDir The directory in which the config file resides.
   * @param relativeFilename The path to the config file, relative to
   * {@code appDir}.
   */
  public AppEngineWebXmlReader(String appDir, String relativeFilename) {
    // Be friendly, make sure we end with a slash.
    if (appDir.length() > 0 && appDir.charAt(appDir.length() - 1) != File.separatorChar) {
      appDir += File.separatorChar;
    }
    this.filename = appDir + relativeFilename;
  }

  /**
   * Creates a reader for appengine-web.xml.
   *
   * @param appDir The directory in which the config file resides.  The
   * path to the config file relative to the directory is assumed to be
   * {@link #DEFAULT_RELATIVE_FILENAME}.
   */
  public AppEngineWebXmlReader(String appDir) {
    this(appDir, DEFAULT_RELATIVE_FILENAME);
  }

  /**
   * @return A {@link AppEngineWebXml} config object derived from the
   * contents of <appdir>WEB-INF/appengine-web.xml.
   *
   * @throws AppEngineConfigException If <appdir>WEB-INF/appengine-web.xml does
   * not exist.  Also thrown if we are unable to parse the xml.
   */
  public AppEngineWebXml readAppEngineWebXml() {
    InputStream is = null;
    AppEngineWebXml appEngineWebXml;
    try {
      is = getInputStream();
      appEngineWebXml = processXml(is);
      logger.fine("Successfully processed " + getFilename());
      if ("legacy".equals(appEngineWebXml.getAutoIdPolicy())) {
        logger.warning("You have set the datastore auto id policy to 'legacy'. It is recommended "
            + "that you select 'default' instead.\nLegacy auto ids are deprecated. You can "
            + "continue to allocate legacy ids manually using the allocateIds() API functions.\n"
            + "For more information see:\n"
            + APPCFG_AUTO_IDS_URL + "\n" + DATASTORE_AUTO_IDS_URL + "\n");
      }
    } catch (Exception e) {
      String msg = "Received exception processing " + getFilename();
      logger.log(Level.SEVERE, msg, e);
      // Guarantee that the only exceptions thrown from this method are of
      // type AppEngineConfigException.
      if (e instanceof AppEngineConfigException) {
        throw (AppEngineConfigException) e;
      }
      throw new AppEngineConfigException(msg, e);
    } finally {
      close(is);
    }
    return appEngineWebXml;
  }

  protected boolean allowMissingThreadsafeElement() {
    return false;
  }

  public String getFilename() {
    return filename;
  }

  private void close(InputStream is) {
    if (is != null) {
      try {
        is.close();
      } catch (IOException e) {
        throw new AppEngineConfigException(e);
      }
    }
  }

  // broken out for testing
  protected AppEngineWebXml processXml(InputStream is) {
    return new AppEngineWebXmlProcessor().processXml(is);
  }

  protected InputStream getInputStream() {
    try {
      return new FileInputStream(getFilename());
    } catch (FileNotFoundException fnfe) {
      // Having the full path in the exception is helpful for debugging.
      throw new AppEngineConfigException(
          "Could not locate " + new File(getFilename()).getAbsolutePath(), fnfe);
    }
  }
}
