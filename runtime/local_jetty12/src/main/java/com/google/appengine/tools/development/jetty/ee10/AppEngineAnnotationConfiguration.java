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

import java.util.ArrayList;
import java.util.List;
import jakarta.servlet.ServletContainerInitializer;
import org.eclipse.jetty.ee10.annotations.AnnotationConfiguration;
import org.eclipse.jetty.ee10.apache.jsp.JettyJasperInitializer;
import org.eclipse.jetty.ee10.webapp.WebAppContext;

/**
 * Customization of AnnotationConfiguration which correctly configures the JSP Jasper initializer.
 * For more context, see b/37513903
 */
public class AppEngineAnnotationConfiguration extends AnnotationConfiguration {
  @Override
  public List<ServletContainerInitializer> getNonExcludedInitializers(WebAppContext context)
      throws Exception {
    ArrayList<ServletContainerInitializer> nonExcludedInitializers =
        new ArrayList<>(super.getNonExcludedInitializers(context));
    for (ServletContainerInitializer sci : nonExcludedInitializers) {
      if (sci instanceof JettyJasperInitializer) {
        // Jasper is already there, no need to add it.
        return nonExcludedInitializers;
      }
    }
    nonExcludedInitializers.add(new JettyJasperInitializer());

    return nonExcludedInitializers;
  }
}
