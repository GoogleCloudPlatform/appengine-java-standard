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

package com.google.appengine.tools.admin;

import java.io.File;
import java.util.Arrays;
import javax.xml.XMLConstants;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import org.xml.sax.SAXException;

/**
 * Validates XML files passed on the command line against schemas also passed on the command line.
 * Command-line arguments come in pairs: first the name of an XML file, then the name of the XSD
 * file against which it should be validated.
 *
 * <p>The exit status of this program is 0 if and only if all of the schemas were successfully
 * validated.
 */
public class XmlValidator {
  private XmlValidator() {}

  public static void main(String[] args) throws Exception {
    if (args.length == 0 || args.length % 2 != 0) {
      System.err.println(
          "Expected a non-zero number of xml-file xml-schema argument pairs: "
              + Arrays.toString(args));
      System.exit(1);
    }
    File[] files = new File[args.length];
    int errors = 0;
    for (int i = 0; i < args.length; i++) {
      files[i] = new File(args[i]);
      if (!files[i].canRead()) {
        System.err.println("Does not exist or cannot be read: " + files[i]);
        errors++;
      }
    }
    if (errors > 0) {
      System.exit(1);
    }

    SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
    for (int i = 0; i < args.length; i += 2) {
      try {
        Schema schema = schemaFactory.newSchema(files[i + 1]);
        Source xmlSource = new StreamSource(files[i]);
        schema.newValidator().validate(xmlSource);
      } catch (SAXException e) {
        System.err.println("Validation of " + files[i] + " failed: " + e);
        errors++;
      }
    }

    System.exit((errors == 0) ? 0 : 1);
  }
}
