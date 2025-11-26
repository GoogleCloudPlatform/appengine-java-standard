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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.appengine.api.NamespaceManager;
import com.google.appengine.api.blobstore.BlobKey;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.tools.development.testing.LocalBlobstoreServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalDatastoreServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalImagesServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import com.google.common.io.Resources;
import com.google.common.truth.Expect;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.function.BiConsumer;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Tests the LocalBlobImageServlet.
 *
 */
@RunWith(JUnit4.class)
public class LocalBlobImageServletTest {
  // Set by reflection from GenerateGoldenFiles.java.
  private static BiConsumer<File, byte[]> generateGoldenFiles;

  private static final String TESTDATA = "javatests/com/google/appengine/api/images/dev/testdata";

  @Rule
  public final Expect expect = Expect.create();

  @Mock private HttpServletRequest mockRequest;
  @Mock private HttpServletResponse mockResponse;
  @Mock private ServletOutputStream mockOutputStream;
  private final LocalServiceTestHelper helper =
      new LocalServiceTestHelper(
          new LocalImagesServiceTestConfig(),
          new LocalBlobstoreServiceTestConfig(),
          new LocalDatastoreServiceTestConfig());

  private LocalBlobImageServlet servlet;
  private LocalImagesService imagesService;
  private byte[] validImage;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    helper.setUp();
    imagesService = LocalImagesServiceTestConfig.getLocalImagesService();
    servlet =
        new LocalBlobImageServlet() {
          @Override
          LocalImagesService getLocalImagesService() {
            return imagesService;
          }
        };
    servlet.init();
    validImage = readImage("before.png");
  }

  @After
  public void tearDown() {
    helper.tearDown();
    imagesService = null;
    validImage = null;
    servlet = null;
  }

  /**
   * Reads in an image and returns its contents as a byte array.
   *
   * @param filename Name of the file to be opened.
   * @return The file contents as a byte array.
   */
  private byte[] readImage(String filename) throws IOException {
    String jdk11Name = newerJDKFileName(filename);
    URL resource = getClass().getResource("testdata/" + jdk11Name);

    if (resource == null) {
      resource = getClass().getResource("testdata/" + filename);
    }
    assertWithMessage("Could not find resource for %s", filename).that(resource).isNotNull();
    return Resources.toByteArray(resource);
  }

  /**
   * Adds a blob represented by the {@code blobKey} and {@code image} arguments to the BlobStorage.
   *
   * @param blobKey a blob key
   * @param image the image bytes associated with the key
   */
  protected void addBlob(String blobKey, byte[] image) throws Exception {
    try (OutputStream out = imagesService.getBlobStorage().storeBlob(new BlobKey(blobKey))) {
      out.write(image);
    }
    // Add an entry so that the blob can be served.
    addServingUrlEntry(blobKey);
  }

  void addServingUrlEntry(String blobKey) {
    DatastoreService datastoreService = DatastoreServiceFactory.getDatastoreService();
    String namespace = NamespaceManager.get();
    try {
      NamespaceManager.set("");
      Entity blobServingUrlEntity = new Entity(ImagesReservedKinds.BLOB_SERVING_URL_KIND, blobKey);
      blobServingUrlEntity.setProperty("blob_key", blobKey);
      datastoreService.put(blobServingUrlEntity);
    } finally {
      NamespaceManager.set(namespace);
    }
  }

  void removeServingUrlEntry(String blobKey) {
    DatastoreService datastoreService = DatastoreServiceFactory.getDatastoreService();
    String namespace = NamespaceManager.get();
    try {
      NamespaceManager.set("");
      Key key = KeyFactory.createKey(null, ImagesReservedKinds.BLOB_SERVING_URL_KIND, blobKey);
      datastoreService.delete(key);
    } finally {
      NamespaceManager.set(namespace);
    }
  }

  /**
   * Tests parsing a URL and asserts some properties about it.
   *
   * @param url the URL to parse
   * @param blobkey the expected parsed blobkey
   * @param resize the expected resize parameter. If set to -1, the assertion is skipped
   * @param crop the expected crop parameter
   */
  protected void runParseUrl(String url, String blobkey, int resize, boolean crop) {
    LocalBlobImageServlet.ParsedUrl parsedUrl =
        LocalBlobImageServlet.ParsedUrl.createParsedUrl(url);
    assertThat(parsedUrl.getBlobKey()).isEqualTo(blobkey);
    if (resize >= 0) {
      assertThat(parsedUrl.getResize()).isEqualTo(resize);
    }
    assertThat(parsedUrl.getCrop()).isEqualTo(crop);
  }

  /**
   * Tests that parsing a URL will throw an {@code IllegalArgumentException}.
   *
   * @param url the URL to parse
   */
  protected void runParseUrlIgnoreException(String url) {
    assertThrows(
        IllegalArgumentException.class, () -> LocalBlobImageServlet.ParsedUrl.createParsedUrl(url));
  }

  /**
   * Transforms an image and verifies the resulting image.
   *
   * @param url the URL representing an image to transform
   * @param expectedImageFile the expected image returned
   */
  private void runTransform(String url, String expectedImageFile) throws Exception {
    LocalBlobImageServlet.Image image =
        servlet.transformImage(LocalBlobImageServlet.ParsedUrl.createParsedUrl(url));
    byte[] expectedImage = readImage(expectedImageFile);
    if (!Arrays.equals(image.getImage(), expectedImage)) {
      File jdkFile = new File(TESTDATA, newerJDKFileName(expectedImageFile));
      if (!jdkFile.exists() && generateGoldenFiles != null) {
        generateGoldenFiles.accept(jdkFile, image.getImage());
      }
    }
    expect.that(image.getImage()).isEqualTo(expectedImage);
  }

  private static final String newerJDKFileName(String filename) {
    // For all JDK non Java8, the files containing the name jdk11 are working.
    return filename.replaceAll("(?!-jdk11)\\.(png|jpg)$", "-jdk11.$1");
  }

  @Test
  public void testParseUrl() throws Exception {
    runParseUrl("/_ah/img/SomeBlobKey", "SomeBlobKey", -1, false);
    runParseUrl("/_ah/img/SomeBlobKey=s32", "SomeBlobKey", 32, false);
    runParseUrl("/_ah/img/SomeBlobKey=s0", "SomeBlobKey", 0, false);
    runParseUrl("/_ah/img/SomeBlobKey=s32-c", "SomeBlobKey", 32, true);
    runParseUrl("/_ah/img/foo=s123", "foo", 123, false);
    runParseUrl("/_ah/img/foo=s800-c", "foo", 800, true);
    runParseUrl(
        "/_ah/img/encoded_gs_key:SomeBlobKey=s800-c", "encoded_gs_key:SomeBlobKey", 800, true);
    runParseUrlIgnoreException("_ah/img/foo");
    runParseUrlIgnoreException("/_ah/img/foo=s1601");
    runParseUrlIgnoreException("/_ah/img/foo=s1601-c");
  }

  /** Transforms some blobs with resize and crop. */
  @Test
  public void testTransform() throws Exception {
    addBlob("Landscape", validImage);
    addBlob("Portrait", readImage("after-rotate-90.png"));
    addBlob("Jpeg", readImage("before.jpg"));
    addBlob("TransparentPng", readImage("flame.png"));
    addBlob("LargeTransparentPng", readImage("large_flame.png"));
    addBlob("Gif", readImage("test.gif"));
    addBlob("Bridge", readImage("bridge.jpg"));
    runTransform("/_ah/img/Landscape=s32-c", "after-landscape-s32-c.png");
    runTransform("/_ah/img/Landscape=s32", "after-landscape-s32.png");
    runTransform("/_ah/img/Portrait=s32-c", "after-portrait-s32-c.png");
    runTransform("/_ah/img/Portrait=s32", "after-portrait-s32.png");
    runTransform("/_ah/img/Jpeg=s32", "after.jpg");
    runTransform("/_ah/img/TransparentPng=s32", "scaled_flame.png");
    runTransform("/_ah/img/TransparentPng=s32-c", "scaled_flame.png");
    runTransform("/_ah/img/LargeTransparentPng=s32-c", "cropped_flame.png");
    runTransform("/_ah/img/Bridge", "after-bridge.jpg");
    runTransform("/_ah/img/Gif", "giftranscode.png");
  }

  @Test
  public void testServeJpeg() throws Exception {
    addBlob("JpegBlob", readImage("before.jpg"));
    when(mockResponse.getOutputStream()).thenReturn(mockOutputStream);
    when(mockRequest.getRequestURI()).thenReturn("/_ah/img/JpegBlob=s32");
    servlet.doGet(mockRequest, mockResponse);
    verify(mockResponse).setContentType("image/jpeg");
    verify(mockOutputStream).write(readImage("after.jpg"));
    verify(mockOutputStream).close();
  }

  /** Executes a servlet get on a landscape image with s32-c option. */
  @Test
  public void testDoGetLanscape_s32c() throws Exception {
    addBlob("Landscape", validImage);
    when(mockResponse.getOutputStream()).thenReturn(mockOutputStream);
    when(mockRequest.getRequestURI()).thenReturn("/_ah/img/Landscape=s32-c");
    servlet.doGet(mockRequest, mockResponse);
    verify(mockResponse).setContentType("image/png");
    verify(mockOutputStream).write(readImage("after-landscape-s32-c.png"));
    verify(mockOutputStream).close();
  }

  /** Executes a servlet get on a portrait image with s32-c option. */
  @Test
  public void testDoGetPortrait_s32c() throws Exception {
    addBlob("Portrait", readImage("after-rotate-90.png"));
    when(mockResponse.getOutputStream()).thenReturn(mockOutputStream);
    when(mockRequest.getRequestURI()).thenReturn("/_ah/img/Portrait=s32-c");
    servlet.doGet(mockRequest, mockResponse);
    verify(mockResponse).setContentType("image/png");
    verify(mockOutputStream).write(readImage("after-portrait-s32-c.png"));
    verify(mockOutputStream).close();
  }

  /** Tests a servlet get returning a 404. */
  @Test
  public void testDoGet_notFound() throws Exception {
    addServingUrlEntry("Portrait");
    when(mockResponse.getOutputStream()).thenReturn(mockOutputStream);
    when(mockRequest.getRequestURI()).thenReturn("/_ah/img/Portrait=s32-c");
    servlet.doGet(mockRequest, mockResponse);
    verify(mockResponse)
        .sendError(HttpServletResponse.SC_NOT_FOUND, "ApplicationError: 6: Could not read blob.");
    verify(mockOutputStream).close();
  }

  /** Tests a servlet get returning a 500. */
  @Test
  public void testDoGet_internalServerError() throws Exception {
    when(mockResponse.getOutputStream()).thenThrow(new IOException("TestError"));
    servlet.doGet(mockRequest, mockResponse);
    verify(mockResponse).sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "TestError");
  }

  /** Tests a servlet get returning a 500 due to unsupported size. */
  @Test
  public void testDoGet_unsupportedSize() throws Exception {
    when(mockResponse.getOutputStream()).thenReturn(mockOutputStream);
    when(mockRequest.getRequestURI()).thenReturn("/_ah/img/Portrait=s1601-c");
    servlet.doGet(mockRequest, mockResponse);
    verify(mockResponse).sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Invalid resize");
    verify(mockOutputStream).close();
  }
}
