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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A transform that represents zero or more transforms executed in series.
 *
 */
public class CompositeTransform extends Transform {

  private static final long serialVersionUID = -7811378887181575342L;

  private final List<Transform> transforms = new ArrayList<>();

  /**
   * Creates a new empty composite transform.
   */
  CompositeTransform() {
  }

  /**
   * Creates a new composite transform consisting of the provided collection of transforms in
   * series.
   * @param transformsToAdd transforms to be executed
   */
  CompositeTransform(Collection<Transform> transformsToAdd) {
    this();
    transforms.addAll(transformsToAdd);
  }

  /**
   * Concatenates a transform to the end of this composite transform.
   * @param other the transform to be appended
   * @return this transform with the other transform appended
   */
  public CompositeTransform concatenate(Transform other) {
    transforms.add(other);
    return this;
  }

  /**
   * Concatenates a transform to the start of this composite transform.
   * @param other the transform to be prepended
   * @return this transform with the other transform prepended
   */
  public CompositeTransform preConcatenate(Transform other) {
    transforms.add(0, other);
    return this;
  }

  /** {@inheritDoc} */
  @Override
  void apply(ImagesServicePb.ImagesTransformRequest.Builder request) {
    for (Transform transform : transforms) {
      transform.apply(request);
    }
  }

}
