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

package com.google.appengine.tools.util;

import java.io.Serializable;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.logging.Logger;

/**
 * Client-side cookie manager.
 *
 * <p>This class implements the client side of the Netscape&nbsp;V0 and
 * RFC&nbsp;2109 (V1) cookie protocols.  It does <b>not</b> support
 * RFC&nbsp;2965 (V2) cookies.
 *
 * <p>There should be one instance of this class per user.  Call
 * <code>readCookies()</code> after each HTTP response from the
 * server, e.g., after <code>URLConnection.getInputStream()</code>.
 * Call <code>writeCookies()</code> before each HTTP request to the
 * server, e.g., before <code>URLConnection.getOutputStream()</code>.
 * These methods know how to match cookies against domains, expire
 * cookies, etc.
 *
 * <p>There is a hard limit on the number of cookies stored at any
 * moment.  The default limit is 300 cookies for all hosts combined.
 * If this limit is exceeded, cookies are removed by the LRU approximation
 * policy.
 *
 * <p>Caveats:
 *
 * <ol>
 * <li>This class does not strictly adhere to the cookie specs.  Both
 * specs are quite buggy and contradictory.  Some behavior is taken
 * from RFC&nbsp;2965 because it makes more sense (e.g., domain names
 * are case insensitive).</li>
 * <li>Beware of HTTP redirects!  By default, Sun's
 * <code>HttpURLConnection</code> silently follows redirects, which
 * means that you will <b>not</b> get to see the cookies in 3XX
 * responses.  This is wrong.  Call
 * <code>HttpURLConnection.setInstanceFollowRedirects(false)</code> and
 * handle the 3XX response codes yourself.  HTTP allows for at most five
 * redirects for a given request.</li>
 * <li>You do not have any control over <code>URLConnection</code>s.
 * Sun's implementation does some primitive connection pooling, but
 * it's not nearly as good as <code>com.google.io.ConnectionPool</code>.
 * Sun's <code>URLConnection</code> also does some HTTP magic behind your
 * back.</li>
 * </ol>
 *
 */
// TODO: add RFC&nbsp;2965 (V2) support.
public class ClientCookieManager implements Serializable {

  private static Logger logger =
      Logger.getLogger(ClientCookieManager.class.getName());

  /**
   * Maximum number of cookies in the list.
   * @serial
   */
  private int maxCookies_;

  /**
   * Maximum character length for cookie name and value combined.
   * @serial
   */
  private int maxCookieSize_;

  /**
   * List of cookies.  Most recently used cookies are near the head.
   * @serial
   */
  private LinkedList<ClientCookie> cookies_ = new LinkedList<ClientCookie>();

  /**
   * Create a cookie manager.
   * <p>This constructor creates a cookie manager that remembers
   * at most 300 recent cookies for all hosts combined, and
   * limits cookie length to 4K characters.  Caveat: this class
   * is too slow for 300 cookies, but 300 is required by the spec.
   */
  public ClientCookieManager() {
    maxCookies_ = 300;
    maxCookieSize_ = 4096;
  }

  /**
   * Create a cookie manager with given limits.
   * @param maxCookies maximum number of cookies for all hosts combined.
   * @param maxCookieSize maximum number of characters in a cookie.
   * @exception IllegalArgumentException if either argument is non-positive.
   */
  public ClientCookieManager(int maxCookies, int maxCookieSize) {
    if (maxCookies <= 0) {
      throw new IllegalArgumentException("maxCookies <= 0");
    }
    if (maxCookieSize <= 0) {
      throw new IllegalArgumentException("maxCookieSize <= 0");
    }
    maxCookies_ = maxCookies;
    maxCookieSize_ = maxCookieSize;
  }


  /**
   * Read cookies off of a URL connection and remember them.
   * @param conn URL connection, after the response is received.
   */
  public synchronized void readCookies(URLConnection conn) {

    // pick out all Set-Cookie headers (yes, n starts from one)
    final URL requestURL = conn.getURL();
    for (int n = 1; true; n++) {
      final String fieldKey = conn.getHeaderFieldKey(n);
      if (fieldKey == null) {
        break;
      } else if (fieldKey.equalsIgnoreCase("Set-Cookie")) {
        try {

          // read cookies from the header
          final ListIterator<ClientCookie> values =
            ClientCookie.parseSetCookie(conn.getHeaderField(n), requestURL)
                        .listIterator();

          // add cookies to cache
          while (values.hasNext()) {
            final ClientCookie cookie = values.next();
            // remove old version of the cookie
            cookies_.remove(cookie);
            // Do *not* follow Netscape V0 cookie spec and silently trim
            // the value when it's too long -- instead, ignore such cookies.
            if (cookie.getName().length() + cookie.getValue().length() <=
                  maxCookieSize_ &&
                cookie.getEffectiveDomain().length() <= maxCookieSize_ &&
                cookie.getEffectivePath().length() <= maxCookieSize_) {
              cookies_.addFirst(cookie);
              logger.fine("stored cookie: " + cookie.getName() + "=" +
                cookie.getValue() + "; domain = " +
                cookie.getEffectiveDomain() + "; path=" +
                cookie.getEffectivePath() +
                "; expires=" + cookie.getExpirationTime());
            }
          }

        } catch (HttpHeaderParseException e) {
          logger.info(e.getMessage());
        }
      }
    }

    // remove expired cookies and enforce maxCookies_ (LRU approximation)
    final long currentTime = System.currentTimeMillis();
    final ListIterator<ClientCookie> li = cookies_.listIterator();
    int pos = 0;
    while (li.hasNext()) {
      final ClientCookie cookie = li.next();
      if (cookie.getExpirationTime() <= currentTime || pos >= maxCookies_) {
        logger.fine("removed cookie: " + cookie.getName());
        li.remove();
      } else {
        pos++;
      }
    }

  }


  /**
   * Write matching cookies to a URL connection.
   * @param conn URL connection, before the request is sent.
   */
  public synchronized void writeCookies(URLConnection conn) {

    final URL requestURL = conn.getURL();
    // current time for removing expired cookies
    final long currentTime = System.currentTimeMillis();
    // list of cookies in the request
    final LinkedList<ClientCookie> requestCookies =
        new LinkedList<ClientCookie>();

    // linearly search all stored cookies
    final ListIterator<ClientCookie> li = cookies_.listIterator();
    while (li.hasNext()) {
      final ClientCookie cookie = li.next();
      if (cookie.getExpirationTime() <= currentTime) {
        // remove expired cookies
        li.remove();
      } else if (cookie.match(requestURL)) {
        li.remove(); // it will be moved to the front of the list
        requestCookies.add(cookie);
      }
    }

    // process matching cookies
    if (!requestCookies.isEmpty()) {

      // make the cookie string
      final StringBuffer sb = new StringBuffer();
      // see RFC 2109, section 4.3.4
      Collections.sort(requestCookies);
      final ListIterator<ClientCookie> cookies = requestCookies.listIterator();
      while (cookies.hasNext()) {
        final ClientCookie cookie = cookies.next();
        if (sb.length() > 0) {
          sb.append("; ");
        }
        sb.append(cookie.encode());
        // put the cookie back in the list
        cookies_.addFirst(cookie);
      }

      // set the header field
      logger.fine("sent cookie: " + sb);
      conn.setRequestProperty("Cookie", sb.toString());
    }
  }

  /**
   * Returns an iterator over the cookies stored by the ClientCookieManager.
   */
  public Iterator<ClientCookie> getCookies() {
    return cookies_.listIterator();
  }
}
