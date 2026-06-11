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

import static java.util.Objects.requireNonNull;

import com.google.appengine.api.blobstore.BlobKey;
import com.google.appengine.api.blobstore.BlobstoreServiceFactory;
import com.google.appengine.api.images.ImagesServicePb.ImageData;
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
import com.google.appengine.api.images.ImagesServicePb.OutputSettings.MIME_TYPE;
import com.google.appengine.api.utils.FutureWrapper;
import com.google.apphosting.api.ApiProxy;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;
import org.jspecify.annotations.Nullable;

/**
 * Implementation of the ImagesService interface.
 *
 */
final class ImagesServiceImpl implements ImagesService {
  static final String PACKAGE = "images";

  /** {@inheritDoc} */
  @Override
  public Image applyTransform(Transform transform, Image image) {
    return applyTransform(transform, image, OutputEncoding.PNG);
  }

  /** {@inheritDoc} */
  @Override
  public Future<Image> applyTransformAsync(Transform transform, Image image) {
    return applyTransformAsync(transform, image, OutputEncoding.PNG);
  }

  /** {@inheritDoc} */
  @Override
  public Image applyTransform(Transform transform, Image image, OutputEncoding encoding) {
    return applyTransform(transform, image, new OutputSettings(encoding));
  }

  /** {@inheritDoc} */
  @Override
  public Future<Image> applyTransformAsync(
      Transform transform, final Image image, OutputEncoding encoding) {
    return applyTransformAsync(transform, image, new OutputSettings(encoding));
  }

  /** {@inheritDoc} */
  @Override
  public Image applyTransform(Transform transform, Image image, OutputSettings settings) {
    return applyTransform(transform, image, new InputSettings(), settings);
  }

  /** {@inheritDoc} */
  @Override
  public Future<Image> applyTransformAsync(
      Transform transform, final Image image, OutputSettings settings) {
    return applyTransformAsync(transform, image, new InputSettings(), settings);
  }

  /** {@inheritDoc} */
  @Override
  public Image applyTransform(
      Transform transform,
      Image image,
      InputSettings inputSettings,
      OutputSettings outputSettings) {
    ImagesTransformRequest.Builder request =
      generateImagesTransformRequest(transform, image, inputSettings, outputSettings);

    ImagesTransformResponse.Builder response = ImagesTransformResponse.newBuilder();
    try {
      byte[] responseBytes = ApiProxy.makeSyncCall(PACKAGE, "Transform",
                                                   request.build().toByteArray());
      response.mergeFrom(responseBytes);
    } catch (InvalidProtocolBufferException ex) {
      throw new ImagesServiceFailureException("Invalid protocol buffer:", ex);
    } catch (ApiProxy.ApplicationException ex) {
      throw convertApplicationException(request, ex);
    }
    image.setImageData(response.getImage().getContent().toByteArray());
    return image;
  }

  /** {@inheritDoc} */
  @Override
  public Future<Image> applyTransformAsync(
      Transform transform,
      final Image image,
      InputSettings inputSettings,
      OutputSettings outputSettings) {
    final ImagesTransformRequest.Builder request =
      generateImagesTransformRequest(transform, image, inputSettings, outputSettings);

    Future<byte[]> responseBytes = ApiProxy.makeAsyncCall(PACKAGE, "Transform",
        request.build().toByteArray());
    return new FutureWrapper<byte[], Image>(responseBytes){
      @Override
      protected Image wrap(byte @Nullable[] responseBytes) throws IOException {
        ImagesTransformResponse.Builder response =
          ImagesTransformResponse.newBuilder()
          .mergeFrom(responseBytes);

        image.setImageData(response.getImage().getContent().toByteArray());
        return image;
      }

      @Override
      protected Throwable convertException(Throwable cause) {
        if (cause instanceof ApiProxy.ApplicationException applicationException) {
          return convertApplicationException(request, applicationException);
        }
        return cause;
      }
    };
  }

  /** {@inheritDoc} */
  @Override
  public Image composite(Collection<Composite> composites, int width, int height, long color) {
    return composite(composites, width, height, color, OutputEncoding.PNG);
  }

  @Override
  public Image composite(
      Collection<Composite> composites,
      int width,
      int height,
      long color,
      OutputEncoding encoding) {
    return composite(composites, width, height, color, new OutputSettings(encoding));
  }

  /** {@inheritDoc} */
  @Override
  public Image composite(
      Collection<Composite> composites,
      int width,
      int height,
      long color,
      OutputSettings settings) {
    ImagesCompositeRequest.Builder request = ImagesCompositeRequest.newBuilder();
    ImagesCompositeResponse.Builder response = ImagesCompositeResponse.newBuilder();
    if (composites.size() > MAX_COMPOSITES_PER_REQUEST) {
      throw new IllegalArgumentException(
          "A maximum of " + MAX_COMPOSITES_PER_REQUEST
          + " composites can be applied in a single request");
    }
    if (width > MAX_RESIZE_DIMENSIONS || width <= 0
        || height > MAX_RESIZE_DIMENSIONS || height <= 0) {
      throw new IllegalArgumentException(
          "Width and height must <= " + MAX_RESIZE_DIMENSIONS + " and > 0");
    }
    if (color > 0xffffffffL || color < 0L) {
      throw new IllegalArgumentException(
          "Color must be in the range [0, 0xffffffff]");
    }
    // Convert from unsigned color to a signed int.
    if (color >= 0x80000000) {
      color -= 0x100000000L;
    }
    int fixedColor = (int) color;
    ImagesServicePb.ImagesCanvas.Builder canvas = ImagesServicePb.ImagesCanvas.newBuilder();
    canvas.setWidth(width);
    canvas.setHeight(height);
    canvas.setColor(fixedColor);
    canvas.setOutput(convertOutputSettings(settings));
    request.setCanvas(canvas);

    Map<Image, Integer> imageIdMap = new HashMap<Image, Integer>();
    for (Composite composite : composites) {
      composite.apply(request, imageIdMap);
    }

    try {
      byte[] responseBytes = ApiProxy.makeSyncCall(PACKAGE, "Composite",
                                                   request.build().toByteArray());
      response.mergeFrom(responseBytes);
    } catch (InvalidProtocolBufferException ex) {
      throw new ImagesServiceFailureException("Invalid protocol buffer:", ex);
    } catch (ApiProxy.ApplicationException ex) {
      ErrorCode code = ErrorCode.forNumber(ex.getApplicationError());
      if (code != null && code != ErrorCode.UNSPECIFIED_ERROR) {
        throw new IllegalArgumentException(ex.getErrorDetail());
      } else {
        throw new ImagesServiceFailureException(ex.getErrorDetail());
      }

    }
    return ImagesServiceFactory.makeImage(response.getImage().getContent().toByteArray());
  }

  /** {@inheritDoc} */
  @Override
  public int[][] histogram(Image image) {
    ImagesHistogramRequest.Builder request = ImagesHistogramRequest.newBuilder();
    ImagesHistogramResponse.Builder response = ImagesHistogramResponse.newBuilder();
    request.setImage(convertImageData(image));
    try {
      byte[] responseBytes = ApiProxy.makeSyncCall(PACKAGE, "Histogram",
                                                   request.build().toByteArray());
      response.mergeFrom(responseBytes);
    } catch (InvalidProtocolBufferException ex) {
      throw new ImagesServiceFailureException("Invalid protocol buffer:", ex);
    } catch (ApiProxy.ApplicationException ex) {
      ErrorCode code = ErrorCode.forNumber(ex.getApplicationError());
      if (code != null && code != ErrorCode.UNSPECIFIED_ERROR) {
        throw new IllegalArgumentException(ex.getErrorDetail());
      } else {
        throw new ImagesServiceFailureException(ex.getErrorDetail());
      }
    }
    ImagesHistogram histogram = response.getHistogram();
    int[][] result = new int[3][];
    for (int i = 0; i < 3; i++) {
      result[i] = new int[256];
    }
    for (int i = 0; i < 256; i++) {
      result[0][i] = histogram.getRed(i);
      result[1][i] = histogram.getGreen(i);
      result[2][i] = histogram.getBlue(i);
    }
    return result;
  }

  /** {@inheritDoc} */
  public String getServingUrl(BlobKey blobKey) {
    return getServingUrl(blobKey, false);
  }

  /** {@inheritDoc} */
  public String getServingUrl(BlobKey blobKey, boolean secureUrl) {
    // The following check maintains the pre-existing contract for this method.
    if (blobKey == null) {
      throw new NullPointerException("blobKey cannot be null");
    }
    ServingUrlOptions options = ServingUrlOptions.Builder.withBlobKey(blobKey)
        .secureUrl(secureUrl);
    return getServingUrl(options);
  }

  /** {@inheritDoc} */
  @Override
  public String getServingUrl(BlobKey blobKey, int imageSize, boolean crop) {
    return getServingUrl(blobKey, imageSize, crop, false);
  }

  /** {@inheritDoc} */
  @Override
  public String getServingUrl(BlobKey blobKey, int imageSize, boolean crop, boolean secureUrl) {
    // The following check maintains the pre-existing contract for this method.
    if (blobKey == null) {
      throw new NullPointerException("blobKey cannot be null");
    }
    ServingUrlOptions options = ServingUrlOptions.Builder.withBlobKey(blobKey)
        .imageSize(imageSize)
        .crop(crop)
        .secureUrl(secureUrl);

    return getServingUrl(options);
  }

  @Override
  public String getServingUrl(ServingUrlOptions options) {
    ImagesGetUrlBaseRequest.Builder request = ImagesGetUrlBaseRequest.newBuilder();
    ImagesGetUrlBaseResponse.Builder response = ImagesGetUrlBaseResponse.newBuilder();

    if (!options.hasBlobKey() && !options.hasGoogleStorageFileName()) {
      throw new IllegalArgumentException(
          "Must specify either a BlobKey or a Google Storage file name.");
    }
    if (options.hasBlobKey()) {
      request.setBlobKey(options.getBlobKey().getKeyString());
    }
    if (options.hasGoogleStorageFileName()) {
      BlobKey blobKey = BlobstoreServiceFactory.getBlobstoreService().createGsBlobKey(
          options.getGoogleStorageFileName());
      request.setBlobKey(blobKey.getKeyString());
    }
    if (options.hasSecureUrl()) {
      request.setCreateSecureUrl(options.getSecureUrl());
    }
    try {
      byte[] responseBytes = ApiProxy.makeSyncCall(PACKAGE, "GetUrlBase",
                                                   request.build().toByteArray());
      response.mergeFrom(responseBytes);
    } catch (InvalidProtocolBufferException ex) {
      throw new ImagesServiceFailureException("Invalid protocol buffer:", ex);
    } catch (ApiProxy.ApplicationException ex) {
      ErrorCode code = ErrorCode.forNumber(ex.getApplicationError());
      if (code != null && code != ErrorCode.UNSPECIFIED_ERROR) {
        throw new IllegalArgumentException(code + ": " + ex.getErrorDetail());
      } else {
        throw new ImagesServiceFailureException(ex.getErrorDetail());
      }
    }
    StringBuilder url = new StringBuilder(response.getUrl());

    if (options.hasImageSize()) {
      url.append("=s");
      url.append(options.getImageSize());
      if (options.hasCrop() && options.getCrop()) {
        url.append("-c");
      }
    }
    return url.toString();
  }

  /** {@inheritDoc} */
  @Override
  public void deleteServingUrl(BlobKey blobKey) {
    ImagesDeleteUrlBaseRequest.Builder request = ImagesDeleteUrlBaseRequest.newBuilder();
    ImagesDeleteUrlBaseResponse.Builder response = ImagesDeleteUrlBaseResponse.newBuilder();
    if (blobKey == null) {
      throw new NullPointerException();
    }
    request.setBlobKey(blobKey.getKeyString());
    try {
      byte[] responseBytes = ApiProxy.makeSyncCall(PACKAGE, "DeleteUrlBase",
                                                   request.build().toByteArray());
      response.mergeFrom(responseBytes);
    } catch (InvalidProtocolBufferException ex) {
      throw new ImagesServiceFailureException("Invalid protocol buffer:", ex);
    } catch (ApiProxy.ApplicationException ex) {
      ErrorCode code = ErrorCode.forNumber(ex.getApplicationError());
      if (code != null && code != ErrorCode.UNSPECIFIED_ERROR) {
        throw new IllegalArgumentException(ex.getErrorDetail());
      } else {
        throw new ImagesServiceFailureException(ex.getErrorDetail());
      }
    }
  }

  static ImageData convertImageData(Image image) {
    ImageData.Builder builder = ImageData.newBuilder();
    BlobKey blobKey = image.getBlobKey();
    if (blobKey != null) {
      builder.setBlobKey(blobKey.getKeyString());
      builder.setContent(ByteString.EMPTY);
    } else {
      byte[] data = requireNonNull(image.getImageData());
      builder.setContent(ByteString.copyFrom(data));
    }
    return builder.build();
  }

  private ImagesTransformRequest.Builder generateImagesTransformRequest(
      Transform transform,
      Image image,
      InputSettings inputSettings,
      OutputSettings outputSettings) {
    ImagesTransformRequest.Builder request =
      ImagesTransformRequest.newBuilder()
      .setImage(convertImageData(image))
      .setOutput(convertOutputSettings(outputSettings))
      .setInput(convertInputSettings(inputSettings));
    transform.apply(request);

    if (request.getTransformCount() > MAX_TRANSFORMS_PER_REQUEST) {
      throw new IllegalArgumentException(
          "A maximum of " + MAX_TRANSFORMS_PER_REQUEST + " basic transforms "
          + "can be requested in a single transform request");
    }
    return request;
  }

  private ImagesServicePb.OutputSettings convertOutputSettings(OutputSettings settings) {
    ImagesServicePb.OutputSettings.Builder pbSettings =
        ImagesServicePb.OutputSettings.newBuilder();
    switch (settings.getOutputEncoding()) {
      case PNG -> pbSettings.setMimeType(MIME_TYPE.PNG);
      case JPEG -> {
        pbSettings.setMimeType(MIME_TYPE.JPEG);
        if (settings.hasQuality()) {
          pbSettings.setQuality(settings.getQuality());
        }
      }
      case WEBP -> {
        pbSettings.setMimeType(MIME_TYPE.WEBP);
        if (settings.hasQuality()) {
          pbSettings.setQuality(settings.getQuality());
        }
      }
      default -> throw new IllegalArgumentException("Invalid output encoding requested");
    }
    return pbSettings.build();
  }

  private ImagesServicePb.InputSettings convertInputSettings(InputSettings settings) {
    ImagesServicePb.InputSettings.Builder pbSettings = ImagesServicePb.InputSettings.newBuilder();
    switch (settings.getOrientationCorrection()) {
      case UNCHANGED_ORIENTATION ->
          pbSettings.setCorrectExifOrientation(ORIENTATION_CORRECTION_TYPE.UNCHANGED_ORIENTATION);
      case CORRECT_ORIENTATION ->
          pbSettings.setCorrectExifOrientation(ORIENTATION_CORRECTION_TYPE.CORRECT_ORIENTATION);
    }
    return pbSettings.build();
  }

  private RuntimeException convertApplicationException(ImagesTransformRequest.Builder request,
      ApiProxy.ApplicationException ex) {
    ErrorCode errorCode = ErrorCode.forNumber(ex.getApplicationError());
    if (errorCode != null && errorCode != ErrorCode.UNSPECIFIED_ERROR) {
      return new IllegalArgumentException(ex.getErrorDetail());
    } else {
      return new ImagesServiceFailureException(ex.getErrorDetail());
    }
  }
}
