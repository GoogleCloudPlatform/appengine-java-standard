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

package javax.mail.internet;

// can be in the form major/minor; charset=jobby

/**
 * @version $Rev: 669445 $ $Date: 2008-06-19 05:48:18 -0500 (Thu, 19 Jun 2008) $
 */
public class ContentType {
    private ParameterList _list;
    private String _minor;
    private String _major;

    public ContentType() {
        // the Sun version makes everything null here.
    }

    public ContentType(String major, String minor, ParameterList list) {
        _major = major;
        _minor = minor;
        _list = list;
    }

    public ContentType(String type) throws ParseException {
        // get a token parser for the type information
        HeaderTokenizer tokenizer = new HeaderTokenizer(type, HeaderTokenizer.MIME);

        // get the first token, which must be an ATOM
        HeaderTokenizer.Token token = tokenizer.next();
        if (token.getType() != HeaderTokenizer.Token.ATOM) {
            throw new ParseException("Invalid content type");
        }

        _major = token.getValue();

        // the MIME type must be major/minor
        token = tokenizer.next();
        if (token.getType() != '/') {
            throw new ParseException("Invalid content type");
        }


        // this must also be an atom.  Content types are not permitted to be wild cards.
        token = tokenizer.next();
        if (token.getType() != HeaderTokenizer.Token.ATOM) {
            throw new ParseException("Invalid content type");
        }

        _minor = token.getValue();

        // the remainder is parameters, which ParameterList will take care of parsing.
        String remainder = tokenizer.getRemainder();
        if (remainder != null) {
            _list = new ParameterList(remainder);
        }
    }

    public String getPrimaryType() {
        return _major;
    }

    public String getSubType() {
        return _minor;
    }

    public String getBaseType() {
        return _major + "/" + _minor;
    }

    public String getParameter(String name) {
        return (_list == null ? null : _list.get(name));
    }

    public ParameterList getParameterList() {
        return _list;
    }

    public void setPrimaryType(String major) {
        _major = major;
    }

    public void setSubType(String minor) {
        _minor = minor;
    }

    public void setParameter(String name, String value) {
        if (_list == null) {
            _list = new ParameterList();
        }
        _list.set(name, value);
    }

    public void setParameterList(ParameterList list) {
        _list = list;
    }

    public String toString() {
        if (_major == null || _minor == null) {
            return null;
        }
        
        // We need to format this as if we're doing it to set into the Content-Type
        // header.  So the parameter list gets added on as if the header name was 
        // also included. 
        String baseType = getBaseType(); 
        if (_list != null) {
            baseType += _list.toString(baseType.length() + "Content-Type: ".length()); 
        }
        
        return baseType;
    }

    public boolean match(ContentType other) {
        return _major.equalsIgnoreCase(other._major)
                && (_minor.equalsIgnoreCase(other._minor)
                || _minor.equals("*")
                || other._minor.equals("*"));
    }

    public boolean match(String contentType) {
        try {
            return match(new ContentType(contentType));
        } catch (ParseException e) {
            return false;
        }
    }
}
