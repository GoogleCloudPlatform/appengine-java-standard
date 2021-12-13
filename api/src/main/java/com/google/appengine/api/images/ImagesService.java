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
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Future;

/**
 * The images service provides methods to apply transformations to images.
 *
 */
public interface ImagesService {

  public static final int MAX_TRANSFORMS_PER_REQUEST = 10;
  public static final int MAX_RESIZE_DIMENSIONS = 4000;
  public static final int MAX_COMPOSITES_PER_REQUEST = 16;
  public static final int SERVING_SIZES_LIMIT = 1600;

  @Deprecated
  public static final Set<Integer> SERVING_SIZES = new TreeSet<Integer>(
      Arrays.asList(
          0, 32, 48, 64, 72, 80, 90, 94, 104, 110, 120, 128, 144,
          150, 160, 200, 220, 288, 320, 400, 512, 576, 640, 720,
          800, 912, 1024, 1152, 1280, 1440, 1600));

  @Deprecated
  public static final Set<Integer> SERVING_CROP_SIZES = new TreeSet<Integer>(
      Arrays.asList(
          32, 48, 64, 72, 80, 104, 136, 144, 150, 160));

  /**
   * Valid output encoding formats usable for image transforms.
   *
   * @see <a href="http://www.libpng.org/pub/png/">PNG Image Format.</a>
   * @see <a href="https://en.wikipedia.org/wiki/JPEG">JPEG Image Format.</a>
   * @see <a href="https://developers.google.com/speed/webp/">WEBP Image Format.</a>
   */
  public static enum OutputEncoding {
    PNG, JPEG, WEBP}

  /**
   * Applies the provided {@code transform} to the provided {@code image}
   * encoding the transformed image stored using PNG file format. The
   * transform is applied in place to the provided image.
   *
   * @param transform transform to be applied
   * @param image     image to be transformed
   * @return transformed image
   * @throws IllegalArgumentException If {@code transform} or {@code image}
   * are invalid.
   * @throws ImagesServiceFailureException If there is a problem with the
   * Images Service
   */
  public Image applyTransform(Transform transform, Image image);

  /**
   * Asynchronously applies the provided {@code transform} to the
   * provided {@code image} encoding the transformed image stored using
   * PNG file format. The transform is applied in place to the provided image.
   *
   * @param transform transform to be applied
   * @param image     image to be transformed
   * @return A future containing the transformed image or one of the
   * exceptions documented for {@link #applyTransform(Transform, Image)}.
   */
  public Future<Image> applyTransformAsync(Transform transform, Image image);

  /**
   * Applies the provided {@code transform} to the provided {@code image}
   * encoding the transformed image stored using {@code encoding} file
   * format. The transform is applied in place to the provided image.
   *
   * @param transform transform to be applied
   * @param image     image to be transformed
   * @param encoding  output encoding to be used
   * @return transformed image
   * @throws IllegalArgumentException If {@code transform}, {@code image} or
   * {@code encoding} are invalid.
   * @throws ImagesServiceFailureException If there is a problem with the
   * Images Service
   */
  public Image applyTransform(Transform transform, Image image,
                              OutputEncoding encoding);

  /**
   * Asynchronously applies the provided {@code transform} to the provided
   * {@code image} encoding the transformed image stored using {@code encoding}
   * file format. The transform is applied in place to the provided image.
   *
   * @param transform transform to be applied
   * @param image     image to be transformed
   * @param encoding  output encoding to be used
   * @return A future containing the transformed image or one of the
   * exceptions documented for {@link #applyTransform(Transform, Image, OutputEncoding)}.
   */
  public Future<Image> applyTransformAsync(Transform transform, Image image,
                              OutputEncoding encoding);

  /**
   * Applies the provided {@code transform} to the provided {@code image}
   * encoding the transformed image stored using {@code settings}.
   * The transform is applied in place to the provided image.
   *
   * @param transform transform to be applied
   * @param image     image to be transformed
   * @param settings output settings to be used
   * @return transformed image
   * @throws IllegalArgumentException If {@code transform}, {@code image} or
   * {@code settings} are invalid.
   * @throws ImagesServiceFailureException If there is a problem with the
   * Images Service
   */
  public Image applyTransform(Transform transform, Image image,
                              OutputSettings settings);

  /**
   * Asynchronously applies the provided {@code transform} to the provided
   * {@code image} encoding the transformed image stored using {@code settings}.
   * The transform is applied in place to the provided image.
   *
   * @param transform transform to be applied
   * @param image     image to be transformed
   * @param settings  output settings to be used
   * @return A future containing the transformed image or one of the
   * exceptions documented for {@link #applyTransform(Transform, Image, OutputSettings)}.
   */
  public Future<Image> applyTransformAsync(Transform transform, Image image,
                                           OutputSettings settings);

  /**
   * Applies the provided {@code transform} to the provided {@code image}
   * encoding the transformed image stored using {@code outputSettings}
   * interpreting {@code image} according to {@code inputSettings}.
   * The transform is applied in place to the provided image.
   *
   * @param transform transform to be applied
   * @param image     image to be transformed
   * @param inputSettings input settings to be used
   * @param outputSettings output settings to be used
   * @return transformed image
   * @throws IllegalArgumentException If {@code transform}, {@code image},
   * {@code inputSettings} or {@code outputSettings} are invalid.
   * @throws ImagesServiceFailureException If there is a problem with the
   * Images Service
   */
  public Image applyTransform(Transform transform, Image image,
                              InputSettings inputSettings, OutputSettings outputSettings);

  /**
   * Asynchronously applies the provided {@code transform} to the provided
   * {@code image} encoding the transformed image stored using {@code settings}
   * interpreting {@code image} according to {@code inputSettings}.
   * The transform is applied in place to the provided image.
   *
   * @param transform transform to be applied
   * @param image     image to be transformed
   * @param inputSettings input settings to be used
   * @param outputSettings  output settings to be used
   * @return A future containing the transformed image or one of the
   * exceptions documented for
   * {@link #applyTransform(Transform, Image, InputSettings, OutputSettings)}.
   */
  public Future<Image> applyTransformAsync(Transform transform, Image image,
                                           InputSettings inputSettings,
                                           OutputSettings outputSettings);

  /**
   * Applies the provided {@link Collection} of {@link Composite}s using a
   * canvas with dimensions determined by {@code width} and {@code height}
   * and background color {@code color}. Uses PNG as its output encoding.
   * @param composites Compositing operations to be applied.
   * @param width Width of the canvas in pixels.
   * @param height Height of the canvas in pixels.
   * @param color Background color of the canvas in ARGB format.
   * @return A new image containing the result of composition.
   * @throws IllegalArgumentException If {@code width} or {@code height} is
   * greater than {@value #MAX_RESIZE_DIMENSIONS}, color is outside the range
   * [0, 0xffffffff], {@code composites} contains more than
   * {@value #MAX_COMPOSITES_PER_REQUEST} elements or something is wrong with
   * the contents of {@code composites}.
   * @throws ImagesServiceFailureException If there is a problem with the
   * Images Service
   */
  public Image composite(Collection<Composite> composites, int width,
                         int height, long color);

  /**
   * Applies the provided {@link Collection} of {@link Composite}s using a
   * canvas with dimensions determined by {@code width} and {@code height}
   * and background color {@code color}.
   * @param composites Compositing operations to be applied.
   * @param width Width of the canvas in pixels.
   * @param height Height of the canvas in pixels.
   * @param color Background color of the canvas in ARGB format.
   * @param encoding Encoding to be used for the resulting image.
   * @return A new image containing the result of composition.
   * @throws IllegalArgumentException If {@code width} or {@code height} is
   * greater than {@value #MAX_RESIZE_DIMENSIONS}, color is outside the range
   * [0, 0xffffffff], {@code composites} contains more than
   * {@value #MAX_COMPOSITES_PER_REQUEST} elements or something is wrong with
   * the contents of {@code composites}.
   * @throws ImagesServiceFailureException If there is a problem with the
   * Images Service
   */
  public Image composite(Collection<Composite> composites, int width,
                         int height, long color,
                         OutputEncoding encoding);

  /**
   * Applies the provided {@link Collection} of {@link Composite}s using a
   * canvas with dimensions determined by {@code width} and {@code height}
   * and background color {@code color}.
   * @param composites Compositing operations to be applied.
   * @param width Width of the canvas in pixels.
   * @param height Height of the canvas in pixels.
   * @param color Background color of the canvas in ARGB format.
   * @param settings OutputSettings to be used for the resulting image.
   * @return A new image containing the result of composition.
   * @throws IllegalArgumentException If {@code width} or {@code height} is
   * greater than {@value #MAX_RESIZE_DIMENSIONS}, color is outside the range
   * [0, 0xffffffff], {@code composites} contains more than
   * {@value #MAX_COMPOSITES_PER_REQUEST} elements or something is wrong with
   * the contents of {@code composites}.
   * @throws ImagesServiceFailureException If there is a problem with the
   * Images Service
   */
  public Image composite(Collection<Composite> composites, int width,
                         int height, long color,
                         OutputSettings settings);

  /**
   * Calculates the histogram of the image.
   *
   * @param image image for which to calculate a histogram
   * @return An array of 3 arrays of length 256, each containing the image
   *   histogram for one color channel. The channels are ordered RGB from
   *   entry 0 to 3. Each channel ranges from 0 where the color is not
   *   present to 255 where the color is fully bright.
   */
  public int[][] histogram(Image image);

  /**
   * Obtains a URL that can serve the image stored as a blob dynamically.
   * <p>
   * This URL is served by a high-performance dynamic image serving
   * infrastructure that is available globally. The URL returned by this method
   * is always public, but not guessable; private URLs are not currently
   * supported. If you wish to stop serving the URL, delete the underlying blob
   * key. This takes up to 24 hours to take effect.
   *
   * The URL format also allows dynamic resizing and crop with certain
   * restrictions. To get dynamic resizing and cropping simply append options to
   * the end of the url obtained via this call. Here is an example: {@code
   * getServingUrl -> "http://lh3.ggpht.com/SomeCharactersGoesHere"}
   * <p>
   * To get a 32 pixel sized version (aspect-ratio preserved) simply append
   * "=s32" to the url:
   * {@code "http://lh3.ggpht.com/SomeCharactersGoesHere=s32"}
   * <p>
   * To get a 32 pixel cropped version simply append "=s32-c":
   * {@code "http://lh3.ggpht.com/SomeCharactersGoesHere=s32-c"}
   * <p>
   * Valid sizes are any integer in the range [0, 1600] and is available as
   * SERVING_SIZES_LIMIT.
   *
   * @param blobKey blob key of the image to serve by the returned URL.
   *
   * @return a URL that can serve the image dynamically.
   * @throws IllegalArgumentException If blob key is not valid or doesn't contain
   * an image.
   * @throws ImagesServiceFailureException If there is a problem with the Images Service
   *
   * @deprecated Replaced by {@link #getServingUrl(ServingUrlOptions)}.
   */
  @Deprecated
  public String getServingUrl(BlobKey blobKey);

  /**
   * Obtains a URL that can serve the image stored as a blob dynamically.
   * <p>
   * This URL is served by a high-performance dynamic image serving
   * infrastructure that is available globally. The URL returned by this method
   * is always public, but not guessable; private URLs are not currently
   * supported. If you wish to stop serving the URL, delete the underlying blob
   * key. This takes up to 24 hours to take effect.
   *
   * The URL format also allows dynamic resizing and crop with certain
   * restrictions. To get dynamic resizing and cropping simply append options to
   * the end of the url obtained via this call. Here is an example: {@code
   * getServingUrl -> "http://lh3.ggpht.com/SomeCharactersGoesHere"}
   * <p>
   * To get a 32 pixel sized version (aspect-ratio preserved) simply append
   * "=s32" to the url:
   * {@code "http://lh3.ggpht.com/SomeCharactersGoesHere=s32"}
   * <p>
   * To get a 32 pixel cropped version simply append "=s32-c":
   * {@code "http://lh3.ggpht.com/SomeCharactersGoesHere=s32-c"}
   * <p>
   * Valid sizes are any integer in the range [0, 1600] and is available as
   * SERVING_SIZES_LIMIT.
   *
   * @param blobKey blob key of the image to serve by the returned URL.
   * @param secureUrl controls if the url scheme should be https or http.
   *
   * @return a URL that can serve the image dynamically.
   * @throws IllegalArgumentException If blob key is not valid or doesn't contain
   * an image.
   * @throws ImagesServiceFailureException If there is a problem with the Images Service
   *
   * @deprecated Replaced by {@link #getServingUrl(ServingUrlOptions)}.
   */
  @Deprecated
  public String getServingUrl(BlobKey blobKey, boolean secureUrl);

  /**
   * Calculates the serving URL for specific size and crop parameters from
   * generic URL returned by {@link #getServingUrl(BlobKey)}.
   *
   * @param blobKey blob key of the image to serve by the returned URL with
   * specified size and crop.
   * @param imageSize size of the served image in pixels.
   * @param crop controls whether the image should be resized or cropped.
   *
   * @return a URL that can serve the image dynamically.
   * @throws IllegalArgumentException If blob key is not valid or doesn't contain
   * an image or specified size is not supported by the service.
   * @throws ImagesServiceFailureException If there is a problem with the Images Service
   * @see ImagesService#getServingUrl(BlobKey)
   *
   * @deprecated Replaced by {@link #getServingUrl(ServingUrlOptions)}.
   */
  @Deprecated
  public String getServingUrl(BlobKey blobKey, int imageSize, boolean crop);

  /**
   * Calculates the serving URL for specific size and crop parameters from
   * generic URL returned by {@link #getServingUrl(BlobKey)}.
   *
   * @param blobKey blob key of the image to serve by the returned URL with
   * specified size and crop.
   * @param imageSize size of the served image in pixels.
   * @param crop controls whether the image should be resized or cropped.
   * @param secureUrl controls if the url scheme should be https or http.
   *
   * @return a URL that can serve the image dynamically.
   * @throws IllegalArgumentException If blob key is not valid or doesn't contain
   * an image or specified size is not supported by the service.
   * @throws ImagesServiceFailureException If there is a problem with the Images Service
   * @see ImagesService#getServingUrl(BlobKey)
   *
   * @deprecated Replaced by {@link #getServingUrl(ServingUrlOptions)}.
   */
  @Deprecated
  public String getServingUrl(BlobKey blobKey, int imageSize, boolean crop, boolean secureUrl);

  /**
   * Obtains a URL that can dynamically serve the image stored as a blob.
   * <p>
   * This URL is served by a high-performance dynamic image serving
   * infrastructure that is available globally. The URL returned by this method
   * is always public, but not guessable; private URLs are not currently
   * supported. If you wish to stop serving the URL, delete the underlying blob
   * key. This takes up to 24 hours to take effect.
   *
   * The URL format also allows dynamic resizing and crop with certain
   * restrictions. To get dynamic resizing and cropping simply append options to
   * the end of the url obtained via this call. Here is an example: {@code
   * getServingUrl -> "http://lh3.ggpht.com/SomeCharactersGoesHere"}
   * <p>
   * To get a 32 pixel sized version (aspect-ratio preserved) simply append
   * "=s32" to the url:
   * {@code "http://lh3.ggpht.com/SomeCharactersGoesHere=s32"}
   * <p>
   * To get a 32 pixel cropped version simply append "=s32-c":
   * {@code "http://lh3.ggpht.com/SomeCharactersGoesHere=s32-c"}
   * <p>
   * Valid sizes are any integer in the range [0, 1600] (maximum is available as
   * {@link #SERVING_SIZES_LIMIT}).
   *
   * @param options Specific options for generating the serving URL.
   *
   * @return a URL that can serve the image dynamically.
   * @throws IllegalArgumentException If options does not contain a valid blobKey or
   * googleStorageFileName.
   * @throws ImagesServiceFailureException If there is a problem with the Images Service
   */
  public String getServingUrl(ServingUrlOptions options);

  /**
   * Deletes a URL that was previously generated by {@code getServingUrl(BlobKey)}.
   *
   * @param blobKey blob key that was previously used in the call to create the
   * serving URL.
   *
   * @throws IllegalArgumentException If blob key is not valid.
   */
  public void deleteServingUrl(BlobKey blobKey);
}
