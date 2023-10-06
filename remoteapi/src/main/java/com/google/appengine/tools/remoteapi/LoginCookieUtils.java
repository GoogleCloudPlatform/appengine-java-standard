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

package com.google.appengine.tools.remoteapi;

//import com.google.security.annotations.SuppressInsecureCipherModeCheckerNoReview;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * {@code LoginCookieUtils} encapsulates the creation, deletion, and parsing of the fake
 * authentication cookie used by the Development Appserver to simulate login.
 */
final class LoginCookieUtils {
  /**
   * The URL path for the authentication cookie.
   */
  public static final String COOKIE_PATH = "/";

  /**
   * The name of the authentication cookie.
   */
  public static final String COOKIE_NAME = "dev_appserver_login";

  static String encodeEmailAsUserId(String email) {
    // This is sort of a weird way of doing this, but it matches
    // Python. See dev_appserver_login.py, method CreateCookieData
    try {
      MessageDigest md5 = MessageDigest.getInstance("MD5");
      md5.update(email.toLowerCase().getBytes(StandardCharsets.UTF_8));
      StringBuilder builder = new StringBuilder();
      builder.append("1");
      for (byte b : md5.digest()) {
        builder.append(String.format("%02d", b & 0xff));
      }
      // This is structured differently from its python equivalent, since here
      // the substring method is called after prefixing it with a "1".
      return builder.toString().substring(0, 21);
    } catch (NoSuchAlgorithmException ex) {
      return "";
    }
  }

  private LoginCookieUtils() {
    // Utility class -- do not instantiate.
  }
}
