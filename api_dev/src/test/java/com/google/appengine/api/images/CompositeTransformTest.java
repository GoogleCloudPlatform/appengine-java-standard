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

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.InOrder;

/**
 * Tests the Composite Transform.
 *
 */
@RunWith(JUnit4.class)
public class CompositeTransformTest {

  /**
   * Tests that the transform correctly applies transforms passed into its constructor.
   */
  @Test
  public void testConstructor() throws Exception {
    Transform transform1 = mock(Transform.class);
    Transform transform2 = mock(Transform.class);
    Transform transform3 = mock(Transform.class);
    ImagesServicePb.ImagesTransformRequest.Builder request =
        ImagesServicePb.ImagesTransformRequest.newBuilder();
    ImmutableList<Transform> transformList = ImmutableList.of(transform1, transform2, transform3);
    CompositeTransform transforms = new CompositeTransform(transformList);
    transforms.apply(request);
    InOrder inOrder = inOrder(transform1, transform2, transform3);
    inOrder.verify(transform1).apply(request);
    inOrder.verify(transform2).apply(request);
    inOrder.verify(transform3).apply(request);
  }

  /**
   * Tests that concatenating transforms works correctly.
   */
  @Test
  public void testConcatenate() throws Exception {
    Transform transform1 = mock(Transform.class);
    Transform transform2 = mock(Transform.class);
    Transform transform3 = mock(Transform.class);
    ImagesServicePb.ImagesTransformRequest.Builder request =
        ImagesServicePb.ImagesTransformRequest.newBuilder();
    CompositeTransform transforms = new CompositeTransform();
    transforms.concatenate(transform1).concatenate(transform2);
    transforms.concatenate(transform3);
    transforms.apply(request);
    InOrder inOrder = inOrder(transform1, transform2, transform3);
    inOrder.verify(transform1).apply(request);
    inOrder.verify(transform2).apply(request);
    inOrder.verify(transform3).apply(request);
  }

  /**
   * Tests that preConcatenating transforms works correctly.
   */
  @Test
  public void testPreConcatenate() throws Exception {
    Transform transform1 = mock(Transform.class);
    Transform transform2 = mock(Transform.class);
    Transform transform3 = mock(Transform.class);
    ImagesServicePb.ImagesTransformRequest.Builder request =
        ImagesServicePb.ImagesTransformRequest.newBuilder();
    CompositeTransform transforms = new CompositeTransform();
    transforms.preConcatenate(transform1).preConcatenate(transform2);
    transforms.preConcatenate(transform3);
    transforms.apply(request);
    InOrder inOrder = inOrder(transform1, transform2, transform3);
    inOrder.verify(transform3).apply(request);
    inOrder.verify(transform2).apply(request);
    inOrder.verify(transform1).apply(request);
  }

  /**
   * Tests that using concatenate and preConcatenate work correctly in conjunction.
   */
  @Test
  public void testConcatenateAndPreConcatenate() throws Exception {
    Transform transform1 = mock(Transform.class);
    Transform transform2 = mock(Transform.class);
    Transform transform3 = mock(Transform.class);
    ImagesServicePb.ImagesTransformRequest.Builder request =
        ImagesServicePb.ImagesTransformRequest.newBuilder();
    CompositeTransform transforms = new CompositeTransform();
    transforms.concatenate(transform1).preConcatenate(transform2);
    transforms.concatenate(transform3);
    transforms.apply(request);
    InOrder inOrder = inOrder(transform1, transform2, transform3);
    inOrder.verify(transform2).apply(request);
    inOrder.verify(transform1).apply(request);
    inOrder.verify(transform3).apply(request);
  }
}
