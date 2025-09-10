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

package com.google.appengine.api.users.dev.jakarta;

// <internal24>
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * {@code LoginCookieUtils} encapsulates the creation, deletion, and
 * parsing of the fake authentication cookie used by the Development
 * Appserver to simulate login.
 *
 */
public final class LoginCookieUtils {
  /**
   * The URL path for the authentication cookie.
   */
  public static final String COOKIE_PATH = "/";

  /**
   * The name of the authentication cookie.
   */
  public static final String COOKIE_NAME = "dev_appserver_login";

  /**
   * The age of the authentication cookie.  -1 means the cookie should
   * not be persisted to disk, and will be erased when the browser is
   * restarted.
   */
  private static final int COOKIE_AGE = -1;

  /**
   * Create a fake authentication {@link Cookie} with the specified data.
   */
  public static Cookie createCookie(String email, boolean isAdmin) {
    String userId = encodeEmailAsUserId(email);

    Cookie cookie = new Cookie(COOKIE_NAME, email + ":" + isAdmin + ":" + userId);
    cookie.setPath(COOKIE_PATH);
    cookie.setMaxAge(COOKIE_AGE);
    return cookie;
  }

  /**
   * Remove the fake authentication {@link Cookie}, if present.
   */
  public static void removeCookie(HttpServletRequest req, HttpServletResponse resp) {
    Cookie cookie = findCookie(req);
    if (cookie != null) {
      // The browser doesn't send the original path, but it's part of
      // the cookie's identity, so we need to re-set it if we want to
      // delete the same cookie.
      cookie.setPath(COOKIE_PATH);

      // This causes the cookie to expire immediately (i.e. to be deleted).
      cookie.setMaxAge(0);

      // Now we need to send the cookie back to the client so it knows
      // we deleted it.
      resp.addCookie(cookie);
    }
  }

  /**
   * Parse the fake authentication {@link Cookie}.
   *
   * @return A parsed {@link CookieData}, or {@code null} if the
   * user is not logged in.
   */
  public static CookieData getCookieData(HttpServletRequest req) {
    Cookie cookie = findCookie(req);
    if (cookie == null) {
      return null;
    } else {
      return parseCookie(cookie);
    }
  }

  // <internal25>
  public static String encodeEmailAsUserId(String email) {
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

  /**
   * Parse the specified {@link Cookie} into a {@link CookieData}.
   */
  private static CookieData parseCookie(Cookie cookie) {
    String value = cookie.getValue();
    String[] parts = value.split(":");
    String userId = null;
    if (parts.length > 2) {
      userId = parts[2];
    }
    return new CookieData(parts[0], Boolean.parseBoolean(parts[1]), userId);
  }

  private static Cookie findCookie(HttpServletRequest req) {
    Cookie[] cookies = req.getCookies();
    if (cookies != null) {
      for (Cookie cookie : cookies) {
        if (cookie.getName().equals(COOKIE_NAME)) {
          return cookie;
        }
      }
    }
    return null;
  }

  private LoginCookieUtils() {
    // Utility class -- do not instantiate.
  }

  /**
   * {@code CookieData} encapsulates all of the data contained in the
   * fake authentication cookie.
   */
  public static final class CookieData {
    private final String email;
    private final boolean isAdmin;
    private final String userId;

    CookieData(String email, boolean isAdmin, String userId) {
      this.email = email;
      this.isAdmin = isAdmin;
      this.userId = userId;
    }

    public String getEmail() {
      return email;
    }

    public boolean isAdmin() {
      return isAdmin;
    }

    public String getUserId() {
      return userId;
    }
  }
}
