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

import com.google.common.collect.ImmutableList;
import java.util.List;

/**
 * Parsed configuration from a dispatch.xml file.
 */
public class DispatchXml {

  private final List<DispatchEntry> dispatchEntries;

  public static Builder builder() {
    return new Builder();
  }

  private DispatchXml(List<DispatchEntry> dispatchEntries) {
    this.dispatchEntries = dispatchEntries;
  }

  /**
   * Returns the YAML equivalent of this dispatch.xml file.
   *
   * @return contents of an equivalent {@code dos.yaml} file.
   */
  public String toYaml() {
    StringBuilder builder = new StringBuilder("dispatch:\n");
    for (DispatchEntry entry : dispatchEntries) {
      builder.append("- url: " + yamlQuote(entry.getUrl()) + "\n");
      builder.append("  module: " + entry.getModule() + "\n");
    }
    return builder.toString();
  }

  /**
   * Surrounds the provided string with single quotes, escaping any single
   * quotes in the string by replacing them with ''.
   */
  private String yamlQuote(String str) {
    return "'" + str.replace("'", "''") + "'";
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((dispatchEntries == null) ? 0 : dispatchEntries.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof DispatchXml)) {
      return false;
    }
    DispatchXml other = (DispatchXml) obj;
    if (dispatchEntries == null) {
      if (other.dispatchEntries != null) {
        return false;
      }
    } else if (!dispatchEntries.equals(other.dispatchEntries)) {
      return false;
    }
    return true;
  }

  /**
   * Builder for a {@link DispatchXml}.
   */
  public static class Builder {
    private final ImmutableList.Builder<DispatchEntry> dispatchEntriesBuilder =
        ImmutableList.builder();

    private Builder() {
    }

    public Builder addDispatchEntry(DispatchEntry entry) {
      entry.validate();
      dispatchEntriesBuilder.add(entry);
      return this;
    }

    public DispatchXml build() {
      return new DispatchXml(dispatchEntriesBuilder.build());
    }
  }

  /**
   * Describes a single dispatch entry.
   */
  public static class DispatchEntry {

    private final String url;
    private final String module;

    /** Create an empty denylist entry. */
    public DispatchEntry(String url, String module) {
      this.url = url;
      this.module = module;
    }

    public String getUrl() {
      return url;
    }

    public String getModule() {
      return module;
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((module == null) ? 0 : module.hashCode());
      result = prime * result + ((url == null) ? 0 : url.hashCode());
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (!(obj instanceof DispatchEntry)) {
        return false;
      }
      DispatchEntry other = (DispatchEntry) obj;
      if (module == null) {
        if (other.module != null) {
          return false;
        }
      } else if (!module.equals(other.module)) {
        return false;
      }
      if (url == null) {
        if (other.url != null) {
          return false;
        }
      } else if (!url.equals(other.url)) {
        return false;
      }
      return true;
    }

    private void validate() {
      if (url == null) {
        throw new AppEngineConfigException("Invalid url in dispatch.xml - " + url);
      }
      if (module == null) {
        throw new AppEngineConfigException("Invalid module in dispatch.xml - " + module);
      }
    }
  }
}
