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

import com.google.appengine.api.blobstore.BlobKey;
import com.google.appengine.spi.ServiceFactoryFactory;
import java.util.Collection;

/**
 * Factory for creating an {@link ImagesService}, {@link Image}s and
 * {@link Transform}s.
 *
 */
public final class ImagesServiceFactory {

  /**
   * Creates an implementation of the ImagesService.
   * @return an images service
   */
  public static ImagesService getImagesService() {
    return getFactory().getImagesService();
  }

  /**
   * Creates an image from the provided {@code imageData}.
   * @param imageData image data to store in the image
   * @return an Image containing the supplied image data
   * @throws IllegalArgumentException If {@code imageData} is null or empty.
   */
  public static Image makeImage(byte[] imageData) {
    return getFactory().makeImage(imageData);
  }

  /**
   * Create an image backed by the specified {@code blobKey}.  Note
   * that the returned {@link Image} object can be used with all
   * {@link ImagesService} methods, but most of the methods on the
   * Image itself will currently throw {@link
   * UnsupportedOperationException}.
   *
   * @param blobKey referencing the image
   * @return an Image that references the specified blob data
   */
  public static Image makeImageFromBlob(BlobKey blobKey) {
    return getFactory().makeImageFromBlob(blobKey);
  }

  /**
   * Create an image backed by the specified {@code filename}. Note
   * that the returned {@link Image} object can be used with all
   * {@link ImagesService} methods, but most of the methods on the
   * Image itself will currently throw {@link
   * UnsupportedOperationException}.
   *
   * @param filename referencing the image. Currently only Google Storage files
   * in the format "/gs/bucket_name/object_name" are supported.
   * @return an Image that references the specified blob data
   * @throws IllegalArgumentException If {@code filename} is not in the format
   * "/gs/bucket_name/object_name".
   * @throws com.google.appengine.api.blobstore.BlobstoreFailureException If there is an error
   * obtaining the Google Storage access token for the {@code filename}.
   */
  public static Image makeImageFromFilename(String filename) {
    return getFactory().makeImageFromFilename(filename);
  }

  /**
   * Creates a transform that will resize an image to fit within a box with
   * width {@code width} and height {@code height}.
   * @param width width of the bounding box
   * @param height height of the bounding box
   * @return a resize transform
   * @throws IllegalArgumentException If {@code width} or {@code height} are
   * negative or greater than {@code MAX_RESIZE_DIMENSIONS} or if both
   * {@code width} and {@code height} are 0.
   */
  public static Transform makeResize(int width, int height) {
    return getFactory().makeResize(width, height);
  }

  /**
   * Creates a resize transform that will resize an image to fit within a box
   * of width {@code width} and height {@code height}. If {@code allowStretch}
   * is {@code true}, the aspect ratio of the original image will be ignored.
   *
   * @param width width of the bounding box
   * @param height height of the bounding box
   * @param allowStretch allow the image to be resized ignoring the aspect ratio
   * @return a resize transform
   * @throws IllegalArgumentException If {@code width} or {@code height} are negative or greater
   *         than {@code MAX_RESIZE_DIMENSIONS}, if both {@code width} and {@code height} are 0 or
   *         if {@code allowStretch} is True and either {@code width} or {@code height} are 0.
   */
  public static Transform makeResize(int width, int height, boolean allowStretch) {
    return getFactory().makeResize(width, height, allowStretch);
  }


  /**
   * Creates a transform that will resize an image to exactly fit a box with
   * width {@code width} and height {@code height} by resizing to the less
   * constraining dimension and cropping the other. The center of the crop
   * region is controlled by {@code cropOffsetX} and {@code cropOffsetY}.
   * @param width width of the bounding box
   * @param height height of the bounding box
   * @param cropOffsetX the relative horizontal position of the center
   * @param cropOffsetY the relative vertical position of the center
   * @return a resize transform
   * @throws IllegalArgumentException If {@code width} or {@code height} are
   * negative or greater than {@code MAX_RESIZE_DIMENSIONS}, if either of
   * {@code width} and {@code height} are 0 or if {@code cropOffsetX} or
   * {@code cropOffsetY} are outside the range 0.0 to 1.0.
   */
  public static Transform makeResize(int width, int height, float cropOffsetX, float cropOffsetY) {
    return getFactory().makeResize(width, height, cropOffsetX, cropOffsetY);
  }

  /**
   * Creates a transform that will resize an image to exactly fit a box with
   * width {@code width} and height {@code height} by resizing to the less
   * constraining dimension and cropping the other. The center of the crop
   * region is controlled by {@code cropOffsetX} and {@code cropOffsetY}.
   * @param width width of the bounding box
   * @param height height of the bounding box
   * @param cropOffsetX the relative horizontal position of the center
   * @param cropOffsetY the relative vertical position of the center
   * @return a resize transform
   * @throws IllegalArgumentException If {@code width} or {@code height} are
   * negative or greater than {@code MAX_RESIZE_DIMENSIONS}, if either of
   * {@code width} and {@code height} are 0 or if {@code cropOffsetX} or
   * {@code cropOffsetY} are outside the range 0.0 to 1.0.
   */
  public static Transform makeResize(int width, int height, double cropOffsetX,
      double cropOffsetY) {
    return getFactory().makeResize(width, height, cropOffsetX, cropOffsetY);
  }

  /**
   * Creates a transform that will crop an image to fit within the bounding
   * box specified.
   *
   * The arguments define the top left and bottom right corners of the
   * bounding box used to crop the image as a percentage of the total image
   * size. Each argument should be in the range 0.0 to 1.0 inclusive.
   * @param leftX X coordinate of the top left corner of the bounding box
   * @param topY Y coordinate of the top left corner of the bounding box
   * @param rightX X coordinate of the bottom right corner of the bounding box
   * @param bottomY Y coordinate of the bottom right corner of the bounding box
   * @return a crop transform
   * @throws IllegalArgumentException If any of the arguments are outside the
   * range 0.0 to 1.0 or if {@code leftX >= rightX} or {@code topY >= bottomY}.
   */
  public static Transform makeCrop(float leftX, float topY, float rightX, float bottomY) {
    return getFactory().makeCrop(leftX, topY, rightX, bottomY);
  }

  /**
   * Creates a transform that will crop an image to fit within the bounding
   * box specified.
   *
   * The arguments define the top left and bottom right corners of the
   * bounding box used to crop the image as a percentage of the total image
   * size. Each argument should be in the range 0.0 to 1.0 inclusive.
   * @param leftX X coordinate of the top left corner of the bounding box
   * @param topY Y coordinate of the top left corner of the bounding box
   * @param rightX X coordinate of the bottom right corner of the bounding box
   * @param bottomY Y coordinate of the bottom right corner of the bounding box
   * @return a crop transform
   * @throws IllegalArgumentException If any of the arguments are outside the
   * range 0.0 to 1.0 or if {@code leftX >= rightX} or {@code topY >= bottomY}.
   */
  public static Transform makeCrop(double leftX, double topY, double rightX, double bottomY) {
    return getFactory().makeCrop(leftX, topY, rightX, bottomY);
  }

  /**
   * Creates a transform that will vertically flip an image.
   * @return a vertical flip transform
   */
  public static Transform makeVerticalFlip() {
    return getFactory().makeVerticalFlip();
  }

  /**
   * Creates a transform that will horizontally flip an image.
   * @return a horizontal flip transform
   */
  public static Transform makeHorizontalFlip() {
    return getFactory().makeHorizontalFlip();
  }

  /**
   * Creates a transform that rotates an image by {@code degrees} degrees
   * clockwise.
   *
   * @param degrees The number of degrees by which to rotate. Must be a
   *                multiple of 90.
   * @return a rotation transform
   * @throws IllegalArgumentException If {@code degrees} is not divisible by 90
   */
  public static Transform makeRotate(int degrees) {
    return getFactory().makeRotate(degrees);
  }

  /**
   * Creates a transform that automatically adjust contrast and color levels.
   *
   * This is similar to the "I'm Feeling Lucky" button in Picasa.
   * @return an ImFeelingLucky autolevel transform
   */
  public static Transform makeImFeelingLucky() {
    return getFactory().makeImFeelingLucky();
  }

  /**
   * Creates a composite transform that can represent multiple transforms
   * applied in series.
   *
   * @param transforms Transforms for this composite transform to apply.
   * @return a composite transform containing the provided transforms
   */
  public static CompositeTransform makeCompositeTransform(Collection<Transform> transforms) {
    return getFactory().makeCompositeTransform(transforms);
  }

  /**
   * Creates a composite transform that can represent multiple transforms
   * applied in series.
   * @return an empty composite transform
   */
  public static CompositeTransform makeCompositeTransform() {
    return getFactory().makeCompositeTransform();
  }

  /**
   * Creates an image composition operation.
   * @param image The image to be composited.
   * @param xOffset Offset in the x axis from the anchor point.
   * @param yOffset Offset in the y axis from the anchor point.
   * @param opacity Opacity to be used for the image in range [0.0, 1.0].
   * @param anchor Anchor position from the enum {@link Composite.Anchor}.
   * The anchor position of the image is aligned with the anchor position of
   * the canvas and then the offsets are applied.
   * @return A composition operation.
   * @throws IllegalArgumentException If {@code image} is null or empty,
   * {@code xOffset} or {@code yOffset} is outside the range
   * [-{@value
   * com.google.appengine.api.images.ImagesService#MAX_RESIZE_DIMENSIONS},
   * {@value
   * com.google.appengine.api.images.ImagesService#MAX_RESIZE_DIMENSIONS}],
   * {@code opacity} is outside the range [0.0, 1.0] or {@code anchor} is null.
   */
  public static Composite makeComposite(Image image, int xOffset, int yOffset, float opacity,
      Composite.Anchor anchor) {
    return getFactory().makeComposite(image, xOffset, yOffset, opacity, anchor);
  }

  // Do not instantiate.
  private ImagesServiceFactory() {
  }

  private static IImagesServiceFactory getFactory() {
    return ServiceFactoryFactory.getFactory(IImagesServiceFactory.class);
  }
}
