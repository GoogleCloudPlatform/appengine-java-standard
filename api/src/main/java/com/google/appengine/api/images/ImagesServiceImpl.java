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

import com.google.appengine.api.EnvironmentProvider;
import com.google.appengine.api.SystemEnvironmentProvider;
import com.google.appengine.api.blobstore.BlobInfo;
import com.google.appengine.api.blobstore.BlobInfoFactory;
import com.google.appengine.api.blobstore.BlobKey;
import com.google.appengine.api.blobstore.BlobstoreFailureException;
import com.google.appengine.api.blobstore.BlobstoreInputStream;
import com.google.appengine.api.blobstore.BlobstoreService;
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
import com.google.appengine.api.images.proto.ImagesServiceGrpc.ImagesServiceBlockingStub;
import com.google.appengine.api.utils.FutureWrapper;
import com.google.apphosting.api.ApiProxy;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.ByteString;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.InvalidProtocolBufferException;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.logging.Logger;
import org.jspecify.annotations.Nullable;

/** Implementation of the ImagesService interface. */
final class ImagesServiceImpl implements ImagesService {
  private static final Logger logger = Logger.getLogger(ImagesServiceImpl.class.getName());

  static final String PACKAGE = "images";
  private static final int MAX_BLOB_SIZE = 32 * 1024 * 1024; // 32MB

  private final EnvironmentProvider environmentProvider;
  private volatile GrpcImagesClient grpcClient;


  private final BlobstoreService blobstoreService;
  private final BlobstoreReference blobstoreReference;
  private final BlobInfoFactory blobInfoFactory;
  private final @Nullable Storage storage;

  interface BlobstoreReference {
    InputStream openStream(BlobKey key) throws IOException;
  }

  public ImagesServiceImpl() {
    this(new SystemEnvironmentProvider(), null, null, null, null);
  }

  @VisibleForTesting
  ImagesServiceImpl(EnvironmentProvider environmentProvider, GrpcImagesClient grpcClient) {
    this(environmentProvider, grpcClient, null, null, null);
  }

  @VisibleForTesting
  ImagesServiceImpl(
      EnvironmentProvider environmentProvider,
      GrpcImagesClient grpcClient,
      BlobstoreService blobstoreService) {
    this(environmentProvider, grpcClient, blobstoreService, null, null);
  }

  @VisibleForTesting
  ImagesServiceImpl(
      EnvironmentProvider environmentProvider,
      GrpcImagesClient grpcClient,
      BlobstoreService blobstoreService,
      BlobstoreReference blobstoreReference) {
    this(environmentProvider, grpcClient, blobstoreService, blobstoreReference, null);
  }

  @VisibleForTesting
  ImagesServiceImpl(
      EnvironmentProvider environmentProvider,
      GrpcImagesClient grpcClient,
      BlobstoreService blobstoreService,
      BlobstoreReference blobstoreReference,
      Storage storage) {
    this(environmentProvider, grpcClient, blobstoreService, blobstoreReference, storage, null);
  }

  @VisibleForTesting
  ImagesServiceImpl(
      EnvironmentProvider environmentProvider,
      GrpcImagesClient grpcClient,
      BlobstoreService blobstoreService,
      BlobstoreReference blobstoreReference,
      Storage storage,
      BlobInfoFactory blobInfoFactory) {
    this.environmentProvider = environmentProvider;
    this.grpcClient = grpcClient;
    this.blobstoreService = blobstoreService;
    this.storage = storage;
    this.blobInfoFactory = blobInfoFactory != null ? blobInfoFactory : new BlobInfoFactory();
    this.blobstoreReference =
        blobstoreReference != null
            ? blobstoreReference
            : new BlobstoreReference() {
              @Override
              public InputStream openStream(BlobKey key) throws IOException {
                return new BlobstoreInputStream(key);
              }
            };
  }

  //  A package-private method to get the GrpcImagesClient instance, visible for testing.
  @VisibleForTesting
  GrpcImagesClient getGrpcClient() {
    if (grpcClient == null) {
      synchronized (this) {
        if (grpcClient == null) {
          grpcClient = new GrpcImagesClient();
        }
      }
    }
    return grpcClient;
  }

  private ImagesServiceBlockingStub getGrpcStub() {
    return getGrpcClient().getBlockingStub();
  }

  @VisibleForTesting
  boolean useGrpc() {
    String envVar = environmentProvider.getenv("USE_CUSTOM_IMAGES_GRPC_SERVICE");
    return Boolean.parseBoolean(envVar);
  }

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
    if (useGrpc()) {
      try {
        ImageData imageData = loadImageData(image, getBlobstoreService());
        ImagesTransformRequest request =
            generateImagesTransformRequest(transform, imageData, inputSettings, outputSettings)
                .build();
        ImagesTransformResponse response = getGrpcStub().transform(request);
        image.setImageData(response.getImage().getContent().toByteArray());
        return image;
      } catch (StatusRuntimeException e) {
        throw convertGrpcException(e);
      }
    }
    ImagesTransformRequest.Builder request =
        generateImagesTransformRequest(
            transform, convertImageData(image), inputSettings, outputSettings);

    ImagesTransformResponse.Builder response = ImagesTransformResponse.newBuilder();
    try {
      byte[] responseBytes =
          ApiProxy.makeSyncCall(PACKAGE, "Transform", request.build().toByteArray());
      response.mergeFrom(responseBytes, ExtensionRegistry.getEmptyRegistry());
    } catch (InvalidProtocolBufferException ex) {
      throw new ImagesServiceFailureException("Invalid protocol buffer:", ex);
    } catch (ApiProxy.ApplicationException ex) {
      throw convertApplicationException(ex);
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
    if (useGrpc()) {
      ImageData imageData = loadImageData(image, getBlobstoreService());
      ImagesTransformRequest request =
          generateImagesTransformRequest(transform, imageData, inputSettings, outputSettings)
              .build();
        ListenableFuture<ImagesTransformResponse> responseFuture =
            getGrpcClient().getFutureStub().transform(request);

        return new FutureWrapper<ImagesTransformResponse, Image>(responseFuture) {
          @Override
          protected Image wrap(ImagesTransformResponse response) {
            image.setImageData(response.getImage().getContent().toByteArray());
            return image;
          }

          @Override
          protected Throwable convertException(Throwable cause) {
            if (cause instanceof StatusRuntimeException statusRuntimeException) {
              return convertGrpcException(statusRuntimeException);
            }
            return cause;
          }
        };
    }
    final ImagesTransformRequest.Builder request =
        generateImagesTransformRequest(
            transform, convertImageData(image), inputSettings, outputSettings);

    Future<byte[]> responseBytes =
        ApiProxy.makeAsyncCall(PACKAGE, "Transform", request.build().toByteArray());
    return new FutureWrapper<byte[], Image>(responseBytes) {
      @Override
      protected Image wrap(byte @Nullable [] responseBytes) throws IOException {
        ImagesTransformResponse.Builder response =
            ImagesTransformResponse.newBuilder()
                .mergeFrom(responseBytes, ExtensionRegistry.getEmptyRegistry());

        image.setImageData(response.getImage().getContent().toByteArray());
        return image;
      }

      @Override
      protected Throwable convertException(Throwable cause) {
        if (cause instanceof ApiProxy.ApplicationException applicationException) {
          return convertApplicationException(applicationException);
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

    if (useGrpc()) {
      Map<Image, Integer> imageIdMap = new HashMap<>();
      BlobstoreService blobstoreService = getBlobstoreService();
      for (Composite composite : composites) {
        composite.apply(request, imageIdMap, img -> loadImageData(img, blobstoreService));
      }
      try {
        ImagesCompositeResponse grpcResponse = getGrpcStub().composite(request.build());
        return ImagesServiceFactory.makeImage(grpcResponse.getImage().getContent().toByteArray());
      } catch (StatusRuntimeException e) {
        throw convertGrpcException(e);
      }
    }

    Map<Image, Integer> imageIdMap = new HashMap<>();
    for (Composite composite : composites) {
      composite.apply(request, imageIdMap, ImagesServiceImpl::convertImageData);
    }

    try {
      byte[] responseBytes = ApiProxy.makeSyncCall(PACKAGE, "Composite",
                                                   request.build().toByteArray());
      response.mergeFrom(responseBytes, ExtensionRegistry.getEmptyRegistry());
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
    if (useGrpc()) {
      ImagesHistogramRequest.Builder request =
          ImagesHistogramRequest.newBuilder()
              .setImage(loadImageData(image, BlobstoreServiceFactory.getBlobstoreService()));
      ImagesHistogramResponse response;
      try {
        response = getGrpcStub().histogram(request.build());
      } catch (StatusRuntimeException e) {
        throw convertGrpcException(e);
      }
      ImagesHistogram histogram = response.getHistogram();
      int[][] result = new int[3][];
      for (int i = 0; i < 3; i++) {
        result[i] = new int[256];
      }
      int redCount = Math.min(256, histogram.getRedCount());
      int greenCount = Math.min(256, histogram.getGreenCount());
      int blueCount = Math.min(256, histogram.getBlueCount());

      for (int i = 0; i < redCount; i++) {
        result[0][i] = histogram.getRed(i);
      }
      for (int i = 0; i < greenCount; i++) {
        result[1][i] = histogram.getGreen(i);
      }
      for (int i = 0; i < blueCount; i++) {
        result[2][i] = histogram.getBlue(i);
      }
      return result;
    }
    ImagesHistogramRequest.Builder request = ImagesHistogramRequest.newBuilder();
    ImagesHistogramResponse.Builder response = ImagesHistogramResponse.newBuilder();
    request.setImage(convertImageData(image));
    try {
      byte[] responseBytes = ApiProxy.makeSyncCall(PACKAGE, "Histogram",
                                                   request.build().toByteArray());
      response.mergeFrom(responseBytes, ExtensionRegistry.getEmptyRegistry());
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
    if (useGrpc()) {
      throw new UnsupportedOperationException(
          "getServingUrl is not supported when using the gRPC Images Service.");
    }
    return getServingUrl(blobKey, false);
  }

  /** {@inheritDoc} */
  public String getServingUrl(BlobKey blobKey, boolean secureUrl) {
    if (useGrpc()) {
      throw new UnsupportedOperationException(
          "getServingUrl is not supported when using the gRPC Images Service.");
    }
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
    if (useGrpc()) {
      throw new UnsupportedOperationException(
          "getServingUrl is not supported when using the gRPC Images Service.");
    }
    return getServingUrl(blobKey, imageSize, crop, false);
  }

  /** {@inheritDoc} */
  @Override
  public String getServingUrl(BlobKey blobKey, int imageSize, boolean crop, boolean secureUrl) {
    if (useGrpc()) {
      throw new UnsupportedOperationException(
          "getServingUrl is not supported when using the gRPC Images Service.");
    }
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
    if (useGrpc()) {
      throw new UnsupportedOperationException(
          "getServingUrl is not supported when using the gRPC Images Service.");
    }
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
      response.mergeFrom(responseBytes, ExtensionRegistry.getEmptyRegistry());
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
    if (useGrpc()) {
      throw new UnsupportedOperationException(
          "deleteServingUrl is not supported when using the gRPC Images Service.");
    }
    ImagesDeleteUrlBaseRequest.Builder request = ImagesDeleteUrlBaseRequest.newBuilder();
    if (blobKey == null) {
      throw new NullPointerException();
    }
    request.setBlobKey(blobKey.getKeyString());
    try {
      byte[] responseBytes = ApiProxy.makeSyncCall(PACKAGE, "DeleteUrlBase",
                                                   request.build().toByteArray());
      ImagesDeleteUrlBaseResponse unused = ImagesDeleteUrlBaseResponse.parseFrom(responseBytes, ExtensionRegistry.getEmptyRegistry());
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

  @VisibleForTesting
  ImageData loadImageData(Image image, BlobstoreService blobstoreService) {
    logger.info("loadImageData image: " + image);
    BlobKey blobKey = image.getBlobKey();
    logger.info("blobKey: " + blobKey);
    if (blobKey != null) {
      String keyString = blobKey.getKeyString();
      logger.info("keyString: " + keyString);
      if (keyString.startsWith("/gs/")) {
        return fetchGcsImageData(keyString);
      }

      // Check if BlobInfo has GCS object name
      BlobInfo blobInfo = blobInfoFactory.loadBlobInfo(blobKey);
      logger.info(
          "blobInfo: "
              + blobInfo
              + " GsObjectName: "
              + (blobInfo != null ? blobInfo.getGsObjectName() : "null"));
      if (blobInfo != null && blobInfo.getGsObjectName() != null) {
        String gsObjectName = blobInfo.getGsObjectName();
        logger.fine("Found GCS object name for BlobKey: " + gsObjectName);
        try {
          return fetchGcsImageData(gsObjectName);
        } catch (ImagesServiceFailureException e) {
          logger.fine(
              "Failed to fetch GCS blob data, falling back to BlobstoreInputStream: "
                  + e.getMessage());
        }
      }
      logger.fine("No GCS object name found for BlobKey, falling back to BlobstoreInputStream");

      try (InputStream inputStream = blobstoreReference.openStream(blobKey)) {
        return ImageData.newBuilder().setContent(ByteString.readFrom(inputStream)).build();
      } catch (IOException | BlobstoreFailureException e) {
        throw new ImagesServiceFailureException("Failed to fetch blob data", e);
      }
    }
    return convertImageData(image);
  }

  private ImageData fetchGcsImageData(String keyString) {
    try {
      return ImageData.newBuilder()
          .setContent(ByteString.copyFrom(fetchGcsContent(keyString)))
          .setBlobKey(keyString)
          .build();
    } catch (RuntimeException e) {
      throw new ImagesServiceFailureException("Failed to fetch GCS blob data", e);
    }
  }

  private RuntimeException convertGrpcException(StatusRuntimeException e) {
    Status.Code code = e.getStatus().getCode();
    if (code == Status.Code.UNAUTHENTICATED || code == Status.Code.PERMISSION_DENIED) {
      return new ImagesServiceFailureException("Authentication failed for Images Service", e);
    } else if (code == Status.Code.RESOURCE_EXHAUSTED) {
      return new ImagesServiceFailureException("Image too large", e);
    } else if (code == Status.Code.INVALID_ARGUMENT) {
      return new IllegalArgumentException(e.getStatus().getDescription(), e);
    } else {
      return new ImagesServiceFailureException(e.getStatus().getDescription(), e);
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
      ImageData imageData,
      InputSettings inputSettings,
      OutputSettings outputSettings) {
    ImagesTransformRequest.Builder request =
        ImagesTransformRequest.newBuilder()
            .setImage(imageData)
            .setOutput(convertOutputSettings(outputSettings))
            .setInput(convertInputSettings(inputSettings));
    transform.apply(request);

    if (request.getTransformCount() > MAX_TRANSFORMS_PER_REQUEST) {
      throw new IllegalArgumentException(
          "A maximum of "
              + MAX_TRANSFORMS_PER_REQUEST
              + " basic transforms "
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

  private RuntimeException convertApplicationException(ApiProxy.ApplicationException ex) {
    ErrorCode errorCode = ErrorCode.forNumber(ex.getApplicationError());
    if (errorCode != null && errorCode != ErrorCode.UNSPECIFIED_ERROR) {
      return new IllegalArgumentException(ex.getErrorDetail());
    } else {
      return new ImagesServiceFailureException(ex.getErrorDetail());
    }
  }

  private BlobstoreService getBlobstoreService() {
    return blobstoreService != null
        ? blobstoreService
        : BlobstoreServiceFactory.getBlobstoreService();
  }

  // We use double-checked locking here to allow lazy initialization and
  // mock injection in tests without triggering eager class loading of heavy
  // Storage clients which might fail in test environments.
  private static class StorageHolder {
    private static volatile Storage instance;

    static Storage get() {
      if (instance == null) {
        synchronized (StorageHolder.class) {
          if (instance == null) {
            instance = StorageOptions.getDefaultInstance().getService();
          }
        }
      }
      return instance;
    }

    @VisibleForTesting
    static void setForTesting(Storage storage) {
      instance = storage;
    }
  }

  Storage getStorage() {
    return storage != null ? storage : StorageHolder.get();
  }

  @VisibleForTesting
  static void setStorageForTesting(Storage storage) {
    StorageHolder.setForTesting(storage);
  }

  private byte[] fetchGcsContent(String keyString) {
    // keyString is like "/gs/bucket/object"
    // Remove "/gs/" prefix if present
    String path = keyString;
    if (path.startsWith("/gs/")) {
      path = path.substring(4);
    } else if (path.startsWith("/")) {
      path = path.substring(1);
    }
    int index = path.indexOf('/');
    if (index == -1) {
      throw new IllegalArgumentException("Invalid GCS key: " + keyString);
    }
    String bucket = path.substring(0, index);
    String object = path.substring(index + 1);

    Blob blob = getStorage().get(BlobId.of(bucket, object));
    if (blob == null) {
      throw new ImagesServiceFailureException("GCS blob not found: " + keyString);
    }
    if (blob.getSize() > MAX_BLOB_SIZE) {
      throw new ImagesServiceFailureException(
          "GCS blob is too large: " + blob.getSize() + " bytes");
    }
    byte[] content = blob.getContent();
    logger.info(
        "Successfully fetched GCS blob content for key: " + keyString + " size: " + content.length);
    return content;
  }
}
