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

package com.google.appengine.tools.development.jetty.ee10;

import jakarta.servlet.ServletContainerInitializer;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jetty.ee10.annotations.AnnotationConfiguration;
import org.eclipse.jetty.ee10.apache.jsp.JettyJasperInitializer;

/**
 * Customization of AnnotationConfiguration which correctly configures the JSP Jasper initializer.
 * For more context, see b/37513903
 */
public class AppEngineAnnotationConfiguration extends AnnotationConfiguration {
  @Override
  protected List<ServletContainerInitializer> getNonExcludedInitializers(State state) {

    List<ServletContainerInitializer> initializers = super.getNonExcludedInitializers(state);
    for (ServletContainerInitializer sci : initializers) {
      if (sci instanceof JettyJasperInitializer) {
        // Jasper is already there, no need to add it.
        return initializers;
      }
    }

    initializers = new ArrayList<>(initializers);
    initializers.add(new JettyJasperInitializer());
    return initializers;
  }
}
