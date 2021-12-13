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
import java.io.Serializable;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * {@code Image} represents an image that can be manipulated by the
 * {@link ImagesService}.
 *
 */
public interface Image extends Serializable {

  /**
   * Image formats usable by the images api.
   */
  public enum Format {PNG, JPEG, GIF, TIFF, BMP, ICO, WEBP}

  /**
   * Gets the width of the image.
   * @return image width
   * @throws IllegalArgumentException If the {@code imageData} provided is
   *                                  invalid
   */
  public int getWidth();

  /**
   * Gets the height of the image.
   * @return image height
   * @throws IllegalArgumentException If the {@code imageData} provided is
   *                                  invalid
   */
  public int getHeight();

  /**
   * Gets the encoding format of the image.
   * @return image format
   * @throws IllegalArgumentException If the {@code imageData} provided is
   * invalid
   */
  public @Nullable Format getFormat();

  /**
   * Gets the raw imageData of the image.
   * @return the image data of the image
   */
  public byte @Nullable [] getImageData();

  /**
   * Sets the image to contain the image data contained in {@code imageData}.
   * @param imageData new image data for the image to store
   * @throws IllegalArgumentException If {@code imageData} is null or empty
   */
  public void setImageData(byte[] imageData);

  /**
   * If this image is backed by a blob, return the associated {@link
   * BlobKey}.  If this method returns non-{@code null}, none of the
   * other methods will currently be available.
   */
  public @Nullable BlobKey getBlobKey();
}
