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
 * Tests the Resize transform.
 *
 */
@RunWith(JUnit4.class)
public class ResizeTest {

  /**
   * Tests that valid resize arguments are correctly added to the request and exception is thrown if
   * invalid arguments are supplied.
   */
  @Test
  public void testResize() {
    runValidResize(1, 2, false, 0.5f, 0.5f);
    runValidResize(1, 0, false, 0.5f, 0.5f);
    runValidResize(0, 2, false, 0.5f, 0.5f);
    runValidResize(123, 456, false, 0.5f, 0.5f);
    runValidResize(4000, 4000, false, 0.5f, 0.5f);
    runValidResize(1, 2, true, 0.5f, 0.5f);
    runValidResize(123, 456, true, 0.5f, 0.5f);
    runValidResize(4000, 4000, true, 0.5f, 0.5f);
    runValidResize(10, 10, true);
    runValidResize(4000, 4000, true, 0.5f, 0.5f, true);

    runInvalidResize(0, 0, false, 0.5f, 0.5f);
    runInvalidResize(4001, 123, false, 0.5f, 0.5f);
    runInvalidResize(45, 4002, false, 0.5f, 0.5f);
    runInvalidResize(0, 0, true, 0.5f, 0.5f);
    runInvalidResize(1, 0, true, 0.5f, 0.5f);
    runInvalidResize(0, 2, true, 0.5f, 0.5f);
    runInvalidResize(123, 456, true, 1.5f, 0.5f);
    runInvalidResize(123, 456, true, 1.5f, 1.5f);
    runInvalidResize(123, 456, true, 0.5f, 1.5f);
    runInvalidResize(123, 456, true, -0.5f, 0.5f);
    runInvalidResize(123, 456, true, -0.5f, -0.5f);
    runInvalidResize(123, 456, true, 0.5f, -0.5f);
    runInvalidResize(0, 0, true);
    runInvalidResize(0, 100, true);
    runInvalidResize(100, 0, true);
  }

  /**
   * Runs a resize test with valid arguments and checks that they are correctly added to the
   * request.
   *
   * @param width width of the resize
   * @param height height of the resize
   * @param allowStretch allowStretch of the resize
   */
  void runValidResize(int width, int height, boolean allowStretch) {
    runValidResize(width, height, false, 0, 0, allowStretch);
  }

  /**
   * Runs a resize test with valid arguments and checks that they are correctly added to the
   * request.
   *
   * @param width width of the resize
   * @param height height of the resize
   * @param cropToFit cropToFit of the resize
   * @param cropOffsetX cropOffsetX of the resize
   * @param cropOffsetY cropOffsetY of the resize
   */
  void runValidResize(
      int width, int height, boolean cropToFit, float cropOffsetX, float cropOffsetY) {
    runValidResize(width, height, cropToFit, cropOffsetX, cropOffsetY, false);
  }

  /**
   * Runs a resize test with valid arguments and checks that they are correctly added to the
   * request.
   *
   * @param width width of the resize
   * @param height height of the resize
   * @param cropToFit cropToFit of the resize
   * @param cropOffsetX cropOffsetX of the resize
   * @param cropOffsetY cropOffsetY of the resize
   * @param allowStretch allowStretch of the resize
   */
  void runValidResize(
      int width,
      int height,
      boolean cropToFit,
      float cropOffsetX,
      float cropOffsetY,
      boolean allowStretch) {
    Transform transform =
        new Resize(width, height, cropToFit, cropOffsetX, cropOffsetY, allowStretch);
    ImagesServicePb.ImagesTransformRequest.Builder request =
        ImagesServicePb.ImagesTransformRequest.newBuilder();
    transform.apply(request);
    assertThat(request.getTransformCount()).isEqualTo(1);
    assertThat(request.getTransform(0).hasWidth()).isTrue();
    assertThat(request.getTransform(0).hasHeight()).isTrue();
    assertThat(request.getTransform(0).hasCropToFit()).isTrue();
    assertThat(request.getTransform(0).hasCropOffsetX()).isTrue();
    assertThat(request.getTransform(0).hasCropOffsetY()).isTrue();
    assertThat(request.getTransform(0).hasAllowStretch()).isTrue();
    assertThat(request.getTransform(0).getWidth()).isEqualTo(width);
    assertThat(request.getTransform(0).getHeight()).isEqualTo(height);
    assertThat(request.getTransform(0).getCropToFit()).isEqualTo(cropToFit);
    assertThat(request.getTransform(0).getCropOffsetX()).isEqualTo(cropOffsetX);
    assertThat(request.getTransform(0).getCropOffsetY()).isEqualTo(cropOffsetY);
    assertThat(request.getTransform(0).getAllowStretch()).isEqualTo(allowStretch);
  }

  /**
   * Runs a resize test with invalid arguments and checks for the right exception being thrown.
   *
   * @param width width of the resize
   * @param height height of the resize
   * @param allowStretch allowStrech of the resize
   */
  void runInvalidResize(int width, int height, boolean allowStretch) {
    runInvalidResize(width, height, false, 0, 0, allowStretch);
  }

  /**
   * Runs a resize test with invalid arguments and checks for the right exception being thrown.
   *
   * @param width width of the resize
   * @param height height of the resize
   * @param cropToFit cropToFit of the resize
   * @param cropOffsetX cropOffsetX of the resize
   * @param cropOffsetY cropOffsetY of the resize
   */
  void runInvalidResize(
      int width, int height, boolean cropToFit, float cropOffsetX, float cropOffsetY) {
    runInvalidResize(width, height, cropToFit, cropOffsetX, cropOffsetY, false);
  }

  /**
   * Runs a resize test with invalid arguments and checks for the right exception being thrown.
   *
   * @param width width of the resize
   * @param height height of the resize
   * @param cropToFit cropToFit of the resize
   * @param cropOffsetX cropOffsetX of the resize
   * @param cropOffsetY cropOffsetY of the resize
   * @param allowStretch allowStrech of the resize
   */
  void runInvalidResize(
      int width,
      int height,
      boolean cropToFit,
      float cropOffsetX,
      float cropOffsetY,
      boolean allowStretch) {
    assertThrows(
        IllegalArgumentException.class,
        () -> new Resize(width, height, cropToFit, cropOffsetX, cropOffsetY, allowStretch));
  }
}
