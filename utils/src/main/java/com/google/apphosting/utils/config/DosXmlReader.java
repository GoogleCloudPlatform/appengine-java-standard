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

import java.io.InputStream;
import org.w3c.dom.Element;

/**
 * Creates an {@link DosXml} instance from
 * <appdir>WEB-INF/dos.xml.  If you want to read the configuration
 * from a different file, subclass and override {@link #getFilename()}.  If you
 * want to read the configuration from something that isn't a file, subclass
 * and override {@link #getInputStream()}.
 *
 */
public class DosXmlReader extends AbstractConfigXmlReader<DosXml> {

  // N.B.(schwardo): this class is not currently used in, and
  // therefore has not been tested in, the runtime.  Before adding a
  // dependency on this code from the runtime please ensure that there
  // is no possibility for external entity references or other
  // dependencies that may cause it to fail when running under the
  // restricted environment

  // Relative location of the config file
  private static final String FILENAME = "WEB-INF/dos.xml";

  // XML Constants
  private static final String DENYLISTENTRIES_TAG = "blacklistentries";
  private static final String DENYLIST_TAG = "blacklist";
  private static final String DESCRIPTION_TAG = "description";
  private static final String SUBNET_TAG = "subnet";
  
  /**
   * Constructs the reader for {@code dos.xml} in a given application directory.
   * @param appDir the application directory
   */
  public DosXmlReader(String appDir) {
    super(appDir, false);
  }

  @Override
  protected String getRelativeFilename() {
    return FILENAME;
  }

  /**
   * Parses the config file.
   * @return A {@link DosXml} object representing the parsed configuration.
   */
  public DosXml readDosXml() {
    return readConfigXml();
  }
  
  @Override
  protected DosXml processXml(InputStream is) {
    DosXml dosXml = new DosXml();
    Element root = XmlUtils.parseXml(is).getDocumentElement();
    if (!root.getTagName().equals(DENYLISTENTRIES_TAG)) {
      throw new AppEngineConfigException(getFilename() + " does not contain <"
          + DENYLISTENTRIES_TAG + ">");
    }
    for (Element child : XmlUtils.getChildren(root)) {
      if (child.getTagName().equals(DENYLIST_TAG)) {
        parseDenylist(child, dosXml.addNewBlacklistEntry());
      } else {
        throw new AppEngineConfigException(getFilename() + " contains <"
            + child.getTagName() + "> instead of <" + DENYLIST_TAG + "/>");
      }
    }
    dosXml.validateLastEntry();
    return dosXml;
  }

  private void parseDenylist(Element denylistElement, DosXml.BlacklistEntry denylistEntry) {
    for (Element child : XmlUtils.getChildren(denylistElement)) {
      switch (child.getTagName()) {
        case DESCRIPTION_TAG:
          denylistEntry.setDescription(stringContents(child));
          break;
        case SUBNET_TAG:
         denylistEntry.setSubnet(stringContents(child));
          break;
        default:
          throw new AppEngineConfigException(getFilename() + " contains unknown <"
              + child.getTagName() + "> inside <" + DENYLIST_TAG + "/>");
      }
    }
  }
}
