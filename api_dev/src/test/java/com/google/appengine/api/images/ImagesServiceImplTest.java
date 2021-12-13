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
import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.notNull;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.appengine.api.blobstore.BlobKey;
import com.google.appengine.api.blobstore.BlobstoreServicePb.CreateEncodedGoogleStorageKeyRequest;
import com.google.appengine.api.blobstore.BlobstoreServicePb.CreateEncodedGoogleStorageKeyResponse;
import com.google.appengine.api.images.ImagesServicePb.CompositeImageOptions;
import com.google.appengine.api.images.ImagesServicePb.CompositeImageOptions.ANCHOR;
import com.google.appengine.api.images.ImagesServicePb.ImageData;
import com.google.appengine.api.images.ImagesServicePb.ImagesCanvas;
import com.google.appengine.api.images.ImagesServicePb.ImagesCompositeRequest;
import com.google.appengine.api.images.ImagesServicePb.ImagesCompositeResponse;
import com.google.appengine.api.images.ImagesServicePb.ImagesDeleteUrlBaseRequest;
import com.google.appengine.api.images.ImagesServicePb.ImagesDeleteUrlBaseResponse;
import com.google.appengine.api.images.ImagesServicePb.ImagesGetUrlBaseRequest;
import com.google.appengine.api.images.ImagesServicePb.ImagesGetUrlBaseResponse;
import com.google.appengine.api.images.ImagesServicePb.ImagesHistogram;
import com.google.appengine.api.images.ImagesServicePb.ImagesHistogramRequest;
import com.google.appengine.api.images.ImagesServicePb.ImagesHistogramResponse;
import com.google.appengine.api.images.ImagesServicePb.ImagesServiceError.ErrorCode;
import com.google.appengine.api.images.ImagesServicePb.ImagesTransformRequest;
import com.google.appengine.api.images.ImagesServicePb.ImagesTransformResponse;
import com.google.appengine.api.images.ImagesServicePb.InputSettings.ORIENTATION_CORRECTION_TYPE;
import com.google.appengine.api.images.InputSettings.OrientationCorrection;
import com.google.appengine.api.testing.LocalServiceTestHelperRule;
import com.google.apphosting.api.ApiProxy;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/**
 * Tests the ImagesService implementation.
 *
 */
@RunWith(JUnit4.class)
public class ImagesServiceImplTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Rule public LocalServiceTestHelperRule testHelperRule = new LocalServiceTestHelperRule();

  private static final String BLOB_KEY = "foo";

  @Mock private ApiProxy.Delegate<ApiProxy.Environment> delegate;
  private ImageData blobImageData;

  @Before
  public void setUp() throws Exception {
    ApiProxy.setDelegate(delegate);

    blobImageData =
        ImageData.newBuilder().setContent(ByteString.EMPTY).setBlobKey(BLOB_KEY).build();
  }

  /** Tests that apply correctly passes the request to the delegate. */
  @Test
  public void testApply() {
    byte[] imageData = "an image".getBytes(UTF_8);
    byte[] fakeResponse = "a nicer image".getBytes(UTF_8);
    ImagesService service = new ImagesServiceImpl();
    Transform transform = mock(Transform.class);
    ImagesTransformRequest request =
        ImagesTransformRequest.newBuilder()
            .setImage(imageData(imageData))
            .setOutput(
                ImagesServicePb.OutputSettings.newBuilder()
                    .setMimeType(ImagesServicePb.OutputSettings.MIME_TYPE.JPEG))
            .setInput(
                ImagesServicePb.InputSettings.newBuilder()
                    .setCorrectExifOrientation(ORIENTATION_CORRECTION_TYPE.UNCHANGED_ORIENTATION))
            .build();
    ImagesTransformResponse response =
        ImagesTransformResponse.newBuilder().setImage(imageData(fakeResponse)).build();

    ApiProxy.setDelegate(new FakeDelegate(request, response.toByteArray()));
    Image out =
        service.applyTransform(
            transform, new ImageImpl(imageData), ImagesService.OutputEncoding.JPEG);
    verify(transform).apply(notNull());
    assertThat(out.getImageData()).isEqualTo(fakeResponse);
  }

  /** Tests that apply correctly passes the request to the delegate. */
  @Test
  public void testApplyWithOutputQuality() {
    testApplyWithOutputBody(
        ImagesServicePb.OutputSettings.MIME_TYPE.JPEG, ImagesService.OutputEncoding.JPEG);
    testApplyWithOutputBody(
        ImagesServicePb.OutputSettings.MIME_TYPE.WEBP, ImagesService.OutputEncoding.WEBP);
  }

  private void testApplyWithOutputBody(
      ImagesServicePb.OutputSettings.MIME_TYPE inputType, ImagesService.OutputEncoding outputType) {
    byte[] imageData = "an image".getBytes(UTF_8);
    byte[] fakeResponse = "a nicer image".getBytes(UTF_8);
    ImagesService service = new ImagesServiceImpl();
    Transform transform = mock(Transform.class);
    ImagesTransformRequest request =
        ImagesTransformRequest.newBuilder()
            .setImage(imageData(imageData))
            .setOutput(
                ImagesServicePb.OutputSettings.newBuilder().setMimeType(inputType).setQuality(50))
            .setInput(
                ImagesServicePb.InputSettings.newBuilder()
                    .setCorrectExifOrientation(ORIENTATION_CORRECTION_TYPE.UNCHANGED_ORIENTATION))
            .build();
    ImagesTransformResponse response =
        ImagesTransformResponse.newBuilder().setImage(imageData(fakeResponse)).build();

    ApiProxy.setDelegate(new FakeDelegate(request, response.toByteArray()));
    OutputSettings settings = new OutputSettings(outputType);

    settings.setQuality(50);

    Image out = service.applyTransform(transform, new ImageImpl(imageData), settings);
    verify(transform).apply(notNull());
    assertThat(out.getImageData()).isEqualTo(fakeResponse);
  }

  /**
   * Tests that apply correctly passes the request to the delegate with
   * ImagesServicePb.InputSettings.
   */
  @Test
  public void testApplyWithCorrectOrientationUnchanged() {
    byte[] imageData = "an image".getBytes(UTF_8);
    byte[] fakeResponse = "a nicer image".getBytes(UTF_8);
    ImagesService service = new ImagesServiceImpl();
    Transform transform = mock(Transform.class);
    ImagesTransformRequest request =
        ImagesTransformRequest.newBuilder()
            .setImage(imageData(imageData))
            .setOutput(
                ImagesServicePb.OutputSettings.newBuilder()
                    .setMimeType(ImagesServicePb.OutputSettings.MIME_TYPE.JPEG))
            .setInput(
                ImagesServicePb.InputSettings.newBuilder()
                    .setCorrectExifOrientation(ORIENTATION_CORRECTION_TYPE.UNCHANGED_ORIENTATION))
            .build();
    ImagesTransformResponse response =
        ImagesTransformResponse.newBuilder().setImage(imageData(fakeResponse)).build();

    ApiProxy.setDelegate(new FakeDelegate(request, response.toByteArray()));
    InputSettings inputSettings = new InputSettings();
    inputSettings.setOrientationCorrection(OrientationCorrection.UNCHANGED_ORIENTATION);
    OutputSettings outputSettings = new OutputSettings(ImagesService.OutputEncoding.JPEG);
    Image out =
        service.applyTransform(transform, new ImageImpl(imageData), inputSettings, outputSettings);
    verify(transform).apply(notNull());
    assertThat(out.getImageData()).isEqualTo(fakeResponse);
  }

  /**
   * Tests that apply correctly passes the request to the delegate with
   * ImagesServicePb.InputSettings.
   */
  @Test
  public void testApplyWithCorrectOrientationDoCorrect() {
    byte[] imageData = "an image".getBytes(UTF_8);
    byte[] fakeResponse = "a nicer image".getBytes(UTF_8);
    ImagesService service = new ImagesServiceImpl();
    Transform transform = mock(Transform.class);
    ImagesTransformRequest request =
        ImagesTransformRequest.newBuilder()
            .setImage(imageData(imageData))
            .setOutput(
                ImagesServicePb.OutputSettings.newBuilder()
                    .setMimeType(ImagesServicePb.OutputSettings.MIME_TYPE.JPEG)
                    .setQuality(20))
            .setInput(
                ImagesServicePb.InputSettings.newBuilder()
                    .setCorrectExifOrientation(ORIENTATION_CORRECTION_TYPE.CORRECT_ORIENTATION))
            .build();
    ImagesTransformResponse response =
        ImagesTransformResponse.newBuilder().setImage(imageData(fakeResponse)).build();

    ApiProxy.setDelegate(new FakeDelegate(request, response.toByteArray()));
    InputSettings inputSettings = new InputSettings();
    inputSettings.setOrientationCorrection(OrientationCorrection.CORRECT_ORIENTATION);
    OutputSettings outputSettings = new OutputSettings(ImagesService.OutputEncoding.JPEG);
    outputSettings.setQuality(20);
    Image out =
        service.applyTransform(transform, new ImageImpl(imageData), inputSettings, outputSettings);
    verify(transform).apply(notNull());
    assertThat(out.getImageData()).isEqualTo(fakeResponse);
  }

  /**
   * Tests that applyTransform correctly passes the request to the delegate in an asynchronous
   * manner.
   */
  @Test
  public void testAsync_apply() throws Exception {
    byte[] imageData = "an image".getBytes(UTF_8);
    byte[] fakeResponse = "a nicer image".getBytes(UTF_8);
    ImagesService service = new ImagesServiceImpl();
    Transform transform = mock(Transform.class);
    ImagesTransformRequest request =
        ImagesTransformRequest.newBuilder()
            .setImage(imageData(imageData))
            .setOutput(
                ImagesServicePb.OutputSettings.newBuilder()
                    .setMimeType(ImagesServicePb.OutputSettings.MIME_TYPE.JPEG))
            .setInput(
                ImagesServicePb.InputSettings.newBuilder()
                    .setCorrectExifOrientation(ORIENTATION_CORRECTION_TYPE.UNCHANGED_ORIENTATION))
            .build();
    ImagesTransformResponse response =
        ImagesTransformResponse.newBuilder().setImage(imageData(fakeResponse)).build();

    Future<byte[]> futureMock = immediateFuture(response.toByteArray());
    when(delegate.makeAsyncCall(
            same(ApiProxy.getCurrentEnvironment()),
            eq(ImagesServiceImpl.PACKAGE),
            eq("Transform"),
            eq(request.toByteArray()),
            isA(ApiProxy.ApiConfig.class)))
        .thenReturn(futureMock);

    Future<Image> out =
        service.applyTransformAsync(
            transform, new ImageImpl(imageData), ImagesService.OutputEncoding.JPEG);
    Image image = out.get();
    verify(transform).apply(notNull());

    assertThat(image.getImageData()).isEqualTo(fakeResponse);
  }

  /**
   * Tests that applyTransform correctly passes the request to the delegate in an asynchronous
   * manner.
   */
  @Test
  public void testAsync_applyWithSettings() throws Exception {
    byte[] imageData = "an image".getBytes(UTF_8);
    byte[] fakeResponse = "a nicer image".getBytes(UTF_8);
    ImagesService service = new ImagesServiceImpl();
    Transform transform = mock(Transform.class);
    ImagesTransformRequest request =
        ImagesTransformRequest.newBuilder()
            .setImage(imageData(imageData))
            .setOutput(
                ImagesServicePb.OutputSettings.newBuilder()
                    .setMimeType(ImagesServicePb.OutputSettings.MIME_TYPE.JPEG)
                    .setQuality(20))
            .setInput(
                ImagesServicePb.InputSettings.newBuilder()
                    .setCorrectExifOrientation(ORIENTATION_CORRECTION_TYPE.CORRECT_ORIENTATION))
            .build();
    ImagesTransformResponse response =
        ImagesTransformResponse.newBuilder().setImage(imageData(fakeResponse)).build();

    Future<byte[]> futureMock = immediateFuture(response.toByteArray());
    when(delegate.makeAsyncCall(
            same(ApiProxy.getCurrentEnvironment()),
            eq(ImagesServiceImpl.PACKAGE),
            eq("Transform"),
            eq(request.toByteArray()),
            isA(ApiProxy.ApiConfig.class)))
        .thenReturn(futureMock);

    InputSettings inputSettings = new InputSettings();
    inputSettings.setOrientationCorrection(OrientationCorrection.CORRECT_ORIENTATION);
    OutputSettings outputSettings = new OutputSettings(ImagesService.OutputEncoding.JPEG);
    outputSettings.setQuality(20);
    Future<Image> out =
        service.applyTransformAsync(
            transform, new ImageImpl(imageData), inputSettings, outputSettings);
    Image image = out.get();
    verify(transform).apply(notNull());

    assertThat(image.getImageData()).isEqualTo(fakeResponse);
  }

  /** Tests that the asynchronous version of applyTransform handles exceptions properly. */
  @Test
  public void testAsync_apply_exception() throws Exception {
    byte[] imageData = "an image".getBytes(UTF_8);
    ImagesService service = new ImagesServiceImpl();
    Transform transform = mock(Transform.class);
    ImagesTransformRequest request =
        ImagesTransformRequest.newBuilder()
            .setImage(imageData(imageData))
            .setOutput(
                ImagesServicePb.OutputSettings.newBuilder()
                    .setMimeType(ImagesServicePb.OutputSettings.MIME_TYPE.JPEG))
            .setInput(
                ImagesServicePb.InputSettings.newBuilder()
                    .setCorrectExifOrientation(ORIENTATION_CORRECTION_TYPE.UNCHANGED_ORIENTATION))
            .build();

    Future<byte[]> futureMock =
        immediateFailedFuture(
            new ApiProxy.ApplicationException(
                ErrorCode.UNSPECIFIED_ERROR.getNumber(), "Could not transform image"));
    when(delegate.makeAsyncCall(
            same(ApiProxy.getCurrentEnvironment()),
            eq(ImagesServiceImpl.PACKAGE),
            eq("Transform"),
            eq(request.toByteArray()),
            isA(ApiProxy.ApiConfig.class)))
        .thenReturn(futureMock);

    Future<Image> out =
        service.applyTransformAsync(
            transform, new ImageImpl(imageData), ImagesService.OutputEncoding.JPEG);

    ExecutionException ex = assertThrows(ExecutionException.class, out::get);
    assertThat(ex).hasCauseThat().isInstanceOf(ImagesServiceFailureException.class);
    assertThat(ex).hasCauseThat().hasMessageThat().isEqualTo("Could not transform image");
    verify(transform).apply(notNull());
  }

  @Test
  public void testApplyWithBlobKey() {
    byte[] fakeResponse = "a nicer image".getBytes(UTF_8);
    ImagesService service = new ImagesServiceImpl();
    ImagesTransformRequest request =
        ImagesTransformRequest.newBuilder()
            .setImage(blobImageData)
            .setOutput(
                ImagesServicePb.OutputSettings.newBuilder()
                    .setMimeType(ImagesServicePb.OutputSettings.MIME_TYPE.JPEG))
            .setInput(
                ImagesServicePb.InputSettings.newBuilder()
                    .setCorrectExifOrientation(ORIENTATION_CORRECTION_TYPE.UNCHANGED_ORIENTATION))
            .build();
    ImagesTransformResponse response =
        ImagesTransformResponse.newBuilder().setImage(imageData(fakeResponse)).build();

    Transform transform = mock(Transform.class);

    ApiProxy.setDelegate(new FakeDelegate(request, response.toByteArray()));
    Image out =
        service.applyTransform(
            transform, new ImageImpl(new BlobKey(BLOB_KEY)), ImagesService.OutputEncoding.JPEG);
    assertThat(out.getImageData()).isEqualTo(fakeResponse);
    verify(transform).apply(notNull());
  }

  @Test
  public void testHistogram() {
    byte[] imageData = "an image".getBytes(UTF_8);
    ImagesHistogramRequest request =
        ImagesHistogramRequest.newBuilder().setImage(imageData(imageData)).build();
    ImagesHistogram.Builder histogramBuilder = ImagesHistogram.newBuilder();
    for (int i = 0; i < 256; i++) {
      histogramBuilder.addRed(i % 60);
      histogramBuilder.addGreen(i % 40);
      histogramBuilder.addBlue(i % 50);
    }
    ImagesHistogramResponse response =
        ImagesHistogramResponse.newBuilder().setHistogram(histogramBuilder).build();
    ImagesService service = new ImagesServiceImpl();
    ApiProxy.setDelegate(new FakeDelegate(request, response.toByteArray()));
    int[][] histogram = service.histogram(new ImageImpl(imageData));
    for (int i = 0; i < 256; i++) {
      assertThat(histogram[0][i]).isEqualTo(response.getHistogram().getRed(i));
      assertThat(histogram[1][i]).isEqualTo(response.getHistogram().getGreen(i));
      assertThat(histogram[2][i]).isEqualTo(response.getHistogram().getBlue(i));
    }
  }

  @Test
  public void testHistogramWithBlobKey() {
    ImagesHistogramRequest request =
        ImagesHistogramRequest.newBuilder().setImage(blobImageData).build();
    ImagesHistogram.Builder histogramBuilder = ImagesHistogram.newBuilder();
    for (int i = 0; i < 256; i++) {
      histogramBuilder.addRed(i % 60);
      histogramBuilder.addGreen(i % 40);
      histogramBuilder.addBlue(i % 50);
    }
    ImagesHistogramResponse response =
        ImagesHistogramResponse.newBuilder().setHistogram(histogramBuilder).build();
    ImagesService service = new ImagesServiceImpl();
    ApiProxy.setDelegate(new FakeDelegate(request, response.toByteArray()));
    int[][] histogram = service.histogram(new ImageImpl(new BlobKey(BLOB_KEY)));
    for (int i = 0; i < 256; i++) {
      assertThat(histogram[0][i]).isEqualTo(response.getHistogram().getRed(i));
      assertThat(histogram[1][i]).isEqualTo(response.getHistogram().getGreen(i));
      assertThat(histogram[2][i]).isEqualTo(response.getHistogram().getBlue(i));
    }
  }

  /** Tests that composite correctly passes the request to the delegate. */
  @Test
  public void testComposite() {
    byte[] imageData = "an image".getBytes(UTF_8);
    byte[] secondImageData = "another image".getBytes(UTF_8);
    byte[] fakeResponse = "a canvas".getBytes(UTF_8);
    ImagesService service = new ImagesServiceImpl();
    FakeComposite secondComposite =
        new FakeComposite(
            new ImageImpl(secondImageData), 50, 40, 0.9f, Composite.Anchor.TOP_CENTER);
    FakeComposite composite =
        new FakeComposite(new ImageImpl(imageData), 20, 30, 0.5f, Composite.Anchor.CENTER_LEFT);
    ImagesCompositeRequest request =
        ImagesCompositeRequest.newBuilder()
            .addImage(imageData(imageData))
            .addImage(imageData(secondImageData))
            .setCanvas(
                ImagesCanvas.newBuilder()
                    .setOutput(
                        ImagesServicePb.OutputSettings.newBuilder()
                            .setMimeType(ImagesServicePb.OutputSettings.MIME_TYPE.JPEG))
                    .setWidth(100)
                    .setHeight(50)
                    .setColor(0))
            .addOptions(
                CompositeImageOptions.newBuilder()
                    .setXOffset(20)
                    .setYOffset(30)
                    .setAnchor(ANCHOR.LEFT)
                    .setSourceIndex(0)
                    .setOpacity(0.5f))
            .addOptions(
                CompositeImageOptions.newBuilder()
                    .setXOffset(50)
                    .setYOffset(40)
                    .setAnchor(ANCHOR.TOP)
                    .setSourceIndex(1)
                    .setOpacity(0.9f))
            .build();

    ImagesCompositeResponse response =
        ImagesCompositeResponse.newBuilder().setImage(imageData(fakeResponse)).build();

    ApiProxy.setDelegate(new FakeDelegate(request, response.toByteArray()));
    ImmutableList<Composite> composites = ImmutableList.of(composite, secondComposite);
    Image out = service.composite(composites, 100, 50, 0, ImagesService.OutputEncoding.JPEG);
    assertThat(out.getImageData()).isEqualTo(fakeResponse);
    assertThat(composite.applications).isEqualTo(1);
    assertThat(secondComposite.applications).isEqualTo(1);
  }

  /** Tests that composite correctly passes the request to the delegate. */
  @Test
  public void testCompositeWithOutputQuality() {
    byte[] imageData = "an image".getBytes(UTF_8);
    byte[] secondImageData = "another image".getBytes(UTF_8);
    byte[] fakeResponse = "a canvas".getBytes(UTF_8);
    ImagesService service = new ImagesServiceImpl();
    FakeComposite secondComposite =
        new FakeComposite(
            new ImageImpl(secondImageData), 50, 40, 0.9f, Composite.Anchor.TOP_CENTER);
    FakeComposite composite =
        new FakeComposite(new ImageImpl(imageData), 20, 30, 0.5f, Composite.Anchor.CENTER_LEFT);
    ImagesCompositeRequest request =
        ImagesCompositeRequest.newBuilder()
            .addImage(imageData(imageData))
            .addImage(imageData(secondImageData))
            .setCanvas(
                ImagesCanvas.newBuilder()
                    .setOutput(
                        ImagesServicePb.OutputSettings.newBuilder()
                            .setMimeType(ImagesServicePb.OutputSettings.MIME_TYPE.JPEG)
                            .setQuality(50))
                    .setWidth(100)
                    .setHeight(50)
                    .setColor(0))
            .addOptions(
                CompositeImageOptions.newBuilder()
                    .setXOffset(20)
                    .setYOffset(30)
                    .setAnchor(ANCHOR.LEFT)
                    .setSourceIndex(0)
                    .setOpacity(0.5f))
            .addOptions(
                CompositeImageOptions.newBuilder()
                    .setXOffset(50)
                    .setYOffset(40)
                    .setAnchor(ANCHOR.TOP)
                    .setSourceIndex(1)
                    .setOpacity(0.9f))
            .build();

    ImagesCompositeResponse response =
        ImagesCompositeResponse.newBuilder().setImage(imageData(fakeResponse)).build();

    ApiProxy.setDelegate(new FakeDelegate(request, response.toByteArray()));
    ImmutableList<Composite> composites = ImmutableList.of(composite, secondComposite);

    OutputSettings settings = new OutputSettings(ImagesService.OutputEncoding.JPEG);
    settings.setQuality(50);

    Image out = service.composite(composites, 100, 50, 0, settings);
    assertThat(out.getImageData()).isEqualTo(fakeResponse);
    assertThat(composite.applications).isEqualTo(1);
    assertThat(secondComposite.applications).isEqualTo(1);
  }

  @Test
  public void testCompositeFromBlobKey() {
    byte[] imageData = "an image".getBytes(UTF_8);
    byte[] fakeResponse = "a canvas".getBytes(UTF_8);
    ImagesService service = new ImagesServiceImpl();
    FakeComposite secondComposite =
        new FakeComposite(
            new ImageImpl(new BlobKey(BLOB_KEY)), 50, 40, 0.9f, Composite.Anchor.TOP_CENTER);
    FakeComposite composite =
        new FakeComposite(new ImageImpl(imageData), 20, 30, 0.5f, Composite.Anchor.CENTER_LEFT);
    ImagesCompositeRequest request =
        ImagesCompositeRequest.newBuilder()
            .addImage(imageData(imageData))
            .addImage(blobImageData)
            .setCanvas(
                ImagesCanvas.newBuilder()
                    .setOutput(
                        ImagesServicePb.OutputSettings.newBuilder()
                            .setMimeType(ImagesServicePb.OutputSettings.MIME_TYPE.JPEG))
                    .setWidth(100)
                    .setHeight(50)
                    .setColor(0))
            .addOptions(
                ImagesServicePb.CompositeImageOptions.newBuilder()
                    .setXOffset(20)
                    .setYOffset(30)
                    .setAnchor(ANCHOR.LEFT)
                    .setSourceIndex(0)
                    .setOpacity(0.5f))
            .addOptions(
                ImagesServicePb.CompositeImageOptions.newBuilder()
                    .setXOffset(50)
                    .setYOffset(40)
                    .setAnchor(ANCHOR.TOP)
                    .setSourceIndex(1)
                    .setOpacity(0.9f))
            .build();

    ImagesCompositeResponse response =
        ImagesCompositeResponse.newBuilder().setImage(imageData(fakeResponse)).build();

    ApiProxy.setDelegate(new FakeDelegate(request, response.toByteArray()));
    ImmutableList<Composite> composites = ImmutableList.of(composite, secondComposite);
    Image out = service.composite(composites, 100, 50, 0, ImagesService.OutputEncoding.JPEG);
    assertThat(out.getImageData()).isEqualTo(fakeResponse);
    assertThat(composite.applications).isEqualTo(1);
    assertThat(secondComposite.applications).isEqualTo(1);
  }

  @Test
  public void testGetServingUrlInvalidParameters() {
    BlobKey blobKey = new BlobKey("some blob key");
    ImagesService service = new ImagesServiceImpl();
    assertThrows(NullPointerException.class, () -> service.getServingUrl((BlobKey) null));
    assertThrows(IllegalArgumentException.class, () -> service.getServingUrl(blobKey, 3333, false));
    assertThrows(IllegalArgumentException.class, () -> service.getServingUrl(blobKey, 1601, true));

    assertThrows(
        IllegalArgumentException.class,
        () -> service.getServingUrl(ServingUrlOptions.Builder.withBlobKey(null)));
  }

  @Test
  public void testGetServingUrlWithBlobKey() {
    BlobKey blobKey = new BlobKey("some blob key");
    String expectedUrl = "http://lh3.ggpht.com/SomeImage.jpg";
    ImagesGetUrlBaseRequest request =
        ImagesGetUrlBaseRequest.newBuilder()
            .setBlobKey(blobKey.getKeyString())
            .setCreateSecureUrl(false)
            .build();
    ImagesGetUrlBaseResponse response =
        ImagesGetUrlBaseResponse.newBuilder().setUrl(expectedUrl).build();
    ImagesService service = new ImagesServiceImpl();
    ApiProxy.setDelegate(new FakeDelegate(request, response.toByteArray()));

    assertThat(service.getServingUrl(blobKey)).isEqualTo(expectedUrl);
    assertThat(service.getServingUrl(blobKey, 150, false)).isEqualTo(expectedUrl + "=s150");
    assertThat(service.getServingUrl(blobKey, 150, true)).isEqualTo(expectedUrl + "=s150-c");
  }

  @Test
  public void testGetServingUrlWithGoogleStorageFile() {
    String filename = "/gs/bucket/object";
    String encodedGsKey = "some_fancy_blobkey";
    BlobKey blobKey = new BlobKey(encodedGsKey);
    String expectedUrl = "http://lh3.ggpht.com/SomeImage.jpg";

    CreateEncodedGoogleStorageKeyRequest keyRequest =
        CreateEncodedGoogleStorageKeyRequest.newBuilder().setFilename(filename).build();
    CreateEncodedGoogleStorageKeyResponse keyResponse =
        CreateEncodedGoogleStorageKeyResponse.newBuilder().setBlobKey(encodedGsKey).build();

    when(delegate.makeSyncCall(
            same(ApiProxy.getCurrentEnvironment()),
            eq("blobstore"),
            eq("CreateEncodedGoogleStorageKey"),
            eq(keyRequest.toByteArray())))
        .thenReturn(keyResponse.toByteArray());

    ImagesGetUrlBaseRequest request =
        ImagesGetUrlBaseRequest.newBuilder().setBlobKey(blobKey.getKeyString()).build();
    ImagesGetUrlBaseResponse response =
        ImagesGetUrlBaseResponse.newBuilder().setUrl(expectedUrl).build();

    when(delegate.makeSyncCall(
            same(ApiProxy.getCurrentEnvironment()),
            eq(ImagesServiceImpl.PACKAGE),
            eq("GetUrlBase"),
            eq(request.toByteArray())))
        .thenReturn(response.toByteArray());

    ApiProxy.setDelegate(delegate);

    ImagesService service = new ImagesServiceImpl();
    assertThat(service.getServingUrl(ServingUrlOptions.Builder.withGoogleStorageFileName(filename)))
        .isEqualTo(expectedUrl);
  }

  @Test
  public void testGetServingUrlWithBlobKeyHttps() {
    BlobKey blobKey = new BlobKey("some blob key");
    String expectedUrl = "https://lh3.ggpht.com/SomeImage.jpg";
    ImagesGetUrlBaseRequest request =
        ImagesGetUrlBaseRequest.newBuilder()
            .setBlobKey(blobKey.getKeyString())
            .setCreateSecureUrl(true)
            .build();
    ImagesGetUrlBaseResponse response =
        ImagesGetUrlBaseResponse.newBuilder().setUrl(expectedUrl).build();
    ImagesService service = new ImagesServiceImpl();
    ApiProxy.setDelegate(new FakeDelegate(request, response.toByteArray()));

    assertThat(service.getServingUrl(blobKey, true)).isEqualTo(expectedUrl);
    assertThat(service.getServingUrl(blobKey, 150, false, true)).isEqualTo(expectedUrl + "=s150");
    assertThat(service.getServingUrl(blobKey, 150, true, true)).isEqualTo(expectedUrl + "=s150-c");
  }

  @Test
  public void testDeleteServingUrlWithBlobKey() {
    BlobKey blobKey = new BlobKey("some blob key");
    ImagesDeleteUrlBaseRequest request =
        ImagesDeleteUrlBaseRequest.newBuilder().setBlobKey(blobKey.getKeyString()).build();
    ImagesDeleteUrlBaseResponse response = ImagesDeleteUrlBaseResponse.getDefaultInstance();
    ImagesService service = new ImagesServiceImpl();
    ApiProxy.setDelegate(new FakeDelegate(request, response.toByteArray()));

    service.deleteServingUrl(blobKey);
  }

  @Test
  public void testDeleteServingUrlWithInvalidParameters() {
    ImagesService service = new ImagesServiceImpl();
    assertThrows(NullPointerException.class, () -> service.deleteServingUrl(null));
  }

  static class FakeComposite extends Composite {
    int applications;

    private final Composite delegate;

    FakeComposite(Image image, int xOffset, int yOffset, float opacity, Anchor anchor) {
      delegate = new CompositeImpl(image, xOffset, yOffset, opacity, anchor);
      applications = 0;
    }

    @Override
    void apply(
        ImagesServicePb.ImagesCompositeRequest.Builder request, Map<Image, Integer> imageIndexMap) {
      delegate.apply(request, imageIndexMap);
      applications++;
    }
  }

  /**
   * A fake delegate mock that will check that the correct input is passed and then returns a
   * supplied response.
   */
  static class FakeDelegate implements ApiProxy.Delegate<ApiProxy.Environment> {
    final Message expectedRequest;
    final byte[] response;

    FakeDelegate(Message request, byte[] response) {
      expectedRequest = request;
      this.response = response;
    }

    @Override
    public byte[] makeSyncCall(
        ApiProxy.Environment environment,
        String packageName,
        String methodName,
        byte[] requestBytes) {
      Message.Builder actualRequestBuilder = expectedRequest.newBuilderForType();
      try {
        actualRequestBuilder.mergeFrom(requestBytes, ExtensionRegistry.getEmptyRegistry());
      } catch (InvalidProtocolBufferException e) {
        throw new IllegalArgumentException(e);
      }

      assertThat(actualRequestBuilder.build()).isEqualTo(expectedRequest);

      return response;
    }

    @Override
    public Future<byte[]> makeAsyncCall(
        ApiProxy.Environment environment,
        String packageName,
        String methodName,
        byte[] requestBytes,
        ApiProxy.ApiConfig apiConfig) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void log(ApiProxy.Environment environment, ApiProxy.LogRecord record) {}

    @Override
    public void flushLogs(ApiProxy.Environment environment) {}

    @Override
    public List<Thread> getRequestThreads(ApiProxy.Environment environment) {
      return ImmutableList.of();
    }
  }

  private static ImageData imageData(byte[] bytes) {
    return ImageData.newBuilder().setContent(ByteString.copyFrom(bytes)).build();
  }
}
