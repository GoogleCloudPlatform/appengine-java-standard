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

package com.google.appengine.api.images.dev.ee10;

import com.google.appengine.api.images.ImagesServicePb.ImageData;
import com.google.appengine.api.images.ImagesServicePb.ImagesServiceError.ErrorCode;
import com.google.appengine.api.images.ImagesServicePb.OutputSettings.MIME_TYPE;
import com.google.appengine.api.images.ImagesServicePb.Transform;
import com.google.appengine.api.images.dev.LocalImagesService;
import com.google.appengine.tools.development.ApiProxyLocal;
import com.google.appengine.tools.development.LocalRpcService.Status;
import com.google.apphosting.api.ApiProxy;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.ByteString;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stubs out dynamic image server.
 *
 */
public class LocalBlobImageServlet extends HttpServlet {
  private static final long serialVersionUID = -12394724046108259L;
  private static final Set<String> transcodeToPng = ImmutableSet.of("png", "gif");
  private LocalImagesService imagesService;
  private static final int DEFAULT_SERVING_SIZE = 512;

  @Override
  public void init() throws ServletException {
    super.init();
    imagesService = getLocalImagesService();
  }

  LocalImagesService getLocalImagesService() {
    ApiProxyLocal apiProxyLocal = (ApiProxyLocal) getServletContext().getAttribute(
        "com.google.appengine.devappserver.ApiProxyLocal");
    return(LocalImagesService) apiProxyLocal.getService(LocalImagesService.PACKAGE);
  }

  /**
   * Utility wrapper to return image bytes and its mime type.
   */
  protected static class Image {
    private byte[] image;
    private String mimeType;

    Image(byte[] image, String mimeType) {
      this.image = image;
      this.mimeType = mimeType;
    }

    public byte[] getImage() {
      return image;
    }

    public String getMimeType() {
      return mimeType;
    }
  }

  @Override
  public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    try {
      OutputStream out = resp.getOutputStream();
      try {
        ParsedUrl parsedUrl = ParsedUrl.createParsedUrl(req.getRequestURI());
        // TODO: Revisit and possibly re-enable once b/7031367 is understood
        // Key key = KeyFactory.createKey(ImagesReservedKinds.BLOB_SERVING_URL_KIND,
        //     parsedUrl.getBlobKey());
        // try {
        //   datastoreService.get(key);
        // } catch (EntityNotFoundException ex) {
        //   // Not finding the key is only a warning at this stage to support
        //   // older apps.
        //   // TODO: Make this an error by returning SC_NOT_FOUND once
        //   // this code has been released for a few cycles.
        //   logger.log(Level.WARNING, "Missing serving URL key for blobKey " + key.toString() +
        //       ". Ensure that getServingUrl is called before serving a blob.");
        //   resp.sendError(HttpServletResponse.SC_NOT_FOUND);
        // }
        Image image = transformImage(parsedUrl);
        resp.setContentType(image.getMimeType());
        out.write(image.getImage());
      } finally {
        out.close();
      }
    } catch (ApiProxy.ApplicationException e) {
      ErrorCode code = ErrorCode.forNumber(e.getApplicationError());
      if (code == null) {
        code = ErrorCode.UNSPECIFIED_ERROR;
      }
      switch (code) {
        case NOT_IMAGE:
        case INVALID_BLOB_KEY:
          resp.sendError(HttpServletResponse.SC_NOT_FOUND, e.getMessage());
          break;
        default:
          resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
      }
    } catch(IllegalArgumentException e) {
      resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
    } catch (IOException e) {
      resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
    }
  }

  /**
   * Utility class to parse a Local URL into its component parts.
   *
   * The Local url format is as follows:
   *
   * /_ah/img/SomeValidBlobKey[=options]
   *
   * where options is either "sX" where X is from ParsedUrl.uncroppedSizes or
   * "sX-c" where X is from ParsedUrl.croppedSizes.
   */
  protected static class ParsedUrl {
    private String blobKey;
    private String options;
    private int resize;
    private boolean crop;
    private static final Pattern pattern = Pattern.compile(
        "/_ah/img/([-\\w:]+)(=[-\\w]+)?");
    private static final Pattern optionsPattern = Pattern.compile(
        "^s(\\d+)(-c)?");
    private static final int SIZE_LIMIT = 1600;

    /**
     * Checks if the parsed url has options.
     */
    public boolean hasOptions() {
      if (options == null || options.length() == 0) {
        return false;
      }
      return true;
    }

    /**
     * Returns the parsed BlobKey.
     */
    public String getBlobKey() {
      return blobKey;
    }

    /**
     * Returns the resize option. Only valid if hasOption() is {@code true}.
     */
    public int getResize() {
      return resize;
    }

    /**
     * Returns the crop option. Only valid if hasOption() is {@code true}.
     */
    public boolean getCrop() {
      return crop;
    }

    /**
     * Creates a {@code ParsedUrl} instance from the given URL.
     *
     * @param requestUri the requested URL
     *
     * @return an instance
     */
    protected static ParsedUrl createParsedUrl(String requestUri) {
      ParsedUrl parsedUrl = new ParsedUrl();
      parsedUrl.parse(requestUri);
      return parsedUrl;
    }

    /**
     * Parses a Local URL to its component parts.
     *
     * @param requestUri the Local request URL
     * @throws IllegalArgumentException for malformed URLs
     */
    protected void parse(String requestUri) {
      Matcher matcher = pattern.matcher(requestUri);
      if (!matcher.matches()) {
        throw new IllegalArgumentException("Malformed URL.");
      }
      blobKey = matcher.group(1);
      options = matcher.group(2);
      if (options != null && options.startsWith("=")) {
        options = options.substring(1);
      }
      parseOptions();
    }

    /**
     * Parses URL options to its component parts.
     *
     * @throws IllegalArgumentException for malformed options
     */
    protected void parseOptions() {
      try {
        if (!hasOptions()) {
          return;
        }
        Matcher matcher = optionsPattern.matcher(options);
        if (!matcher.matches()) {
          throw new IllegalArgumentException("Malformed URL Options");
        }
        resize = Integer.parseInt(matcher.group(1));
        crop = false;
        if (matcher.group(2) != null) {
          crop = true;
        }

        // Check resize against the allowlist
        if (resize > SIZE_LIMIT || resize < 0) {
          throw new IllegalArgumentException("Invalid resize");
        }
      } catch (NumberFormatException e) {
        options = null;
        throw new IllegalArgumentException("Invalid resize", e);
      }
    }

    private ParsedUrl() {
    }
  }

  /**
   * Transforms the given image specified in the {@code ParseUrl} argument.
   *
   * <p>Applies all the requested resize and crop operations to a valid image.
   *
   * @param request a valid {@code ParseUrl} instance
   * @return the transformed image in an Image class
   * @throws ApiProxy.ApplicationException If the image cannot be opened, encoded, or if the
   *     transform is malformed
   */
  protected Image transformImage(final ParsedUrl request) {
    // Obtain the image bytes as a BufferedImage
    Status unusedStatus = new Status();
    ImageData imageData =
        ImageData.newBuilder()
            .setBlobKey(request.getBlobKey())
            .setContent(ByteString.EMPTY)
            .build();

    String originalMimeType = imagesService.getMimeType(imageData);
    BufferedImage img = imagesService.openImage(imageData, unusedStatus);

    // Apply the transform
    if (request.hasOptions()) {
      // Crop
      if (request.getCrop()) {
        Transform.Builder cropXform = null;
        float width = img.getWidth();
        float height = img.getHeight();
        if (width > height) {
          cropXform = Transform.newBuilder();
          float delta = (width - height) / (width * 2.0f);
          cropXform.setCropLeftX(delta);
          cropXform.setCropRightX(1.0f - delta);
        } else if (width < height) {
          cropXform = Transform.newBuilder();
          float delta = (height - width) / (height * 2.0f);
          float topDelta = Math.max(0.0f, delta - 0.25f);
          float bottomDelta = 1.0f - (2.0f * delta) + topDelta;
          cropXform.setCropTopY(topDelta);
          cropXform.setCropBottomY(bottomDelta);
        }
        if (cropXform != null) {
          img = imagesService.processTransform(img, cropXform.build(), unusedStatus);
        }
      }

      // Resize
      Transform resizeXform =
          Transform.newBuilder()
              .setWidth(request.getResize())
              .setHeight(request.getResize())
              .build();
      img = imagesService.processTransform(img, resizeXform, unusedStatus);
    } else if (img.getWidth() > DEFAULT_SERVING_SIZE || img.getHeight() > DEFAULT_SERVING_SIZE) {
      // Resize down to default serving size.
      Transform resizeXform =
          Transform.newBuilder()
              .setWidth(DEFAULT_SERVING_SIZE)
              .setHeight(DEFAULT_SERVING_SIZE)
              .build();
      img = imagesService.processTransform(img, resizeXform, unusedStatus);
    }

    MIME_TYPE outputMimeType = MIME_TYPE.JPEG;
    String outputMimeTypeString = "image/jpeg";
    if (transcodeToPng.contains(originalMimeType)) {
      outputMimeType = MIME_TYPE.PNG;
      outputMimeTypeString = "image/png";
    }
    return new Image(
        imagesService.saveImage(img, outputMimeType, unusedStatus), outputMimeTypeString);
  }
}
