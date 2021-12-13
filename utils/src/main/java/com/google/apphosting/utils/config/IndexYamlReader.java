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

import static com.google.apphosting.utils.config.IndexesXml.DIRECTION_VALUE_ASC;
import static com.google.apphosting.utils.config.IndexesXml.DIRECTION_VALUE_DESC;
import static com.google.apphosting.utils.config.IndexesXml.MODE_VALUE_GEOSPATIAL;

import com.esotericsoftware.yamlbeans.YamlException;
import com.esotericsoftware.yamlbeans.YamlReader;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

// CAUTION: this is one of several files that implement parsing and
// validation of the index definition schema; they all must be kept in
// sync.  Please refer to java/com/google/appengine/tools/development/datastore-indexes.xsd
// for the list of these files.
/**
 * Class to parse index.yaml into a IndexesXml object.
 *
 */
public class IndexYamlReader {

  // These are python object serialization tags found in the yaml strings that
  // are returned in some responses from the admin server. We must remove these
  // tags in order to parse the yaml into Java beans.
  public static final String INDEX_DEFINITIONS_TAG =
      "!!python/object:google.appengine.datastore.datastore_index.IndexDefinitions";
  public static final String INDEX_TAG =
      "!!python/object:google.appengine.datastore.datastore_index.Index";
  public static final String PROPERTY_TAG =
      "!!python/object:google.appengine.datastore.datastore_index.Property";

  /**
   * Wrapper around IndexesXml to make the JavaBeans properties match the YAML
   * file syntax.
   */
  public static class IndexYaml {

    public String application;
    /**
     * JavaBean wrapper for Index entries in IndexesXml.
     */
    public static class Index {
      public String kind;
      protected boolean ancestor;
      public List<Property> properties;

      public void setAncestor(String ancestor) {
        // Curse yamlbeans and its noncompliance.
        this.ancestor = YamlUtils.parseBoolean(ancestor);
      }

      public String getAncestor() {
        return "" + ancestor;
      }
    }

    /**
     * JavaBean wrapper for IndexesXml properties.
     */
    public static class Property {
      private String name = null;
      private String direction = null;
      private String mode = null;

      public void setDirection(String direction) {
        if (DIRECTION_VALUE_DESC.equals(direction)
            || DIRECTION_VALUE_ASC.equals(direction)) {
          this.direction = direction;
        } else {
          throw new AppEngineConfigException(
              "Invalid direction '" + direction + "': expected '" + DIRECTION_VALUE_ASC
              + "' or '" + DIRECTION_VALUE_DESC + "'.");
        }
      }

      public String getName() {
        return name;
      }

      public void setName(String name) {
        this.name = name;
      }

      public String getDirection() {
        return direction;
      }

      public void setMode(String mode) {
        // The Python serializer outputs "null" as a string by default.
        // However, the default behavior is overridden so it should not happen.
        // This serves as a safeguard.
        if ("null".equals(mode)) {
          mode = null;
        }

        if (mode == null || mode.equals(MODE_VALUE_GEOSPATIAL)) {
          this.mode = mode;
        } else {
          throw new AppEngineConfigException("Invalid mode: '" + mode);
        }
      }

      public String getMode() {
        return mode;
      }
    }

    private List<Index> indexes;

    public List<Index> getIndexes() {
      return indexes;
    }

    public void setIndexes(List<Index> indexes) {
      this.indexes = indexes;
    }

    public IndexesXml toXml(IndexesXml xml) {
      if (xml == null) {
        xml = new IndexesXml();
      }
      if (indexes != null) {
        for (Index yamlIndex : indexes) {
          if (yamlIndex.kind == null) {
            throw new AppEngineConfigException("Index missing required element 'kind'");
          }
          IndexesXml.Index xmlIndex = xml.addNewIndex(yamlIndex.kind, yamlIndex.ancestor);
          if (yamlIndex.properties != null) {
            for (Property property : yamlIndex.properties) {
              if (property.getName() == null) {
                throw new AppEngineConfigException("Property is missing required element 'name'.");
              }
              xmlIndex.addNewProperty(
                  property.getName(), property.getDirection(), property.getMode());
            }
          }
        }
      }
      return xml;
    }
  }

  public static IndexesXml parse(Reader yaml, IndexesXml xml) {
    List<IndexesXml> list;
    try {
      list = parseMultiple(yaml, xml);
    } catch (YamlException ex) {
      throw new AppEngineConfigException(ex.getMessage(), ex);
    }
    if (list.isEmpty()) {
      throw new AppEngineConfigException(
          "Empty index configuration. The index.yaml file should at least have an "
          + "empty 'indexes:' block.");
    }
    if (list.size() > 1) {
      throw new AppEngineConfigException(
          "yaml unexepectedly contains more than one document: " + list.size());
    }
    return list.get(0);
  }

  /**
   * Parses the Yaml from {@code yaml} into a Yaml document and deserializes
   * the document into an instance of {@link IndexesXml}.
   * @param yaml  A {@link String} from which to read the Yaml. This String is allowed to
   * be in the style generated by the admin server, including the Python-specific tags.
   * This method will safely ignore those tags.
   * @return An instance of {@link IndexesXml}.
   */
  public static IndexesXml parse(String yaml) {
    return parse(new StringReader(clean(yaml)), null);
  }

  /**
   * Parses the Yaml from {@code yaml} into one or more documents, and
   * deserializes the documents into one or more instances of {@link IndexesXml}
   * which are returned in a {@link List}.
   *
   * @param yaml A {@link String} from which to read the Yyaml. This String is
   *        allowed to be in the style generated by the admin server, including
   *        the Python-specific tags. This method will safely ignore those tags.
   * @return A {@link List} of {@link IndexesXml} instances representing one or
   *         more parsed Yaml documents.
   */
  public static List<IndexesXml> parseMultiple(String yaml) {
    try {
      return parseMultiple(new StringReader(clean(yaml)), null);
    } catch (YamlException ex) {
      throw new AppEngineConfigException(ex.getMessage(), ex);
    }
  }

  /**
   * Cleans a Yaml String by removing Pyton-specific tags.
   * These tags are written by the admin server when it generates
   * Yaml representing indexes.
   * @param yaml A {@link String} containing Yaml
   * @return The cleaned {@link String}
   */
  private static String clean(String yaml) {
    // TODO Remove this method once b/3244938 is fixed.
    return yaml
        .replaceAll(INDEX_DEFINITIONS_TAG, "")
        .replaceAll(INDEX_TAG, "")
        .replaceAll(PROPERTY_TAG, "")
        .trim();
  }

  /**
   * Parses the Yaml from {@code yaml} into one or more documents, and
   * deserializes the documents into one or more instances of {@link IndexesXml}
   * which are returned in a {@link List}.
   *
   * @param yaml A {@link Reader} from which to read the yaml
   * @param xml A possibly {@code null} {@link IndexesXml} instance. If this
   *        parameter is not {@code null} then each of the yaml documents will
   *        be deserialized into it. This parameter is intended to be
   *        used in cases where there is only one Yaml document expected and so
   *        there will only be one instance of {@link IndexesXml} in the
   *        returned {@link List}. If this parameter is not {@code null} and the
   *        returned list has length greater than 1, then the list will contain
   *        multiple copies of this parameter.
   * @return A {@link List} of {@link IndexesXml} instances representing one or
   *         more parsed Yaml documents.
   * @throws YamlException If the Yaml parser has trouble parsing.
   */
  private static List<IndexesXml> parseMultiple(Reader yaml, IndexesXml xml) throws YamlException {
    YamlReader reader = new YamlReader(yaml);
    reader.getConfig().setPropertyElementType(IndexYaml.class, "indexes", IndexYaml.Index.class);
    reader.getConfig().setPropertyElementType(
        IndexYaml.Index.class, "properties", IndexYaml.Property.class);
    List<IndexesXml> list = new ArrayList<IndexesXml>();
    while (true) {
      IndexYaml indexYaml = reader.read(IndexYaml.class);
      if (null == indexYaml) {
        break;
      } else {
        list.add(indexYaml.toXml(xml));
      }
    }
    return list;
  }

}
