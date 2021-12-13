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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests the Crop transform.
 *
 */
@RunWith(JUnit4.class)
public class CropTest {

  /**
   * Tests that crop correctly adds its arguments to a transform request for valid requests and
   * throws the right exception for invalid arguments.
   *
   * @throws Exception
   */
  @Test
  public void testCrop() throws Exception {
    runValidCrop(0.0f, 0.0f, 1.0f, 1.0f);
    runValidCrop(0.2f, 0.3f, 0.3f, 0.31f);

    runInvalidCrop(-1.0f, 0.0f, 1.0f, 1.0f);
    runInvalidCrop(0.0f, -1.0f, 1.0f, 1.0f);
    runInvalidCrop(0.0f, 0.0f, 1.1f, 1.0f);
    runInvalidCrop(0.0f, 0.0f, 1.0f, 1.1f);
    runInvalidCrop(0.0f, 0.0f, 0.0f, 1.0f);
    runInvalidCrop(0.0f, 0.0f, 1.0f, 0.0f);
    runInvalidCrop(1.0f, 0.0f, 1.0f, 1.0f);
    runInvalidCrop(0.0f, 1.0f, 1.0f, 1.0f);
    runInvalidCrop(0.0f, 0.5f, 1.0f, 0.4f);
  }

  /**
   * Runs a crop test with valid arguments and tests that they are added to the request correctly.
   *
   * @param leftX X coordinate of the top left corner
   * @param topY Y coordinate of the top left corner
   * @param rightX X coordinate of the bottom right corner
   * @param bottomY Y coordinate of the bottom right corner
   */
  void runValidCrop(float leftX, float topY, float rightX, float bottomY) {
    Transform transform = new Crop(leftX, topY, rightX, bottomY);
    ImagesServicePb.ImagesTransformRequest.Builder request =
        ImagesServicePb.ImagesTransformRequest.newBuilder();
    transform.apply(request);
    assertThat(request.getTransformCount()).isEqualTo(1);
    assertThat(request.getTransform(0).hasCropLeftX()).isTrue();
    assertThat(request.getTransform(0).hasCropTopY()).isTrue();
    assertThat(request.getTransform(0).hasCropRightX()).isTrue();
    assertThat(request.getTransform(0).hasCropBottomY()).isTrue();
    assertThat(request.getTransform(0).getCropLeftX()).isEqualTo(leftX);
    assertThat(request.getTransform(0).getCropTopY()).isEqualTo(topY);
    assertThat(request.getTransform(0).getCropRightX()).isEqualTo(rightX);
    assertThat(request.getTransform(0).getCropBottomY()).isEqualTo(bottomY);
  }
  /**
   * Runs a crop test with invalid arguments and tests the right exception is thrown.
   *
   * @param leftX X coordinate of the top left corner
   * @param topY Y coordinate of the top left corner
   * @param rightX X coordinate of the bottom right corner
   * @param bottomY Y coordinate of the bottom right corner
   */
  void runInvalidCrop(float leftX, float topY, float rightX, float bottomY) {
    assertThrows(IllegalArgumentException.class, () -> new Crop(leftX, topY, rightX, bottomY));
  }
}
