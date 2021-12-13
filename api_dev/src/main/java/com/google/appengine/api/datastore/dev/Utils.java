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

package com.google.appengine.api.datastore.dev;

import static com.google.common.collect.Iterables.getLast;

import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.ApplicationException;
import com.google.apphosting.datastore.DatastoreV3Pb.Error.ErrorCode;
import com.google.storage.onestore.v3.OnestoreEntity.Path.Element;
import com.google.storage.onestore.v3.OnestoreEntity.Reference;

/**
 * Utility functions for the development datastore.
 *
 */
class Utils {
  /** Throws a BAD_REQUEST exception with the given message if {@code ok} is false */
  static void checkRequest(boolean ok, String message) {
    if (!ok) {
      throw newError(ErrorCode.BAD_REQUEST, message);
    }
  }

  static ApplicationException newError(ErrorCode error, String message) {
    return new ApiProxy.ApplicationException(error.getValue(), message);
  }

  static Element getLastElement(Reference key) {
    return getLast(key.getPath().elements());
  }

  static String getKind(Reference key) {
    return getLastElement(key).getType();
  }
}
