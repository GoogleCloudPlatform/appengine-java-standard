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

package com.google.apphosting.utils.servlet;

import com.google.appengine.api.blobstore.BlobInfo;
import com.google.appengine.api.blobstore.BlobKey;
import com.google.appengine.api.blobstore.BlobstoreService;
import com.google.appengine.api.blobstore.BlobstoreServiceFactory;
import com.google.appengine.api.blobstore.FileInfo;
import com.google.appengine.tools.development.testing.FakeHttpServletRequest;
import com.google.appengine.tools.development.testing.FakeHttpServletResponse;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import junit.framework.TestCase;

/** Provides tests for {@link ParseBlobUploadFilter}. */
public class ParseBlobUploadFilterTest extends TestCase {
  // This REQUEST is an abuse, as either all files or none will have the header
  // X-appengine-cloud-storage-object set.
  private static final String REQUEST =
      "--foo\r\n"
          + "Content-Disposition: form-data; name=\"string\"\r\n\r\n"
          + "Example string.\r\n"
          + "--foo\r\n"
          + "Content-Type: message/external-body; "
          + "charset=ISO-8859-1; blob-key=\"blob-0\"\r\n"
          + "Content-Disposition: form-data; "
          + "name=upload-0; filename=\"file-0.jpg\"\r\n"
          + "\r\n"
          + "Content-Type: image/jpeg\r\n"
          + "MIME-Version: 1.0\r\n"
          + "Content-Length: 5\r\n"
          + "X-appengine-upload-creation: 2008-11-12 10:40:00.020000\r\n"
          + "X-appengine-cloud-storage-object: /bucket_name/some_random_filename1\r\n"
          + "Content-MD5: md5-hash\r\n"
          + "Content-Disposition: form-data; name=\"upload-0\"; filename=\"file-0.jpg\"\r\n"
          + "--foo\r\n"
          + "Content-Type: message/external-body; "
          + "charset=ISO-8859-1; blob-key=\"blob-1\"\r\n"
          + "Content-Disposition: form-data; "
          + "name=upload-0; filename=\"file-1.jpg\"\r\n"
          + "\r\n"
          + "Content-Type: image/jpeg\r\n"
          + "MIME-Version: 1.0\r\n"
          + "Content-Length: 5\r\n"
          + "X-appengine-upload-creation: 2008-11-12 10:40:00.020000\r\n"
          + "Content-MD5: md5-hash\r\n"
          + "Content-Disposition: form-data; name=\"upload-0\"; filename=\"file-1.jpg\"\r\n"
          + "--foo\r\n"
          + "Content-type: image/png\r\n"
          + "Content-Disposition: form-data; name=\"image\"; filename=\"foo.png\"\r\n\r\n"
          + "...\r\n"
          + "--foo\r\n"
          + "Content-type: text/plain\r\n"
          + "Content-Disposition: form-data; name=\"text\"; filename=\"example.txt\"\r\n\r\n"
          + "Example content.\r\n";

  public void testParse() throws Exception {
    FakeHttpServletRequest req = new FakeHttpServletRequest();
    req.setContentType("multipart/form-data; boundary=\"foo\"");
    req.setPostData(REQUEST, "UTF-8");
    req.setHeader("X-AppEngine-BlobUpload", "true");

    final FakeHttpServletResponse resp = new FakeHttpServletResponse();

    new ParseBlobUploadFilter()
        .doFilter(
            req,
            resp,
            (request, unusedResponse) -> {
              assertEquals("Example string.", request.getParameter("string"));
              assertEquals(null, request.getParameter("image"));

              BlobstoreService blobstoreService = BlobstoreServiceFactory.getBlobstoreService();
              Map<String, List<BlobKey>> blobs =
                  blobstoreService.getUploads((HttpServletRequest) request);
              assertEquals(1, blobs.size());
              List<BlobKey> keys = blobs.get("upload-0");
              assertEquals(2, keys.size());
              assertEquals(new BlobKey("blob-0"), keys.get(0));
              assertEquals(new BlobKey("blob-1"), keys.get(1));
            });
  }

  public void testParseBlobInfos() throws Exception {
    FakeHttpServletRequest req = new FakeHttpServletRequest();
    req.setContentType("multipart/form-data; boundary=\"foo\"");
    req.setPostData(REQUEST, "UTF-8");
    req.setHeader("X-AppEngine-BlobUpload", "true");

    final FakeHttpServletResponse resp = new FakeHttpServletResponse();

    new ParseBlobUploadFilter()
        .doFilter(
            req,
            resp,
            (request, unusedResponse) -> {
              assertEquals("Example string.", request.getParameter("string"));
              assertEquals(null, request.getParameter("image"));

              BlobstoreService blobstoreService = BlobstoreServiceFactory.getBlobstoreService();
              Map<String, List<BlobInfo>> blobs =
                  blobstoreService.getBlobInfos((HttpServletRequest) request);
              assertEquals(1, blobs.size());
              List<BlobInfo> infos = blobs.get("upload-0");
              assertEquals(2, infos.size());

              @SuppressWarnings("JavaUtilDate")
              Date expectedCreationDate =
                  new Date(
                      new GregorianCalendar(2008, 11 - 1, 12, 10, 40, 00).getTimeInMillis() + 20);
              BlobInfo expected1 =
                  new BlobInfo(
                      new BlobKey("blob-0"),
                      "image/jpeg",
                      expectedCreationDate,
                      "file-0.jpg",
                      5,
                      "md5-hash",
                      "/bucket_name/some_random_filename1");
              BlobInfo expected2 =
                  new BlobInfo(
                      new BlobKey("blob-1"),
                      "image/jpeg",
                      expectedCreationDate,
                      "file-1.jpg",
                      5,
                      "md5-hash");

              assertEquals(expected1, infos.get(0));
              assertEquals(expected2, infos.get(1));
            });
  }

  public void testParseFileInfos() throws Exception {
    FakeHttpServletRequest req = new FakeHttpServletRequest();
    req.setContentType("multipart/form-data; boundary=\"foo\"");
    req.setPostData(REQUEST, "UTF-8");
    req.setHeader("X-AppEngine-BlobUpload", "true");

    final FakeHttpServletResponse resp = new FakeHttpServletResponse();

    new ParseBlobUploadFilter()
        .doFilter(
            req,
            resp,
            (request, unusedResponse) -> {
              assertEquals("Example string.", request.getParameter("string"));
              assertEquals(null, request.getParameter("image"));

              BlobstoreService blobstoreService = BlobstoreServiceFactory.getBlobstoreService();
              Map<String, List<FileInfo>> files =
                  blobstoreService.getFileInfos((HttpServletRequest) request);
              assertEquals(1, files.size());
              List<FileInfo> infos = files.get("upload-0");
              assertEquals(2, infos.size());

              @SuppressWarnings("JavaUtilDate")
              Date expectedCreationDate =
                  new Date(
                      new GregorianCalendar(2008, 11 - 1, 12, 10, 40, 00).getTimeInMillis() + 20);
              FileInfo expected1 =
                  new FileInfo(
                      "image/jpeg",
                      expectedCreationDate,
                      "file-0.jpg",
                      5,
                      "md5-hash",
                      "/bucket_name/some_random_filename1");
              FileInfo expected2 =
                  new FileInfo(
                      "image/jpeg", expectedCreationDate, "file-1.jpg", 5, "md5-hash", null);

              assertEquals(expected1, infos.get(0));
              assertEquals(expected2, infos.get(1));
            });
  }
}
