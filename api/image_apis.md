<!--
 Copyright 2021 Google LLC

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

     https://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
-->

# Google App Engine Images API Documentation

*   [Images API](#images-api)
    *   [Overview](#overview)
    *   [Supported Image Sources](#supported-image-sources)
    *   [Cloud Storage Configuration Requirements](#cloud-storage-configuration-requirements)
    *   [Important Serving Restrictions](#important-serving-restrictions)
    *   [Image Service API](#image-service-api)
    *   [Available Image Transformations](#available-image-transformations)
        *   [Resize](#resize)
        *   [Rotate](#rotate)
        *   [Flip horizontally](#flip-horizontally)
        *   [Flip vertically](#flip-vertically)
        *   [Crop](#crop)
        *   [I'm Feeling Lucky](#im-feeling-lucky)
    *   [Image Formats](#image-formats)
    *   [Transforming Images](#transforming-images)
    *   [Using getServingUrl](#using-getservingurl)
        *   [Dynamic Resizing and Cropping](#dynamic-resizing-and-cropping)
        *   [Deleting Serving URLs](#deleting-serving-urls)
    *   [Images and the Development Server](#images-and-the-development-server)
    *   [Quotas, Limits, and Pricing](#quotas-limits-and-pricing)

## Images API

### Overview

The Images API provides image manipulation capabilities through a dedicated
service. The API allows applications to transform images, composite multiple
images, convert formats, and retrieve image metadata including format, width,
height, and color histograms.

### Supported Image Sources

The Images API accepts image data from: 

- Direct image data passed by the app 
- Cloud Storage objects 
- Cloud Blobstore objects (Cloud Storage is recommended)
Images stored in Cloud Storage or Blobstore can be up to the maximum size
allowed by the respective service. Transformed images are returned directly to
the app and must not exceed 32 megabytes.

### Cloud Storage Configuration Requirements

Cloud Storage buckets must use fine-grained Access Control Lists for the Images
API to work. Buckets configured with uniform bucket-level access will fail with
a `TransformationError`. If your bucket uses uniform bucket-level access, you
can disable this setting to use the Images API.

### Important Serving Restrictions

Only the first app that calls `getServingUrl()` on an image can obtain the
serving URL. Other apps cannot serve the same image. If a second app needs to
serve the image, it must first copy the image and then invoke `getServingUrl()`
on the copy.

### Image Service API

The Image Service API applies transformations using a service instead of
processing on the application server. The basic workflow: 1. Prepare an `Image`
object with image data to transform 2. Create a `Transform` object with
transformation instructions 3. Get an `ImagesService` object 4. Call
`applyTransform()` with the Image and Transform objects 5. Receive the
transformed `Image` object Get instances using `ImagesServiceFactory`:

```java
// Get an instance of the imagesService
ImagesService imagesService = ImagesServiceFactory.getImagesService();
// Make an image directly from a byte
// array and transform it
Image image = ImagesServiceFactory.makeImage(imageBytes);
Transform resize = ImagesServiceFactory.makeResize(100, 50);
Image resizedImage = imagesService.applyTransform(resize, image);
// Write the transformed image back to a Cloud Storage object
gcsService.createOrReplace(
    new GcsFilename(bucket, "resizedImage.jpeg"),
    new GcsFileOptions.Builder().mimeType("image/jpeg").build(),
    ByteBuffer.wrap(resizedImage.getImageData()));
```

Multiple transforms can be combined into a single action using a
`CompositeTransform` instance.

### Available Image Transformations

#### Resize

You can resize the image while maintaining the same aspect ratio. Neither the
width nor the height of the resized image can exceed 4000 pixels.
![Original image before transformation](./transform_before.jpg) ![Image after resizing](./transform_resize_after.jpg)

#### Rotate

You can rotate the image in 90 degree increments. ![Original image before transformation](./transform_before.jpg)
![Image after rotating](./transform_rotate_after.jpg)

#### Flip horizontally

You can flip the image horizontally. ![Original image before transformation](./transform_before.jpg)
![Image after horizontal flip](./transform_fliph_after.jpg)

#### Flip vertically

You can flip the image vertically. ![Original image before transformation](./transform_before.jpg)
![Image after vertical flip](./transform_flipv_after.jpg)

#### Crop

You can crop the image with a given bounding box. ![Original image before transformation](./transform_before.jpg)
![Image after cropping](./transform_crop_after.png)

#### I'm Feeling Lucky

The "I'm Feeling Lucky" transform enhances dark and bright colors in an image
and adjusts both color and optimizes contrast. ![Original image before transformation](./transform_before.jpg)
![Image after applying 'I'm Feeling Lucky' transform](./transform_lucky_after.png)

### Image Formats

**Accepted formats**: JPEG, PNG, WEBP, GIF (including animated GIF), BMP, TIFF,
and ICO
**Output formats**: JPEG, WEBP, and PNG If input and output formats
differ, the service converts the input to the output format before performing
the transformation.
**Note**: Multilayer TIFF images are not supported.

### Transforming Images

Transform images from Cloud Storage or Blobstore by creating the Image object
using `ImagesServiceFactory.makeImageFromBlob()`:

```java
// Make an image from a Cloud Storage object and transform it
BlobstoreService blobstoreService = BlobstoreServiceFactory.getBlobstoreService();
BlobKey blobKey = blobstoreService.createGsBlobKey("/gs/" + bucket + "/image.jpeg");
Image blobImage = ImagesServiceFactory.makeImageFromBlob(blobKey);
Transform rotate = ImagesServiceFactory.makeRotate(90);
Image rotatedImage = imagesService.applyTransform(rotate, blobImage);
// Write the transformed image
// back to a Cloud Storage object
gcsService.createOrReplace(
    new GcsFilename(bucket, "rotatedImage.jpeg"),
    new GcsFileOptions.Builder().mimeType("image/jpeg").build(),
    ByteBuffer.wrap(rotatedImage.getImageData()));
```

The `applyTransform()` method returns the result of transforms, or throws
`ImagesServiceFailureException` if the result exceeds 32 megabytes.

### Using getServingUrl

The `getServingUrl()` method generates a fixed, dedicated URL for an image
stored in Cloud Storage or Blobstore:

```java
// Create a fixed dedicated URL that
// points to the GCS hosted file
ServingUrlOptions options =
    ServingUrlOptions.Builder.withGoogleStorageFileName("/gs/" + bucket + "/image.jpeg")
        .imageSize(150)
        .crop(true)
        .secureUrl(true);
String url = imagesService.getServingUrl(options);
```

The generated URL uses highly-optimized image serving infrastructure separate
from your application, avoiding load on your app and providing cost-effective
serving. The URL is always publicly accessible but not guessable. Example
default URL format: `http://lhx.ggpht.com/randomStringImageId`

#### Dynamic Resizing and Cropping

Resize and crop images dynamically by specifying arguments in the URL: - `=sxx`:
Where xx is an integer from 0–2560 representing the longest side length in
pixels. Example: `=s32` resizes so the longest dimension is 32 pixels. -
`=sxx-c`: Where xx is an integer from 0–2560 and `-c` tells the system to crop
the image. Examples:

```

# Resize to 32 pixels (aspect-ratio preserved)

http://lhx.ggpht.com/randomStringImageId=s32

# Crop the image to 32 pixels

http://lhx.ggpht.com/randomStringImageId=s32-c
```

**Important**: Only resize and crop arguments are supported. Using other
arguments may cause breaking failures.

#### Deleting Serving URLs

Stop serving a URL using the `deleteServingUrl()` method:

```java
imagesService.deleteServingUrl(blobKey);
```

Avoid directly deleting images in Cloud Storage or Blobstore, as they can remain
accessible through serving URLs. Serving URLs stop working if the application
that created them is disabled or deleted, even if the underlying image remains
available.

### Images and the Development Server

The development server uses your local machine to perform Images service
capabilities. The Java development server uses the ImageIO framework to simulate
the Image service. Limitations include: - The "I'm Feeling Lucky" photo
enhancement feature is not supported - WEBP format is only supported if a
suitable decoder plugin is installed (the Java VP8 decoder can be used as an
example) - The `getServingUrl()` method is not available

### Quotas, Limits, and Pricing

**Pricing**: There is no additional charge for using the Images API.

**Quotas**: Each Images API request counts toward the Image Manipulation API
Calls quota. An app can perform multiple transformations in a single API call.

-   Data sent to the Images service counts toward the Data Sent to (Images) API
    quota
-   Data received counts toward the Data Received from (Images) API quota
-   Each transformation counts toward the Transformations Executed quota

See the Quotas documentation for details. View current quota usage on the Google
Cloud console Quota Details tab.

**Limits**:

Limit                                            | Amount
------------------------------------------------ | -------------
Maximum data size of image sent to service       | 32 megabytes
Maximum data size of image received from service | 32 megabytes
Maximum size of image (sent or received)         | 50 megapixels
