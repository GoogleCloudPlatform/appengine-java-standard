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

/**
 * Provides facilities for the creation and manipulation of images. The starting point is the {@link
 * com.google.appengine.api.images.ImagesServiceFactory} class, which can produce the {@link
 * com.google.appengine.api.images.ImagesService}, but also the basic {@link
 * com.google.appengine.api.images.Image} object and {@link
 * com.google.appengine.api.images.Transform} classes. More information is available in the <a
 * href="http://cloud.google.com/appengine/docs/java/images/">on-line documentation</a>.
 *
 * <p>Image data is represented as a {@code byte[]} of data, in any of the supported formats: JPEG,
 * PNG, GIF (including animated GIF), BMP, TIFF, and ICO formats. The format can be accessed via the
 * {@link com.google.appengine.api.images.Image#getFormat()} method. The image format may be
 * converted during transformation.
 *
 * <p>Supported transformations include cropping, resizing, rotating in 90-degree increments,
 * horizontal and vertical flips, and automated color enhancement.
 *
 * @see com.google.appengine.api.images.ImagesService
 * @see <a href="http://cloud.google.com/appengine/docs/java/images/">The Images Java API in the
 *     <em>Google App Engine Developer's Guide</em></a>.
 */
package com.google.appengine.api.images;
