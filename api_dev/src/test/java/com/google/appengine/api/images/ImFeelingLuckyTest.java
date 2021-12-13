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
 * Tests the ImFeelingLucky autolevels transform.
 *
 */
@RunWith(JUnit4.class)
public class ImFeelingLuckyTest {

  /**
   * Tests that imFeelingLucky correctly adds an autolevel transform to the request.
   *
   * @throws Exception
   */
  @Test
  public void testImFeelingLucky() throws Exception {
    Transform transform = new ImFeelingLucky();
    ImagesServicePb.ImagesTransformRequest.Builder request =
        ImagesServicePb.ImagesTransformRequest.newBuilder();
    transform.apply(request);
    assertThat(request.getTransformCount()).isEqualTo(1);
    assertThat(request.getTransform(0).hasAutolevels()).isTrue();
    assertThat(request.getTransform(0).getAutolevels()).isTrue();
  }
}
