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

import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests the CompositeImpl class.
 *
 */
@RunWith(JUnit4.class)
public class CompositeImplTest {

  private Map<Image, Integer> imageIndexMap;
  private ImagesServicePb.ImagesCompositeRequest.Builder request;
  private static final byte[] imageData = "an image".getBytes();
  private static final byte[] otherImageData = "a different image".getBytes();

  @Before
  public void setUp() throws Exception {
    imageIndexMap = new HashMap<>();
    request = ImagesServicePb.ImagesCompositeRequest.newBuilder();
    request.setCanvas(
        ImagesServicePb.ImagesCanvas.newBuilder()
            .setWidth(640)
            .setHeight(480)
            .setOutput(ImagesServicePb.OutputSettings.getDefaultInstance()));
  }

  /** Tests a simple composite. */
  @Test
  public void testApply_singleImage() throws Exception {
    Image image = new ImageImpl(imageData);
    int xOffset = 20;
    int yOffset = 30;
    float opacity = 0.8f;
    Composite.Anchor anchor = Composite.Anchor.CENTER_LEFT;
    Composite composite = new CompositeImpl(image, xOffset, yOffset, opacity, anchor);
    composite.apply(request, imageIndexMap);
    assertThat(request.getOptionsCount()).isEqualTo(1);
    assertThat(request.getOptions(0).getXOffset()).isEqualTo(xOffset);
    assertThat(request.getOptions(0).getYOffset()).isEqualTo(yOffset);
    assertThat(request.getOptions(0).getOpacity()).isEqualTo(opacity);
    assertThat(request.getOptions(0).getAnchor().getNumber()).isEqualTo(anchor.ordinal());
    assertThat(request.getImageCount()).isEqualTo(1);
    assertThat(request.getImage(0).getContent().toByteArray()).isEqualTo(image.getImageData());
  }

  /** Tests composite with multiple outputs using the same image. */
  @Test
  public void testApply_singleImageMultipleOptions() throws Exception {
    Image image = new ImageImpl(imageData);
    int xOffset = 20;
    int yOffset = 30;
    float opacity = 1.0f;
    Composite.Anchor anchor = Composite.Anchor.CENTER_LEFT;
    int otherXOffset = 42;
    int otherYOffset = 61;
    float otherOpacity = 0.1f;
    Composite.Anchor otherAnchor = Composite.Anchor.BOTTOM_RIGHT;
    Composite composite = new CompositeImpl(image, xOffset, yOffset, opacity, anchor);
    composite.apply(request, imageIndexMap);
    composite = new CompositeImpl(image, otherXOffset, otherYOffset, otherOpacity, otherAnchor);
    composite.apply(request, imageIndexMap);
    assertThat(request.getOptionsCount()).isEqualTo(2);
    assertThat(request.getOptions(0).getXOffset()).isEqualTo(xOffset);
    assertThat(request.getOptions(0).getYOffset()).isEqualTo(yOffset);
    assertThat(request.getOptions(0).getOpacity()).isEqualTo(opacity);
    assertThat(request.getOptions(0).getAnchor().getNumber()).isEqualTo(anchor.ordinal());
    assertThat(request.getOptions(0).getSourceIndex()).isEqualTo(0);
    assertThat(request.getOptions(1).getXOffset()).isEqualTo(otherXOffset);
    assertThat(request.getOptions(1).getYOffset()).isEqualTo(otherYOffset);
    assertThat(request.getOptions(1).getOpacity()).isEqualTo(otherOpacity);
    assertThat(request.getOptions(1).getAnchor().getNumber()).isEqualTo(otherAnchor.ordinal());
    assertThat(request.getOptions(1).getSourceIndex()).isEqualTo(0);
    assertThat(request.getImageCount()).isEqualTo(1);
    assertThat(request.getImage(0).getContent().toByteArray()).isEqualTo(image.getImageData());
    assertThat(imageIndexMap).hasSize(1);
    assertThat(imageIndexMap).containsKey(image);
    assertThat(imageIndexMap.get(image).intValue()).isEqualTo(0);
  }

  /** Tests composite with multiple outputs using an identical image. */
  @Test
  public void testApply_identicalImagesMultipleOptions() throws Exception {
    Image image = new ImageImpl(imageData);
    int xOffset = 20;
    int yOffset = 30;
    float opacity = 0.2f;
    Composite.Anchor anchor = Composite.Anchor.CENTER_LEFT;
    Image otherImage = new ImageImpl(imageData);
    int otherXOffset = 42;
    int otherYOffset = 61;
    float otherOpacity = 0.3f;
    Composite.Anchor otherAnchor = Composite.Anchor.BOTTOM_RIGHT;
    Composite composite = new CompositeImpl(image, xOffset, yOffset, opacity, anchor);
    composite.apply(request, imageIndexMap);
    composite =
        new CompositeImpl(otherImage, otherXOffset, otherYOffset, otherOpacity, otherAnchor);
    composite.apply(request, imageIndexMap);
    assertThat(request.getOptionsCount()).isEqualTo(2);
    assertThat(request.getOptions(0).getXOffset()).isEqualTo(xOffset);
    assertThat(request.getOptions(0).getYOffset()).isEqualTo(yOffset);
    assertThat(request.getOptions(0).getOpacity()).isEqualTo(opacity);
    assertThat(request.getOptions(0).getAnchor().getNumber()).isEqualTo(anchor.ordinal());
    assertThat(request.getOptions(0).getSourceIndex()).isEqualTo(0);
    assertThat(request.getOptions(1).getXOffset()).isEqualTo(otherXOffset);
    assertThat(request.getOptions(1).getYOffset()).isEqualTo(otherYOffset);
    assertThat(request.getOptions(1).getOpacity()).isEqualTo(otherOpacity);
    assertThat(request.getOptions(1).getAnchor().getNumber()).isEqualTo(otherAnchor.ordinal());
    assertThat(request.getOptions(1).getSourceIndex()).isEqualTo(0);
    assertThat(request.getImageCount()).isEqualTo(1);
    assertThat(request.getImage(0).getContent().toByteArray()).isEqualTo(imageData);
    assertThat(imageIndexMap).hasSize(1);
    assertThat(imageIndexMap).containsKey(image);
    assertThat(imageIndexMap.get(image).intValue()).isEqualTo(0);
  }

  /** Tests composite with multiple input images and one output per input image. */
  @Test
  public void testApply_multipleImagesSingleOptions() throws Exception {
    Image image = new ImageImpl(imageData);
    int xOffset = 20;
    int yOffset = 30;
    float opacity = 0.99999f;
    Composite.Anchor anchor = Composite.Anchor.CENTER_LEFT;
    Image otherImage = new ImageImpl(otherImageData);
    int otherXOffset = 42;
    int otherYOffset = 61;
    float otherOpacity = 0.1111f;
    Composite.Anchor otherAnchor = Composite.Anchor.BOTTOM_RIGHT;
    Composite composite = new CompositeImpl(image, xOffset, yOffset, opacity, anchor);
    composite.apply(request, imageIndexMap);
    composite =
        new CompositeImpl(otherImage, otherXOffset, otherYOffset, otherOpacity, otherAnchor);
    composite.apply(request, imageIndexMap);
    assertThat(request.getOptionsCount()).isEqualTo(2);
    assertThat(request.getOptions(0).getXOffset()).isEqualTo(xOffset);
    assertThat(request.getOptions(0).getYOffset()).isEqualTo(yOffset);
    assertThat(request.getOptions(0).getOpacity()).isEqualTo(opacity);
    assertThat(request.getOptions(0).getAnchor().getNumber()).isEqualTo(anchor.ordinal());
    assertThat(request.getOptions(0).getSourceIndex()).isEqualTo(0);
    assertThat(request.getOptions(1).getXOffset()).isEqualTo(otherXOffset);
    assertThat(request.getOptions(1).getYOffset()).isEqualTo(otherYOffset);
    assertThat(request.getOptions(1).getOpacity()).isEqualTo(otherOpacity);
    assertThat(request.getOptions(1).getAnchor().getNumber()).isEqualTo(otherAnchor.ordinal());
    assertThat(request.getOptions(1).getSourceIndex()).isEqualTo(1);
    assertThat(request.getImageCount()).isEqualTo(2);
    assertThat(request.getImage(0).getContent().toByteArray()).isEqualTo(imageData);
    assertThat(request.getImage(1).getContent().toByteArray()).isEqualTo(otherImageData);
    assertThat(imageIndexMap).hasSize(2);
    assertThat(imageIndexMap).containsKey(image);
    assertThat(imageIndexMap.get(image).intValue()).isEqualTo(0);
    assertThat(imageIndexMap.get(otherImage).intValue()).isEqualTo(1);
  }

  /** Tests composite with multiple input images and multiple outputs. */
  @Test
  public void testApply_multipleImagesLotsOfOptions() throws Exception {
    int xOffset = 20;
    int yOffset = 30;
    float opacity = 0.1234f;
    Composite.Anchor anchor = Composite.Anchor.CENTER_LEFT;
    int otherXOffset = 42;
    int otherYOffset = 61;
    float otherOpacity = 0.5678f;
    Composite.Anchor otherAnchor = Composite.Anchor.BOTTOM_RIGHT;
    int yetAnotherXOffset = 420;
    int yetAnotherYOffset = -30;
    float yetAnotherOpacity = 0.9012f;
    Composite.Anchor yetAnotherAnchor = Composite.Anchor.CENTER_CENTER;
    int xOffset3 = 0;
    int yOffset3 = 0;
    float opacity3 = 0.1f;
    Composite.Anchor anchor3 = Composite.Anchor.TOP_LEFT;
    int xOffset4 = -1000;
    int yOffset4 = 100;
    float opacity4 = 0.9f;
    Composite.Anchor anchor4 = Composite.Anchor.TOP_CENTER;
    new CompositeImpl(new ImageImpl(imageData), xOffset, yOffset, opacity, anchor)
        .apply(request, imageIndexMap);
    new CompositeImpl(
            new ImageImpl(otherImageData), otherXOffset, otherYOffset, otherOpacity, otherAnchor)
        .apply(request, imageIndexMap);
    new CompositeImpl(
            new ImageImpl(imageData),
            yetAnotherXOffset,
            yetAnotherYOffset,
            yetAnotherOpacity,
            yetAnotherAnchor)
        .apply(request, imageIndexMap);
    new CompositeImpl(new ImageImpl(otherImageData), xOffset3, yOffset3, opacity3, anchor3)
        .apply(request, imageIndexMap);
    new CompositeImpl(new ImageImpl(otherImageData), xOffset4, yOffset4, opacity4, anchor4)
        .apply(request, imageIndexMap);
    assertThat(request.getOptionsCount()).isEqualTo(5);
    assertThat(request.getOptions(0).getXOffset()).isEqualTo(xOffset);
    assertThat(request.getOptions(0).getYOffset()).isEqualTo(yOffset);
    assertThat(request.getOptions(0).getOpacity()).isEqualTo(opacity);
    assertThat(request.getOptions(0).getAnchor().getNumber()).isEqualTo(anchor.ordinal());
    assertThat(request.getOptions(0).getSourceIndex()).isEqualTo(0);
    assertThat(request.getOptions(1).getXOffset()).isEqualTo(otherXOffset);
    assertThat(request.getOptions(1).getYOffset()).isEqualTo(otherYOffset);
    assertThat(request.getOptions(1).getOpacity()).isEqualTo(otherOpacity);
    assertThat(request.getOptions(1).getAnchor().getNumber()).isEqualTo(otherAnchor.ordinal());
    assertThat(request.getOptions(1).getSourceIndex()).isEqualTo(1);
    assertThat(request.getOptions(2).getXOffset()).isEqualTo(yetAnotherXOffset);
    assertThat(request.getOptions(2).getYOffset()).isEqualTo(yetAnotherYOffset);
    assertThat(request.getOptions(2).getOpacity()).isEqualTo(yetAnotherOpacity);
    assertThat(request.getOptions(2).getAnchor().getNumber()).isEqualTo(yetAnotherAnchor.ordinal());
    assertThat(request.getOptions(2).getSourceIndex()).isEqualTo(0);
    assertThat(request.getOptions(3).getXOffset()).isEqualTo(xOffset3);
    assertThat(request.getOptions(3).getYOffset()).isEqualTo(yOffset3);
    assertThat(request.getOptions(3).getOpacity()).isEqualTo(opacity3);
    assertThat(request.getOptions(3).getAnchor().getNumber()).isEqualTo(anchor3.ordinal());
    assertThat(request.getOptions(3).getSourceIndex()).isEqualTo(1);
    assertThat(request.getOptions(4).getXOffset()).isEqualTo(xOffset4);
    assertThat(request.getOptions(4).getYOffset()).isEqualTo(yOffset4);
    assertThat(request.getOptions(4).getOpacity()).isEqualTo(opacity4);
    assertThat(request.getOptions(4).getAnchor().getNumber()).isEqualTo(anchor4.ordinal());
    assertThat(request.getOptions(4).getSourceIndex()).isEqualTo(1);
    assertThat(request.getImageCount()).isEqualTo(2);
    assertThat(request.getImage(0).getContent().toByteArray()).isEqualTo(imageData);
    assertThat(request.getImage(1).getContent().toByteArray()).isEqualTo(otherImageData);
    assertThat(imageIndexMap).hasSize(2);
    assertThat(imageIndexMap).containsKey(new ImageImpl(imageData));
    assertThat(imageIndexMap).containsKey(new ImageImpl(otherImageData));
    assertThat(imageIndexMap.get(new ImageImpl(imageData)).intValue()).isEqualTo(0);
    assertThat(imageIndexMap.get(new ImageImpl(otherImageData)).intValue()).isEqualTo(1);
  }

  /**
   * Tests that an x offset that definitely place the image outside the canvas throw the correct
   * exception.
   */
  @Test
  public void testConstructor_invalidXOffset() throws Exception {
    Image image = new ImageImpl(imageData);
    int xOffset = 5000;
    int yOffset = 30;
    float opacity = 0.8f;
    Composite.Anchor anchor = Composite.Anchor.CENTER_LEFT;
    assertThrows(
        IllegalArgumentException.class,
        () -> new CompositeImpl(image, xOffset, yOffset, opacity, anchor));
  }

  /**
   * Tests that a y offset that definitely place the image outside the canvas throw the correct
   * exception.
   */
  @Test
  public void testConstructor_invalidYOffset() throws Exception {
    Image image = new ImageImpl(imageData);
    int xOffset = 0;
    int yOffset = 4001;
    float opacity = 0.8f;
    Composite.Anchor anchor = Composite.Anchor.CENTER_LEFT;
    assertThrows(
        IllegalArgumentException.class,
        () -> new CompositeImpl(image, xOffset, yOffset, opacity, anchor));
  }

  /** Tests that invalid opacity throws the correct exception. */
  @Test
  public void testConstructor_invalidOpacity() throws Exception {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CompositeImpl(new ImageImpl(imageData), 100, 200, 1.2f, Composite.Anchor.TOP_LEFT));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CompositeImpl(
                new ImageImpl(imageData), 100, 200, -0.2f, Composite.Anchor.TOP_LEFT));
  }

  /** Tests that a null anchor throws the correct exception. */
  @Test
  public void testConstructor_nullAnchor() throws Exception {
    assertThrows(
        IllegalArgumentException.class,
        () -> new CompositeImpl(new ImageImpl(imageData), 100, 200, 0.2f, null));
  }

  /**
   * Tests that the mapping of Anchor to CompositeImageOptions.ANCHOR preserves ordinal numbers.
   * (This isn't strictly specified but happens to be true; this is a sanity check for typos in the
   * enum translation.)
   */
  @Test
  public void testConvertAnchor() {
    for (Composite.Anchor anchor : Composite.Anchor.values()) {
      assertThat(CompositeImpl.convertAnchor(anchor).getNumber()).isEqualTo(anchor.ordinal());
    }
  }
}
