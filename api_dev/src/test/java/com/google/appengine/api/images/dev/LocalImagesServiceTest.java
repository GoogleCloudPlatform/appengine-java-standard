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

package com.google.appengine.api.images.dev;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;

import com.google.appengine.api.blobstore.BlobKey;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.images.ImagesService;
import com.google.appengine.api.images.ImagesServicePb.CompositeImageOptions;
import com.google.appengine.api.images.ImagesServicePb.CompositeImageOptions.ANCHOR;
import com.google.appengine.api.images.ImagesServicePb.ImageData;
import com.google.appengine.api.images.ImagesServicePb.ImagesCanvas;
import com.google.appengine.api.images.ImagesServicePb.ImagesCompositeRequest;
import com.google.appengine.api.images.ImagesServicePb.ImagesCompositeResponse;
import com.google.appengine.api.images.ImagesServicePb.ImagesGetUrlBaseRequest;
import com.google.appengine.api.images.ImagesServicePb.ImagesGetUrlBaseResponse;
import com.google.appengine.api.images.ImagesServicePb.ImagesHistogramRequest;
import com.google.appengine.api.images.ImagesServicePb.ImagesHistogramResponse;
import com.google.appengine.api.images.ImagesServicePb.ImagesServiceError.ErrorCode;
import com.google.appengine.api.images.ImagesServicePb.ImagesTransformRequest;
import com.google.appengine.api.images.ImagesServicePb.ImagesTransformResponse;
import com.google.appengine.api.images.ImagesServicePb.InputSettings;
import com.google.appengine.api.images.ImagesServicePb.InputSettings.ORIENTATION_CORRECTION_TYPE;
import com.google.appengine.api.images.ImagesServicePb.OutputSettings;
import com.google.appengine.api.images.ImagesServicePb.OutputSettings.MIME_TYPE;
import com.google.appengine.api.images.ImagesServicePb.Transform;
import com.google.appengine.tools.development.LocalRpcService;
import com.google.appengine.tools.development.testing.LocalBlobstoreServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalDatastoreServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalImagesServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import com.google.apphosting.api.ApiProxy;
import com.google.common.base.StandardSystemProperty;
import com.google.common.io.Resources;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for the LocalImagesService Implementation.
 *
 */
@RunWith(JUnit4.class)
public class LocalImagesServiceTest {
  private final LocalServiceTestHelper helper =
      new LocalServiceTestHelper(
          new LocalImagesServiceTestConfig(),
          new LocalBlobstoreServiceTestConfig(),
          new LocalDatastoreServiceTestConfig());

  private LocalImagesService service;
  private LocalRpcService.Status status;
  private ImagesTransformRequest.Builder request;
  private ImagesCompositeRequest.Builder compositeRequest;
  byte[] validImage;
  private static final int EXIF_ORIENTATION_LOCATION = 0x60;

  @Before
  public void setUp() throws Exception {
    helper.setUp();
    service = LocalImagesServiceTestConfig.getLocalImagesService();
    validImage = readImage("before.png");
    request = ImagesTransformRequest.newBuilder()
        .setOutput(OutputSettings.newBuilder().setMimeType(MIME_TYPE.PNG))
        .setImage(imageData(validImage));
    status = new LocalRpcService.Status();
    compositeRequest =
        ImagesCompositeRequest.newBuilder()
            .setCanvas(
                ImagesCanvas.newBuilder()
                    .setOutput(OutputSettings.getDefaultInstance())
                    .setWidth(640)
                    .setHeight(480)
                    .setColor(0xee446688))
            .addImage(imageData(validImage))
            .addOptions(
                CompositeImageOptions.newBuilder()
                    .setAnchor(CompositeImageOptions.ANCHOR.TOP_LEFT)
                    .setXOffset(30)
                    .setYOffset(40)
                    .setOpacity(1.0f)
                    .setSourceIndex(0));
  }

  @After
  public void tearDown() throws Exception {
    helper.tearDown();
  }

  /**
   * Tests that an invalid image being supplied results in the correct
   * exception being thrown.
   */
  @Test
  public void testTransform_invalidImage() {
    request.setImage(imageData("aaa".getBytes(UTF_8)));
    request.addTransform(Transform.getDefaultInstance());
    ApiProxy.ApplicationException ex =
        assertThrows(
            ApiProxy.ApplicationException.class, () -> service.transform(status, request.build()));
    assertThat(ex.getApplicationError()).isEqualTo(3);
    assertThat(status.isSuccessful()).isFalse();
    assertThat(status.getErrorCode()).isEqualTo(3);
  }

  /**
   * Tests that the correct exception is thrown if and only if there are more
   * than {@code MAX_TRANSFORMS_PER_REQUEST} transforms supplied.
   */
  @Test
  public void testTransform_tooManyTransforms() {
    for (int i = 0; i < ImagesService.MAX_TRANSFORMS_PER_REQUEST + 1; i++) {
      service.transform(status, request.build());
      request = request.clone().addTransform(Transform.newBuilder().setHorizontalFlip(true));
    }
    ApiProxy.ApplicationException ex =
        assertThrows(
            ApiProxy.ApplicationException.class, () -> service.transform(status, request.build()));
    assertThat(ex.getApplicationError()).isEqualTo(2);
    assertThat(status.isSuccessful()).isFalse();
    assertThat(status.getErrorCode()).isEqualTo(2);
  }

  /**
   * Tests that a stretch resize with invalid arguments generates the correct exception.
   */
  @Test
  public void testTransform_invalidStretch() {
    request.addTransform(Transform.newBuilder().setAllowStretch(true));
    ApiProxy.ApplicationException ex =
        assertThrows(
            ApiProxy.ApplicationException.class, () -> service.transform(status, request.build()));
    assertThat(ex.getApplicationError()).isEqualTo(2);
    assertThat(status.isSuccessful()).isFalse();
    assertThat(status.getErrorCode()).isEqualTo(2);
  }

  /**
   * Tests that an invalid transform results in the correct exception being thrown.
   */
  @Test
  public void testTransform_invalidTransform() {
    request.addTransform(Transform.newBuilder().setCropBottomY(-1));
    ApiProxy.ApplicationException ex =
        assertThrows(
            ApiProxy.ApplicationException.class, () -> service.transform(status, request.build()));
    assertThat(ex.getApplicationError()).isEqualTo(2);
    assertThat(status.isSuccessful()).isFalse();
    assertThat(status.getErrorCode()).isEqualTo(2);
  }

  @Test
  public void testTransform_blobNotFound() throws Exception {
    request.setImage(ImageData.newBuilder(request.getImage()).setBlobKey("non-existent"));
    request.addTransform(Transform.newBuilder().setVerticalFlip(true));
    ApiProxy.ApplicationException ex =
        assertThrows(
            ApiProxy.ApplicationException.class, () -> service.transform(status, request.build()));
    assertThat(ex.getApplicationError()).isEqualTo(ErrorCode.INVALID_BLOB_KEY.getNumber());
  }

  /**
   * Tests that a vertical flip transform produces the correct result.
   */
  @Test
  public void testTransform_validVerticalFlip() throws Exception {
    request.addTransform(Transform.newBuilder().setVerticalFlip(true));
    runTransformTest("after-verticalFlip.png");
  }

  /**
   * Tests that a horizontal flip transform produces the correct result.
   */
  @Test
  public void testTransform_validHorizontalFlip() throws Exception {
    request.addTransform(Transform.newBuilder().setHorizontalFlip(true));
    runTransformTest("after-horizontalFlip.png");
  }

  /**
   * Tests that a 90 degree rotate transform produces the correct result.
   */
  @Test
  public void testTransform_validRotate90() throws Exception {
    request.addTransform(Transform.newBuilder().setRotate(90));
    runTransformTest("after-rotate-90.png");
  }

  /**
   * Tests that a 180 degree rotate transform produces the correct result.
   */
  @Test
  public void testTransform_validRotate180() throws Exception {
    request.addTransform(Transform.newBuilder().setRotate(180));
    runTransformTest("after-rotate-180.png");
  }

  /**
   * Tests that a 270 degree rotate transform produces the correct result.
   */
  @Test
  public void testTransform_validRotate270() throws Exception {
    request.addTransform(Transform.newBuilder().setRotate(270));
    runTransformTest("after-rotate-270.png");
  }

  /**
   * Tests that a resize transform produces the correct result.
   */
  @Test
  public void testTransform_validResize() throws Exception {
    request.addTransform(Transform.newBuilder().setWidth(30).setHeight(40));
    runTransformTest("after-resize.png");
  }

  /**
   * Tests that a resize with crop to fit produces the correct result.
   */
  @Test
  public void testTransform_validResizeWithCropToFit() throws Exception {
    request.addTransform(Transform.newBuilder()
                         .setWidth(30)
                         .setHeight(40)
                         .setCropToFit(true)
                         .setCropOffsetX(0.65f)
                         .setCropOffsetY(1.0f));
    runTransformTest("after-resize-crop-to-fit.png");
  }

  /**
   * Tests that a resize with stretch produces the correct result.
   */
  @Test
  public void testTransform_validResizeWithStretch() throws Exception {
    request.addTransform(Transform.newBuilder()
                         .setWidth(300)
                         .setHeight(300)
                         .setAllowStretch(true));
    runTransformTest("after-stretch-300-300.png");
  }

  /**
   * Tests that a resize with stretch produces the correct result.
   */
  @Test
  public void testTransform_validResizeWithStretchAndCrop() throws Exception {
    request.addTransform(Transform.newBuilder()
                         .setCropToFit(true)
                         .setCropOffsetX(0.2f)
                         .setCropOffsetY(0.2f)
                         .setWidth(300)
                         .setHeight(300)
                         .setAllowStretch(true));
    runTransformTest("after-stretch-300-300.png");
  }

  /**
   * Tests that a crop transform produces the correct result.
   */
  @Test
  public void testTransform_validCrop() throws Exception {
    request.addTransform(Transform.newBuilder().setCropLeftX(0.2f).setCropTopY(0.4f).
        setCropRightX(0.8f).setCropBottomY(0.7f));
    runTransformTest("after-crop.png");
  }

  /**
   * Tests that a crop transform produces the correct result in presence of
   * rounding issues.
   */
  @Test
  public void testTransform_validCropRounding() throws Exception {
    // These values were chosen because:
    // width = (1.0 - 0.8) * 145 = 28.999999999999993 -> 29
    // startY = 0.5 * 111 = 55.5 -> 56
    // height = (1.0 - 0.5) = 55.5 -> 56 -> 55 (maximum possible width given the Y offset)
    request.addTransform(Transform.newBuilder().setCropLeftX(0.8f).setCropTopY(0.5f).
        setCropRightX(1.0f).setCropBottomY(1.0f));
    runTransformTest("after-crop-rounding.png");
  }

  /**
   * Tests that a crop transform followed by a resize transform produces the
   * correct result.
   */
  @Test
  public void testTransform_validCropThenResize() throws Exception {
    request.addTransform(Transform.newBuilder().setCropLeftX(0.2f).setCropTopY(0.4f).
        setCropRightX(0.8f).setCropBottomY(0.7f));
    request.addTransform(Transform.newBuilder().setWidth(100).setHeight(100));
    runTransformTest("after-crop-resize.png");
  }

  /**
   * Tests that a resize transform followed by a crop transform produces the
   * correct result.
   */
  @Test
  public void testTransform_validResizeThenCrop() throws Exception {
    request.addTransform(Transform.newBuilder().setWidth(100).setHeight(100));
    request.addTransform(Transform.newBuilder().setCropLeftX(0.2f).setCropTopY(0.4f).
        setCropRightX(0.8f).setCropBottomY(0.7f));
    runTransformTest("after-resize-crop.png");
  }

  @Test
  public void testTransform_blob() throws Exception {
    try (OutputStream out = service.getBlobStorage().storeBlob(new BlobKey("foo"))) {
      out.write(validImage);
    }

    request =
        ImagesTransformRequest.newBuilder()
            .setOutput(OutputSettings.newBuilder().setMimeType(MIME_TYPE.PNG))
            .setImage(ImageData.newBuilder().setContent(ByteString.EMPTY).setBlobKey("foo"))
            .addTransform(Transform.newBuilder().setWidth(30).setHeight(40));
    runTransformTest("after-resize.png");
  }

  /**
   * Tests that a composition works correctly.
   */
  @Test
  public void testComposite_validComposite() throws Exception {
    String[] otherFilenames = {"after-verticalFlip.png", "after-rotate-90.png"};
    for (String name : otherFilenames) {
      compositeRequest.addImage(imageData(readImage(name)));
    }
    System.out.println(compositeRequest.getImageCount());
    compositeRequest
        .setCanvas(
            ImagesCanvas.newBuilder(compositeRequest.getCanvas())
            .setWidth(640)
            .setHeight(480)
            .setColor(0xee446688))
        .addOptions(
            CompositeImageOptions.newBuilder()
            .setAnchor(ANCHOR.TOP_LEFT)
            .setXOffset(300)
            .setYOffset(400)
            .setOpacity(0.2f)
            .setSourceIndex(0))
        .addOptions(
            CompositeImageOptions.newBuilder()
            .setAnchor(ANCHOR.TOP_LEFT)
            .setXOffset(20)
            .setYOffset(90)
            .setOpacity(0.1f)
            .setSourceIndex(1))
        .addOptions(
            CompositeImageOptions.newBuilder()
            .setAnchor(ANCHOR.TOP)
            .setXOffset(0)
            .setYOffset(-10)
            .setOpacity(0.77f)
            .setSourceIndex(1))
        .addOptions(
            CompositeImageOptions.newBuilder()
            .setAnchor(ANCHOR.BOTTOM_RIGHT)
            .setXOffset(0)
            .setYOffset(0)
            .setOpacity(0.5f)
            .setSourceIndex(2))
        .addOptions(
            CompositeImageOptions.newBuilder()
            .setAnchor(ANCHOR.TOP_LEFT)
            .setXOffset(-50)
            .setYOffset(400)
            .setOpacity(0.2f)
            .setSourceIndex(2));
    ImagesCompositeResponse response = service.composite(new LocalRpcService.Status(),
                                                         compositeRequest.build());
    compareImage("after-composite.png", response.getImage().getContent().toByteArray());
  }

  /**
   * Tests that an invalid source image index throws the correct exception.
   */
  @Test
  public void testComposite_invalidSourceIndex() throws Exception {
    compositeRequest.setOptions(0,
        CompositeImageOptions.newBuilder(compositeRequest.getOptions(0))
        .setSourceIndex(1));
    assertThrows(
        ApiProxy.ApplicationException.class,
        () -> service.composite(status, compositeRequest.build()));
  }

  /**
   * Tests that an invalid opacity throws the correct exception.
   */
  @Test
  public void testComposite_invalidOpacity() throws Exception {
    compositeRequest.setOptions(0,
        CompositeImageOptions.newBuilder(compositeRequest.getOptions(0))
        .setOpacity(1.2f));
    assertThrows(
        ApiProxy.ApplicationException.class,
        () -> service.composite(status, compositeRequest.build()));
    compositeRequest = compositeRequest.clone();
    compositeRequest.setOptions(0,
        CompositeImageOptions.newBuilder(compositeRequest.getOptions(0))
        .setOpacity(-0.1f));
    assertThrows(
        ApiProxy.ApplicationException.class,
        () -> service.composite(status, compositeRequest.build()));
  }

  /**
   * Tests that an invalid input image throws the correct exception.
   */
  @Test
  public void testComposite_invalidImage() throws Exception {
    compositeRequest.setImage(0,
        ImageData.newBuilder(compositeRequest.getImage(0))
        .setContent(ByteString.copyFromUtf8("blah")));
    assertThrows(
        ApiProxy.ApplicationException.class,
        () -> service.composite(status, compositeRequest.build()));
  }

  /**
   * Tests that the correct exception is thrown if and only if there are more
   * than {@code MAX_COMPOSITES_PER_REQUEST} composites supplied.
   */
  @Test
  public void testComposite_tooManyComposites() {
    // We already added one option in the setUp method.
    for (int i = 0; i < ImagesService.MAX_COMPOSITES_PER_REQUEST; i++) {
      service.composite(status, compositeRequest.clone().build());
      compositeRequest.addOptions(
          CompositeImageOptions.newBuilder()
          .setAnchor(ANCHOR.TOP_LEFT)
          .setXOffset(300)
          .setYOffset(400)
          .setOpacity(0.2f)
          .setSourceIndex(0));
    }
    ApiProxy.ApplicationException ex =
        assertThrows(
            ApiProxy.ApplicationException.class,
            () -> service.composite(status, compositeRequest.build()));
    assertThat(ex.getApplicationError()).isEqualTo(2);
    assertThat(status.isSuccessful()).isFalse();
    assertThat(status.getErrorCode()).isEqualTo(2);
  }

  /**
   * Runs a transform test and tests that the result is the correct image.
   * @param filename The filename of the expected image.
   */
  private void runTransformTest(String filename) throws Exception {
    ImagesTransformResponse response = service.transform(status, request.build());
    assertThat(status.isSuccessful()).isTrue();
    compareImage(filename, response.getImage().getContent().toByteArray());
  }

  private void compareImage(String filename, byte[] responseImage) throws IOException {
    byte[] expectedImage = readImage(filename);
    assertThat(responseImage).isEqualTo(expectedImage);
  }

  private static boolean isJDK8() {
    return StandardSystemProperty.JAVA_SPECIFICATION_VERSION.value().equals("1.8");
  }

  /**
   * We have 2 test files per image: one for jdk8 and the other one (11) used by jdk 11,17 and above
   */
  private static final String jdk11(String filename) {
    return filename.replaceAll("(?!-jdk11)\\.(png|jpg)$", "-jdk11.$1");
  }


  /**
   * Reads in an image and returns its contents as a byte array.
   * @param filename Name of the file to be opened.
   * @return The file contents as a byte array.
   */
  private byte[] readImage(String filename) throws IOException {
    URL resource = null;
    if (!isJDK8()) {
      String jdk11Name = jdk11(filename);
      resource = getClass().getResource("testdata/" + jdk11Name);
    }
    if (resource == null) {
      resource = getClass().getResource("testdata/" + filename);
    }
    assertWithMessage("Could not find resource for %s", filename).that(resource).isNotNull();
    return Resources.toByteArray(resource);
  }

  /**
   * Tests that histograms work correctly.
   */
  @Test
  public void testHistogram() throws Exception {
    ImagesHistogramRequest histogramRequest = ImagesHistogramRequest.newBuilder()
        .setImage(imageData(validImage))
        .build();
    ImagesHistogramResponse response = service.histogram(status, histogramRequest);
    int[] expectedBlue = {
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 3,
        5, 3, 1, 3, 4, 2, 5, 6, 8, 4, 9, 8, 9, 11, 12, 8, 15, 18, 19, 12, 20, 9, 18, 14, 10, 12, 7,
        11, 22, 16, 9, 12, 9, 22, 16, 9, 17, 23, 17, 22, 18, 21, 20, 28, 46, 107, 214, 233, 71, 48,
        55, 58, 53, 71, 67, 84, 81, 85, 78, 97, 118, 86, 53, 54, 40, 46, 69, 66, 68, 71, 66, 74, 60,
        44, 62, 39, 43, 45, 49, 64, 47, 67, 42, 59, 62, 60, 47, 52, 63, 38, 44, 45, 45, 33, 35, 61,
        52, 52, 73, 91, 114, 136, 109, 135, 83, 132, 187, 225, 262, 209, 172, 112, 97, 121, 75, 44,
        43, 40, 33, 36, 24, 18, 6, 13, 7, 6, 18, 18, 38, 263, 195, 8842};
    int[] expectedGreen = {
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 1, 3, 6, 5, 7, 9, 8, 11, 13, 15, 13, 6, 24, 13, 11, 9, 14, 13, 13, 11,
        12, 11, 10, 9, 10, 13, 7, 10, 24, 34, 37, 26, 19, 31, 31, 36, 27, 37, 41, 40, 31, 42, 41,
        35, 30, 45, 29, 27, 29, 38, 32, 35, 32, 14, 20, 19, 20, 14, 21, 14, 14, 14, 17, 17, 7, 22,
        21, 19, 16, 13, 20, 25, 17, 17, 24, 21, 27, 22, 23, 27, 23, 31, 21, 24, 27, 31, 22, 31, 24,
        34, 27, 87, 214, 244, 49, 34, 40, 31, 30, 37, 34, 28, 32, 31, 33, 29, 33, 29, 20, 26, 34,
        31, 30, 21, 33, 72, 59, 62, 44, 40, 36, 39, 28, 33, 26, 24, 28, 41, 30, 26, 33, 29, 37, 34,
        35, 30, 34, 37, 34, 33, 31, 31, 28, 49, 49, 38, 50, 87, 109, 111, 132, 114, 217, 181, 224,
        222, 230, 204, 111, 107, 66, 52, 29, 47, 40, 24, 33, 31, 24, 12, 12, 7, 12, 4, 5, 14, 10, 4,
        9, 11, 18, 12, 30, 33, 74, 490, 8617};
    int[] expectedRed = {
        1, 2, 6, 9, 4, 4, 7, 6, 10, 9, 7, 12, 16, 20, 15, 16, 13, 14, 12, 19, 11, 11, 8,
        13, 18, 21, 24, 9, 15, 13, 19, 15, 9, 17, 14, 12, 3, 10, 18, 10, 10, 7, 16, 17,
        14, 11, 9, 12, 16, 17, 17, 15, 14, 18, 10, 19, 19, 16, 14, 23, 29, 15, 17, 18, 16,
        18, 20, 23, 23, 24, 19, 18, 19, 19, 23, 22, 23, 21, 25, 25, 8, 9, 25, 19, 8, 10,
        17, 18, 12, 12, 11, 9, 15, 14, 17, 10, 11, 13, 7, 12, 12, 11, 12, 11, 12, 18, 12,
        21, 19, 21, 16, 12, 11, 20, 16, 17, 18, 11, 11, 18, 9, 18, 10, 10, 3, 9, 5, 17,
        10, 11, 7, 5, 7, 7, 11, 9, 16, 13, 15, 14, 14, 21, 18, 14, 12, 20, 19, 64, 201,
        213, 52, 35, 36, 27, 28, 34, 34, 27, 28, 39, 24, 38, 21, 21, 25, 25, 39, 41, 38,
        28, 53, 43, 53, 44, 38, 32, 35, 26, 29, 24, 30, 30, 29, 29, 28, 24, 28, 20, 31,
        38, 27, 28, 34, 32, 27, 38, 35, 34, 24, 47, 35, 48, 64, 76, 80, 107, 136, 130,
        162, 118, 138, 141, 187, 256, 193, 131, 77, 68, 75, 60, 19, 18, 27, 28, 30, 23,
        24, 26, 32, 27, 22, 5, 11, 1, 8, 7, 10, 7, 5, 9, 9, 7, 8, 12, 15, 14, 16, 14, 32,
        24, 34, 66, 65, 95, 595, 8297};
    for (int i = 0; i < 256; i++) {
      assertThat(response.getHistogram().getRed(i)).isEqualTo(expectedRed[i]);
      assertThat(response.getHistogram().getGreen(i)).isEqualTo(expectedGreen[i]);
      assertThat(response.getHistogram().getBlue(i)).isEqualTo(expectedBlue[i]);
    }
  }

  /**
   * Tests that histogram works with a transparent png.
   */
  @Test
  public void testHistogram_transparent() throws Exception {
    ImagesHistogramRequest histogramRequest = ImagesHistogramRequest.newBuilder()
        .setImage(imageData(readImage("transparent.png")))
        .build();
    ImagesHistogramResponse response = service.histogram(status, histogramRequest);
    int[] expectedRed = {
      60, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 14, 0, 0, 0, 0, 0, 0,
          0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 14, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
          0, 0, 0, 0, 14, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 14,
          0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 14, 0, 0, 0, 0, 0, 0,
          0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 14, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
          0, 0, 0, 0, 14, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 14,
          0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 14, 0, 0, 0, 0, 0, 0,
          0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 14, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
          0, 0, 0, 0, 14, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 14,
          0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 14, 0, 0, 0, 0, 0, 0,
          0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 14, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
          0, 0, 0, 0, 0};
    int[] expectedGreen = {
      60, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 14, 0, 0, 0, 0, 0, 0,
          0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 14, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
          0, 0, 0, 0, 14, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 14,
          0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 14, 0, 0, 0, 0, 0, 0,
          0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 14, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
          0, 0, 0, 0, 14, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 14,
          0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 14, 0, 0, 0, 0, 0, 0,
          0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 14, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
          0, 0, 0, 0, 14, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 14,
          0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 14, 0, 0, 0, 0, 0, 0,
          0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 14, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
          0, 0, 0, 0, 0};
    int[] expectedBlue = {
      0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 14, 0, 0, 0, 0, 0, 0,
          0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 14, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
          0, 0, 0, 0, 14, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 14,
          0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 14, 0, 0, 0, 0, 0, 0,
          0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 14, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
          0, 0, 0, 0, 14, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 14,
          0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 14, 0, 0, 0, 0, 0, 0,
          0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 14, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
          0, 0, 0, 0, 14, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 14,
          0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 14, 0, 0, 0, 0, 0, 0,
          0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 14, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
          0, 0, 0, 0, 60};
    for (int i = 0; i < 256; i++) {
      assertThat(response.getHistogram().getRed(i)).isEqualTo(expectedRed[i]);
      assertThat(response.getHistogram().getGreen(i)).isEqualTo(expectedGreen[i]);
      assertThat(response.getHistogram().getBlue(i)).isEqualTo(expectedBlue[i]);
    }
  }

  /**
   * Tests getting a Local image Url.
   */
  @Test
  public void testGetUrlBase() throws Exception {
    try (OutputStream out = service.getBlobStorage().storeBlob(new BlobKey("blob-key"))) {
      out.write(validImage);
    }

    ImagesGetUrlBaseRequest getUrlBaseRequest =
        ImagesGetUrlBaseRequest.newBuilder().setBlobKey("blob-key").build();
    ImagesGetUrlBaseResponse response = service.getUrlBase(status, getUrlBaseRequest);
    assertThat(response.getUrl()).isEqualTo("http://localhost:8080/_ah/img/blob-key");
  }

  @Test
  public void testGetUrlBaseOnInvalidImage() throws Exception {
    try (OutputStream out = service.getBlobStorage().storeBlob(new BlobKey("not-an-image"))) {
      out.write("This is not an image but a blob".getBytes(UTF_8));
    }

    ImagesGetUrlBaseRequest getUrlBaseRequest =
        ImagesGetUrlBaseRequest.newBuilder().setBlobKey("not-an-image").build();
    ApiProxy.ApplicationException ex =
        assertThrows(
            ApiProxy.ApplicationException.class,
            () -> service.getUrlBase(status, getUrlBaseRequest));
    assertThat(ex.getApplicationError()).isEqualTo(ErrorCode.NOT_IMAGE.getNumber());
    // Should be no blob key.
    DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
    assertThrows(
        EntityNotFoundException.class,
        () ->
            datastore.get(
                KeyFactory.createKey(ImagesReservedKinds.BLOB_SERVING_URL_KIND, "blob-key")));
  }

  /**
   * Tests getting a Local image Url on a non-existent blob
   */
  @Test
  public void testGetUrlBaseBlobNotFound() throws Exception {
    ImagesGetUrlBaseRequest getUrlBaseRequest = ImagesGetUrlBaseRequest.newBuilder()
        .setBlobKey("non-existent")
        .build();
    ApiProxy.ApplicationException ex =
        assertThrows(
            ApiProxy.ApplicationException.class,
            () -> service.getUrlBase(status, getUrlBaseRequest));
    assertThat(ex.getApplicationError()).isEqualTo(ErrorCode.INVALID_BLOB_KEY.getNumber());
  }

  /**
   * Tests that EXIF orientation is stripped and applied only with
   * CORRECT_ORIENTATION set.
   */
  @Test
  public void testCorrectOrientation() throws Exception {
    request.setImage(imageData(readImage("exif.jpg")));
    request.addTransform(Transform.newBuilder().setAutolevels(true));
    OutputSettings pbSettings = OutputSettings.newBuilder().setMimeType(MIME_TYPE.JPEG).build();
    request.setOutput(pbSettings);
    runTransformTest("exif-without-correction.jpg");
    request.setInput(InputSettings.newBuilder().setCorrectExifOrientation(
        ORIENTATION_CORRECTION_TYPE.CORRECT_ORIENTATION).build());
    runTransformTest("exif-after-correction.jpg");
  }

  /**
   * Tests that an image without EXIF data is not transformed.
   */
  @Test
  public void testCorrectOrientationWithoutExif() throws Exception {
    request.setImage(imageData(readImage("before.jpg")));
    request.addTransform(Transform.newBuilder().setAutolevels(true));
    OutputSettings pbSettings = OutputSettings.newBuilder().setMimeType(MIME_TYPE.JPEG).build();
    request.setOutput(pbSettings);
    runTransformTest("exif-without-correction.jpg");
    request.setInput(InputSettings.newBuilder().setCorrectExifOrientation(
        ORIENTATION_CORRECTION_TYPE.CORRECT_ORIENTATION).build());
    runTransformTest("exif-without-correction.jpg");
  }

  /**
   * Tests that an invalid EXIF orientation value is ignored.
   */
  @Test
  public void testCorrectOrientationWithInvalidExif() throws Exception {
    byte[] image = readImage("exif.jpg");
    image[EXIF_ORIENTATION_LOCATION] = (byte) 0;
    request.setImage(imageData(image));
    request.addTransform(Transform.newBuilder().setAutolevels(true));
    OutputSettings pbSettings = OutputSettings.newBuilder().setMimeType(MIME_TYPE.JPEG).build();
    request.setOutput(pbSettings);
    runTransformTest("exif-without-correction.jpg");
    request.setInput(InputSettings.newBuilder().setCorrectExifOrientation(
        ORIENTATION_CORRECTION_TYPE.CORRECT_ORIENTATION).build());
    runTransformTest("exif-without-correction.jpg");
  }

  /**
   * Tests that EXIF orientation is stripped and applied only with
   * CORRECT_ORIENTATION set with the orientation correction requiring a flip
   * and a rotation.
   */
  @Test
  public void testCorrectOrientationFlipAndRotate() throws Exception {
    request.setImage(imageData(readImage("exif-5.jpg")));
    request.addTransform(Transform.newBuilder().setAutolevels(true));
    OutputSettings pbSettings = OutputSettings.newBuilder().setMimeType(MIME_TYPE.JPEG).build();
    request.setOutput(pbSettings);
    runTransformTest("exif-without-correction.jpg");
    request.setInput(InputSettings.newBuilder().setCorrectExifOrientation(
        ORIENTATION_CORRECTION_TYPE.CORRECT_ORIENTATION).build());
    runTransformTest("exif-5-after-correction.jpg");
  }

  /**
   * Tests that EXIF orientation is stripped and not applied to an image that is
   * already portrait.
   */
  @Test
  public void testCorrectOrientationPortraitUnchanged() throws Exception {
    byte[] image = readImage("exif-portrait.jpg");
    request.addTransform(Transform.newBuilder().setAutolevels(true));
    OutputSettings pbSettings = OutputSettings.newBuilder().setMimeType(MIME_TYPE.JPEG).build();
    request.setOutput(pbSettings);
    for (int i = 0; i <= 9; i++) {
      image[EXIF_ORIENTATION_LOCATION] = (byte) i;
      request.setImage(imageData(image));
      runTransformTest("exif-portrait-without-correction.jpg");
      request.setInput(InputSettings.newBuilder().setCorrectExifOrientation(
          ORIENTATION_CORRECTION_TYPE.CORRECT_ORIENTATION).build());
      runTransformTest("exif-portrait-without-correction.jpg");
    }
  }

  private static ImageData imageData(byte[] bytes) {
    return ImageData.newBuilder()
        .setContent(ByteString.copyFrom(bytes))
        .build();
  }
}
