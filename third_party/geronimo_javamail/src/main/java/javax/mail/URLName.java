/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package javax.mail;

import java.io.ByteArrayOutputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * @version $Rev: 593290 $ $Date: 2007-11-08 14:18:29 -0600 (Thu, 08 Nov 2007) $
 */
public class URLName {
    private static final String nonEncodedChars = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ_-.*";
    
    private String file;
    private String host;
    private String password;
    private int port;
    private String protocol;
    private String ref;
    private String username;
    protected String fullURL;
    private int hashCode;

    public URLName(String url) {
        parseString(url);
    }

    protected void parseString(String url) {
        URI uri;
        try {
            if (url == null) {
                uri = null;
            } else {
                uri = new URI(url);
            }
        } catch (URISyntaxException e) {
            uri = null;
        }
        if (uri == null) {
            protocol = null;
            host = null;
            port = -1;
            file = null;
            ref = null;
            username = null;
            password = null;
            return;
        }

        protocol = checkBlank(uri.getScheme());
        host = checkBlank(uri.getHost());
        port = uri.getPort();
        file = checkBlank(uri.getPath());
        // if the file starts with "/", we need to strip that off. 
        // URL and URLName do not have the same behavior when it comes 
        // to keeping that there. 
        if (file != null && file.length() > 1 && file.startsWith("/")) {
            file = checkBlank(file.substring(1)); 
        }
        
        ref = checkBlank(uri.getFragment());
        String userInfo = checkBlank(uri.getUserInfo());
        if (userInfo == null) {
            username = null;
            password = null;
        } else {
            int pos = userInfo.indexOf(':');
            if (pos == -1) {
                username = userInfo;
                password = null;
            } else {
                username = userInfo.substring(0, pos);
                password = userInfo.substring(pos + 1);
            }
        }
        updateFullURL();
    }

    public URLName(String protocol, String host, int port, String file, String username, String password) {
        this.protocol = checkBlank(protocol);
        this.host = checkBlank(host);
        this.port = port;
        if (file == null || file.length() == 0) {
            this.file = null;
            ref = null;
        } else {
            int pos = file.indexOf('#');
            if (pos == -1) {
                this.file = file;
                ref = null;
            } else {
                this.file = file.substring(0, pos);
                ref = file.substring(pos + 1);
            }
        }
        this.username = checkBlank(username);
        if (this.username != null) {
            this.password = checkBlank(password);
        } else {
            this.password = null;
        }
        username = encode(username); 
        password = encode(password); 
        updateFullURL();
    }

    public URLName(URL url) {
        protocol = checkBlank(url.getProtocol());
        host = checkBlank(url.getHost());
        port = url.getPort();
        file = checkBlank(url.getFile());
        ref = checkBlank(url.getRef());
        String userInfo = checkBlank(url.getUserInfo());
        if (userInfo == null) {
            username = null;
            password = null;
        } else {
            int pos = userInfo.indexOf(':');
            if (pos == -1) {
                username = userInfo;
                password = null;
            } else {
                username = userInfo.substring(0, pos);
                password = userInfo.substring(pos + 1);
            }
        }
        updateFullURL();
    }

    private static String checkBlank(String target) {
        if (target == null || target.length() == 0) {
            return null;
        } else {
            return target;
        }
    }

    private void updateFullURL() {
        hashCode = 0;
        StringBuffer buf = new StringBuffer(100);
        if (protocol != null) {
            buf.append(protocol).append(':');
            if (host != null) {
                buf.append("//");
                if (username != null) {
                    buf.append(encode(username));
                    if (password != null) {
                        buf.append(':').append(encode(password));
                    }
                    buf.append('@');
                }
                buf.append(host);
                if (port != -1) {
                    buf.append(':').append(port);
                }
                if (file != null) {
                    buf.append('/').append(file);
                }
                hashCode = buf.toString().hashCode();
                if (ref != null) {
                    buf.append('#').append(ref);
                }
            }
        }
        fullURL = buf.toString();
    }

    public boolean equals(Object o) {
        if (o instanceof URLName == false) {
            return false;
        }
        URLName other = (URLName) o;
        // check same protocol - false if either is null
        if (protocol == null || other.protocol == null || !protocol.equals(other.protocol)) {
            return false;
        }

        if (port != other.port) {
            return false;
        }

        // check host - false if not (both null or both equal)
        return areSame(host, other.host) && areSame(file, other.file) && areSame(username, other.username) && areSame(password, other.password);
    }

    private static boolean areSame(String s1, String s2) {
        if (s1 == null) {
            return s2 == null;
        } else {
            return s1.equals(s2);
        }
    }

    public int hashCode() {
        return hashCode;
    }

    public String toString() {
        return fullURL;
    }

    public String getFile() {
        return file;
    }

    public String getHost() {
        return host;
    }

    public String getPassword() {
        return password;
    }

    public int getPort() {
        return port;
    }

    public String getProtocol() {
        return protocol;
    }

    public String getRef() {
        return ref;
    }

    public URL getURL() throws MalformedURLException {
        return new URL(fullURL);
    }

    public String getUsername() {
        return username;
    }
    
    /**
     * Perform an HTTP encoding to the username and 
     * password elements of the URLName.  
     * 
     * @param v      The input (uncoded) string.
     * 
     * @return The HTTP encoded version of the string. 
     */
    private static String encode(String v) {
        // make sure we don't operate on a null string
        if (v == null) {
            return null; 
        }
        boolean needsEncoding = false; 
        for (int i = 0; i < v.length(); i++) {
            // not in the list of things that don't need encoding?
            if (nonEncodedChars.indexOf(v.charAt(i)) == -1) {
                // got to do this the hard way
                needsEncoding = true; 
                break; 
            }
        }
        // just fine the way it is. 
        if (!needsEncoding) {
            return v; 
        }
        
        // we know we're going to be larger, but not sure by how much.  
        // just give a little extra
        StringBuffer encoded = new StringBuffer(v.length() + 10);
            
        // we get the bytes so that we can have the default encoding applied to 
        // this string.  This will flag the ones we need to give special processing to. 
        byte[] data = v.getBytes(); 
        
        for (int i = 0; i < data.length; i++) {
            // pick this up as a one-byte character The 7-bit ascii ones will be fine 
            // here. 
            char ch = (char)(data[i] & 0xff); 
            // blanks get special treatment 
            if (ch == ' ') {
                encoded.append('+'); 
            }
            // not in the list of things that don't need encoding?
            else if (nonEncodedChars.indexOf(ch) == -1) {
                // forDigit() uses the lowercase letters for the radix.  The HTML specifications 
                // require the uppercase letters. 
                char firstChar = Character.toUpperCase(Character.forDigit((ch >> 4) & 0xf, 16)); 
                char secondChar = Character.toUpperCase(Character.forDigit(ch & 0xf, 16)); 
                
                // now append the encoded triplet. 
                encoded.append('%'); 
                encoded.append(firstChar); 
                encoded.append(secondChar); 
            }
            else {
                // just add this one to the buffer 
                encoded.append(ch); 
            }
        }
        // convert to string form. 
        return encoded.toString(); 
    }
}
