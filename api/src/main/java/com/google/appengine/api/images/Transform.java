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

package com.google.appengine.api.images;

/**
 * A transform that can be applied to an {@link Image}.
 *
 */
public abstract class Transform implements java.io.Serializable {

  private static final long serialVersionUID = -8951126706057535378L;

  /**
   * Adds this transform to the supplied {@code request}.
   * @param request request for the transform to be added to
   */
  abstract void apply(ImagesServicePb.ImagesTransformRequest.Builder request);

}
