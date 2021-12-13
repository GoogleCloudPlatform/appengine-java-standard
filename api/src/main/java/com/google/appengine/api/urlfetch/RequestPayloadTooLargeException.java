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

package com.google.appengine.api.urlfetch;

import java.net.MalformedURLException;

/**
 * {@code RequestPayloadTooLargeException} is thrown when the payload of a {@link URLFetchService}
 * request is too large.
 *
 * <p>This is a subclass of MalformedURLException for backwards compatibility as it is thrown in
 * places where MalformedURLException was thrown previously.
 *
 */
public class RequestPayloadTooLargeException extends MalformedURLException {
  private static final long serialVersionUID = -3367212092992458054L;

  private static final String MESSAGE_FORMAT = "The request to %s exceeded the 10 MiB limit.";

  public RequestPayloadTooLargeException(String url) {
    super(String.format(MESSAGE_FORMAT, url));
  }
}
