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

import com.google.appengine.api.testing.SerializationTestBase;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.io.Serializable;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 */
@RunWith(JUnit4.class)
public class SerializationTest extends SerializationTestBase {

  private static class DummyTransform extends Transform implements HasOverriddenClass {
    private static final long serialVersionUID = -746750563525312936L;

    @Override
    void apply(ImagesServicePb.ImagesTransformRequest.Builder request) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Class<? extends Serializable> getOverriddenClass() {
      return Transform.class;
    }
  }

  @Override
  protected Iterable<Serializable> getCanonicalObjects() {
    return ImmutableList.of(
        Composite.Anchor.BOTTOM_CENTER,
        new CompositeTransform(Lists.<Transform>newArrayList(new HorizontalFlip())),
        new Crop(0.2f, 0.3f, 0.4f, 0.5f),
        new HorizontalFlip(),
        new ImFeelingLucky(),
        Image.Format.GIF,
        new ImageImpl("blarblarblar".getBytes()),
        ImagesService.OutputEncoding.JPEG,
        new ImagesServiceFailureException("boom"),
        new Resize(33, 34, false, 0.65f, 0.1f),
        new Rotate(90),
        new VerticalFlip(),
        new DummyTransform(),
        InputSettings.OrientationCorrection.UNCHANGED_ORIENTATION
        );
  }

  @Override
  protected Class<?> getClassInApiJar() {
    return ImagesService.class;
  }

  /**
   * Instructions for generating new golden files are in the BUILD file in this
   * directory.
   */
  public static void main(String[] args) throws IOException {
    SerializationTest st = new SerializationTest();
    st.writeCanonicalObjects();
  }
}
