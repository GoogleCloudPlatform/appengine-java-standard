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

/**
 * Exposes constants for reserved datastore kinds used by the local blobstore
 * service.
 *
 */
public final class ReservedKinds {

  public static final String BLOB_UPLOAD_SESSION_KIND = "__BlobUploadSession__";

  // This is used only in the dev server to assist in tracking Google
  // Storage files in the absence of a real service.
  public static final String GOOGLE_STORAGE_FILE_KIND = "__GsFileInfo__";

  private ReservedKinds() { }
}
