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

package com.google.appengine.apicompat.usage;

import static com.google.appengine.apicompat.Utils.classes;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.appengine.api.blobstore.BlobKey;
import com.google.appengine.api.images.Composite;
import com.google.appengine.api.images.CompositeTransform;
import com.google.appengine.api.images.IImagesServiceFactory;
import com.google.appengine.api.images.IImagesServiceFactoryProvider;
import com.google.appengine.api.images.Image;
import com.google.appengine.api.images.ImagesService;
import com.google.appengine.api.images.ImagesServiceFactory;
import com.google.appengine.api.images.ImagesServiceFailureException;
import com.google.appengine.api.images.InputSettings;
import com.google.appengine.api.images.OutputSettings;
import com.google.appengine.api.images.ServingUrlOptions;
import com.google.appengine.api.images.Transform;
import com.google.appengine.apicompat.ExhaustiveApiInterfaceUsage;
import com.google.appengine.apicompat.ExhaustiveApiUsage;
import com.google.appengine.spi.FactoryProvider;
import com.google.appengine.tools.development.testing.LocalBlobstoreServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import java.io.Serializable;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.Future;

/** Exhaustive usage of the Images Api. Used for backward compatibility checks. */
public class ImagesApiUsage {

  /**
   * Exhaustive use of {@link ImagesServiceFactory}.
   */
  public static class ImagesServiceFactoryUsage extends ExhaustiveApiUsage<ImagesServiceFactory> {

    @Override
    public Set<Class<?>> useApi() {
      LocalServiceTestHelper helper =
          new LocalServiceTestHelper(new LocalBlobstoreServiceTestConfig());
      helper.setUp();
      try {
        ImagesService svc = ImagesServiceFactory.getImagesService();
        Image img = ImagesServiceFactory.makeImage("not an image".getBytes(UTF_8));
        Composite comp =
            ImagesServiceFactory.makeComposite(img, 0, 10, .5f, Composite.Anchor.BOTTOM_CENTER);
        CompositeTransform compTransform = ImagesServiceFactory.makeCompositeTransform();
        compTransform =
            ImagesServiceFactory.makeCompositeTransform(Collections.<Transform>emptyList());
        Transform transform = ImagesServiceFactory.makeCrop(0f, 0f, .5f, .5f);
        transform = ImagesServiceFactory.makeCrop(0d, 0d, .5d, .5d);
        transform = ImagesServiceFactory.makeHorizontalFlip();
        img = ImagesServiceFactory.makeImageFromBlob(new BlobKey("yar"));
        img = ImagesServiceFactory.makeImageFromFilename("/gs/yar");
        transform = ImagesServiceFactory.makeImFeelingLucky();
        transform = ImagesServiceFactory.makeResize(10, 10);
        transform = ImagesServiceFactory.makeResize(10, 10, true);
        transform = ImagesServiceFactory.makeResize(10, 10, .5f, .5f);
        transform = ImagesServiceFactory.makeResize(10, 10, .5d, .5d);
        transform = ImagesServiceFactory.makeRotate(90);
        transform = ImagesServiceFactory.makeVerticalFlip();
        return classes(Object.class);
      } finally {
        helper.tearDown();
      }
    }
  }

  /**
   * Exhaustive use of {@link IImagesServiceFactory}.
   */
  public static class IImagesServiceFactoryUsage
      extends ExhaustiveApiInterfaceUsage<IImagesServiceFactory> {

    @Override
    public Set<Class<?>> useApi(IImagesServiceFactory iImagesServiceFactory) {
      LocalServiceTestHelper helper =
          new LocalServiceTestHelper(new LocalBlobstoreServiceTestConfig());
      helper.setUp();
      try {
        ImagesService svc = iImagesServiceFactory.getImagesService();
        Image img = iImagesServiceFactory.makeImage("not an image".getBytes(UTF_8));
        Composite comp =
            iImagesServiceFactory.makeComposite(img, 0, 10, .5f, Composite.Anchor.BOTTOM_CENTER);
        CompositeTransform compTransform = iImagesServiceFactory.makeCompositeTransform();
        compTransform =
            iImagesServiceFactory.makeCompositeTransform(Collections.<Transform>emptyList());
        Transform transform = iImagesServiceFactory.makeCrop(0f, 0f, .5f, .5f);
        transform = iImagesServiceFactory.makeCrop(0d, 0d, .5d, .5d);
        transform = iImagesServiceFactory.makeHorizontalFlip();
        img = iImagesServiceFactory.makeImageFromBlob(new BlobKey("yar"));
        img = iImagesServiceFactory.makeImageFromFilename("/gs/yar");
        transform = iImagesServiceFactory.makeImFeelingLucky();
        transform = iImagesServiceFactory.makeResize(10, 10);
        transform = iImagesServiceFactory.makeResize(10, 10, true);
        transform = iImagesServiceFactory.makeResize(10, 10, .5f, .5f);
        transform = iImagesServiceFactory.makeResize(10, 10, .5d, .5d);
        transform = iImagesServiceFactory.makeRotate(90);
        transform = iImagesServiceFactory.makeVerticalFlip();
        return classes();
      } finally {
        helper.tearDown();
      }
    }
  }

  /**
   * Exhaustive use of {@link IImagesServiceFactoryProvider}.
   */
  public static class IImagesServiceFactoryProviderUsage
      extends ExhaustiveApiUsage<IImagesServiceFactoryProvider> {

    @Override
    public Set<Class<?>> useApi() {
      IImagesServiceFactoryProvider iImagesServiceFactoryProvider
          = new IImagesServiceFactoryProvider();
      return classes(FactoryProvider.class, Comparable.class, Object.class);
    }
  }


  /**
   * Exhaustive use of {@link ImagesService}.
   */
  public static class ImagesServiceUsage extends ExhaustiveApiInterfaceUsage<ImagesService> {

    int ___apiConstant_MAX_COMPOSITES_PER_REQUEST;
    int ___apiConstant_MAX_RESIZE_DIMENSIONS;
    Set<Integer> ___apiConstant_SERVING_SIZES;
    int ___apiConstant_SERVING_SIZES_LIMIT;
    int ___apiConstant_MAX_TRANSFORMS_PER_REQUEST;
    Set<Integer> ___apiConstant_SERVING_CROP_SIZES;
    
    @Override
    @SuppressWarnings("deprecation")
    protected Set<Class<?>> useApi(ImagesService svc) {
      Transform transform = ImagesServiceFactory.makeImFeelingLucky();
      Image img = ImagesServiceFactory.makeImage("not an image".getBytes(UTF_8));
      Image img2 = svc.applyTransform(transform, img);
      img2 = svc.applyTransform(transform, img, ImagesService.OutputEncoding.JPEG);
      img2 = svc
          .applyTransform(transform, img, new OutputSettings(ImagesService.OutputEncoding.JPEG));
      img2 = svc.applyTransform(transform, img, new InputSettings(),
          new OutputSettings(ImagesService.OutputEncoding.JPEG));
      Future<Image> future = svc.applyTransformAsync(transform, img);
      future = svc.applyTransformAsync(transform, img, ImagesService.OutputEncoding.JPEG);
      future = svc.applyTransformAsync(transform, img,
          new OutputSettings(ImagesService.OutputEncoding.JPEG));
      future = svc.applyTransformAsync(transform, img, new InputSettings(),
          new OutputSettings(ImagesService.OutputEncoding.JPEG));
      BlobKey blobKey = new BlobKey("yar");
      String strVal = svc.getServingUrl(ServingUrlOptions.Builder.withBlobKey(blobKey));
      strVal = svc.getServingUrl(blobKey);
      strVal = svc.getServingUrl(blobKey, false);
      strVal = svc.getServingUrl(blobKey, 1, false);
      strVal = svc.getServingUrl(blobKey, 1, false, false);
      Composite comp = ImagesServiceFactory
          .makeComposite(img, 0, 10, .5f, Composite.Anchor.BOTTOM_CENTER);
      img2 = svc.composite(Collections.singleton(comp), 5, 10, 30L);
      img2 = svc
          .composite(Collections.singleton(comp), 5, 10, 30L, ImagesService.OutputEncoding.JPEG);
      img2 = svc.composite(Collections.singleton(comp), 5, 10, 30L,
          new OutputSettings(ImagesService.OutputEncoding.JPEG));
      int[][] histogram = svc.histogram(img);
      svc.deleteServingUrl(blobKey);

      ___apiConstant_MAX_COMPOSITES_PER_REQUEST = ImagesService.MAX_COMPOSITES_PER_REQUEST;
      ___apiConstant_MAX_TRANSFORMS_PER_REQUEST = ImagesService.MAX_TRANSFORMS_PER_REQUEST;
      ___apiConstant_MAX_RESIZE_DIMENSIONS = ImagesService.MAX_RESIZE_DIMENSIONS;
      ___apiConstant_SERVING_SIZES_LIMIT = ImagesService.SERVING_SIZES_LIMIT;
      ___apiConstant_SERVING_SIZES = ImagesService.SERVING_SIZES;
      ___apiConstant_SERVING_CROP_SIZES = ImagesService.SERVING_CROP_SIZES;
      return classes();
    }
  }

  /**
   * Exhaustive use of {@link Composite.Anchor}.
   */
  public static class AnchorUsage extends ExhaustiveApiUsage<Composite.Anchor> {

    @Override
    public Set<Class<?>> useApi() {
      Composite.Anchor anchor = Composite.Anchor.BOTTOM_CENTER;
      anchor = Composite.Anchor.BOTTOM_LEFT;
      anchor = Composite.Anchor.BOTTOM_RIGHT;
      anchor = Composite.Anchor.CENTER_CENTER;
      anchor = Composite.Anchor.CENTER_LEFT;
      anchor = Composite.Anchor.CENTER_RIGHT;
      anchor = Composite.Anchor.TOP_CENTER;
      anchor = Composite.Anchor.TOP_LEFT;
      anchor = Composite.Anchor.TOP_RIGHT;
      anchor = Composite.Anchor.valueOf("BOTTOM_CENTER");
      Composite.Anchor[] values = Composite.Anchor.values();
      return classes(Object.class, Enum.class, Comparable.class, Serializable.class);
    }
  }

  /**
   * Exhaustive use of {@link CompositeTransform}.
   */
  public static class CompositeTransformUsage extends ExhaustiveApiUsage<CompositeTransform> {

    @Override
    public Set<Class<?>> useApi() {
      Transform transform = ImagesServiceFactory.makeCrop(0f, 0f, .5f, .5f);
      CompositeTransform compTransform = ImagesServiceFactory.makeCompositeTransform();
      compTransform = compTransform.concatenate(transform);
      compTransform = compTransform.preConcatenate(transform);
      return classes(Object.class, Transform.class, Serializable.class);
    }
  }

  /**
   * Exhaustive use of {@link Image}.
   */
  public static class ImageUsage extends ExhaustiveApiInterfaceUsage<Image> {

    @Override
    protected Set<Class<?>> useApi(Image img) {
      BlobKey key = img.getBlobKey();
      Image.Format fmt = img.getFormat();
      int intVal = img.getHeight();
      byte[] data = img.getImageData();
      intVal = img.getWidth();
      img.setImageData(data);
      return classes(Serializable.class);
    }
  }

  /**
   * Exhaustive use of {@link Image.Format}.
   */
  public static class ImageFormatUsage extends ExhaustiveApiUsage<Image.Format> {

    @Override
    public Set<Class<?>> useApi() {
      Image.Format fmt = Image.Format.GIF;
      fmt = Image.Format.BMP;
      fmt = Image.Format.ICO;
      fmt = Image.Format.JPEG;
      fmt = Image.Format.PNG;
      fmt = Image.Format.TIFF;
      fmt = Image.Format.WEBP;
      fmt = Image.Format.valueOf("BMP");
      Image.Format[] values = Image.Format.values();
      return classes(Object.class, Enum.class, Comparable.class, Serializable.class);
    }
  }

  /**
   * Exhaustive use of {@link InputSettings}.
   */
  public static class InputSettingsUsage extends ExhaustiveApiUsage<InputSettings> {

    @Override
    public Set<Class<?>> useApi() {
      InputSettings settings = new InputSettings();
      settings.setOrientationCorrection(InputSettings.OrientationCorrection.CORRECT_ORIENTATION);
      InputSettings.OrientationCorrection oc = settings.getOrientationCorrection();
      return classes(Object.class);
    }
  }

  /**
   * Exhaustive use of {@link InputSettings.OrientationCorrection}.
   */
  public static class OrientationCorrectionUsage
      extends ExhaustiveApiUsage<InputSettings.OrientationCorrection> {

    @Override
    public Set<Class<?>> useApi() {
      InputSettings.OrientationCorrection oc =
          InputSettings.OrientationCorrection.CORRECT_ORIENTATION;
      oc = InputSettings.OrientationCorrection.UNCHANGED_ORIENTATION;
      oc = InputSettings.OrientationCorrection.valueOf("UNCHANGED_ORIENTATION");
      InputSettings.OrientationCorrection[] values = InputSettings.OrientationCorrection.values();
      return classes(Object.class, Enum.class, Comparable.class, Serializable.class);
    }
  }

  /**
   * Exhaustive use of {@link OutputSettings}.
   */
  public static class OutputSettingsUsage extends ExhaustiveApiUsage<OutputSettings> {

    @Override
    public Set<Class<?>> useApi() {
      OutputSettings settings = new OutputSettings(ImagesService.OutputEncoding.JPEG);
      ImagesService.OutputEncoding encoding = settings.getOutputEncoding();
      int intVal = settings.getQuality();
      boolean boolVal = settings.hasQuality();
      settings.setOutputEncoding(ImagesService.OutputEncoding.JPEG);
      settings.setQuality(3);
      return classes(Object.class);
    }
  }

  /**
   * Exhaustive use of {@link ServingUrlOptions}.
   */
  public static class ServingUrlOptionsUsage extends ExhaustiveApiUsage<ServingUrlOptions> {

    @Override
    public Set<Class<?>> useApi() {
      BlobKey key = new BlobKey("yar");
      ServingUrlOptions opts = ServingUrlOptions.Builder.withBlobKey(key);
      opts = opts.blobKey(key);
      opts = opts.crop(true);
      opts = ServingUrlOptions.Builder.withGoogleStorageFileName("/gs/yar");
      opts = opts.googleStorageFileName("/gs/yar");
      opts = opts.imageSize(5);
      opts = opts.secureUrl(true);
      boolean boolVal = opts.equals(opts);
      int intVal = opts.hashCode();
      String strVal = opts.toString();
      return classes(Object.class);
    }
  }

  /**
   * Exhaustive use of {@link ServingUrlOptions.Builder}.
   */
  public static class ServingUrlOptionsBuilderUsage
      extends ExhaustiveApiUsage<ServingUrlOptions.Builder> {

    @Override
    public Set<Class<?>> useApi() {
      BlobKey key = new BlobKey("yar");
      ServingUrlOptions opts = ServingUrlOptions.Builder.withBlobKey(key);
      opts = ServingUrlOptions.Builder.withGoogleStorageFileName("/gs/yar");
      return classes(Object.class);
    }
  }

  /**
   * Exhaustive use of {@link ImagesServiceFailureException}.
   */
  public static class ImagesServiceFailureExceptionUsage
      extends ExhaustiveApiUsage<ImagesServiceFailureException> {

    @Override
    public Set<Class<?>> useApi() {
      ImagesServiceFailureException ex = new ImagesServiceFailureException("msg");
      ex = new ImagesServiceFailureException("msg", new Throwable());
      return classes(Object.class, RuntimeException.class, Exception.class, Throwable.class,
          Serializable.class);
    }
  }

  /**
   * Exhaustive use of {@link ImagesService.OutputEncoding}.
   */
  public static class OutputEncodingUsage extends ExhaustiveApiUsage<ImagesService.OutputEncoding> {

    @Override
    public Set<Class<?>> useApi() {
      ImagesService.OutputEncoding encoding = ImagesService.OutputEncoding.JPEG;
      encoding = ImagesService.OutputEncoding.PNG;
      encoding = ImagesService.OutputEncoding.WEBP;
      encoding = ImagesService.OutputEncoding.valueOf("JPEG");
      ImagesService.OutputEncoding[] values = ImagesService.OutputEncoding.values();
      return classes(Object.class, Enum.class, Comparable.class, Serializable.class);
    }
  }

  /**
   * Exhaustive use of {@link Composite}.
   */
  public static class CompositeUsage extends ExhaustiveApiUsage<Composite> {

    @Override
    public Set<Class<?>> useApi() {
      Image img = ImagesServiceFactory.makeImage("not an image".getBytes(UTF_8));
      Composite comp = ImagesServiceFactory.makeComposite(img, 0, 10, .5f,
          Composite.Anchor.BOTTOM_CENTER);
      return classes(Object.class);
    }
  }

  /**
   * Exhaustive use of {@link TransformUsage}.
   */
  public static class TransformUsage extends ExhaustiveApiUsage<Transform> {

    @Override
    public Set<Class<?>> useApi() {
      Transform transform = ImagesServiceFactory.makeCrop(0f, 0f, .5f, .5f);
      return classes(Object.class, Serializable.class);
    }
  }

}
