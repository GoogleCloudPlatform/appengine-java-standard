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

package com.google.appengine.api.datastore;

import java.io.Serializable;
import org.jspecify.annotations.Nullable;

/**
 * A human-readable mailing address. Mailing address formats vary widely so no validation is
 * performed.
 *
 */
public final class PostalAddress implements Serializable, Comparable<PostalAddress> {

  public static final long serialVersionUID = -1090628591187239495L;

  // This attribute needs to be non-final to support GWT serialization
  private String address;

  public PostalAddress(String address) {
    if (address == null) {
      throw new NullPointerException("address must not be null");
    }
    this.address = address;
  }

  /**
   * This constructor exists for frameworks (e.g. Google Web Toolkit) that require it for
   * serialization purposes. It should not be called explicitly.
   */
  @SuppressWarnings({"nullness", "unused"})
  private PostalAddress() {
    address = null;
  }

  public String getAddress() {
    return address;
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    PostalAddress that = (PostalAddress) o;

    if (!address.equals(that.address)) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    return address.hashCode();
  }

  @Override
  public int compareTo(PostalAddress o) {
    return address.compareTo(o.address);
  }
}
