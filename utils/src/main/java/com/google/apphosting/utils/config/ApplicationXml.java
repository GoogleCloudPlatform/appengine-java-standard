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
 * Holder for application.xml properties. Only properties needed to upload
 * an application or run it in the Java Development Server are included.
 *
 */
public class ApplicationXml {
  private final Modules modules;

  private ApplicationXml(Modules modules) {
    this.modules = modules;
  }

  public Modules getModules() {
    return modules;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((modules == null) ? 0 : modules.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    ApplicationXml other = (ApplicationXml) obj;
    if (modules == null) {
      if (other.modules != null) {
        return false;
      }
    } else if (!modules.equals(other.modules)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "ApplicationXml: modules=" + modules;
  }

  /**
   * Returns a {@link Builder}.
   */
  static Builder builder() {
    return new Builder();
  }

  /**
   * Builder for an {@link ApplicationXml}.
   */
  public static class Builder {
    Modules.Builder modulesBuilder = Modules.Builder.builder();

    private Builder() {
    }

    /**
     * Returns a {@link Modules.Builder} for adding modules.
     */
    public Modules.Builder getModulesBuilder() {
      return modulesBuilder;
    }

    /**
     * Builds and returns a new {@link ApplicationXml}.
      */
    public ApplicationXml build() {
      return new ApplicationXml(modulesBuilder.build());
    }
  }

  /**
   * Holder for an application's modules properties.
   */
  public static class Modules {
    private final ImmutableList<Web> web;

    private Modules(ImmutableList<Web> web) {
      this.web = web;
    }

    public List<Web> getWeb() {
      return web;
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((web == null) ? 0 : web.hashCode());
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      Modules other = (Modules) obj;
      if (web == null) {
        if (other.web != null) {
          return false;
        }
      } else if (!web.equals(other.web)) {
        return false;
      }
      return true;
    }

    @Override
    public String toString() {
      return "Modules: web=" + web;
    }

    /**
     * Builder for {@link Modules}.
     */
    static class Builder {
      ImmutableList.Builder<Web> webBuilder = ImmutableList.builder();

      private Builder() {
      }

      /**
       * Returns a new {@link Builder}.
       */
      private static Builder builder() {
        return new Builder();
      }

      /**
       * Adds a {@link Web}.
       */
      Builder addWeb(Web web) {
        webBuilder.add(web);
        return this;
      }

      /**
       * Builds and returns the newly constructed {@link Modules}.
        */
      Modules build() {
        return new Modules(webBuilder.build());
      }
    }

    /**
     * Holder for properties for a web module.
     */
    public static class Web {

      @Override
      public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((contextRoot == null) ? 0 : contextRoot.hashCode());
        result = prime * result + ((webUri == null) ? 0 : webUri.hashCode());
        return result;
      }

       @Override
      public boolean equals(Object obj) {
        if (this == obj) {
          return true;
        }
        if (obj == null) {
          return false;
        }
        if (getClass() != obj.getClass()) {
          return false;
        }
        Web other = (Web) obj;
        if (contextRoot == null) {
          if (other.contextRoot != null) {
            return false;
          }
        } else if (!contextRoot.equals(other.contextRoot)) {
          return false;
        }
        if (webUri == null) {
          if (other.webUri != null) {
            return false;
          }
        } else if (!webUri.equals(other.webUri)) {
          return false;
        }
        return true;
      }


      private final String webUri;
      private final String contextRoot;

      Web(String webUri, String contextRoot) {
        this.webUri = webUri;
        this.contextRoot = contextRoot;
      }

      public String getWebUri() {
        return webUri;
      }

      public String getContextRoot() {
        return contextRoot;
      }


      @Override
      public String toString() {
        return "Web:  webUri=" + webUri + " contextRoot=" + contextRoot;
      }
    }
  }
}
