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
 * Tests the Rotate transform.
 *
 */
@RunWith(JUnit4.class)
public class RotateTest {

  /**
   * Tests that valid arguments are correctly added to transforms and that the correct exception is
   * thrown for invalid arguments.
   */
  @Test
  public void testRotate() {
    runValidRotate(270);
    runValidRotate(12345 * 90);
    runValidRotate(-810);
    runValidRotate(90);
    runInvalidRotate(1);
    runInvalidRotate(12345);
  }

  /**
   * Checks that a valid rotation value is correctly added to a transform request - in the range
   * [0,360).
   *
   * @param degrees Degrees to rotate in this test.
   */
  void runValidRotate(int degrees) {
    Transform transform = new Rotate(degrees);
    ImagesServicePb.ImagesTransformRequest.Builder request =
        ImagesServicePb.ImagesTransformRequest.newBuilder();
    transform.apply(request);
    assertThat(request.getTransformCount()).isEqualTo(1);
    assertThat(request.getTransform(0).hasRotate()).isTrue();
    // First mod to get in range (-360,360), add and mod again to get in range [0,360).
    assertThat(request.getTransform(0).getRotate()).isEqualTo((((degrees % 360) + 360) % 360));
  }

  /**
   * Tests that invalid rotate requests throw the correct exception.
   *
   * @param degrees Degrees to rotate in this test.
   */
  void runInvalidRotate(int degrees) {
    assertThrows(IllegalArgumentException.class, () -> new Rotate(degrees));
  }
}
