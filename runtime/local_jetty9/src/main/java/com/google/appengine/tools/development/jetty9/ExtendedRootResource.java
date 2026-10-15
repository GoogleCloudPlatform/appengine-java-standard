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

package com.google.appengine.tools.development.jetty9;

import java.io.File;
import org.eclipse.jetty.util.resource.FileResource;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceCollection;

/**
 * A {@code Resource} that represents a web root extended to include an external resource directory.
 * <p>
 * We replace Jetty's baseResource with an instance of this class in order to implement our
 * "External Resource Directory" feature.
 *
 */
//
// Implementation note: Jetty's ResourceCollection class seems to give us
// exactly the functionality we need. But we have chosen to create this
// subclass so that we can more easily fine-tune the behavior to correspond to
// the specification of the External Resource Directory feature.
// See go/appengine-external-resource-dirs
//
public class ExtendedRootResource extends ResourceCollection {
  public ExtendedRootResource(Resource webRoot, File externalResourceDir) {
    // We want to search the externalResourceDir before the webRoot, so
    // we put it first.
    super(new Resource[] {toResource(externalResourceDir), webRoot});
  }

  private static Resource toResource(File f) {
    if (f == null) {
      throw new NullPointerException("externalResourceDir may not be null");
    }
    try {
      return new FileResource(f.toURI().toURL());
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid externalResourceDirectory: " + f.getPath(), e);
    }
  }
}
