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

import com.google.common.base.Objects;
import java.io.Serializable;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * A client-side cookie.
 *
 * @see com.google.yans.ClientCookieManager
 */
public class ClientCookie implements Comparable<ClientCookie>, Serializable {

  private static final long serialVersionUID = 1L;

  private static final Logger logger = Logger.getLogger(
      ClientCookie.class.getName());

  /**
   * Cookie name (never null).
   * @serial
   */
  private String name_ = null;
  /**
   * Cookie value (never null).
   * @serial
   */
  private String value_ = null;
  /**
   * Cookie comment (always null for V0 cookies).
   * @serial
   */
  private String comment_ = null;
  /**
   * Cookie domain, as seen in the HTTP header (can be null).
   * @serial
   */
  private String domain_ = null;
  /**
   * Effective cookie domain, used in matches (never null).
   * @serial
   */
  private String effectiveDomain_ = null;
  /**
   * Cookie path, as seen in the HTTP header (can be null).
   * @serial
   */
  private String path_ = null;
  /**
   * Effective cookie path, used in matches (never null).
   * @serial
   */
  private String effectivePath_ = null;
  /**
   * Is this cookie secure?  This field is set but currently unused.
   * @serial
   */
  private boolean secure_ = false;
  /**
   * Absolute cookie expiration time, in milliseconds since the epoch.
   * @serial
   */
  private long expires_ = Long.MAX_VALUE;
  /**
   * Cookie version, as seen in the HTTP header (zero if not in header).
   * @serial
   */
  private int version_ = 0;
  /**
   * Effective cookie version, as determined by the parser.
   * @serial
   */
  private int effectiveVersion_ = 0;
  /**
   * Is this cookie only available via HTTP?  This field is set but currently unused.
   * @serial
   */
  private boolean httponly_ = false;


  // Special domains for V0 cookies.
  private static final String[] SPECIAL_DOMAINS = {
    ".com", ".edu", ".net", ".org", ".gov", ".mil", ".int"
  };


  /**
   * Get cookie name.
   * @return cookie name, never null.
   */
  public String getName() { return name_; }


  /**
   * Get cookie value.
   * @return cookie value, never null.
   */
  public String getValue() { return value_; }


  /**
   * Get cookie comment (RFC 2109 and RFC 2965 cookies only).
   * @return cookie comment, or null if not specified in the header.
   */
  public String getComment() { return comment_; }


  /**
   * Get cookie domain.
   * @return cookie domain, or null if not specified in the header.
   */
  public String getDomain() { return domain_; }


  /**
   * Get effective cookie domain.
   * @return effective domain, never null.
   */
  public String getEffectiveDomain() { return effectiveDomain_; }


  /**
   * Get cookie path.
   * @return cookie path, or null if not specified in the header.
   */
  public String getPath() { return path_; }


  /**
   * Get effective cookie path.
   * @return effective path, never null.
   */
  public String getEffectivePath() { return effectivePath_; }


  /**
   * Is this cookie secure?
   * @return true if the cookie is secure, false if not.
   */
  public boolean isSecure() { return secure_; }


  /**
   * Is this cookie only available via HTTP?
   * @return true if the cookie is only available via HTTP, false if not.
   */
  public boolean isHttpOnly() { return httponly_; }


  /**
   * Get cookie expiration time.
   * @return absolute expiration time, in milliseconds, or large value if none.
   */
  public long getExpirationTime() { return expires_; }


  /**
   * Get cookie version.
   * @return cookie version, or zero if not in the header.
   */
  public int getVersion() { return version_; }


  /**
   * Get effective cookie version.
   * @return effective version: 0, 1, or 2.
   */
  public int getEffectiveVersion() { return effectiveVersion_; }


  /**
   * Match the cookie against a request.
   * 
   * <p>See V0, RFC 2109 sec. 2, RFC 2965 sec. 1.
   * 
   * @param url request URL.
   * @return true if the cookie matches this request, false if not.
   */
  public boolean match(URL url) {
    final String requestHost = url.getHost().toLowerCase();
    final String requestPath = url.getPath();
    if (effectiveDomain_.startsWith(".")) {
      if (!requestHost.endsWith(effectiveDomain_) &&
          !requestHost.equals(effectiveDomain_.substring(1))) {
        return false;
      }
    } else {
      if (!requestHost.equals(effectiveDomain_)) {
        return false;
      }
    }
    if (!requestPath.startsWith(effectivePath_)) {
      return false;
    }
    // TODO: we are disregarding secure_ and ports
    return true;
  }


  /**
   * Encode the cookie for transmission to the server.
   * 
   * <p>See V0, RFC 2109 sec. 4.3.4, RFC 2965 sec. 3.3.4.
   * 
   * @return properly encoded cookie.
   */
  public StringBuffer encode() {
    if (effectiveVersion_ == 0) {
      // V0 does not allow quoting
      final StringBuffer result = new StringBuffer(name_);
      result.append('=');
      result.append(value_);
      return result;
    } else {
      final StringBuffer result =
        HttpHeaderParser.makeAttributeValuePair(name_, value_);
      if (path_ != null) {
        result.append("; ");
        result.append(HttpHeaderParser.makeAttributeValuePair("$Path", path_));
      }
      if (domain_ != null) {
        result.append("; ");
        result.append(
          HttpHeaderParser.makeAttributeValuePair("$Domain", domain_));
      }
      return result;
    }
  }


  /**
   * Compare two cookies.
   *
   * <p>See V0, RFC 2109 sec. 4.3.4, RFC 2965 sec. 3.3.3.
   *
   * @param o object to compare this cookie to.
   * @return true if the object is equal to this cookie, false otherwise.
   */
  @Override
  public boolean equals(Object o) {
    if (!(o instanceof ClientCookie)) {
      return false;
    } else {
      ClientCookie cookie = (ClientCookie)o;
      return cookie.name_.equals(name_) &&
             cookie.effectiveDomain_.equals(effectiveDomain_) &&
             cookie.effectivePath_.equals(effectivePath_);
    }
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(name_, effectiveDomain_, effectivePath_);
  }

  /**
   * Compare two cookies.
   *
   * <p>See V0, RFC 2109 sec. 4.3.4, RFC 2965 sec. 3.3.4.
   *
   * @param cookie object to compare this cookie to, must be non-null.
   * @return a negative integer, zero, or positive integer, as per Comparable.
   */
  @Override
  public int compareTo(ClientCookie cookie) {
    int result = cookie.effectivePath_.length() - effectivePath_.length();
    if (result != 0) {
      return result;
    }
    // tie breaking is arbitrary but consistent with equals()
    result = cookie.effectivePath_.compareTo(effectivePath_);
    if (result != 0) {
      return result;
    }
    result = cookie.effectiveDomain_.compareTo(effectiveDomain_);
    if (result != 0) {
      return result;
    }
    return cookie.name_.compareTo(name_);
  }

  /**
   * Count occurrences of a character in a string.
   * @param str string to examine.
   * @param ch character to look for.
   * @return the number of occurrences of ch in str.
   */
  private static int countOccurrences(String str, char ch) {
    int count = 0;
    int index = 0;
    while ((index = str.indexOf(ch, index)) >= 0) {
      count++;
      index++;
    }
    return count;
  }


  /**
   * No externally visible default constructor, see <code>parseSetCookie</code>.
   */
  private ClientCookie() {
    /* do nothing */
  }


  /**
   * Get all cookies from a Set-Cookie header.
   * @param setCookieHeader value of the header, never null.
   * @param url request URL, never null.
   * @return a list of cookies in the header, can be empty.
   * @exception HttpHeaderParseException if the header is misformatted.
   */
  public static List<ClientCookie> parseSetCookie(String setCookieHeader,
                                                  URL url)
    throws HttpHeaderParseException {
    // try RFC 2109 first and fall back to V0 if it fails
    try {
      return parseSetCookie1(setCookieHeader, url);
    } catch (HttpHeaderParseException e) {
      return parseSetCookie0(setCookieHeader, url);
    }
  }


  /**
   * Get all cookies from a Set-Cookie header, assume RFC&nbsp;2109.
   * 
   * <p>See RFC 2109 sec. 4.2.2, 4.3.1, 4.3.2.
   * 
   * @param setCookie1Header value of the header.
   * @param url request URL.
   * @return a list of cookies in the header, can be empty.
   * @exception HttpHeaderParseException if the header is misformatted.
   */
  private static List<ClientCookie> parseSetCookie1(String setCookie1Header,
                                                    URL url)
      throws HttpHeaderParseException {
    final HttpHeaderParser parser = new HttpHeaderParser(setCookie1Header);
    final ArrayList<ClientCookie> results = new ArrayList<ClientCookie>();
    parser.eatLWS();
    while (true) {
      // read name=value
      final ClientCookie cookie = new ClientCookie();
      cookie.effectiveVersion_ = 1;
      cookie.name_ = parser.eatToken();
      parser.eatLWS();
      parser.eatChar('=');
      parser.eatLWS();
      cookie.value_ = parser.eatTokenOrQuotedString();
      parser.eatLWS();

      // read cookie attributes
      while (parser.peek() == ';') {
        parser.eatChar(';');
        parser.eatLWS();
        final String name = parser.eatToken().toLowerCase();
        parser.eatLWS();
        if (name.equals("secure")) {
          cookie.secure_ = true;
        } else if (name.equals("httponly")) {
          cookie.httponly_ = true;
        } else {
          parser.eatChar('=');
          parser.eatLWS();
          final String value = parser.eatTokenOrQuotedString();
          if (name.equals("comment")) {
            cookie.comment_ = value;
          } else if (name.equals("domain")) {
            cookie.domain_ = value.toLowerCase();
          } else if (name.equals("max-age")) {
            final int maxAge;
            try {
              maxAge = Integer.parseInt(value);
              if (maxAge < 0) {
                throw new NumberFormatException(value);
              }
            } catch (NumberFormatException e) {
              throw new HttpHeaderParseException("invalid max-age: " +
                setCookie1Header);
            }
            cookie.expires_ = System.currentTimeMillis() + 1000 * maxAge;
          } else if (name.equals("path")) {
            cookie.path_ = value;
          } else if (name.equals("version")) {
            try {
              cookie.version_ = Integer.parseInt(value);
              if (cookie.version_ <= 0) {
                throw new NumberFormatException(value);
              }
            } catch (NumberFormatException e) {
              throw new HttpHeaderParseException("invalid version: " +
                setCookie1Header);
            }
          } else if (name.equals("expires")) {
            throw new HttpHeaderParseException("this is a v0 cookie");
          } else {
            logger.info("unrecognized v1 cookie attribute: " +
              name + "=" + value);
          }
        }
        parser.eatLWS();
      }

      // validate the cookie - see RFC 2109 sec. 4.3.1, 4.3.2
      boolean valid = true;
      final String requestHost = url.getHost().toLowerCase();
      final String requestPath = url.getPath();
      if (cookie.domain_ == null) {
        cookie.effectiveDomain_ = requestHost;
      } else {
        if (!cookie.domain_.startsWith(".") ||
            cookie.domain_.substring(1, cookie.domain_.length() - 1)
                  .indexOf('.') < 0) {
          logger.info("rejecting v1 cookie [bad domain - no periods]: " +
            setCookie1Header);
          valid = false;
        } else if (!requestHost.endsWith(cookie.domain_)) {
          logger.info("rejecting v1 cookie [bad domain - no match]: " +
            setCookie1Header);
          valid = false;
        } else if (requestHost
                   .substring(0, requestHost.length() - cookie.domain_.length())
                   .indexOf('.') >= 0) {
          logger.info("rejecting v1 cookie [bad domain - extra periods]: " +
            setCookie1Header);
          valid = false;
        } else {
          cookie.effectiveDomain_ = cookie.domain_;
        }
      }
      if (cookie.path_ == null) {
        int index = requestPath.lastIndexOf('/');
        if (index < 0) {
          cookie.effectivePath_ = requestPath;
        } else {
          // trailing slash not included
          cookie.effectivePath_ = requestPath.substring(0, index);
        }
      } else {
        if (!requestPath.startsWith(cookie.path_)) {
          logger.info("rejecting v1 cookie [bad path]: " +
            setCookie1Header);
          valid = false;
        } else {
          cookie.effectivePath_ = cookie.path_;
        }
      }
      if (valid) {
        results.add(cookie);
      }

      // on to next cookie
      if (!parser.isEnd()) {
        parser.eatChar(',');
      } else {
        break;
      }
    }
    return results;
  }


  /**
   * Get all cookies from a Set-Cookie header, assume Netscape V0 cookies.
   * @param setCookie0Header value of the header.
   * @param url request URL.
   * @return a list of cookies in the header, can be empty.
   * @exception HttpHeaderParseException if the header is misformatted.
   */
  private static List<ClientCookie> parseSetCookie0(String setCookie0Header,
                                                    URL url)
    throws HttpHeaderParseException {
    final HttpHeaderParser parser = new HttpHeaderParser(setCookie0Header);
    final ArrayList<ClientCookie> results = new ArrayList<ClientCookie>();
    
    // read name=value
    parser.eatLWS();
    final ClientCookie cookie = new ClientCookie();
    cookie.effectiveVersion_ = 0;
    cookie.name_ = parser.eatV0CookieToken();
    parser.eatLWS();
    parser.eatChar('=');
    parser.eatLWS();
    cookie.value_ = parser.eatV0CookieValue();
    parser.eatLWS();

    // read attributes
    while (!parser.isEnd()) {
      parser.eatChar(';');
      parser.eatLWS();
      final String name = parser.eatV0CookieToken().toLowerCase();
      if (name.equals("secure")) {
        cookie.secure_ = true;
      } else if (name.equals("httponly")) {
        cookie.httponly_ = true;
      } else {
        parser.eatLWS();
        parser.eatChar('=');
        parser.eatLWS();
        if (name.equals("expires")) {
          cookie.expires_ = parser.eatV0CookieDate().getTime();
        } else {
          final String value = parser.eatV0CookieValue();
          if (name.equals("domain")) {
            cookie.domain_ = value.toLowerCase();
          } else if (name.equals("path")) {
            cookie.path_ = value;
          } else {
            logger.info("unrecognized v0 cookie attribute: " +
              name + "=" + value);
          }
        }
      }
      parser.eatLWS();
    }

    // validate the cookie -- see Netscape V0 spec
    final String requestHost = url.getHost().toLowerCase();
    final String requestPath = url.getPath();
    boolean valid = true;
    if (cookie.domain_ == null) {
      cookie.effectiveDomain_ = '.' + requestHost;
    } else {
      if (!requestHost.equals(cookie.domain_)) {
        if (!cookie.domain_.startsWith(".")) {
          cookie.effectiveDomain_ = '.' + cookie.domain_;
        } else {
          cookie.effectiveDomain_ = cookie.domain_;
        }
        if (!requestHost.endsWith(cookie.effectiveDomain_)) {
          logger.info("rejecting v0 cookie [bad domain - no match]: " +
            setCookie0Header);
          valid = false;
        } else {
          final int numPeriods =
            countOccurrences(cookie.effectiveDomain_, '.');
          boolean special = false;
          for (int i = 0; i < SPECIAL_DOMAINS.length; i++) {
            if (cookie.effectiveDomain_.endsWith(SPECIAL_DOMAINS[i])) {
              special = true;
              break;
            }
          }
          if (special ? (numPeriods < 2) : (numPeriods < 3)) {
            logger.info("rejecting v0 cookie [bad domain - no periods]: " +
              setCookie0Header);
            valid = false;
          }
        }
      } else {
        cookie.effectiveDomain_ = '.' + cookie.domain_;
      }
    }
    if (cookie.path_ == null) {
      cookie.effectivePath_ = requestPath;
    } else {
      // no path prefix check here - see the spec
      cookie.effectivePath_ = cookie.path_;
    }
    if (valid) {
      results.add(cookie);
    }

    // done
    return results;
  }
}
