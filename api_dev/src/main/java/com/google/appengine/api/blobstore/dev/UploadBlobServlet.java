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

package com.google.appengine.api.blobstore.dev;

import static com.google.common.io.BaseEncoding.base64Url;

import com.google.appengine.api.blobstore.BlobInfo;
import com.google.appengine.api.blobstore.BlobKey;
import com.google.appengine.tools.development.ApiProxyLocal;
import com.google.appengine.tools.development.Clock;
import com.google.apphosting.utils.servlet.MultipartMimeUtils;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Closeables;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.security.AccessController;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.mail.BodyPart;
import javax.mail.MessagingException;
import javax.mail.internet.ContentType;
import javax.mail.internet.MimeMultipart;
import javax.mail.internet.ParseException;
import javax.servlet.ReadListener;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

/**
 * {@code UploadBlobServlet} handles blob uploads in the development
 * server.  The stub implementation of {@link
 * com.google.appengine.api.blobstore.BlobstoreService#createUploadUrl}
 * returns URLs that are mapped to this servlet.
 *
 * <p>Its primary responsibility is parsing multipart/form-data or
 * multipart/mixed requests made by web browsers.  To minimize
 * dependencies in the SDK, it does using the MimeMultipart class
 * included with JavaMail.
 *
 * <p>After the files are extracted from the multipart request body,
 * they are assigned {@code BlobKey} values and are committed to local
 * storage.  The multipart body parts are then replaced with
 * message/external-body parts that specify the {@link BlobKey} as
 * additional parameters in the Content-type header.
 *
 */
public final class UploadBlobServlet extends HttpServlet {
  private static final long serialVersionUID = -813190429684600745L;
  private static final Logger logger =
      Logger.getLogger(UploadBlobServlet.class.getName());

  static final String UPLOAD_HEADER = "X-AppEngine-BlobUpload";

  static final String UPLOADED_BLOBKEY_ATTR = "com.google.appengine.api.blobstore.upload.blobkeys";

  static final String UPLOADED_BLOBINFO_ATTR =
      "com.google.appengine.api.blobstore.upload.blobinfos";

  static final String UPLOAD_TOO_LARGE_RESPONSE =
    "Your client issued a request that was too large.";

  static final String UPLOAD_BLOB_TOO_LARGE_RESPONSE =
    UPLOAD_TOO_LARGE_RESPONSE +
    " Maximum upload size per blob limit exceeded.";

  static final String UPLOAD_TOTAL_TOO_LARGE_RESPONSE =
    UPLOAD_TOO_LARGE_RESPONSE +
    " Maximum total upload size limit exceeded.";

  private BlobStorage blobStorage;
  private BlobInfoStorage blobInfoStorage;
  private BlobUploadSessionStorage uploadSessionStorage;
  private SecureRandom secureRandom;
  private ApiProxyLocal apiProxyLocal;

  @Override
  public void init() throws ServletException {
    super.init();
    blobStorage = BlobStorageFactory.getBlobStorage();
    blobInfoStorage = BlobStorageFactory.getBlobInfoStorage();
    uploadSessionStorage = new BlobUploadSessionStorage();
    secureRandom = new SecureRandom();
    apiProxyLocal = (ApiProxyLocal) getServletContext().getAttribute(
        "com.google.appengine.devappserver.ApiProxyLocal");
  }

  @Override
  public void doPost(final HttpServletRequest req, final HttpServletResponse resp)
      throws ServletException, IOException {
    try {
      AccessController.doPrivileged(new PrivilegedExceptionAction<Object>() {
          @Override
          public Object run() throws ServletException, IOException {
            handleUpload(req, resp);
            return null;
          }
        });
    } catch (PrivilegedActionException ex) {
      Throwable cause = ex.getCause();
      if (cause instanceof ServletException) {
        throw (ServletException) cause;
      } else if (cause instanceof IOException) {
        throw (IOException) cause;
      } else {
        throw new ServletException(cause);
      }
    }
  }

  private String getSessionId(HttpServletRequest req) {
    return req.getPathInfo().substring(1);
  }

  private Map<String, String> getInfoFromStorage(BlobKey key, BlobUploadSession uploadSession) {
    BlobInfo blobInfo = blobInfoStorage.loadBlobInfo(key);
    Map<String, String> info = new HashMap<String, String>(6);
    info.put("key", key.getKeyString());
    info.put("content-type", blobInfo.getContentType());
    info.put("creation-date", new SimpleDateFormat(
        "yyyy-MM-dd HH:mm:ss.SSS").format(blobInfo.getCreation()));
    info.put("filename", blobInfo.getFilename());
    info.put("size", Long.toString(blobInfo.getSize()));
    info.put("md5-hash", blobInfo.getMd5Hash());

    if (uploadSession.hasGoogleStorageBucketName()) {
      String encoded = key.getKeyString()
          .substring(LocalBlobstoreService.GOOGLE_STORAGE_KEY_PREFIX.length());
      String decoded = new String(base64Url().omitPadding().decode(encoded));
      info.put("gs-name", decoded);
    }

    return info;
  }

  
  @SuppressWarnings("InputStreamSlowMultibyteRead")
  private void handleUpload(final HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    String sessionId = getSessionId(req);
    BlobUploadSession session = uploadSessionStorage.loadSession(sessionId);

    if (session == null) {
      resp.sendError(HttpServletResponse.SC_NOT_FOUND, "No upload session: " + sessionId);
      return;
    }

    Map<String, List<String>> blobKeys = new HashMap<String, List<String>>();
    Map<String, List<Map<String, String>>> blobInfos =
          new HashMap<String, List<Map<String, String>>>();
    final Map<String, List<String>> otherParams = new HashMap<String, List<String>>();
    try {
      MimeMultipart multipart = MultipartMimeUtils.parseMultipartRequest(req);
      int parts = multipart.getCount();

      // Check blob sizes upfront so we don't need to worry about rolling back
      // partial uploads.
      if (session.hasMaxUploadSizeBytes() || session.hasMaxUploadSizeBytesPerBlob()) {
        int totalSize = 0;
        int largestBlobSize = 0;
        for (int i = 0; i < parts; i++) {
          BodyPart part = multipart.getBodyPart(i);
          if (part.getFileName() != null && !part.getFileName().isEmpty()) {
            int size = part.getSize();
            if (size != -1) {
              totalSize += size;
              largestBlobSize = Math.max(size, largestBlobSize);
            } else {
              logger.log(Level.WARNING,
                         "Unable to determine size of upload part named " +
                         part.getFileName() + "." +
                         " Upload limit checks may not be accurate.");
            }
          }
        }
        if (session.hasMaxUploadSizeBytesPerBlob() &&
                session.getMaxUploadSizeBytesPerBlob() < largestBlobSize) {
          resp.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
              UPLOAD_BLOB_TOO_LARGE_RESPONSE);
          return;
        }
        if (session.hasMaxUploadSizeBytes() &&
                session.getMaxUploadSizeBytes() < totalSize) {
          resp.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
              UPLOAD_TOTAL_TOO_LARGE_RESPONSE);
          return;
        }
      }

      for (int i = 0; i < parts; i++) {
        BodyPart part = multipart.getBodyPart(i);
        String fieldName = MultipartMimeUtils.getFieldName(part);
        if (part.getFileName() != null) {
          if (part.getFileName().length() > 0) {
            BlobKey blobKey = assignBlobKey(session);
            List<String> keys = blobKeys.get(fieldName);
            if (keys == null) {
              keys = new ArrayList<String>();
              blobKeys.put(fieldName, keys);
            }
            keys.add(blobKey.getKeyString());

            MessageDigest digest = MessageDigest.getInstance("MD5");
            boolean swallowDueToThrow = true;
            OutputStream outStream = getBlobStorage().storeBlob(blobKey);
            try {
              InputStream inStream = part.getInputStream();
              try {
                final int bufferSize = (1 << 16);
                byte [] buffer = new byte[bufferSize];
                while (true) {
                  int bytesRead = inStream.read(buffer);
                  if (bytesRead == -1) {
                    break;
                  }
                  outStream.write(buffer, 0, bytesRead);
                  digest.update(buffer, 0, bytesRead);
                }
                outStream.close();
                byte[] hash = digest.digest();

                StringBuilder hashString = new StringBuilder();
                for (int j = 0; j < hash.length; j++) {
                  String hexValue = Integer.toHexString(0xFF & hash[j]);
                  if (hexValue.length() == 1) {
                    hashString.append("0");
                  }
                  hashString.append(hexValue);
                }

                String originalContentType = part.getContentType();
                String newContentType = createContentType(blobKey);
                DataSource dataSource = MultipartMimeUtils.createDataSource(
                    newContentType, new byte[0]);
                part.setDataHandler(new DataHandler(dataSource));
                part.addHeader("Content-type", newContentType);
                Clock clock = apiProxyLocal.getClock();
                blobInfoStorage.saveBlobInfo(new BlobInfo(
                    blobKey,
                    originalContentType,
                    new Date(clock.getCurrentTime()),
                    part.getFileName(),
                    part.getSize(),
                    hashString.toString()));
                swallowDueToThrow = false;
              } finally {
                Closeables.close(inStream, swallowDueToThrow);
              }
            } finally {
              Closeables.close(outStream, swallowDueToThrow);
            }

            // This codes must be run after the BlobInfo is persisted locally.
            List<Map<String, String>> infos = blobInfos.get(fieldName);
            if (infos == null) {
              infos = new ArrayList<Map<String, String>>();
              blobInfos.put(fieldName, infos);
            }
            infos.add(getInfoFromStorage(blobKey, session));
          }
        } else {
          List<String> values = otherParams.get(fieldName);
          if (values == null) {
            values = new ArrayList<String>();
            otherParams.put(fieldName, values);
          }
          values.add(MultipartMimeUtils.getTextContent(part));
        }
      }
      req.setAttribute(UPLOADED_BLOBKEY_ATTR, blobKeys);
      req.setAttribute(UPLOADED_BLOBINFO_ATTR, blobInfos);

      uploadSessionStorage.deleteSession(sessionId);

      ByteArrayOutputStream modifiedRequest = new ByteArrayOutputStream();
      String oldValue = System.setProperty("mail.mime.foldtext", "false");
      try {
        multipart.writeTo(modifiedRequest);
      } finally {
        if (oldValue == null) {
          System.clearProperty("mail.mime.foldtext");
        } else {
          System.setProperty("mail.mime.foldtext", oldValue);
        }
      }

      final byte[] modifiedRequestBytes = modifiedRequest.toByteArray();
      final ByteArrayInputStream modifiedRequestStream =
          new ByteArrayInputStream(modifiedRequestBytes);
      final BufferedReader modifiedReader =
          new BufferedReader(new InputStreamReader(modifiedRequestStream));

      HttpServletRequest wrappedRequest =
          new HttpServletRequestWrapper(req) {
            @Override
            public String getHeader(String name) {
              if (Ascii.equalsIgnoreCase(name, UPLOAD_HEADER)) {
                return "true";
              } else if (Ascii.equalsIgnoreCase(name, "Content-Length")) {
                return String.valueOf(modifiedRequestBytes.length);
              } else {
                return super.getHeader(name);
              }
            }

            @Override
            public Enumeration<String> getHeaderNames() {
              List<String> headers = Collections.list(super.getHeaderNames());
              headers.add(UPLOAD_HEADER);
              return Collections.enumeration(headers);
            }

            @Override
            public Enumeration<String> getHeaders(String name) {
              if (Ascii.equalsIgnoreCase(name, UPLOAD_HEADER)) {
                return Collections.enumeration(ImmutableList.of("true"));
              } else if (Ascii.equalsIgnoreCase(name, "Content-Length")) {
                return Collections.enumeration(
                    ImmutableList.of(String.valueOf(modifiedRequestBytes.length)));
              } else {
                return super.getHeaders(name);
              }
            }

            @Override
            public int getIntHeader(String name) {
              if (Ascii.equalsIgnoreCase(name, UPLOAD_HEADER)) {
                throw new NumberFormatException(UPLOAD_HEADER + "does not have an integer value");
              } else if (Ascii.equalsIgnoreCase(name, "Content-Length")) {
                return modifiedRequestBytes.length;
              } else {
                return super.getIntHeader(name);
              }
            }

            @Override
            public ServletInputStream getInputStream() {
              return new ServletInputStream() {
                @Override
                public int read() {
                  return modifiedRequestStream.read();
                }

                @Override
                public void close() throws IOException {
                  modifiedRequestStream.close();
                }

                @Override
                public boolean isFinished() {
                  return true;
                }

                @Override
                public boolean isReady() {
                  return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                  throw new UnsupportedOperationException();
                }
              };
            }

            @Override
            public BufferedReader getReader() {
              return modifiedReader;
            }

            @Override
            public Map<String, String[]> getParameterMap() {
              Map<String, String[]> parameters = super.getParameterMap();
              if (otherParams.isEmpty()) {
                return parameters;
              } else {
                // HttpServlet.getParameterMap() result is immutable so we need to take a copy.
                Map<String, String[]> map = new HashMap<>(parameters);
                for (Map.Entry<String, List<String>> entry : otherParams.entrySet()) {
                  map.put(entry.getKey(), entry.getValue().toArray(new String[0]));
                }
                // Maintain the semantic of ServletRequestWrapper by returning
                // an immutable map.
                return Collections.unmodifiableMap(map);
              }
            }

            @Override
            public Enumeration<String> getParameterNames() {
              List<String> allNames = new ArrayList<>();

              Enumeration<String> names = super.getParameterNames();
              while (names.hasMoreElements()) {
                allNames.add(names.nextElement());
              }
              allNames.addAll(otherParams.keySet());
              return Collections.enumeration(allNames);
            }

            @Override
            public String[] getParameterValues(String name) {
              if (otherParams.containsKey(name)) {
                return otherParams.get(name).toArray(new String[0]);
              } else {
                return super.getParameterValues(name);
              }
            }

            @Override
            public String getParameter(String name) {
              if (otherParams.containsKey(name)) {
                return otherParams.get(name).get(0);
              } else {
                return super.getParameter(name);
              }
            }
          };

      String successPath = session.getSuccessPath();
      getServletContext().getRequestDispatcher(successPath).forward(wrappedRequest,
                                                                    resp);
    } catch (MessagingException | NoSuchAlgorithmException ex) {
      throw new ServletException(ex);
    }
  }

  private BlobStorage getBlobStorage() {
    if (blobStorage == null) {
      // N.B.(schwardo): We need to make sure that the blobstore stub
      // has been initialized and has had a chance to initialize
      // BlobStorageFactory using its properties.
      apiProxyLocal.getService(LocalBlobstoreService.PACKAGE);

      blobStorage = BlobStorageFactory.getBlobStorage();
    }
    return blobStorage;
  }

  private String createContentType(BlobKey blobKey) throws ParseException {
    ContentType contentType = new ContentType("message/external-body");
    contentType.setParameter("blob-key", blobKey.getKeyString());
    return contentType.toString();
  }

  /**
   * Generate a random string to use as a blob key.
   */
  private BlobKey assignBlobKey(BlobUploadSession session) {
    // Python does this by generating an MD5 digest from a random
    // floating point number and the current time stamp.  Since
    // SecureRandom is already doing something cryptographically
    // secure and mixing in the current time, we should be able to get
    // by with just base64 encoding the random bytes directly.  We use
    // the same number of bytes as Python, however (MD5 outputs 128
    // bits).
    byte[] bytes = new byte[16];
    secureRandom.nextBytes(bytes);
    String objectName = base64Url().omitPadding().encode(bytes);
    // If this object is to be uploaded direct to a Google Storage bucket then
    // the BlobKey needs to be of the same format as what is generated by
    // LocalBlobstoreService.createEncodedGoogleStorageKey
    if (session.hasGoogleStorageBucketName()) {
      String fullName = "/gs/" + session.getGoogleStorageBucketName() + "/" + objectName;
      String encodedName = base64Url().omitPadding().encode(fullName.getBytes());
      objectName = LocalBlobstoreService.GOOGLE_STORAGE_KEY_PREFIX + encodedName;
    }
    return new BlobKey(objectName);
  }
}
