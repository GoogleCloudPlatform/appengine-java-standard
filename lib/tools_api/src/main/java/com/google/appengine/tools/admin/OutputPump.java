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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.logging.Level;
import java.util.logging.Logger;

// NB(tobyr) I shamelessly copied this from the SecurityContest app. It'd be
// nice to find a way to share this code, but there may not be a point to it
// once the security contest is over. 
/**
 * Pumps lines from one stream onto another, used specifically for getting
 * the stdout/stderr of a child process onto the parent's.
 *
 *
 */
public class OutputPump implements Runnable {
  private static final Logger logger = Logger.getLogger(OutputPump.class.getName());

  private final BufferedReader stream;
  private final PrintWriter output;

  public OutputPump(InputStream instream, PrintWriter outstream) {
    stream = new BufferedReader(new InputStreamReader(instream, Charset.defaultCharset()));
    output = (outstream == null) ? new PrintWriter(System.out, true) : outstream;
  }

  @Override
  public void run() {
    try {
      String line;
      while ((line = stream.readLine()) != null) {
        output.println(line);
        output.flush();
      }
    } catch (IOException ix) {
      // If we can't log here, then where?
      logger.log(Level.SEVERE, "Unexpected failure while trying to record errors.", ix);
    }
  }
}
