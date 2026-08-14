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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.appengine.api.EnvironmentProvider;
import com.google.appengine.api.SystemEnvironmentProvider;
import com.google.appengine.api.blobstore.BlobKey;
import com.google.appengine.api.blobstore.BlobstoreServiceFactory;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import java.util.Collection;

/**
 * Factory for creating an {@link ImagesService}, {@link Image}s and
 * {@link Transform}s.
 *
 */
final class ImagesServiceFactoryImpl implements IImagesServiceFactory {

  static final String USE_CUSTOM_IMAGES_GRPC_SERVICE_ENV =
      "APPENGINE_USE_CUSTOM_IMAGES_GRPC_SERVICE";

  static final String IMAGES_SERVICE_ENDPOINT_ENV = "APPENGINE_IMAGES_SERVICE_ENDPOINT";

  private EnvironmentProvider environmentProvider = new SystemEnvironmentProvider();

  private static final Supplier<GrpcImagesClient> grpcClientSupplier =
      Suppliers.memoize(() -> new GrpcImagesClient());

  @VisibleForTesting
  void setEnvironmentProvider(EnvironmentProvider environmentProvider) {
    this.environmentProvider = environmentProvider;
  }

  @Override
  public ImagesService getImagesService() {
    GrpcImagesClient client = null;
    if (Boolean.parseBoolean(environmentProvider.getenv(USE_CUSTOM_IMAGES_GRPC_SERVICE_ENV))) {
      client = grpcClientSupplier.get();
    }
    return new ImagesServiceImpl(environmentProvider, client);
  }

  @Override
  public Image makeImage(byte[] imageData) {
    return new ImageImpl(imageData);
  }

  @Override
  public Image makeImageFromBlob(BlobKey blobKey) {
    return new ImageImpl(blobKey);
  }

  @Override
  public Image makeImageFromFilename(String filename) {
    if (Boolean.parseBoolean(environmentProvider.getenv(USE_CUSTOM_IMAGES_GRPC_SERVICE_ENV))) {
      checkArgument(
          filename.startsWith("/gs/"), "Google storage filenames must be prefixed with /gs/");
      return new ImageImpl(new BlobKey(filename));
    }
    BlobKey blobKey = BlobstoreServiceFactory.getBlobstoreService().createGsBlobKey(filename);
    return new ImageImpl(blobKey);
  }

  @Override
  public Transform makeResize(int width, int height) {
    return new Resize(width, height, false, 0.0f, 0.0f);
  }

  @Override
  public Transform makeResize(int width, int height, boolean allowStretch) {
    return new Resize(width, height, allowStretch);
  }


  @Override
  public Transform makeResize(int width, int height, float cropOffsetX, float cropOffsetY) {
    return new Resize(width, height, true, cropOffsetX, cropOffsetY);
  }

  @Override
  public Transform makeResize(int width, int height, double cropOffsetX,
                                     double cropOffsetY) {
    return new Resize(width, height, true, (float) cropOffsetX, (float) cropOffsetY);
  }

  @Override
  public Transform makeCrop(float leftX, float topY, float rightX,
                                   float bottomY) {
    return new Crop(leftX, topY, rightX, bottomY);
  }

  @Override
  public Transform makeCrop(double leftX, double topY,
                                   double rightX, double bottomY) {
    return makeCrop((float) leftX, (float) topY, (float) rightX, (float) bottomY);
  }

  @Override
  public Transform makeVerticalFlip() {
    return new VerticalFlip();
  }

  @Override
  public Transform makeHorizontalFlip() {
    return new HorizontalFlip();
  }

  @Override
  public Transform makeRotate(int degrees) {
    return new Rotate(degrees);
  }

  @Override
  public Transform makeImFeelingLucky() {
    return new ImFeelingLucky();
  }

  @Override
  public CompositeTransform makeCompositeTransform(
      Collection<Transform> transforms) {
    return new CompositeTransform(transforms);
  }

  @Override
  public CompositeTransform makeCompositeTransform() {
    return new CompositeTransform();
  }

  @Override
  public Composite makeComposite(Image image, int xOffset, int yOffset,
                                        float opacity,
                                        Composite.Anchor anchor) {
    return new CompositeImpl(image, xOffset, yOffset, opacity, anchor);
  }

}
