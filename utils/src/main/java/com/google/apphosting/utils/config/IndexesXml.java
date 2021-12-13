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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Parsed datastore-indexes.xml file.
 *
 * Any additions to this class should also be made to the YAML
 * version in IndexYamlReader.java.
 *
 */
public class IndexesXml implements Iterable<IndexesXml.Index>{
  
  // Shared XML/YAML constants.
  public static final String DIRECTION_VALUE_ASC = "asc";
  public static final String DIRECTION_VALUE_DESC = "desc";
  public static final String MODE_VALUE_GEOSPATIAL = "geospatial";

  /**
   * A simple value object, encapsulating the specification of the
   * part of the index configuration attributable to a single
   * property.
   */
  public static class PropertySort {
    private final String propName;
    private final String direction;
    private final String mode;

    // TODO: rename this class, because it's now encapsulating
    // more than just the sort order (of a Property's contribution to
    // an index).
    public PropertySort(String propName, String direction, String mode) {
      this.propName = propName;
      this.direction = direction;
      this.mode = mode;
    }

    public String getPropertyName() {
      return propName;
    }

    public String getDirection() {
      return direction;
    }

    public String getMode() {
      return mode;
    }
  }

  /**
   * The type of index.
   * <p>
   * This is needed during parsing and validation.  The type is
   * not explicitly mentioned in the XML, but must be inferred from
   * the presence of various attributes.  Once the type is
   * established, it constrains the attributes the may appear in the
   * rest of the index configuration.
   */
  // TODO: consider representing by proper subclasses of class Index
  public enum Type {
    /**
     * A traditional Datastore index, with properties appearing in
     * ascending or descending order.
     */
    ORDERED,

    /**
     * An index supporting 2-dimensional distance queries on points on
     * the surface of the Earth.
     */
    GEO_SPATIAL
  };

  /**
   */
  public static class Index {
    private final String kind;
    private final Boolean ancestors;
    private final List<PropertySort> properties;

    public Index(String kind, Boolean ancestors) {
      this.kind = kind;
      this.ancestors = ancestors;
      this.properties = new ArrayList<PropertySort>();
    }

    public void addNewProperty(String name, String direction, String mode) {
      properties.add(new PropertySort(name, direction, mode));
    }

    public String getKind() {
      return kind;
    }

    public boolean doIndexAncestors() {
      return ancestors;
    }

    public List<PropertySort> getProperties() {
      return properties;
    }
    
    public String toYaml() {
      return toLocalStyleYaml();
    }

    /**
     * Builds a Yaml String representing this index, using the style of Yaml
     * generation appropriate for a local indexes.yaml files.
     * @return A Yaml String
     */
    private String toLocalStyleYaml(){
      StringBuilder builder = new StringBuilder(50 * (1 + properties.size()));
      builder.append("- kind: \"" + kind + "\"\n");
      if (Boolean.TRUE.equals(ancestors)) {
        builder.append("  ancestor: yes\n");
      }
      if (!properties.isEmpty()) {
        builder.append("  properties:\n");
        for (PropertySort prop : properties) {
          builder.append("  - name: \"" + prop.getPropertyName() + "\"\n");

          if (prop.getDirection() != null) {
            builder.append("    direction: " + prop.getDirection() + "\n");
          }

          if (prop.getMode() != null) {
            builder.append("    mode: " + prop.getMode() + "\n");
          }
        }
      }
      return builder.toString();
    }

    /**
     * Builds a Yaml string representing this index, mimicking the style of Yaml
     * generation used on the admin server. Since the admin server is written in
     * python, it generates a slightly different style of yaml. This method is
     * useful only for testing that the client-side code is able to parse this
     * style of yaml.
     *
     * @return An admin-server-style Yaml String.
     */
    private String toServerStyleYaml() {
      StringBuilder builder = new StringBuilder(50 * (1 + properties.size()));
      builder.append("- ").append(IndexYamlReader.INDEX_TAG).append("\n");
      builder.append("  kind: " + kind + "\n");
      if (Boolean.TRUE.equals(ancestors)) {
        builder.append("  ancestor: yes\n");
      }
      if (!properties.isEmpty()) {
        builder.append("  properties:\n");
        for (PropertySort prop : properties) {
          builder.append("  - ");
          builder.append(IndexYamlReader.PROPERTY_TAG);
          builder.append(" {");

          if (prop.getDirection() != null) {
            builder.append("direction: ");
            builder.append(prop.getDirection());
            builder.append(",\n");
          }

          if (prop.getMode() != null) {
            builder.append("mode: ");
            builder.append(prop.getMode());
            builder.append(",\n");
          }

          builder.append("    ");
          builder.append("name: " + prop.getPropertyName());
          builder.append("}\n");
        }
      }
      return builder.toString();
    }

    public String toXmlString() {
      StringBuilder builder = new StringBuilder(100 * (1 + properties.size()));
      String ancestorAttribute = ancestors == null ? ""
          : String.format(" ancestor=\"%s\"", ancestors);
      builder.append("<datastore-index kind=\"" + kind + "\"" + ancestorAttribute + ">\n");
      for (PropertySort prop : properties) {
        builder.append("    <property name=\"" + prop.getPropertyName() + "\"");

        if (prop.getDirection() != null) {
          builder.append(" direction=\"" + prop.getDirection() + "\"");
        }

        if (prop.getMode() != null) {
          builder.append(" mode=\"" + prop.getMode() + "\"");
        }

        builder.append("/>\n");
      }
      builder.append("</datastore-index>\n");
      return builder.toString();
    }
  }

  private final List<Index> indexes;

  public IndexesXml() {
    indexes = new ArrayList<Index>();
  }

  @Override
  public Iterator<Index> iterator() {
    return indexes.iterator();
  }

  public int size(){
    return indexes.size();
  }

  public Index addNewIndex(String kind, Boolean ancestors) {
    Index index = new Index(kind, ancestors);
    indexes.add(index);
    return index;
  }

  /**
   * Adds the given {@link Index} to the collection
   * contained in this object. Note that given {@link Index}
   * is not cloned. The provided object instance will become
   * incorporated into this object's collection.
   * @param index
   */
  public void addNewIndex(Index index){
    indexes.add(index);
  }

  public String toYaml() {
    return toYaml(false);
  }

  /**
   * Builds yaml string representing the indexes
   *
   * @param serverStyle Use the admin server style of yaml generation. Since the
   *        admin server is written in python, it generates a slightly different
   *        style of yaml. Setting this parameter to {@code true} is useful only
   *        for testing that the client-side code is able to parse this style of
   *        yaml.
   * @return A Yaml string.
   */
  public String toYaml(boolean serverStyle) {
    StringBuilder builder = new StringBuilder(1024);
    if (serverStyle) {
      builder.append(IndexYamlReader.INDEX_DEFINITIONS_TAG).append("\n");
    }
    builder.append("indexes:");
    int numIndexes = (null == indexes ? 0 : indexes.size());
    if (0 == numIndexes && serverStyle) {
      builder.append(" []");
    }
    builder.append("\n");
    for (Index index : indexes) {
      String indexYaml = (serverStyle ? index.toServerStyleYaml() : index.toLocalStyleYaml());
      builder.append(indexYaml);
    }
    return builder.toString();
  }

}
