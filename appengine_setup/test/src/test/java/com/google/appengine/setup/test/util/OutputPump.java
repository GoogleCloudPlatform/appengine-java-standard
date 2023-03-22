/*
 * Copyright 2022 Google LLC
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

package com.google.appengine.setup.test.util;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;

public class OutputPump implements Runnable {
    private final BufferedReader stream;
    private final String echoPrefix;
    private final BlockingQueue<String> outputQueue = new LinkedBlockingQueue<>();

    public OutputPump(InputStream instream, String echoPrefix) {
      this.stream = new BufferedReader(new InputStreamReader(instream, StandardCharsets.UTF_8));
      this.echoPrefix = echoPrefix;
    }

    @Override
    @SneakyThrows
    public void run() {
      String line;
      while ((line = stream.readLine()) != null) {
        System.out.println(echoPrefix + line);
        outputQueue.add(line);
      }
    }

    void awaitOutputLineMatching(String pattern, long timeoutSeconds) throws InterruptedException {
      long timeoutMillis = TimeUnit.MILLISECONDS.convert(timeoutSeconds, TimeUnit.SECONDS);
      long deadline = System.currentTimeMillis() + timeoutMillis;
      while (true) {
        long remaining = deadline - System.currentTimeMillis();
        if (remaining <= 0) {
          throw new InterruptedException("Did not see pattern before deadline: " + pattern);
        }
        String line = outputQueue.poll(remaining, TimeUnit.MILLISECONDS);
        if (line != null && line.matches(pattern)) {
          return;
        }
      }
    }
  }