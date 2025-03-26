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
import java.net.MalformedURLException;
import java.net.URL;
import org.jspecify.annotations.Nullable;

/**
 * An instant messaging handle. Includes both an address and its protocol. The protocol value is
 * either a standard IM scheme (legal scheme values are defined by {@link Scheme} or a URL
 * identifying the IM network for the protocol (e.g. http://aim.com/).
 */
// TODO: Make the file GWT-compatible (import java.net is not
// supported in GWT, also String.format(String, String, String) is not
// implemented in GWT). Once done add this file to the gwt-datastore BUILD
// target
public final class IMHandle implements Serializable, Comparable<IMHandle> {

  public static final long serialVersionUID = 6963426833434504530L;

  /** Supported IM schemes. */
  public enum Scheme {
    sip,
    unknown,
    xmpp
  }

  // This attribute needs to be non-final to support GWT serialization
  private String protocol;
  // This attribute needs to be non-final to support GWT serialization
  private String address;

  public IMHandle(Scheme scheme, String address) {
    if (scheme == null) {
      throw new NullPointerException("scheme must not be null");
    }
    validateAddress(address);
    this.protocol = scheme.name();
    this.address = address;
  }

  public IMHandle(URL network, String address) {
    if (network == null) {
      throw new NullPointerException("network must not be null");
    }
    validateAddress(address);
    this.protocol = network.toString();
    this.address = address;
  }

  /**
   * This constructor exists for frameworks (e.g. Google Web Toolkit) that require it for
   * serialization purposes. It should not be called explicitly.
   */
  @SuppressWarnings({"nullness", "unused"})
  private IMHandle() {
    this.protocol = null;
    this.address = null;
  }

  /**
   * Constructs an {@code IMHandle} from a string in the format returned by {@link
   * #toDatastoreString()}.
   *
   * @throws IllegalArgumentException If the provided string does not match the format returned by
   *     {@link #toDatastoreString()} or the first component of the string is not a valid {@link
   *     Scheme} and is not a valid {@link URL}.
   */
  static IMHandle fromDatastoreString(String datastoreString) {
    if (datastoreString == null) {
      throw new NullPointerException("datastoreString must not be null");
    }
    // the address can have spaces in it so we only want to split on the first
    // space
    String[] split = datastoreString.split(" ", 2);
    if (split.length != 2) {
      throw new IllegalArgumentException(
          "Datastore string must have at least one space: " + datastoreString);
    }
    // make sure the first String is either a legal Scheme or a legal URL
    try {
      return new IMHandle(IMHandle.Scheme.valueOf(split[0]), split[1]);
    } catch (IllegalArgumentException iae) {
      try {
        return new IMHandle(new URL(split[0]), split[1]);
      } catch (MalformedURLException e) {
        throw new IllegalArgumentException(
            "String in datastore could not be parsed into a valid IMHandle.  "
                + "Protocol must either be a valid scheme or url: "
                + split[0]);
      }
    }
  }

  private static void validateAddress(String address) {
    if (address == null) {
      throw new NullPointerException("address must not be null");
    }
  }

  /** The datastore representation of the {@code IMHandle}. This must not change. */
  String toDatastoreString() {
    return String.format("%s %s", protocol, address);
  }

  public String getProtocol() {
    return protocol;
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

    IMHandle imHandle = (IMHandle) o;

    if (!address.equals(imHandle.address)) {
      return false;
    }
    if (!protocol.equals(imHandle.protocol)) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int result;
    result = protocol.hashCode();
    result = 31 * result + address.hashCode();
    return result;
  }

  /** Sorts first by protocol, then by address. */
  @Override
  public int compareTo(IMHandle o) {
    return toDatastoreString().compareTo(o.toDatastoreString());
  }

  @Override
  public String toString() {
    return toDatastoreString();
  }
}
