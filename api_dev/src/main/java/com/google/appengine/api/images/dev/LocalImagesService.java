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

import com.google.appengine.api.NamespaceManager;
import com.google.appengine.api.blobstore.BlobKey;
import com.google.appengine.api.blobstore.dev.BlobStorage;
import com.google.appengine.api.blobstore.dev.BlobStorageFactory;
import com.google.appengine.api.blobstore.dev.LocalBlobstoreService;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.images.ImagesService;
import com.google.appengine.api.images.ImagesServicePb;
import com.google.appengine.api.images.ImagesServicePb.CompositeImageOptions;
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
import com.google.appengine.api.images.ImagesServicePb.Transform;
import com.google.appengine.tools.development.AbstractLocalRpcService;
import com.google.appengine.tools.development.LocalRpcService;
import com.google.appengine.tools.development.LocalServerEnvironment;
import com.google.appengine.tools.development.LocalServiceContext;
import com.google.apphosting.api.ApiProxy;
import com.google.auto.service.AutoService;
import com.google.protobuf.ByteString;
import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.stream.ImageInputStream;
import mediautil.gen.Log;
import mediautil.image.jpeg.AbstractImageInfo;
import mediautil.image.jpeg.Entry;
import mediautil.image.jpeg.Exif;
import mediautil.image.jpeg.LLJTran;
import mediautil.image.jpeg.LLJTranException;

/**
 * Java stub implementation of the images api backend using Image 2D. Depends on ImageIO being able
 * to load and save in the correct image formats.
 *
 */
@AutoService(LocalRpcService.class)
public final class LocalImagesService extends AbstractLocalRpcService {

  private static final Logger log = Logger.getLogger(LocalImagesService.class.getCanonicalName());
  private String hostPrefix;

  /**
   * The package name for this service.
   */
  public static final String PACKAGE = "images";

  private BlobStorage blobStorage;
  private DatastoreService datastoreService;

  public LocalImagesService() {}

  /** {@inheritDoc} */
  @Override
  public String getPackage() {
    return PACKAGE;
  }

  /** {@inheritDoc} */
  @Override
  public void init(LocalServiceContext context, Map<String, String> properties) {

    // Perform a scan for ImageIO plugins. We need to set the class loader so
    // that we will have the correct permissions to load system classes if
    // the plugin requires them
    ClassLoader oldLoader = Thread.currentThread().getContextClassLoader();
    ClassLoader appLoader = this.getClass().getClassLoader();
    try {
      Thread.currentThread().setContextClassLoader(appLoader);
      ImageIO.scanForPlugins();
    } finally {
      Thread.currentThread().setContextClassLoader(oldLoader);
    }

    String[] inputFormats = {"png", "jpg", "gif", "bmp", "ico", "tif", "webp"};
    String[] outputFormats = {"png", "jpg", "webp"};
    for (String format : inputFormats) {
      if (!ImageIO.getImageReadersByFormatName(format).hasNext()) {
        log.warning(
            "No image reader found for format \"" + format + "\"."
                + " An ImageIO plugin must be installed to use this format"
                + " with the DevAppServer.");
      }
    }
    for (String format : outputFormats) {
      if (!ImageIO.getImageWritersByFormatName(format).hasNext()) {
        log.warning(
            "No image writer found for format \"" + format + "\"."
                + " An ImageIO plugin must be installed to use this format"
                + " with the DevAppServer.");
      }
    }

    // N.B.: This is a bit hacky. We have to force the
    // blobstore service to initialize. We could just call
    // BlobStorageFactory.getBlobStorage() lazily, but we would still
    // have to force it at some point. We may as well force it here.
    context.getLocalService(LocalBlobstoreService.PACKAGE);
    // Now BlobStorageFactory should be set up properly.
    blobStorage = BlobStorageFactory.getBlobStorage();
    datastoreService = DatastoreServiceFactory.getDatastoreService();

    LocalServerEnvironment env = context.getLocalServerEnvironment();
    hostPrefix = "http://" + env.getAddress() + ":" + env.getPort();
    Log.debugLevel = Log.LEVEL_NONE;
  }

  /** {@inheritDoc} */
  @Override
  public void start() {}

  /** {@inheritDoc} */
  @Override
  public void stop() {}

  /**
   * Apply the transform request to the contained image.
   *
   * @param status RPC status
   * @param request request to be processed
   * @return a transform response containing the processed image
   */
  public ImagesTransformResponse transform(
      final Status status, final ImagesTransformRequest request) {
    return AccessController.doPrivileged(
        new PrivilegedAction<ImagesTransformResponse>() {
          @Override
          public ImagesTransformResponse run() {
            BufferedImage img = openImage(request.getImage(), status);
            if (request.getTransformCount() > ImagesService.MAX_TRANSFORMS_PER_REQUEST) {
              // TODO: Do we need to set both fields *and* throw an
              // exception?
              status.setSuccessful(false);
              status.setErrorCode(ErrorCode.BAD_TRANSFORM_DATA.getNumber());
              throw new ApiProxy.ApplicationException(
                  ErrorCode.BAD_TRANSFORM_DATA.getNumber(),
                  String.format(
                      "%d transforms were supplied; the maximum allowed is %d.",
                      request.getTransformCount(), ImagesService.MAX_TRANSFORMS_PER_REQUEST));
            }
            int orientation = 1;
            if (request.getInput().getCorrectExifOrientation()
                == ORIENTATION_CORRECTION_TYPE.CORRECT_ORIENTATION) {
              Exif exif = getExifMetadata(request.getImage());
              if (exif != null) {
                Entry entry = exif.getTagValue(Exif.ORIENTATION, true);
                if (entry != null) {
                  orientation = ((Integer) entry.getValue(0)).intValue();
                  if (img.getHeight() > img.getWidth()) {
                    orientation = 1;
                  }
                }
              }
            }
            for (Transform transform : request.getTransformList()) {
              // In production, orientation correction is done during the first
              // transform. If the first transform is a crop or flip it is done
              // after, otherwise it is done before. To be precise, the order
              // of transformation within a single entry is: Crop, Flip,
              // Rotate, Resize, (Crop-to-fit), Effects (e.g., autolevels).
              // Orientation fix is done within the chain modifying flipping
              // and rotation steps.
              if (orientation != 1
                  && !(transform.hasCropRightX()
                      || transform.hasCropTopY()
                      || transform.hasCropBottomY()
                      || transform.hasCropLeftX())
                  && !transform.hasHorizontalFlip()
                  && !transform.hasVerticalFlip()) {
                img = correctOrientation(img, status, orientation);
                orientation = 1;
              }
              if (transform.getAllowStretch() && transform.getCropToFit()) {
                // Process allow stretch first and then process the crop.
                // This is similar to how it works in production and allows us
                // to keep the dev processing pipeline straightforward for this
                // combination of transforms.
                Transform.Builder stretch = Transform.newBuilder();
                stretch
                    .setWidth(transform.getWidth())
                    .setHeight(transform.getHeight())
                    .setAllowStretch(true);
                img = processTransform(img, stretch.build(), status);
                // Create and process the new crop portion of the transform.
                Transform.Builder crop = Transform.newBuilder();
                crop.setWidth(transform.getWidth())
                    .setHeight(transform.getHeight())
                    .setCropToFit(transform.getCropToFit())
                    .setCropOffsetX(transform.getCropOffsetX())
                    .setCropOffsetY(transform.getCropOffsetY())
                    .setAllowStretch(false);
                img = processTransform(img, crop.build(), status);
              } else {
                img = processTransform(img, transform, status);
              }
              if (orientation != 1) {
                img = correctOrientation(img, status, orientation);
                orientation = 1;
              }
            }
            status.setSuccessful(true);
            ImageData imageData =
                ImageData.newBuilder()
                    .setContent(
                        ByteString.copyFrom(
                            saveImage(img, request.getOutput().getMimeType(), status)))
                    .setWidth(img.getWidth())
                    .setHeight(img.getHeight())
                    .build();
            return ImagesTransformResponse.newBuilder().setImage(imageData).build();
          }
        });
  }

  /**
   * @param status RPC status
   * @param request request to be processed
   * @return a transform response containing the processed image
   */
  public ImagesCompositeResponse composite(
      final Status status, final ImagesCompositeRequest request) {
    return AccessController.doPrivileged(
        new PrivilegedAction<ImagesCompositeResponse>() {
          @Override
          public ImagesCompositeResponse run() {
            List<BufferedImage> images = new ArrayList<BufferedImage>(request.getImageCount());
            for (int i = 0; i < request.getImageCount(); i++) {
              images.add(openImage(request.getImage(i), status));
            }
            if (request.getOptionsCount() > ImagesService.MAX_COMPOSITES_PER_REQUEST) {
              status.setSuccessful(false);
              status.setErrorCode(ErrorCode.BAD_TRANSFORM_DATA.getNumber());
              throw new ApiProxy.ApplicationException(ErrorCode.BAD_TRANSFORM_DATA.getNumber(),
                  String.format("%d composites were supplied; the maximum allowed is %d.",
                      request.getOptionsCount(), ImagesService.MAX_COMPOSITES_PER_REQUEST));
            }
            int width = request.getCanvas().getWidth();
            int height = request.getCanvas().getHeight();
            int color = request.getCanvas().getColor();
            BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            for (int i = 0; i < height; i++) {
              for (int j = 0; j < width; j++) {
                canvas.setRGB(j, i, color);
              }
            }
            for (int i = 0; i < request.getOptionsCount(); i++) {
              CompositeImageOptions options = request.getOptions(i);
              if (options.getSourceIndex() < 0
                  || options.getSourceIndex() >= request.getImageCount()) {
                throw new ApiProxy.ApplicationException(ErrorCode.BAD_TRANSFORM_DATA.getNumber(),
                    String.format("Invalid source image index %d", options.getSourceIndex()));
              }
              processComposite(canvas, options, images.get(options.getSourceIndex()), status);
            }
            status.setSuccessful(true);
            return ImagesCompositeResponse
                .newBuilder()
                .setImage(
                    ImageData.newBuilder().setContent(ByteString.copyFrom(saveImage(canvas, request
                        .getCanvas()
                        .getOutput()
                        .getMimeType(), status))))
                .build();
          }
        });
  }

  /**
   * Obtains the mime type of the image data.
   *
   * @param imageData a reference to the image
   *
   * @return a string representing the mime type. Valid return values include
   *     {@code inputFormats} in LocalImagesService.init().
   * @throws ApiProxy.ApplicationException If the image cannot be opened
   */
  String getMimeType(ImageData imageData) {
    try {
      boolean swallowDueToThrow = true;
      ImageInputStream in = ImageIO.createImageInputStream(extractImageData(imageData));
      try {
        Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
        if (!readers.hasNext()) {
          throw new ApiProxy.ApplicationException(
              ErrorCode.NOT_IMAGE.getNumber(), "Failed to read image");
        }
        ImageReader reader = readers.next();
        swallowDueToThrow = false;
        return reader.getFormatName();
      } finally {
        try {
          in.close();
        } catch (IOException ex) {
          if (swallowDueToThrow) {
            log.log(Level.WARNING, "IOException thrown in close().", ex);
          } else {
            throw ex;
          }
        }
      }
    } catch (IOException ex) {
      throw new ApiProxy.ApplicationException(
          ErrorCode.INVALID_BLOB_KEY.getNumber(), "Could not read blob.");
    }
  }

  /**
   * Obtains the EXIF metadata of the image data.
   *
   * @param imageData a reference to the image
   * @return an {@link Exif} instance for this image if its format is jpeg.
   * @throws ApiProxy.ApplicationException If the image cannot be opened
   */
  Exif getExifMetadata(ImageData imageData) {
    if (getMimeType(imageData).equals("JPEG")) {
      try {
        LLJTran transform = new LLJTran(extractImageData(imageData));
        try {
          transform.read(true);
        } catch (LLJTranException e) {
          throw new ApiProxy.ApplicationException(
              ErrorCode.NOT_IMAGE.getNumber(), "Failed to read image EXIF metadata");
        }
        AbstractImageInfo<?> info = transform.getImageInfo();
        if (info instanceof Exif) {
          return (Exif) info;
        }
      } catch (IOException ex) {
        throw new ApiProxy.ApplicationException(
            ErrorCode.INVALID_BLOB_KEY.getNumber(), "Could not read blob.");
      }
    }
    return null;
  }

  /**
   * Loads an image represented by a byte array into a {@link BufferedImage}.
   *
   * @param imageData A byte array representing an image
   * @param status RPC status
   * @return a {@link BufferedImage} of the image.
   * @throws ApiProxy.ApplicationException If the image cannot be opened.
   */
  BufferedImage openImage(ImageData imageData, Status status) {
    InputStream in = null;
    try {
      try {
        in = extractImageData(imageData);
      } catch (IOException ex) {
        status.setSuccessful(false);
        status.setErrorCode(ErrorCode.INVALID_BLOB_KEY.getNumber());
        throw new ApiProxy.ApplicationException(
            ErrorCode.INVALID_BLOB_KEY.getNumber(), "Could not read blob.");
      }
      BufferedImage img;
      try {
        img = ImageIO.read(in);
      } catch (IOException ex) {
        status.setSuccessful(false);
        status.setErrorCode(ErrorCode.NOT_IMAGE.getNumber());
        throw new ApiProxy.ApplicationException(
            ErrorCode.NOT_IMAGE.getNumber(), "Failed to read image");
      }
      if (img == null) {
        status.setSuccessful(false);
        status.setErrorCode(ErrorCode.NOT_IMAGE.getNumber());
        throw new ApiProxy.ApplicationException(
            ErrorCode.NOT_IMAGE.getNumber(), "Failed to read image");
      }
      return img;
    } finally {
      if (in != null) {
        try {
          in.close();
        } catch (IOException ex) {}
      }
    }
  }

  /**
   * Saves a {@link BufferedImage} into a byte array using the {@code mimeType} encoding.
   *
   * @param image the image to be encoded.
   * @param status RPC status.
   * @return A byte array representing an image.
   * @throws ApiProxy.ApplicationException If the image cannot be encoded.
   */
  byte[] saveImage(BufferedImage image, MIME_TYPE mimeType, Status status) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try {
      if (mimeType == MIME_TYPE.JPEG) {
        ImageIO.write(image, "jpg", out);
      } else if (mimeType == MIME_TYPE.WEBP) {
        ImageIO.write(image, "webp", out);
      } else {
        ImageIO.write(image, "png", out);
      }
    } catch (IOException ex) {
      status.setSuccessful(false);
      status.setErrorCode(ErrorCode.UNSPECIFIED_ERROR.getNumber());
      throw new ApiProxy.ApplicationException(
          ErrorCode.UNSPECIFIED_ERROR.getNumber(), "Failed to encode image");
    }
    return out.toByteArray();
  }

  /**
   * Calculate the histogram of the supplied image.
   *
   * @param status RPC status
   * @param request request to be processed
   * @return a histogram response containing the histogram of the image
   */
  public ImagesHistogramResponse histogram(
      final Status status, final ImagesHistogramRequest request) {
    return AccessController.doPrivileged(
        new PrivilegedAction<ImagesHistogramResponse>() {
          @Override
          public ImagesHistogramResponse run() {
            BufferedImage img = openImage(request.getImage(), status);
            int[] red = new int[256];
            int[] green = new int[256];
            int[] blue = new int[256];
            int pixel;
            for (int i = 0; i < img.getHeight(); i++) {
              for (int j = 0; j < img.getWidth(); j++) {
                pixel = img.getRGB(j, i);
                // Premultiply by alpha to match thumbnailer.
                red[(((pixel >> 16) & 0xff) * ((pixel >> 24) & 0xff)) / 255]++;
                green[(((pixel >> 8) & 0xff) * ((pixel >> 24) & 0xff)) / 255]++;
                blue[((pixel & 0xff) * ((pixel >> 24) & 0xff)) / 255]++;
              }
            }
            ImagesHistogram.Builder imageHistogram = ImagesHistogram.newBuilder();
            for (int i = 0; i < 256; i++) {
              imageHistogram.addRed(red[i]);
              imageHistogram.addGreen(green[i]);
              imageHistogram.addBlue(blue[i]);
            }
            return ImagesHistogramResponse
                .newBuilder()
                .setHistogram(imageHistogram)
                .build();
          }
        });
  }

  /**
   * Gets a Local image URL.
   *
   * @param status RPC status
   * @param request request containing the blobkey to be served
   *
   * @return a response containing the Local image Url
   */
  public ImagesGetUrlBaseResponse getUrlBase(
      final Status status, final ImagesGetUrlBaseRequest request) {
    return AccessController.doPrivileged(
        new PrivilegedAction<ImagesGetUrlBaseResponse>() {
          @Override
          public ImagesGetUrlBaseResponse run() {
            if (request.getCreateSecureUrl()) {
              log.info(
                  "Secure URLs will not be created using the development " + "application server.");
            }
            // Detect the image mimetype to see if is a valid image.
            ImageData imageData =
                ImageData.newBuilder()
                    .setBlobKey(request.getBlobKey())
                    .setContent(ByteString.EMPTY)
                    .build();
            // getMimeType is validating the blob is an image.
            getMimeType(imageData);
            // Note I am commenting out the following line
            // because experimentats indicates that doing so resolves
            // b/7031367 Tests time out with OOMs since 1.7.1
            // TODO Figure out why the following line causes this
            // test to take over one minute to finish:
            // jt/c/g/dotorg/onetoday/server/offer/selection:FriendsMatchingScorerTest
            // addServingUrlEntry(request.getBlobKey());
            return ImagesGetUrlBaseResponse.newBuilder()
                .setUrl(hostPrefix + "/_ah/img/" + request.getBlobKey())
                .build();
          }
        });
  }

  public ImagesDeleteUrlBaseResponse deleteUrlBase(
      final Status status, final ImagesDeleteUrlBaseRequest request) {
    return AccessController.doPrivileged(
        new PrivilegedAction<ImagesDeleteUrlBaseResponse>() {
          @Override
          public ImagesDeleteUrlBaseResponse run() {
            deleteServingUrlEntry(request.getBlobKey());
            return ImagesDeleteUrlBaseResponse.newBuilder().build();
          }
        });
  }

  @Override
  public Integer getMaxApiRequestSize() {
    // Keep this in sync with MAX_REQUEST_SIZE in <internal>.
    return 32 << 20;  // 32 MB
  }

  /**
   * Correct the orientation of image.
   *
   * @param image image to be processed
   * @param status RPC status
   * @param orientation EXIF orientation value
   * @return processed image
   */
  BufferedImage correctOrientation(BufferedImage image, Status status, int orientation) {
    Transform.Builder transform = Transform.newBuilder();
    Transform.Builder secondTransform = Transform.newBuilder();
    switch(orientation) {
      case 2:
        return processTransform(image, transform.setHorizontalFlip(true).build(), status);
      case 3:
        return processTransform(image, transform.setRotate(180).build(), status);
      case 4:
        return processTransform(image, transform.setVerticalFlip(true).build(), status);
      case 5:
        image = processTransform(image, transform.setVerticalFlip(true).build(), status);
        return processTransform(image, secondTransform.setRotate(90).build(), status);
      case 6:
        return processTransform(image, transform.setRotate(90).build(), status);
      case 7:
        image = processTransform(image, transform.setHorizontalFlip(true).build(), status);
        return processTransform(image, secondTransform.setRotate(90).build(), status);
      case 8:
        return processTransform(image, transform.setRotate(270).build(), status);
    }
    return image;
  }

  /**
   * Apply an individual transform to the provided image.
   *
   * @param image image to be processed
   * @param transform transform to be applied to the image
   * @param status RPC status
   * @return processed image
   */
  BufferedImage processTransform(BufferedImage image, Transform transform, Status status) {
    AffineTransform affine = null;
    BufferedImage constraintImage = null;
    if (transform.hasWidth() || transform.hasHeight()) {
      if (transform.getWidth() < 0 || transform.getHeight() < 0
          || transform.getWidth() > ImagesService.MAX_RESIZE_DIMENSIONS
          || transform.getHeight() > ImagesService.MAX_RESIZE_DIMENSIONS) {
        status.setSuccessful(false);
        status.setErrorCode(ErrorCode.BAD_TRANSFORM_DATA.getNumber());
        throw new ApiProxy.ApplicationException(ErrorCode.BAD_TRANSFORM_DATA.getNumber(),
            String.format("Invalid resize: width and height must be in range [0,%d]",
                ImagesService.MAX_RESIZE_DIMENSIONS));
      }
      if (transform.getWidth() == 0 && transform.getHeight() == 0) {
        status.setSuccessful(false);
        status.setErrorCode(ErrorCode.BAD_TRANSFORM_DATA.getNumber());
        throw new ApiProxy.ApplicationException(ErrorCode.BAD_TRANSFORM_DATA.getNumber(),
            "Invalid resize: width and height cannot both be 0.");
      }
      if (transform.getCropToFit() && (transform.getWidth() == 0 || transform.getHeight() == 0)) {
        status.setSuccessful(false);
        status.setErrorCode(ErrorCode.BAD_TRANSFORM_DATA.getNumber());
        throw new ApiProxy.ApplicationException(ErrorCode.BAD_TRANSFORM_DATA.getNumber(),
            "Invalid resize: neither width nor height can be 0 with crop to fit.");
      }
      if (transform.getAllowStretch()
          && (transform.getWidth() == 0 || transform.getHeight() == 0)) {
        status.setSuccessful(false);
        status.setErrorCode(ErrorCode.BAD_TRANSFORM_DATA.getNumber());
        throw new ApiProxy.ApplicationException(ErrorCode.BAD_TRANSFORM_DATA.getNumber(),
            "Invalid resize: neither width nor height can be 0 with allow stretch.");
      }
      if (transform.getCropToFit() && (!validCropArgument(transform.getCropOffsetX())
                                       || !validCropArgument(transform.getCropOffsetY()))) {
        status.setSuccessful(false);
        status.setErrorCode(ErrorCode.BAD_TRANSFORM_DATA.getNumber());
        throw new ApiProxy.ApplicationException(ErrorCode.BAD_TRANSFORM_DATA.getNumber(),
            "Invalid resize: crop offsets must be in the range 0.0 to 1.0.");
      }
      double aspectRatio = (double) image.getWidth() / (double) image.getHeight();
      double xFactor = (double) transform.getWidth() / (double) image.getWidth();
      double yFactor = (double) transform.getHeight() / (double) image.getHeight();
      double transformFactor;

      ImageTypeSpecifier imageSpecifier = ImageTypeSpecifier.createFromRenderedImage(image);
      if (transform.getAllowStretch()) {
        constraintImage = imageSpecifier.createBufferedImage(transform.getWidth(),
                                                             transform.getHeight());
        affine = AffineTransform.getScaleInstance(xFactor, yFactor);
      } else if (transform.getCropToFit()) {
        transformFactor = Math.max(xFactor, yFactor);
        constraintImage = imageSpecifier.createBufferedImage(transform.getWidth(),
                                                             transform.getHeight());
        double uncroppedWidth = image.getWidth() * transformFactor;
        double uncroppedHeight = image.getHeight() * transformFactor;
        affine = new AffineTransform(
            transformFactor, 0, 0, transformFactor,
            (transform.getWidth() - uncroppedWidth) * transform.getCropOffsetX(),
            (transform.getHeight() - uncroppedHeight) * transform.getCropOffsetY());
      } else {
        if (xFactor < yFactor && xFactor != 0) {
          transformFactor = xFactor;
          constraintImage = imageSpecifier.createBufferedImage(
              transform.getWidth(),
              (int) Math.round(transform.getWidth() / aspectRatio));
        } else {
          transformFactor = yFactor;
          constraintImage = imageSpecifier.createBufferedImage(
              (int) Math.round(transform.getHeight() * aspectRatio),
              transform.getHeight());
        }
        affine = AffineTransform.getScaleInstance(transformFactor, transformFactor);
      }
    } else if (transform.hasRotate()) {
      if ((transform.getRotate() % 90) != 0 || transform.getRotate() >= 360
          || transform.getRotate() < 0) {
        status.setSuccessful(false);
        status.setErrorCode(ErrorCode.BAD_TRANSFORM_DATA.getNumber());
        throw new ApiProxy.ApplicationException(
            ErrorCode.BAD_TRANSFORM_DATA.getNumber(), "Invalid rotate.");
      }
      affine = AffineTransform.getRotateInstance(Math.toRadians(transform.getRotate()));
      if (transform.getRotate() == 90) {
        affine.translate(0, -image.getHeight());
      } else if (transform.getRotate() == 180) {
        affine.translate(-image.getWidth(), -image.getHeight());
      } else if (transform.getRotate() == 270) {
        affine.translate(-image.getWidth(), 0);
      }
    } else if (transform.hasHorizontalFlip()) {
      affine = new AffineTransform(-1.0, 0.0, 0.0, 1.0, image.getWidth(), 0.0);
    } else if (transform.hasVerticalFlip()) {
      affine = new AffineTransform(1.0, 0.0, 0.0, -1.0, 0.0, image.getHeight());
    } else if (transform.hasCropLeftX() || transform.hasCropTopY() || transform.hasCropRightX()
        || transform.hasCropBottomY()) {
      if (!validCropArgs(transform)) {
        status.setSuccessful(false);
        status.setErrorCode(ErrorCode.BAD_TRANSFORM_DATA.getNumber());
        throw new ApiProxy.ApplicationException(
            ErrorCode.BAD_TRANSFORM_DATA.getNumber(), "Invalid crop.");
      }
      int startX = Math.round(transform.getCropLeftX() * image.getWidth());
      int startY = Math.round(transform.getCropTopY() * image.getHeight());
      int width = Math.min(
          Math.round((transform.getCropRightX() - transform.getCropLeftX()) * image.getWidth()),
          image.getWidth() - startX);
      int height = Math.min(
          Math.round((transform.getCropBottomY() - transform.getCropTopY()) * image.getHeight()),
          image.getHeight() - startY);
      return image.getSubimage(startX, startY, width, height);
    } else if (transform.hasAutolevels()) {
      log.warning("I'm Feeling Lucky is not available in the SDK.");
    } else {
      status.setSuccessful(false);
      status.setErrorCode(ErrorCode.BAD_TRANSFORM_DATA.getNumber());
      throw new ApiProxy.ApplicationException(ErrorCode.BAD_TRANSFORM_DATA.getNumber());
    }
    if (affine != null) {
      AffineTransformOp op = new AffineTransformOp(affine, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
      return op.filter(image, constraintImage);
    }
    return image;
  }

  /**
   * Process one composition operation.
   *
   * @param canvas Canvas image on which to composite.
   * @param options Composition options.
   * @param image Image to be composited.
   * @param status RPC status.
   * @return The canvas with the composition operation performed.
   */
  private BufferedImage processComposite(
      BufferedImage canvas, CompositeImageOptions options, BufferedImage image, Status status) {
    float opacity = options.getOpacity();
    if (opacity < 0 || opacity > 1.0f) {
      status.setSuccessful(false);
      status.setErrorCode(ErrorCode.BAD_TRANSFORM_DATA.getNumber());
      throw new ApiProxy.ApplicationException(
          ErrorCode.BAD_TRANSFORM_DATA.getNumber(), "Opacity must be in range [0.0, 1.0]");
    }
    if (opacity == 0) {
      return canvas;
    }
    float xAnchor = (options.getAnchor().getNumber() % 3) * 0.5f;
    float yAnchor = (options.getAnchor().getNumber() / 3) * 0.5f;
    int xOffset = (int) (options.getXOffset() + xAnchor * (canvas.getWidth() - image.getWidth()));
    int yOffset = (int) (options.getYOffset() + yAnchor * (canvas.getHeight() - image.getHeight()));

    // Calculate the parts of the input image we'll actually need.
    int yStart = Math.max(0, -yOffset);
    int xStart = Math.max(0, -xOffset);
    int yEnd = Math.min(image.getHeight(), canvas.getHeight() - yOffset);
    int xEnd = Math.min(image.getWidth(), canvas.getWidth() - xOffset);

    // Give up if the image isn't on the canvas at all.
    if (xStart >= xEnd || yStart >= yEnd) {
      return canvas;
    }
    BufferedImage positionedImage =
        new BufferedImage(xEnd + xOffset, yEnd + yOffset, BufferedImage.TYPE_INT_ARGB);
    for (int i = yStart; i < yEnd; i++) {
      for (int j = xStart; j < xEnd; j++) {
        // Copy into the correct position and make it opaque.
        positionedImage.setRGB(j + xOffset, i + yOffset, image.getRGB(j, i) | 0xff000000);
      }
    }
    Composite composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity);
    composite.createContext(positionedImage.getColorModel(), canvas.getColorModel(), null).compose(
        positionedImage.getRaster(), canvas.getRaster(), canvas.getRaster());
    return canvas;
  }

  /**
   * Checks that crop arguments are valid.
   *
   * @param transform transform containing a crop
   * @return true if the crop arguments are valid
   */
  private boolean validCropArgs(ImagesServicePb.Transform transform) {
    return validCropArgument(transform.getCropLeftX()) && validCropArgument(transform.getCropTopY())
        && validCropArgument(transform.getCropRightX())
        && validCropArgument(transform.getCropBottomY())
        && transform.getCropLeftX() < transform.getCropRightX()
        && transform.getCropTopY() < transform.getCropBottomY();
  }

  /**
   * Checks that a crop arguments is valid.
   *
   * @param arg one crop argument
   * @return true if arg is valid
   */
  private boolean validCropArgument(float arg) {
    return arg >= 0 && arg <= 1;
  }

  BlobStorage getBlobStorage() {
    return blobStorage;
  }

  private InputStream extractImageData(ImagesServicePb.ImageData imageData) throws IOException {
    if (imageData.hasBlobKey()) {
      return getBlobStorage().fetchBlob(new BlobKey(imageData.getBlobKey()));
    } else {
      return new ByteArrayInputStream(imageData.getContent().toByteArray());
    }
  }

  private void addServingUrlEntry(String blobKey) {
    String namespace = NamespaceManager.get();
    try {
      NamespaceManager.set("");
      Entity blobServingUrlEntity = new Entity(ImagesReservedKinds.BLOB_SERVING_URL_KIND,
          blobKey);
      blobServingUrlEntity.setProperty("blob_key", blobKey);
      datastoreService.put(blobServingUrlEntity);
    } finally {
      NamespaceManager.set(namespace);
    }
  }

  private void deleteServingUrlEntry(String blobKey) {
    String namespace = NamespaceManager.get();
    try {
      NamespaceManager.set("");
      Key key = KeyFactory.createKey(null, ImagesReservedKinds.BLOB_SERVING_URL_KIND, blobKey);
      datastoreService.delete(key);
    } finally {
      NamespaceManager.set(namespace);
    }
  }
}
