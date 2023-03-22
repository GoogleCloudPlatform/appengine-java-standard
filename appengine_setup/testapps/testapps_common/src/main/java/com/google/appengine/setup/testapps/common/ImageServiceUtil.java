/*
 * Copyright 2022 Google LLC
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

package com.google.appengine.setup.testapps.common;

import com.google.appengine.api.images.Image;
import com.google.appengine.api.images.ImagesService;
import com.google.appengine.api.images.ImagesServiceFactory;
import com.google.appengine.api.images.Transform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import javax.imageio.ImageIO;

class ImageServiceUtil {
    static Image readImage() throws IOException {
        BufferedImage image = ImageIO.read(ImageServiceUtil.class.getClassLoader()
            .getResourceAsStream("static/image.png"));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return ImagesServiceFactory.makeImage(baos.toByteArray());
    }

    static String getBase64HtmlImage(Image image) {
        return String.format(
            "<p style='text-align:center;'><img src='data:image/png;base64,%s' alt='image...'></p>",
            Base64.getEncoder().encodeToString(image.getImageData()));
    }

    static Image transformImage(Image image) {
        ImagesService imagesService = ImagesServiceFactory.getImagesService();
        int resizeWidth = 50;
        int resizeHeight = 50;
        Transform resize = ImagesServiceFactory.makeResize(resizeWidth, resizeHeight, true);
        Image resizedImage = imagesService.applyTransform(resize, image);
        return resizedImage;
    }
}
