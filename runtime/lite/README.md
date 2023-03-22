<!--
 Copyright 2021 Google LLC

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

     https://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
-->

# App Engine Java "Lite" Runtime

The "Lite" runtime is an alternative lightweight entrypoint into the App Engine
Java SDK. This is how we use App Engine inside Google.

**This runtime has an unstable interface**. This interface is undergoing heavy
refactoring and further simplification. Do not use the Lite runtime at this
time, unless you can accommodate arbitrary and unannounced breaking interface
changes!

## To use

To use the Lite runtime, you must provide your own `main` class. For example:

```
package com.mycompany.myapp;

import com.google.appengine.runtime.lite.AppEngineRuntime;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.logging.LogManager;

public class AppMain {

  private AppMain() {}

  public static void main(String[] args) throws Exception {
    // Configure logging:
    try (final InputStream in =
        AppMain.class
            .getClassLoader()
            .getResourceAsStream("com/google/appengine/runtime/lite/logging.properties")) {
      LogManager.getLogManager().readConfiguration(in);
    }

    // Instantiate and run the Lite runtime:
    AppEngineRuntime.builder()
        .setServletWebappPath(Path.get("my/webapp/path"))
        .build()
        .run()
        .join();
  }
}
```

## Differences from the `JavaRuntimeMainWithDefaults`

The Lite runtime **drops support** for a few App Engine Standard features which
have better alternatives. This includes but is not limited to:

1.  You must provide your own `main` (see above).
2.  All jars must be on added to the classpath you configure when you start the
    Java Virtual Machine. The Lite runtime will not load any jars from your
    app's `WEB-INF/lib`, nor any classes from `WEB-INF/classes` *unless you
    explicitly add those paths to your classpath*, **and** configure the
    `Builder` with `setAllowWebinfJars(true)`. (We generally recommend using a
    tool like the Maven Assembly plugin to ship a single prepackaged jar file to
    production, instead of putting individual jars into `WEB-INF/lib` or
    anywhere *else* on your classpath.)
3.  `appengine-web.xml` is not read by the runtime, and thus various parameters
    must be configured in another way:
    1.  Java properties must be set as JVM command line options, or via
        `System.setProperty` in your code.
    2.  HTTP Sessions, if desired, must be configured via
        `AppEngineRuntime.setSessionsConfig()`
