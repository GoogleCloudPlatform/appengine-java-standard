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
 * A transform that will resize an image to fit within a bounding box.
 *
 */
final class Resize extends Transform {

  private static final long serialVersionUID = -889209644904728094L;

  private final int width;
  private final int height;
  private final boolean cropToFit;
  private final float cropOffsetX;
  private final float cropOffsetY;
  private final boolean allowStretch;

  /**
   * Creates a transform that will resize an image to fit within a rectangle
   * with the given dimensions. If {@code allowStretch} is true, then the image
   * is resized without maintaining the original aspect ratio.
   * @param width width of the bounding box
   * @param height height of the bounding box
   * @param allowStretch resize the image without maintaining the aspect ratio.
   * @throws IllegalArgumentException If {@code width} or {@code height} are
   * negative or greater than {@code MAX_RESIZE_DIMENSIONS}, if both
   * {@code width} and {@code height} are 0 or if {@code allowStretch} is
   * set and {@code width} or {@code height} is 0.
   */
  Resize(int width, int height, boolean allowStretch) {
    this(width, height, false, 0, 0, allowStretch);
  }

  /**
   * Creates a transform that will resize an image to fit within a rectangle
   * with the given dimensions. If {@code cropToFit} is true, then the image is
   * cropped to fit, with the center specified by {@code cropOffsetX} and
   * {@code cropOffsetY}.
   * @param width width of the bounding box
   * @param height height of the bounding box
   * @param cropToFit whether the image should be cropped to fit
   * @param cropOffsetX the relative horizontal position of the center
   * @param cropOffsetY the relative vertical position of the center
   * @throws IllegalArgumentException If {@code width} or {@code height} are
   * negative or greater than {@code MAX_RESIZE_DIMENSIONS}, if both
   * {@code width} and {@code height} are 0 or if {@code cropToFit} is
   * set and {@code width} or {@code height} is 0 or {@code cropOffsetX} or
   * {@code cropOffsetY} is outside the range 0.0 to 1.0.
   */
  Resize(int width, int height, boolean cropToFit, float cropOffsetX, float cropOffsetY) {
    this(width, height, cropToFit, cropOffsetX, cropOffsetY, false);
  }

  /**
   * Creates a transform that will resize an image to fit within a rectangle
   * with the given dimensions. If {@code cropToFit} is true, then the image is
   * cropped to fit, with the center specified by {@code cropOffsetX} and
   * {@code cropOffsetY}.
   * @param width width of the bounding box
   * @param height height of the bounding box
   * @param cropToFit whether the image should be cropped to fit
   * @param cropOffsetX the relative horizontal position of the center
   * @param cropOffsetY the relative vertical position of the center
   * @param allowStretch resize the image without maintaining the aspect ratio.
   * @throws IllegalArgumentException If {@code width} or {@code height} are
   * negative or greater than {@code MAX_RESIZE_DIMENSIONS}, if both
   * {@code width} and {@code height} are 0, if {@code allowStretch} is set and
   * and {@code width} or {@code height} is 0 or if {@code cropToFit} is
   * set and {@code width} or {@code height} is 0 or {@code cropOffsetX} or
   * {@code cropOffsetY} is outside the range 0.0 to 1.0.
   */
  Resize(int width, int height, boolean cropToFit, float cropOffsetX,
      float cropOffsetY, boolean allowStretch) {
    if (width > ImagesService.MAX_RESIZE_DIMENSIONS
        || height > ImagesService.MAX_RESIZE_DIMENSIONS) {
      throw new IllegalArgumentException("width and height must be <= "
                                         + ImagesService.MAX_RESIZE_DIMENSIONS);
    }
    if (width < 0 || height < 0) {
      throw new IllegalArgumentException("width and height must be >= 0");
    }
    if (width == 0 && height == 0) {
      throw new IllegalArgumentException("width and height must not both be == 0");
    }
    if (cropToFit) {
      if (width == 0 || height == 0) {
        throw new IllegalArgumentException(
            "neither of width and height can be == 0 with crop to fit enabled");
      }
      checkCropArgument(cropOffsetX);
      checkCropArgument(cropOffsetY);
    }
    if (allowStretch) {
      if (width == 0 || height == 0) {
        throw new IllegalArgumentException(
            "Resize requests with allowStretch as true require that both "
            + "width and hight are non zero");
      }
    }
    this.width = width;
    this.height = height;
    this.cropToFit = cropToFit;
    this.cropOffsetX = cropOffsetX;
    this.cropOffsetY = cropOffsetY;
    this.allowStretch = allowStretch;
  }

  /** {@inheritDoc} */
  @Override
  void apply(ImagesServicePb.ImagesTransformRequest.Builder request) {
    request.addTransform(
        ImagesServicePb.Transform.newBuilder()
        .setWidth(width)
        .setHeight(height)
        .setCropToFit(cropToFit)
        .setCropOffsetX(cropOffsetX)
        .setCropOffsetY(cropOffsetY)
        .setAllowStretch(allowStretch));
  }

  /**
   * Checks that a crop argument is in the valid range.
   * @param arg crop argument
   */
  private void checkCropArgument(float arg) {
    if (arg < 0.0) {
      throw new IllegalArgumentException("Crop offsets must be >= 0");
    }
    if (arg > 1.0) {
      throw new IllegalArgumentException("Crop offsets must be <= 1");
    }
  }
}
