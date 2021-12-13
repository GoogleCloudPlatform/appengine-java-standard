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
 * A transform that will rotate an image by a multiple of 90 degrees.
 *
 */
final class Rotate extends Transform {

  private static final long serialVersionUID = -8585289244565451429L;

  private final int degrees;

  /**
   * Creates a Rotate transform that rotates an image by {@code degrees} degrees.
   * @param degrees number of degrees to rotate
   * @throws IllegalArgumentException If {@code degrees} is not divisible by 90
  */
  Rotate(int degrees) {
    if ((degrees % 90) != 0) {
      throw new IllegalArgumentException("degrees must be a multiple of 90");
    }
    // One mod to get in range (-360,360), addition and another mod to get in
    // range [0,360).
    this.degrees = ((degrees % 360) + 360) % 360;
  }

  /** {@inheritDoc} */
  @Override
  void apply(ImagesServicePb.ImagesTransformRequest.Builder request) {
    request.addTransform(ImagesServicePb.Transform.newBuilder().setRotate(degrees));
  }
}
