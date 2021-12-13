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

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;

import com.google.appengine.api.blobstore.BlobKey;
import com.google.appengine.api.images.Image.Format;
import com.google.common.io.Resources;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for the ImageImpl implementation.
 *
 */
@RunWith(JUnit4.class)
public class ImageImplTest {

  /** Tests that getImageData returns an identical array to the one used to create the image. */
  @Test
  public void testGetImageData() {
    byte[] data = "an image".getBytes(UTF_8);
    Image image = new ImageImpl(data);
    assertThat(image.getImageData()).isEqualTo(data);
    data = "a different image".getBytes(UTF_8);
    image = new ImageImpl(data);
    assertThat(image.getImageData()).isEqualTo(data);
  }

  /**
   * Tests that setImageData correctly sets the image data and getImageData returns what was passed
   * into setImageData.
   */
  @Test
  public void testSetAndGetImageData() {
    byte[] data = "an image".getBytes(UTF_8);
    Image image = new ImageImpl(data);
    assertThat(image.getImageData()).isEqualTo(data);
    data = "a different image".getBytes(UTF_8);
    image.setImageData(data);
    assertThat(image.getImageData()).isEqualTo(data);
  }

  private void testImage(String fileName, int width, int height, Format format) throws Exception {
    byte[] buffer = Resources.toByteArray(getClass().getResource("testdata/" + fileName));
    Image image = new ImageImpl(buffer);
    assertThat(image.getWidth()).isEqualTo(width);
    assertThat(image.getHeight()).isEqualTo(height);
    assertThat(image.getFormat()).isEqualTo(format);
    assertThat(image.getBlobKey()).isEqualTo(null);
  }

  @Test
  public void testBlobKey() {
    BlobKey blobKey = new BlobKey("foo");
    Image image = new ImageImpl(blobKey);
    assertThat(image.getBlobKey()).isEqualTo(blobKey);
    byte[] data = "a different image".getBytes(UTF_8);
    image.setImageData(data);
    assertThat(image.getBlobKey()).isEqualTo(null);
    assertThat(image.getImageData()).isEqualTo(data);
  }

  /**
   * Runs a test of a valid image and checks that the correct width and height are returned by
   * getWidth and getHeight.
   *
   * @param extension file extension of the input file
   * @param width width of the image to be used
   * @param height height of the image to be used
   * @throws Exception
   */
  void runValidImageTest(String extension, int width, int height, Format format) throws Exception {
    String filename = String.format("%dx%d.%s", width, height, extension);
    testImage(filename, width, height, format);
  }

  @Test
  public void testMoreSampleJpegs() throws Exception {
    testImage("sample1.jpg", 2950, 2213, Format.JPEG);
    testImage("sample2.jpg", 600, 800, Format.JPEG);
  }

  @Test
  public void testGetWidthAndGetHeight_validImages() throws Exception {
    String[] extensions = new String[] {"png", "gif", "jpg", "bmp", "tif", "ico", "webp", "webpx"};
    Format[] formats =
        new Format[] {
          Format.PNG,
          Format.GIF,
          Format.JPEG,
          Format.BMP,
          Format.TIFF,
          Format.ICO,
          Format.WEBP,
          Format.WEBP
        };
    int[][] sizes = new int[][] {new int[] {145, 111}, new int[] {32, 24}};
    for (int i = 0; i < extensions.length; i++) {
      for (int[] size : sizes) {
        runValidImageTest(extensions[i], size[0], size[1], formats[i]);
      }
    }
  }

  /** Tests that the right exception is thrown if the image data is not a valid image. */
  @Test
  public void testGetWidthAndGetHeight_notAnImage() {
    byte[] imageData = "image".getBytes(UTF_8);
    Image image1 = new ImageImpl(imageData);
    assertThrows(IllegalArgumentException.class, image1::getWidth);
    imageData = "a bigger image".getBytes(UTF_8);
    Image image2 = new ImageImpl(imageData);
    assertThrows(IllegalArgumentException.class, image2::getWidth);
  }

  /** Tests that an incomplete GIF image data results in the right exception being thrown. */
  @Test
  public void testGetWidthAndGetHeight_invalidGif() {
    byte[] imageData = "GIF87AAA".getBytes(UTF_8);
    Image image1 = new ImageImpl(imageData);
    assertThrows(IllegalArgumentException.class, image1::getWidth);
    imageData = "GIF89AAA".getBytes(UTF_8);
    Image image2 = new ImageImpl(imageData);
    assertThrows(IllegalArgumentException.class, image2::getWidth);
  }

  /** Tests that an incomplete PNG image data results in the right exception being thrown. */
  @Test
  public void testGetWidthAndGetHeight_invalidPng() {
    // Starting bytes of a png header.
    byte[] imageData = new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a};
    Image image = new ImageImpl(imageData);
    assertThrows(IllegalArgumentException.class, image::getWidth);
  }

  /**
   * Tests that a JPEG image missing the header element containing image dimensions results in the
   * right exception being thrown.
   *
   * @throws Exception
   */
  @Test
  public void testGetWidthAndGetHeight_invalidJpeg() throws Exception {
    byte[] buffer = Resources.toByteArray(getClass().getResource("testdata/broken.jpg"));
    Image image = new ImageImpl(buffer);
    assertThrows(IllegalArgumentException.class, image::getWidth);
  }

  /**
   * Tests that width and height values of 0 in ICO files are correctly identified as 256 and that
   * other values are not affected by the handling of this.
   */
  @Test
  public void testGetWidthAndGetHeight_icoCornerCases() {
    // ICO header of a 256x256 image.
    byte[] imageData = new byte[] {0, 0, 1, 0, 0, 0, 0, 0};
    Image image = new ImageImpl(imageData);
    assertThat(image.getWidth()).isEqualTo(256);
    assertThat(image.getHeight()).isEqualTo(256);
    // ICO header of a 2x256 image.
    imageData = new byte[] {0, 0, 1, 0, 0, 0, 2, 0};
    image = new ImageImpl(imageData);
    assertThat(image.getWidth()).isEqualTo(2);
    assertThat(image.getHeight()).isEqualTo(256);
    // ICO header of a 256x1 image.
    imageData = new byte[] {0, 0, 1, 0, 0, 0, 0, 1};
    image = new ImageImpl(imageData);
    assertThat(image.getWidth()).isEqualTo(256);
    assertThat(image.getHeight()).isEqualTo(1);
    // ICO header of a 255x256 image.
    imageData = new byte[] {0, 0, 1, 0, 0, 0, (byte) 0xff, 0};
    image = new ImageImpl(imageData);
    assertThat(image.getWidth()).isEqualTo(255);
    assertThat(image.getHeight()).isEqualTo(256);
    // ICO header of a 256x255 image.
    imageData = new byte[] {0, 0, 1, 0, 0, 0, 0, (byte) 0xff};
    image = new ImageImpl(imageData);
    assertThat(image.getWidth()).isEqualTo(256);
    assertThat(image.getHeight()).isEqualTo(255);
  }

  /** Tests that headers of all BMP versions are correctly parsed. */
  @Test
  public void testGetWidthAndGetHeight_bmpVersions() {
    // BMP V1 header.
    byte[] imageData =
        new byte[] {
          'B', 'M', 0x1a, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x0C, 0, 0, 0, 0x12, 0x00, 0x34, 0x00,
          0x01, 0, 0x01, 0
        };
    Image image = new ImageImpl(imageData);
    assertThat(image.getWidth()).isEqualTo(0x12);
    assertThat(image.getHeight()).isEqualTo(0x34);

    // BMP V2 header.
    imageData =
        new byte[] {
          'B', 'M', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x40, 0, 0, 0, 0x12, 0, 0, 0, 0x34, 0, 0, 0
        };
    image = new ImageImpl(imageData);
    assertThat(image.getWidth()).isEqualTo(0x12);
    assertThat(image.getHeight()).isEqualTo(0x34);

    // BMP V3 header.
    imageData =
        new byte[] {
          'B', 'M', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x28, 0, 0, 0, 0x12, 0, 0, 0, 0x34, 0, 0, 0
        };
    image = new ImageImpl(imageData);
    assertThat(image.getWidth()).isEqualTo(0x12);
    assertThat(image.getHeight()).isEqualTo(0x34);

    // BMP V4 header.
    imageData =
        new byte[] {
          'B', 'M', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x6c, 0, 0, 0, 0x12, 0, 0, 0, 0x34, 0, 0, 0
        };
    image = new ImageImpl(imageData);
    assertThat(image.getWidth()).isEqualTo(0x12);
    assertThat(image.getHeight()).isEqualTo(0x34);

    // BMP V5 header.
    imageData =
        new byte[] {
          'B', 'M', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x7c, 0, 0, 0, 0x12, 0, 0, 0, 0x34, 0, 0, 0
        };
    image = new ImageImpl(imageData);
    assertThat(image.getWidth()).isEqualTo(0x12);
    assertThat(image.getHeight()).isEqualTo(0x34);
  }

  /** Tests that a big endian format TIFF is correctly parsed. */
  @Test
  public void testGetWidthAndGetHeight_tiffBigEndian() {
    // TIFF Big Endian header with one IFD and directory entries for width and
    // height.
    byte[] imageData =
        new byte[] {
          'M',
          'M',
          0x00,
          '*',
          0x00,
          0x00,
          0x00,
          0x08,
          0x00,
          0x02,
          0x01,
          0x00,
          0x00,
          0x04,
          0x00,
          0x00,
          0x00,
          0x01,
          0x00,
          0x00,
          '0',
          '9',
          0x01,
          0x01,
          0x00,
          0x04,
          0x00,
          0x00,
          0x00,
          0x01,
          0x00,
          0x00,
          (byte) 0xdd,
          (byte) 0xd5
        };
    Image image = new ImageImpl(imageData);
    assertThat(image.getWidth()).isEqualTo(12345);
    assertThat(image.getHeight()).isEqualTo(56789);
  }

  @Test
  public void testGetWidthAndGetHeight_invalidWebp() {

    byte[] baseImageData =
        new byte[] {
          'R', 'I', 'F', 'F', 0x00, 0x00, 0x00, 0x00, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' ', 0x00,
          0x00, 0x00, 0x00
        };

    Image image1 = new ImageImpl(baseImageData);
    // Header should be at least 30 bytes
    assertThrows(IllegalArgumentException.class, image1::getWidth);

    byte[] imageData = Arrays.copyOf(baseImageData, 30);
    Arrays.fill(imageData, 20, 29, (byte) 0x00);

    // Test key frame not set
    Image image2 = new ImageImpl(imageData);
    assertThrows(IllegalArgumentException.class, image2::getWidth);

    // Test invalid encode option
    imageData[20] = (byte) 0xEE;
    Image image3 = new ImageImpl(imageData);
    assertThrows(IllegalArgumentException.class, image3::getWidth);

    // Test invalid frame
    imageData[20] = 0x03;
    Image image4 = new ImageImpl(imageData);
    assertThrows(IllegalArgumentException.class, image4::getWidth);
  }
}
