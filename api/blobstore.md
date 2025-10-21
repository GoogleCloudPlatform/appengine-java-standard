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

# Google App Engine Blobstore API Documentation

*   [Overview of Blobstore API](#overview-of-blobstore-api)
    *   [Introducing the Blobstore](#introducing-the-blobstore)
    *   [Using the Blobstore](#using-the-blobstore)
    *   [Uploading a Blob](#uploading-a-blob)
        *   [1. Create an Upload URL](#1-create-an-upload-url)
        *   [2. Create an Upload Form](#2-create-an-upload-form)
        *   [3. Implement Upload Handler](#3-implement-upload-handler)
    *   [Serving a Blob](#serving-a-blob)
    *   [Blob Byte Ranges](#blob-byte-ranges)
    *   [Complete Sample Application](#complete-sample-application)
    *   [Using the Images Service with the Blobstore](#using-the-images-service-with-the-blobstore)
    *   [Using the Blobstore API with Cloud Storage](#using-the-blobstore-api-with-cloud-storage)
    *   [Quotas and Limits](#quotas-and-limits)

## Overview of Blobstore API

**Note:** You should consider using Cloud Storage rather than Blobstore for
storing blob data. The Blobstore API allows your application to serve data
objects, called blobs, that are much larger than the size allowed for objects in
the Datastore service. Blobs are useful for serving large files, such as video
or image files, and for allowing users to upload large data files. Blobs are
created by uploading a file through an HTTP request. Typically, your
applications will do this by presenting a form with a file upload field to the
user. When the form is submitted, the Blobstore creates a blob from the file's
contents and returns an opaque reference to the blob, called a blob key, which
you can later use to serve the blob.

### Introducing the Blobstore

App Engine includes the Blobstore service, which allows applications to serve
data objects limited only by the amount of data that can be uploaded or
downloaded over a single HTTP connection. These objects are called Blobstore
values, or blobs. Blobstore values are served as responses from request handlers
and are created as uploads via web forms. Applications do not create blob data
directly; instead, blobs are created indirectly, by a submitted web form or
other HTTP POST request. 

**Note:** Blobs as defined by the Blobstore service are
not related to blob property values used by the datastore. To prompt a user to
upload a Blobstore value, your application presents a web form with a file
upload field. The application generates the form's action URL by calling the
Blobstore API. The user's browser uploads the file directly to the Blobstore via
the generated URL. Blobstore then stores the blob, rewrites the request to
contain the blob key, and passes it to a path in your application. To serve a
blob, your application sets a header on the outgoing response, and App Engine
replaces the response with the blob value. Blobs can't be modified after they're
created, though they can be deleted. Each blob has a corresponding blob info
record, stored in the datastore, that provides details about the blob, such as
its creation time and content type. An application can read a Blobstore value a
portion at a time using an API call. The size of the portion can be up to the
maximum size of an API return value. This size is a little less than 32
megabytes, represented in Java by the constant
`com.google.appengine.api.blobstore.BlobstoreService.MAX_BLOB_FETCH_SIZE`.

### Using the Blobstore

Applications can use the Blobstore to accept large files as uploads from users
and to serve those files. Files are called blobs once they're uploaded.
Applications don't access blobs directly, instead, applications work with blobs
through blob info entities (represented by the `BlobInfo` class) in the
Datastore. The user creates a blob by submitting an HTML form that includes one
or more file input fields. Your application sets
`blobstoreService.createUploadUrl()` as the destination (action) of this form,
passing the function a URL path of a handler in your application.

### Uploading a Blob

To create and upload a blob, follow this procedure:

#### 1. Create an Upload URL

Call `blobstoreService.createUploadUrl()` to create an upload URL for the form
that the user will fill out, passing the application path to load when the POST
of the form is completed.

```jsp
blobstoreService.createUploadUrl("/upload") %>" method="post"
enctype="multipart/form-data"> <input type="file" name="myFile"> <input
type="submit" value="Submit"> </form> </body>
```

#### 2. Create an Upload Form

The form must include a file upload field, and the form's enctype must be set to
`multipart/form-data`. **Important:** You can't use a global external
Application Load Balancer with a Serverless NEG to handle upload requests sent
to the `/_ah/upload/` URL returned from the `blobstoreService.createUploadUrl()`
call. Instead, you must route those upload requests directly to the App Engine
service.

#### 3. Implement Upload Handler

In this handler, you can store the blob key with the rest of your application's
data model. The blob key itself remains accessible from the blob info entity in
the Datastore. Note that after the user submits the form and your handler is
called, the blob has already been saved and the blob info added to the
Datastore.

```java
 List<BlobKey> blobKeys = blobs.get("myFile");
if (blobKeys == null || blobKeys.isEmpty()) { res.sendRedirect("/");
}
else {
res.sendRedirect("/serve?blob-key=" + blobKeys.get(0).getKeyString());
}
```

When rewriting the user's request, the Blobstore empties the MIME parts of the
uploaded files and adds the blob key as a MIME part header. The Blobstore
preserves all other form fields and parts, passing them to the upload handler.
If you don't specify a content type, the Blobstore will try to infer it from the
file extension.

### Serving a Blob

**Note:** If you are serving images, a more efficient and potentially
less-expensive method is to use `getServingUrl()` using the App Engine Images
API rather than `blobstoreService.serve()`. To serve blobs, you must include a
blob download handler as a path in your application. This handler should pass
the blob key for the desired blob to `blobstoreService.serve(blobKey, res)`:

```java
 public void doGet(HttpServletRequest req, HttpServletResponse res) throws
IOException { 
    BlobKey blobKey = new BlobKey(req.getParameter("blob-key"));
    blobstoreService.serve(blobKey, res);
}
```

### Blob Byte Ranges

The Blobstore supports serving part of a large value instead of the full value
in response to a request. To serve a partial value, include the
`X-AppEngine-BlobRange` header in the outgoing response. Its value is a standard
HTTP byte range. The byte numbering is zero-based. Example ranges include: -
`0-499` serves the first 500 bytes of the value (bytes 0 through 499,
inclusive) - `500-999` serves 500 bytes starting with the 501st byte - `500-`
serves all bytes starting with the 501st byte to the end of the value - `-500`
serves the last 500 bytes of the value

### Complete Sample Application

```java
// file Upload.java
import java.io.IOException;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.google.appengine.api.blobstore.BlobKey;
import com.google.appengine.api.blobstore.BlobstoreService;
import com.google.appengine.api.blobstore.BlobstoreServiceFactory;
public class Upload extends HttpServlet {
  private BlobstoreService blobstoreService = BlobstoreServiceFactory.getBlobstoreService();
  @Override
  public void doPost(HttpServletRequest req, HttpServletResponse res)
      throws ServletException, IOException {
    Map<String, List<BlobKey>> blobs = blobstoreService.getUploads(req);
    List<BlobKey> blobKeys = blobs.get("myFile");
    if (blobKeys == null || blobKeys.isEmpty()) {
      res.sendRedirect("/");
    } else {
      res.sendRedirect("/serve?blob-key=" + blobKeys.get(0).getKeyString());
    }
  }
}
// file Serve.java
import java.io.IOException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.google.appengine.api.blobstore.BlobKey;
import com.google.appengine.api.blobstore.BlobstoreService;
import com.google.appengine.api.blobstore.BlobstoreServiceFactory;
public class Serve extends HttpServlet {
  private BlobstoreService blobstoreService = BlobstoreServiceFactory.getBlobstoreService();
  @Override
  public void doGet(HttpServletRequest req, HttpServletResponse res)
      throws IOException {
    BlobKey blobKey = new BlobKey(req.getParameter("blob-key"));
    blobstoreService.serve(blobKey, res);
  }
}
```

### Using the Images Service with the Blobstore

The Images service can use a Blobstore value as the source of a transformation.
The source image can be as large as the maximum size for a Blobstore value. The
Images service still returns the transformed image to the application, so the
transformed image must be smaller than 32 megabytes.

### Using the Blobstore API with Cloud Storage

You can use the Blobstore API to store blobs in Cloud Storage instead of storing
them in Blobstore. You need to set up a bucket and specify the bucket and
filename in the BlobstoreService `createUploadUrl`, specify the bucket name in
the UploadOptions parameter.

```java
BlobstoreService blobstoreService =
BlobstoreServiceFactory.getBlobstoreService();
BlobKey blobKey =
blobstoreService.createGsBlobKey( "/gs/" + fileName.getBucketName() + "/" +
fileName.getObjectName()); blobstoreService.serve(blobKey, resp);
```

### Quotas and Limits

-   The maximum size of Blobstore data that can be read by the application with
    one API call is 32 megabytes
-   The maximum number of files that can be uploaded in a single form POST is
    500
