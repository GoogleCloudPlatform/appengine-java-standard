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
 * {@code InputSettings} represents the different settings to specify how
 * an {@link Image} is interpreted by a transform.
 *
 */
public class InputSettings {
  /**
   * Actions to take with respect to correcting image orientation based
   * on image metadata. EXIF metadata within the image may contain a parameter
   * indicating its proper orientation. This value can equal 1 through 8,
   * inclusive. "1" means that the image is in its "normal" orientation, i.e.,
   * it should be viewed as it is stored.
   *
   * <p>Regardless of the {@code OrientationCorrection} value used, the
   * orientation value in a transformed image is always cleared to indicate that
   * no additional corrections of the returned image's orientation is necessary.
   */
  public static enum OrientationCorrection {
    /**
     * Do not apply any orientation correction specified in the EXIF metadata.
     */
    UNCHANGED_ORIENTATION,
    /**
     * Apply any orientation specified in the EXIF metadata to the image during
     * the first transformation.
     *
     * <p>NOTE: If the image is already in portrait orientation, i.e., "taller"
     * than it is "wide" no correction will be made, since it appears that the
     * camera has already corrected it.
     */
    CORRECT_ORIENTATION,
  }

  /**
   * What action should be taken with respect to correcting orientation.
   */
  private OrientationCorrection orientationCorrection;

  /**
   * Create an InputSettings with default orientation correction of
   * {@link OrientationCorrection#UNCHANGED_ORIENTATION}.
   */
  public InputSettings() {
    orientationCorrection = OrientationCorrection.UNCHANGED_ORIENTATION;
  }

  /**
   * Set what action to take with respect to correcting image orientation based
   * on image metadata.
   * @param orientationCorrection what should be done to correct orientation
   */
  public void setOrientationCorrection(OrientationCorrection orientationCorrection) {
    this.orientationCorrection = orientationCorrection;
  }

  /**
   * Get what action to take with respect to correcting image orientation based
   * on image metadata.
   * @return what should be done to correct orientation
   */
  public OrientationCorrection getOrientationCorrection() {
    return orientationCorrection;
  }
}
