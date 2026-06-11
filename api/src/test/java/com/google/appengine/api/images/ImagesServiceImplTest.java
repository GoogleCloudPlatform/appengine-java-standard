// Copyright 2024 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.appengine.api.images;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import java.util.concurrent.ExecutionException;

import com.google.appengine.api.EnvironmentProvider;
import com.google.appengine.api.blobstore.BlobInfo;
import com.google.appengine.api.blobstore.BlobInfoFactory;
import com.google.appengine.api.blobstore.BlobKey;
import com.google.appengine.api.blobstore.BlobstoreFailureException;
import com.google.appengine.api.blobstore.BlobstoreService;
import com.google.appengine.api.images.ImagesService.OutputEncoding;
import com.google.appengine.api.images.ImagesServicePb.ImageData;
import com.google.appengine.api.images.ImagesServicePb.ImagesHistogram;
import com.google.appengine.api.images.ImagesServicePb.ImagesHistogramRequest;
import com.google.appengine.api.images.ImagesServicePb.ImagesHistogramResponse;
import com.google.appengine.api.images.ImagesServicePb.ImagesTransformRequest;
import com.google.appengine.api.images.ImagesServicePb.ImagesTransformResponse;
import com.google.appengine.api.images.proto.ImagesServiceGrpc;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.GrpcCleanupRule;
import java.io.ByteArrayInputStream;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Future;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class ImagesServiceImplTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Rule public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

  @Mock private EnvironmentProvider mockEnvironmentProvider;
  @Mock private GrpcImagesClient mockGrpcImagesClient;
  @Mock private BlobstoreService mockBlobstoreService;
  @Mock private BlobInfoFactory mockBlobInfoFactory;
  @Mock private ImagesServiceImpl.BlobstoreReference mockBlobstoreReference;
  @Mock private Storage mockStorage;
  @Mock private Blob mockBlob;

  private ImagesServiceImpl imagesService;
  private ManagedChannel channel;
  private ImagesServiceGrpc.ImagesServiceBlockingStub blockingStub;
  private ImagesServiceGrpc.ImagesServiceFutureStub futureStub;

  // Mock implementation of the ImagesService.
  private final ImagesServiceGrpc.ImagesServiceImplBase serviceImpl =
      new ImagesServiceGrpc.ImagesServiceImplBase() {
        @Override
        public void transform(
            ImagesTransformRequest request,
            StreamObserver<ImagesTransformResponse> responseObserver) {
          // For now, just return a default response
          ImagesTransformResponse response =
              ImagesTransformResponse.newBuilder()
                  .setImage(
                      ImageData.newBuilder().setContent(ByteString.copyFromUtf8("transformed")))
                  .build();
          responseObserver.onNext(response);
          responseObserver.onCompleted();
        }

        @Override
        public void composite(
            ImagesServicePb.ImagesCompositeRequest request,
            StreamObserver<ImagesServicePb.ImagesCompositeResponse> responseObserver) {
          ImagesServicePb.ImagesCompositeResponse response =
              ImagesServicePb.ImagesCompositeResponse.newBuilder()
                  .setImage(
                      ImageData.newBuilder().setContent(ByteString.copyFromUtf8("composited")))
                  .build();
          responseObserver.onNext(response);
          responseObserver.onCompleted();
        }

        @Override
        public void histogram(
            ImagesHistogramRequest request,
            StreamObserver<ImagesHistogramResponse> responseObserver) {

          // Fill the rest with 0s to match 256 size if needed, but the loop in impl handles it.
          // Actually, proto repeated fields are just lists. The array copy loop assumes 256 size or
          // less?
          // "for (int i = 0; i < 256; i++) { result[0][i] = histogram.getRed(i); }"
          // If the list is shorter than 256, getRed(i) might throw IndexOutOfBoundsException or
          // return default?
          // Protobuf list access: getRedList().get(i).
          // Let's populate 256 values to be safe and correct.
          ImagesHistogram.Builder histogramBuilder = ImagesHistogram.newBuilder();
          for (int i = 0; i < 256; i++) {
            histogramBuilder.addRed(i).addGreen(i).addBlue(i);
          }

          ImagesHistogramResponse response =
              ImagesHistogramResponse.newBuilder().setHistogram(histogramBuilder).build();
          responseObserver.onNext(response);
          responseObserver.onCompleted();
        }
      };

  @Test
  public void histogram_grpcPath_success() throws Exception {
    setUpGrpc(true);
    Image image = ImagesServiceFactory.makeImage(new byte[] {1, 2, 3});

    int[][] result = imagesService.histogram(image);

    assertThat(result).hasLength(3);
    assertThat(result[0]).hasLength(256);
    assertThat(result[1]).hasLength(256);
    assertThat(result[2]).hasLength(256);

    // Check a few values based on mock
    assertThat(result[0][0]).isEqualTo(0);
    assertThat(result[0][255]).isEqualTo(255);
    assertThat(result[1][100]).isEqualTo(100);
    assertThat(result[2][50]).isEqualTo(50);
  }

  // Re-write to allow dynamic behavior of mock service
  private void setupGrpcService(ImagesServiceGrpc.ImagesServiceImplBase serviceImplementation)
      throws Exception {
    String serverName = InProcessServerBuilder.generateName();
    grpcCleanup.register(
        InProcessServerBuilder.forName(serverName)
            .directExecutor()
            .addService(serviceImplementation)
            .build()
            .start());
    channel =
        grpcCleanup.register(InProcessChannelBuilder.forName(serverName).directExecutor().build());
    blockingStub = ImagesServiceGrpc.newBlockingStub(channel);
    futureStub = ImagesServiceGrpc.newFutureStub(channel);
    when(mockGrpcImagesClient.getBlockingStub()).thenReturn(blockingStub);
    when(mockGrpcImagesClient.getFutureStub()).thenReturn(futureStub);
    imagesService =
        new ImagesServiceImpl(
            mockEnvironmentProvider, mockGrpcImagesClient, null, mockBlobstoreReference);

    when(mockEnvironmentProvider.getenv("USE_CUSTOM_IMAGES_GRPC_SERVICE")).thenReturn("true");
  }

  @Test
  public void applyTransformAsync_grpcPath_authError_test() throws Exception {
    ImagesServiceGrpc.ImagesServiceImplBase errorService =
        new ImagesServiceGrpc.ImagesServiceImplBase() {
          @Override
          public void transform(
              ImagesTransformRequest request,
              StreamObserver<ImagesTransformResponse> responseObserver) {
            responseObserver.onError(new StatusRuntimeException(Status.UNAUTHENTICATED));
          }
        };
    setupGrpcService(errorService);

    Image originalImage = ImagesServiceFactory.makeImage(new byte[] {1, 2, 3});
    Transform transform = new Crop(0, 0, 1, 1);
    OutputSettings settings = new OutputSettings(OutputEncoding.PNG);

    ExecutionException e =
        assertThrows(
            ExecutionException.class,
            () -> imagesService.applyTransformAsync(transform, originalImage, settings).get());
    assertThat(e).hasCauseThat().isInstanceOf(ImagesServiceFailureException.class);
    assertThat(e).hasCauseThat().hasMessageThat().contains("Authentication failed");
  }

  @Test
  public void applyTransformAsync_grpcPath_tooLargeError_test() throws Exception {
    ImagesServiceGrpc.ImagesServiceImplBase errorService =
        new ImagesServiceGrpc.ImagesServiceImplBase() {
          @Override
          public void transform(
              ImagesTransformRequest request,
              StreamObserver<ImagesTransformResponse> responseObserver) {
            responseObserver.onError(new StatusRuntimeException(Status.RESOURCE_EXHAUSTED));
          }
        };
    setupGrpcService(errorService);

    Image originalImage = ImagesServiceFactory.makeImage(new byte[] {1, 2, 3});
    Transform transform = new Crop(0, 0, 1, 1);
    OutputSettings settings = new OutputSettings(OutputEncoding.PNG);

    ExecutionException e =
        assertThrows(
            ExecutionException.class,
            () -> imagesService.applyTransformAsync(transform, originalImage, settings).get());
    assertThat(e).hasCauseThat().isInstanceOf(ImagesServiceFailureException.class);
    assertThat(e).hasCauseThat().hasMessageThat().contains("Image too large");
  }

  @Test
  public void applyTransformAsync_grpcPath_invalidArgument_test() throws Exception {
    ImagesServiceGrpc.ImagesServiceImplBase errorService =
        new ImagesServiceGrpc.ImagesServiceImplBase() {
          @Override
          public void transform(
              ImagesTransformRequest request,
              StreamObserver<ImagesTransformResponse> responseObserver) {
            responseObserver.onError(
                new StatusRuntimeException(Status.INVALID_ARGUMENT.withDescription("Bad arg")));
          }
        };
    setupGrpcService(errorService);

    Image originalImage = ImagesServiceFactory.makeImage(new byte[] {1, 2, 3});
    Transform transform = new Crop(0, 0, 1, 1);
    OutputSettings settings = new OutputSettings(OutputEncoding.PNG);

    ExecutionException e =
        assertThrows(
            ExecutionException.class,
            () -> imagesService.applyTransformAsync(transform, originalImage, settings).get());
    assertThat(e).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
    assertThat(e).hasCauseThat().hasMessageThat().contains("Bad arg");
  }

  @Test
  public void composite_grpcPath_success() throws Exception {
    setUpGrpc(true);
    Image image1 = ImagesServiceFactory.makeImage(new byte[] {1});
    Image image2 = ImagesServiceFactory.makeImage(new byte[] {2});
    Composite composite1 =
        ImagesServiceFactory.makeComposite(image1, 0, 0, 1.0f, Composite.Anchor.TOP_LEFT);
    Composite composite2 =
        ImagesServiceFactory.makeComposite(image2, 10, 10, 0.5f, Composite.Anchor.CENTER_CENTER);
    List<Composite> composites = Arrays.asList(composite1, composite2);
    OutputSettings settings = new OutputSettings(OutputEncoding.PNG);

    Image result = imagesService.composite(composites, 100, 100, 0L, settings);

    assertThat(result).isNotNull();
    assertThat(result.getImageData())
        .isEqualTo(ByteString.copyFromUtf8("composited").toByteArray());
  }

  @Test
  public void loadImageData_withImageContent() throws Exception {
    setUpGrpc(false);
    byte[] content = new byte[] {1, 2, 3};
    Image image = ImagesServiceFactory.makeImage(content);

    ImageData imageData = imagesService.loadImageData(image, mockBlobstoreService);

    assertThat(imageData.hasBlobKey()).isFalse();
    assertThat(imageData.getContent().toByteArray()).isEqualTo(content);
  }

  @Test
  public void loadImageData_withGsBlobKey() throws Exception {
    setUpGrpc(false);
    String gsKey = "/gs/bucket/object";
    BlobKey blobKey = new BlobKey(gsKey);
    Image image = ImagesServiceFactory.makeImageFromBlob(blobKey);
    byte[] content = new byte[] {1, 2, 3};

    when(mockStorage.get(BlobId.of("bucket", "object"))).thenReturn(mockBlob);
    when(mockBlob.getContent()).thenReturn(content);

    ImageData imageData = imagesService.loadImageData(image, mockBlobstoreService);

    assertThat(imageData.getBlobKey()).isEqualTo(gsKey);
    assertThat(imageData.getContent().toByteArray()).isEqualTo(content);
  }

  @Test
  public void loadImageData_withBlobInfoFallback_success() throws Exception {
    setUpGrpc(false);
    BlobKey blobKey = new BlobKey("some_opaque_key");
    Image image = ImagesServiceFactory.makeImageFromBlob(blobKey);
    BlobInfo blobInfo =
        new BlobInfo(
            blobKey, "type", Date.from(Instant.now()), "file", 0, "hash", "/gs/bucket/object");
    when(mockBlobInfoFactory.loadBlobInfo(blobKey)).thenReturn(blobInfo);
    when(mockStorage.get(BlobId.of("bucket", "object"))).thenReturn(mockBlob);
    when(mockBlob.getContent()).thenReturn("blobContent".getBytes(UTF_8));
    ImageData imageData = imagesService.loadImageData(image, mockBlobstoreService);
    assertThat(imageData.getBlobKey()).isEqualTo("/gs/bucket/object");
    assertThat(imageData.getContent().isEmpty()).isFalse();
    assertThat(imageData.getContent().toStringUtf8()).isEqualTo("blobContent");
  }

  @Test
  public void loadImageData_withLegacyBlobKey_success() throws Exception {
    setUpGrpc(false);

    BlobKey blobKey = new BlobKey("legacy-blob-key");
    byte[] fetchedData = new byte[] {4, 5, 6};

    when(mockBlobstoreReference.openStream(eq(blobKey)))
        .thenReturn(new ByteArrayInputStream(fetchedData));

    Image image = ImagesServiceFactory.makeImageFromBlob(blobKey);

    ImageData imageData = imagesService.loadImageData(image, mockBlobstoreService);

    assertThat(imageData.hasBlobKey()).isFalse();
    assertThat(imageData.getContent().toByteArray()).isEqualTo(fetchedData);
  }

  @Test
  public void loadImageData_withLegacyBlobKey_failure() throws Exception {
    setUpGrpc(false);
    String legacyKey = "legacy-blob-key";
    BlobKey blobKey = new BlobKey(legacyKey);
    Image image = ImagesServiceFactory.makeImageFromBlob(blobKey);

    when(mockBlobstoreReference.openStream(eq(blobKey)))
        .thenThrow(new BlobstoreFailureException("Fetch failed"));

    ImagesServiceFailureException e =
        assertThrows(
            ImagesServiceFailureException.class,
            () -> imagesService.loadImageData(image, mockBlobstoreService));
    assertThat(e).hasMessageThat().contains("Failed to fetch blob data");
    assertThat(e).hasCauseThat().isInstanceOf(BlobstoreFailureException.class);
  }

  public void setUpGrpc(boolean useGrpc) throws Exception {
    ImagesServiceImpl.setStorageForTesting(mockStorage);
    when(mockEnvironmentProvider.getenv("USE_CUSTOM_IMAGES_GRPC_SERVICE"))
        .thenReturn(Boolean.toString(useGrpc));

    if (useGrpc) {
      String serverName = InProcessServerBuilder.generateName();
      grpcCleanup.register(
          InProcessServerBuilder.forName(serverName)
              .directExecutor()
              .addService(serviceImpl)
              .build()
              .start());
      channel =
          grpcCleanup.register(
              InProcessChannelBuilder.forName(serverName).directExecutor().build());
      blockingStub = ImagesServiceGrpc.newBlockingStub(channel);
      futureStub = ImagesServiceGrpc.newFutureStub(channel);
      when(mockGrpcImagesClient.getBlockingStub()).thenReturn(blockingStub);
      when(mockGrpcImagesClient.getFutureStub()).thenReturn(futureStub);

      imagesService =
          new ImagesServiceImpl(
              mockEnvironmentProvider,
              mockGrpcImagesClient,
              mockBlobstoreService,
              mockBlobstoreReference,
              mockStorage,
              mockBlobInfoFactory);
    } else {
      imagesService =
          new ImagesServiceImpl(
              mockEnvironmentProvider,
              null,
              null,
              mockBlobstoreReference,
              mockStorage,
              mockBlobInfoFactory);
    }
  }

  @Test
  public void useGrpc_envVarSetTrue_returnsTrue() throws Exception {
    setUpGrpc(true);
    assertThat(imagesService.useGrpc()).isTrue();
  }

  @Test
  public void useGrpc_envVarSetFalse_returnsFalse() throws Exception {
    setUpGrpc(false);
    assertThat(imagesService.useGrpc()).isFalse();
  }

  @Test
  public void useGrpc_envVarNotSet_returnsFalse() {
    when(mockEnvironmentProvider.getenv("USE_CUSTOM_IMAGES_GRPC_SERVICE")).thenReturn(null);
    imagesService =
        new ImagesServiceImpl(
            mockEnvironmentProvider, null, null, mockBlobstoreReference, null, mockBlobInfoFactory);
    assertThat(imagesService.useGrpc()).isFalse();
  }

  @Test
  public void useGrpc_envVarInvalid_returnsFalse() {
    when(mockEnvironmentProvider.getenv("USE_CUSTOM_IMAGES_GRPC_SERVICE")).thenReturn("yes");
    imagesService =
        new ImagesServiceImpl(
            mockEnvironmentProvider, null, null, mockBlobstoreReference, null, mockBlobInfoFactory);
    assertThat(imagesService.useGrpc()).isFalse();
  }

  @Test
  public void applyTransformAsync_grpcPath_success() throws Exception {
    setUpGrpc(true);
    Image originalImage = ImagesServiceFactory.makeImage(new byte[] {1, 2, 3});
    Transform transform = new Crop(0, 0, 1, 1);
    OutputSettings settings = new OutputSettings(OutputEncoding.PNG);

    Future<Image> future = imagesService.applyTransformAsync(transform, originalImage, settings);
    Image transformedImage = future.get();

    assertThat(transformedImage).isNotNull();
    assertThat(transformedImage.getImageData())
        .isEqualTo(ByteString.copyFromUtf8("transformed").toByteArray());
  }

  @Test
  public void applyTransformAsync_grpcPath_withLegacyBlobKey_success() throws Exception {
    setUpGrpc(true);
    String legacyKey = "legacy-blob-key";
    BlobKey blobKey = new BlobKey(legacyKey);
    Image originalImage = ImagesServiceFactory.makeImageFromBlob(blobKey);
    Transform transform = new Crop(0, 0, 1, 1);
    OutputSettings settings = new OutputSettings(OutputEncoding.PNG);
    byte[] fetchedData = new byte[] {4, 5, 6};

    when(mockBlobstoreReference.openStream(eq(blobKey)))
        .thenReturn(new ByteArrayInputStream(fetchedData));

    Future<Image> future = imagesService.applyTransformAsync(transform, originalImage, settings);
    Image transformedImage = future.get();

    assertThat(transformedImage).isNotNull();
    assertThat(transformedImage.getImageData())
        .isEqualTo(ByteString.copyFromUtf8("transformed").toByteArray());
  }

  @Test
  // Suppressing deprecation warnings as we are testing deprecated methods or options,
  // and UnnecessaryJavacSuppressWarnings to avoid warnings in different compiler versions.
  @SuppressWarnings({"deprecation", "UnnecessaryJavacSuppressWarnings"})
  public void getServingUrl_grpcPath_throwsUnsupportedOperationException() throws Exception {
    setUpGrpc(true);
    // Use a valid blob key format to avoid other validations if they were to run (though they
    // shouldn't)
    String gsKey = "/gs/bucket/object";
    BlobKey blobKey = new BlobKey(gsKey);

    // Test getServingUrl(BlobKey)
    UnsupportedOperationException e1 =
        assertThrows(
            UnsupportedOperationException.class, () -> imagesService.getServingUrl(blobKey));
    assertThat(e1).hasMessageThat().contains("getServingUrl is not supported");

    // Test getServingUrl(BlobKey, boolean)
    UnsupportedOperationException e2 =
        assertThrows(
            UnsupportedOperationException.class, () -> imagesService.getServingUrl(blobKey, true));
    assertThat(e2).hasMessageThat().contains("getServingUrl is not supported");

    // Test getServingUrl(BlobKey, int, boolean)
    UnsupportedOperationException e3 =
        assertThrows(
            UnsupportedOperationException.class,
            () -> imagesService.getServingUrl(blobKey, 32, true));
    assertThat(e3).hasMessageThat().contains("getServingUrl is not supported");

    // Test getServingUrl(BlobKey, int, boolean, boolean)
    UnsupportedOperationException e4 =
        assertThrows(
            UnsupportedOperationException.class,
            () -> imagesService.getServingUrl(blobKey, 32, true, true));
    assertThat(e4).hasMessageThat().contains("getServingUrl is not supported");

    // Test getServingUrl(ServingUrlOptions)
    ServingUrlOptions options = ServingUrlOptions.Builder.withBlobKey(blobKey);
    UnsupportedOperationException e5 =
        assertThrows(
            UnsupportedOperationException.class, () -> imagesService.getServingUrl(options));
    assertThat(e5).hasMessageThat().contains("getServingUrl is not supported");
  }

  @Test
  public void deleteServingUrl_grpcPath_throwsUnsupportedOperationException() throws Exception {
    setUpGrpc(true);
    BlobKey blobKey = new BlobKey("blob-key");

    UnsupportedOperationException e =
        assertThrows(
            UnsupportedOperationException.class, () -> imagesService.deleteServingUrl(blobKey));
    assertThat(e).hasMessageThat().contains("deleteServingUrl is not supported");
  }
}
