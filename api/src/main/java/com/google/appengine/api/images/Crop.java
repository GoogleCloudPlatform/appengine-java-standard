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
 * A transform that will crop an image to fit within a given bounding box.
 *
 */
final class Crop extends Transform {

  private static final long serialVersionUID = -5386318194508610219L;

  private final float leftX;
  private final float topY;
  private final float rightX;
  private final float bottomY;

  /**
   * Creates a crop transform.
   * @param leftX X coordinate of the top left corner
   * @param topY Y coordinate of the top left corner
   * @param rightX X coordinate of the bottom right corner
   * @param bottomY Y coordinate of the bottom right corner
   * @throws IllegalArgumentException If any of the arguments are outside the
   * range 0.0 to 1.0 or if {@code leftX >= rightX} or {@code topY >= bottomY}.
   */
  Crop(float leftX, float topY, float rightX, float bottomY) {
    checkCropArgument(leftX);
    checkCropArgument(topY);
    checkCropArgument(rightX);
    checkCropArgument(bottomY);
    if (leftX >= rightX) {
      throw new IllegalArgumentException("leftX must be < rightX");
    }
    if (topY >= bottomY) {
      throw new IllegalArgumentException("topY must be < bottomY");
    }
    this.leftX = leftX;
    this.topY = topY;
    this.rightX = rightX;
    this.bottomY = bottomY;

  }

  /** {@inheritDoc} */
  @Override
  void apply(ImagesServicePb.ImagesTransformRequest.Builder request) {
    request.addTransform(
        ImagesServicePb.Transform.newBuilder()
        .setCropLeftX(leftX)
        .setCropTopY(topY)
        .setCropRightX(rightX)
        .setCropBottomY(bottomY));
  }

  /**
   * Checks that a crop argument is in the valid range.
   * @param arg crop argument
   */
  private void checkCropArgument(float arg) {
    if (arg < 0.0) {
      throw new IllegalArgumentException("Crop arguments must be >= 0");
    }
    if (arg > 1.0) {
      throw new IllegalArgumentException("Crop arguments must be <= 1");
    }
  }

}
