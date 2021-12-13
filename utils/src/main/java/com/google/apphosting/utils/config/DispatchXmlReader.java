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

import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.io.InputStream;
import java.util.Map;
import java.util.TreeMap;
import org.w3c.dom.Element;

/**
 * Creates a {@link DispatchXml} from dispatch.yaml.
 */
public class DispatchXmlReader extends AbstractConfigXmlReader<DispatchXml> {

  public static final String DEFAULT_RELATIVE_FILENAME = "WEB-INF" + File.separatorChar
      + "dispatch.xml";

  // XML Constants
  private static final String DISPATCH_ENTRIES_TAG = "dispatch-entries";
  private static final String DISPATCH_TAG = "dispatch";
  private static final String URL_TAG = "url";
  private static final String MODULE_TAG = "module";

  private final String relativeFilename;

  public DispatchXmlReader(String warDirectory, String relativeFilename) {
    super(warDirectory, false);
    this.relativeFilename = relativeFilename;
  }

  @Override
  protected String getRelativeFilename() {
    return relativeFilename;
  }

  /**
   * Parses the dispatch.xml file if one exists into an {@link DispatchXml} and otherwise
   * returns null.
   */
  public DispatchXml readDispatchXml() {
    return readConfigXml();
  }

  @Override
  protected DispatchXml processXml(InputStream is) {
    Element root = XmlUtils.parseXml(is, getFilename()).getDocumentElement();
    if (!DISPATCH_ENTRIES_TAG.equals(root.getTagName())) {
      throwExpectingTag(DISPATCH_ENTRIES_TAG, root.getTagName());
    }
    DispatchXml.Builder dispatchXmlBuilder = DispatchXml.builder();
    for (Element child : XmlUtils.getChildren(root)) {
      if (child.getTagName().equals(DISPATCH_TAG)) {
        dispatchXmlBuilder.addDispatchEntry(parseDispatchEntry(child));
      } else {
        throwExpectingTag(DISPATCH_TAG, child.getTagName());
      }
    }
    return dispatchXmlBuilder.build();
  }

  private static final ImmutableSet<String> DISPATCH_TAGS = ImmutableSet.of(URL_TAG, MODULE_TAG);

  private DispatchXml.DispatchEntry parseDispatchEntry(Element dispatchElement) {
    Map<String, String> tagValues = new TreeMap<>();
    for (Element child : XmlUtils.getChildren(dispatchElement)) {
      String tag = child.getTagName();
      if (DISPATCH_TAGS.contains(tag)) {
        if (tagValues.containsKey(tag)) {
          throwDuplicateTag(tag, DISPATCH_TAG);
        } else {
          tagValues.put(tag, stringContents(child));
        }
      } else {
        throwUnsupportedTag(tag, DISPATCH_TAG);
      }
    }
    for (String tag : DISPATCH_TAGS) {
      if (!tagValues.containsKey(tag)) {
        throwExpectingTag(tag, "/dispatch");
      }
    }
    return new DispatchXml.DispatchEntry(tagValues.get(URL_TAG), tagValues.get(MODULE_TAG));
  }

  private void throwExpectingTag(String expecting, String got) {
    throw new AppEngineConfigException(String.format("Expecting <%s> but got <%s> in file %s",
        expecting, got, getFilename()));
  }

  private void throwDuplicateTag(String duplicateTag, String parentTag) {
    if (parentTag == null) {
      throw new AppEngineConfigException(String.format("Duplicate <%s> in file %s",
          duplicateTag, getFilename()));
    } else {
      throw new AppEngineConfigException(String.format("Duplicate <%s> inside <%s> in file %s",
          duplicateTag, parentTag, getFilename()));
    }
  }

  private void throwUnsupportedTag(String tag, String parent) {
    throw new AppEngineConfigException(String.format(
        "Tag <%s> not supported in element <%s> in file %s", tag, parent, getFilename()));
  }
}
