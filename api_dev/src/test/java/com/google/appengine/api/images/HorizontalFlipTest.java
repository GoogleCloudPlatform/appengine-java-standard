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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests the HorizontalFlip transform.
 *
 */
@RunWith(JUnit4.class)
public class HorizontalFlipTest {

  /**
   * Tests that a horizontal flip transform is correctly added to a request.
   *
   * @throws Exception
   */
  @Test
  public void testHorizontalFlip() throws Exception {
    Transform transform = new HorizontalFlip();
    ImagesServicePb.ImagesTransformRequest.Builder request =
      ImagesServicePb.ImagesTransformRequest.newBuilder();
    transform.apply(request);
    assertThat(request.getTransformCount()).isEqualTo(1);
    assertThat(request.getTransform(0).hasHorizontalFlip()).isTrue();
    assertThat(request.getTransform(0).getHorizontalFlip()).isTrue();
  }
}
