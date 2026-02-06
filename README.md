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
[![Java17/21/25](https://github.com/GoogleCloudPlatform/appengine-java-standard/actions/workflows/maven.yml/badge.svg)](https://github.com/GoogleCloudPlatform/appengine-java-standard/actions/workflows/maven.yml)
[![Maven][maven-version-image]][maven-version-link]
[![Code of conduct](https://img.shields.io/badge/%E2%9D%A4-code%20of%20conduct-blue.svg)](https://github.com/GoogleCloudPlatform/appengine-java-standard/blob/main/CODE_OF_CONDUCT.md)

# Google App Engine (GAE) standard environment source code for Java 17, Java 21, Java 25.


This repository contains the Java source code for [Google App Engine
standard environment][ae-docs], the production runtime, the App Engine APIs, and the local SDK.

[ae-docs]: https://cloud.google.com/appengine/docs/standard/java

## Prerequisites

### Use a JDK17 environment, so it can build the Java17 GAE runtime.

[jdk17](https://adoptium.net/), but using a JDK21 or JDK25 is also possible.

The shared codebase is also used for GAE Java 17, Java 21 and Java 25 build and test targets, using GitHub actions:

- [Java 17/21/25 Continuous Integration](https://github.com/GoogleCloudPlatform/appengine-java-standard/actions/workflows/maven.yml)

## Releases

This repository is the open source mirror of the Google App Engine Java source code that was used to produce Maven artifacts and runtime jars.
The open source release mechanism used with this GitHub repository is using the version starting at 3.0.x, compatible for Java 17 or above.


## Modules

This repository is organized into several Maven modules. For a detailed description of each module, its dependencies, and how they relate to each other, see [modules.md](modules.md).

Orange items are public modules artifacts and yellow are internal ones.
Modules ending with * are only used on the production server side.

<img width="964" alt="pom_dependencies" src="https://github.com/GoogleCloudPlatform/appengine-java-standard/blob/main/images/pom_dependencies.png">

### App Engine Java APIs

Source code for all public APIs for com.google.appengine.api.* packages.

- [Public Documentation][ae-docs]
- [Latest javadoc.io API Javadocs from this repository](https://javadoc.io/doc/com.google.appengine/appengine-apis/latest/index.html)
- [Javadocs](https://cloud.google.com/appengine/docs/standard/java-gen2/reference/services/bundled/latest/overview)
- [Source Code](https://github.com/GoogleCloudPlatform/appengine-java-standard/tree/master/api)
- [Source Code for repackaged API jar](https://github.com/GoogleCloudPlatform/appengine-java-standard/tree/master/appengine-api-1.0-sdk)

Note that some App Engine APIs such as Blobstore and Taskqueues provide classes that depend on servlet APIs.
The base packages `com.google.appengine.api.blobstore` and `com.google.appengine.api.taskqueue` contain classes that use `javax.servlet.*` for EE6/EE8 compatibility.
For EE10 and EE11 environments that use the `jakarta.servlet.*` namespace, use classes from `com.google.appengine.api.blobstore.jakarta` and `com.google.appengine.api.taskqueue.jakarta` packages.
The packages `com.google.appengine.api.blobstore.ee10` and `com.google.appengine.api.taskqueue.ee10` are deprecated starting from version 3.0.0.

*  Maven pom.xml

    ```
    <packaging>war</packaging><!-- Servlet 3.1 WAR packaging-->
    ...
    <dependencies>
        <dependency>
            <groupId>com.google.appengine</groupId>
            <artifactId>appengine-api-1.0-sdk</artifactId>
            <version>4.0.1</version><!-- or later-->
        </dependency>
        <dependency>
          <groupId>javax.servlet</groupId>
          <artifactId>javax.servlet-api</artifactId>
          <version>3.1</version>
          <scope>provided</scope>
    </dependency>
    ...
    ```

*  Maven Java 21 with jakarta EE 10 support pom.xml

    ```
    <packaging>war</packaging><!-- Servlet 6.0 WAR packaging-->
    ...
    <dependencies>
        <dependency>
            <groupId>com.google.appengine</groupId>
            <artifactId>appengine-api-1.0-sdk</artifactId>
            <version>4.0.1</version><!-- or later-->
        </dependency>
        <dependency>
          <groupId>jakarta.servlet</groupId>
          <artifactId>jakarta.servlet-api</artifactId>
          <version>6.0.0</version>
          <scope>provided</scope>
        </dependency>
    ...
    ```

*  Maven Java 25 with jakarta EE 11 support pom.xml (EE10 is not supported in Java25, EE11 is fully compatible with EE10)

    ```
    <packaging>war</packaging><!-- Servlet 6.1 WAR packaging-->
    ...
    <dependencies>
        <dependency>
            <groupId>com.google.appengine</groupId>
            <artifactId>appengine-api-1.0-sdk</artifactId>
            <version>4.0.1</version><!-- or later-->
        </dependency>
        <dependency>
          <groupId>jakarta.servlet</groupId>
          <artifactId>jakarta.servlet-api</artifactId>
          <version>6.1.0</version>
          <scope>provided</scope>
        </dependency>
    ...
    ```


*  Java 21/25 with javax EE8 profile appengine-web.xml

    ```
    <?xml version="1.0" encoding="utf-8"?>
    <appengine-web-app xmlns="http://appengine.google.com/ns/1.0">
      <runtime>java25</runtime> <-- or java21-->
      <app-engine-apis>true</app-engine-apis>

      <!-- Add optionally:
      <system-properties>
        <property name="appengine.use.EE8" value="true"/>
    </system-properties>
    If you want to keep javax.servlet APIs and not jakarta.servlet by default
    -->
    </appengine-web-app>
    ```

- [Public Java 17/21/25 Documentation](https://cloud.google.com/appengine/docs/standard/java-gen2/runtime)
- [How to upgrade to Java21/25](https://cloud.google.com/appengine/docs/standard/java-gen2/upgrade-java-runtime)

*  Java 17 appengine-web.xml

    ```
    <?xml version="1.0" encoding="utf-8"?>
    <appengine-web-app xmlns="http://appengine.google.com/ns/1.0">
      <runtime>java17</runtime>
      <app-engine-apis>true</app-engine-apis>
    </appengine-web-app>
    ```

*  Java 21 appengine-web.xml (will default to EE10, but EE8 possible)

    ```
    <?xml version="1.0" encoding="utf-8"?>
    <appengine-web-app xmlns="http://appengine.google.com/ns/1.0">
      <runtime>java21</runtime>
      <app-engine-apis>true</app-engine-apis>
    </appengine-web-app>
    ```

*  Java 25 appengine-web.xml (will default to EE11, but EE8 possible)

    ```
    <?xml version="1.0" encoding="utf-8"?>
    <appengine-web-app xmlns="http://appengine.google.com/ns/1.0">
      <runtime>java25</runtime>
      <app-engine-apis>true</app-engine-apis>
    </appengine-web-app>
    ```

### App Engine Java Remote APIs

Source code for remote APIs for App Engine.

- [Public Documentation](https://cloud.google.com/appengine/docs/standard/java/tools/remoteapi)
- [Latest javadoc.io Javadocs from this repository](https://javadoc.io/doc/com.google.appengine/appengine-remote-api)
- [Public Sample remote server](https://github.com/GoogleCloudPlatform/java-docs-samples/tree/main/appengine-java8/remote-server)
- [Public Sample remote client](https://github.com/GoogleCloudPlatform/java-docs-samples/tree/main/appengine-java8/remote-client)
- [Source Code](https://github.com/GoogleCloudPlatform/appengine-java-standard/tree/master/remoteapi)

* Servlet web.xml

```
   <servlet>
     <display-name>Remote API Servlet</display-name>
     <servlet-name>RemoteApiServlet</servlet-name>
     <servlet-class>com.google.apphosting.utils.remoteapi.RemoteApiServlet</servlet-class>
     <load-on-startup>1</load-on-startup>
   </servlet>
   <servlet-mapping>
     <servlet-name>RemoteApiServlet</servlet-name>
     <url-pattern>/remote_api</url-pattern>
   </servlet-mapping>
```


* Servlet jakarta EE10 and EE11 web.xml

```
   <servlet>
     <display-name>Remote API Servlet</display-name>
     <servlet-name>RemoteApiServlet</servlet-name>
     <servlet-class>com.google.apphosting.utils.remoteapi.JakartaRemoteApiServlet</servlet-class>
     <load-on-startup>1</load-on-startup>
   </servlet>
   <servlet-mapping>
     <servlet-name>RemoteApiServlet</servlet-name>
     <url-pattern>/remote_api</url-pattern>
   </servlet-mapping>
```

*  Maven javax and jakarta API pom.xml

```
    <dependency>
       <groupId>com.google.appengine</groupId>
       <artifactId>appengine-remote-api</artifactId>
       <version>4.0.1</version><!-- or later-->
    </dependency>
```

#### User Visible Changes With Maven Builds

We moved `com.google.appengine.api.memcache.stdimpl` and its old dependency
`javax.cache` from `appengine-api-1.0-sdk.jar` to a new jar `appengine-api-legacy.jar`.

- [Latest javadoc.io Javadocs from this repository](https://javadoc.io/doc/com.google.appengine/appengine-api-legacy)

  Users who depend on the
  moved classes will need to also include `appengine-api-legacy.jar` when
  they build/deploy. Separating these classes allows
  `appengine-api-1.0-sdk` users to choose any version of `javax.cache`
  rather than being constrained by an obsolete included version.

  *  Maven pom.xml

```
    <dependency>
       <groupId>com.google.appengine</groupId>
       <artifactId>appengine-api-legacy.jar/artifactId>
       <version>4.0.1</version><!-- Or later-->
    </dependency>
```

###  Local Unit Testing for Java 17, 21, 25

- [Code Sample](https://github.com/GoogleCloudPlatform/java-docs-samples/tree/main/unittests)
- [Latest javadoc.io Javadocs from this repository](https://javadoc.io/doc/com.google.appengine/appengine-testing)

  *  Maven pom.xml

```
    <dependency>
      <groupId>com.google.appengine</groupId>
      <artifactId>appengine-testing</artifactId>
      <version>4.0.1</version><!-- or later-->
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>com.google.appengine</groupId>
      <artifactId>appengine-api-stubs</artifactId>
      <version>4.0.1</version><!-- or later-->
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>com.google.appengine</groupId>
      <artifactId>appengine-tools-sdk</artifactId>
      <version>4.0.1</version><!-- or later-->
      <scope>test</scope>
    </dependency>
```


### App Engine Java local development implementation of the APIs

Implementation of all the App Engine APIs for local environment (devappserver)
and local testing of an application before deployment.

- [Public Documentation][ae-docs]
- [Source Code](https://github.com/GoogleCloudPlatform/appengine-java-standard/tree/master/api_dev)
- [Source Code for repackaged APIs stubs jar](https://github.com/GoogleCloudPlatform/appengine-java-standard/tree/master/appengine-api-stubs)


### App Engine Java various local development utilities and devappserver

Source code for the App Engine local dev application server and local utilities.

- [Public Documentation](https://cloud.google.com/appengine/docs/standard/java/tools/using-local-server)
- [Source Code for tools APIs (appcfg)](https://github.com/GoogleCloudPlatform/appengine-java-standard/tree/master/lib/tools_api)
- [Source Code for XML validator (appcfg)](https://github.com/GoogleCloudPlatform/appengine-java-standard/tree/master/lib/xml_validator)
- [Source Code for local devappserver](https://github.com/GoogleCloudPlatform/appengine-java-standard/tree/master/runtime/local)
- [Source Code for shared utilities (appcfg)](https://github.com/GoogleCloudPlatform/appengine-java-standard/tree/master/shared_sdk)
- [Source Code for shared utilities (config)](https://github.com/GoogleCloudPlatform/appengine-java-standard/tree/master/utils)

### App Engine Java production runtime execution environment

Source code for the App Engine production application server and utilities. It is based on the Jetty9.4 Web Server.

- [Public Documentation][ae-docs]
- [Source Code for the runtime implementation](https://github.com/GoogleCloudPlatform/appengine-java-standard/tree/master/runtime/impl)
- [Source Code for the Java Main](https://github.com/GoogleCloudPlatform/appengine-java-standard/tree/master/runtime/main)
- [End-to-End test Web Applications](https://github.com/GoogleCloudPlatform/appengine-java-standard/tree/master/runtime/testapps)
- [End-to-End tests](https://github.com/GoogleCloudPlatform/appengine-java-standard/tree/master/runtime/test)
- [Source Code for runtime utilities](https://github.com/GoogleCloudPlatform/appengine-java-standard/tree/master/runtime/util)

## Default entrypoint used by Java17, Java21 and Java25

The Java 17, Java 21 and 25 runtimes can benefit from extra user configuration when starting the JVM for web apps.

The default entrypoint used to boot the JVM is generated by App Engine Buildpacks.
Essentially, it is equivalent to define this entrypoint in the `appengine-web.xml` file. For example:

<pre><entrypoint>java --add-opens java.base/java.lang=ALL-UNNAMED  --add-opens java.base/java.nio.charset=ALL-UNNAMED -showversion -Xms32M -Xmx204M -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:+PrintCommandLineFlags -Dclasspath.runtimebase=/base/java_runtime -Djava.class.path=/base/java_runtime/runtime-main.jar -Djava.library.path=/base/java_runtime: com/google/apphosting/runtime/JavaRuntimeMainWithDefaults --fixed_application_path=/workspace /base/java_runtime</entrypoint></pre>

We do not recommend changing this default entrypoint as the memory settings are calculated based on the instance type (F1, F2, F4) and memory available.

By default, we use `--add-opens java.base/java.lang=ALL-UNNAMED  --add-opens java.base/java.nio.charset=ALL-UNNAMED` to open some necessary JDK APIs.


## Entry Point Features

The entry point for the Java 17, Java 21, 25 runtimes can be customized with user-defined environment variables added in the `appengine-web.xml` configuration file.

The following table indicates the environment variables that can be used to enable/disable/configure features, and the default values if they are not set:

| Env Var           | Description          | Type     | Default                                     |
|-------------------|----------------------|----------|---------------------------------------------|
| `CPROF_ENABLE`    | Stackdriver Profiler | boolean  | `false`                                     |
| `GAE_MEMORY_MB`   | Available memory     | size     | Set by GAE or `/proc/meminfo`-400M          |
| `HEAP_SIZE_RATIO` | Memory for the heap  | percent  | 80                                          |
| `HEAP_SIZE_MB`    | Available heap       | size     | `${HEAP_SIZE_RATIO}`% of `${GAE_MEMORY_MB}` |
| `JAVA_HEAP_OPTS`  | JVM heap args        | JVM args | `-Xms${HEAP_SIZE_MB}M -Xmx${HEAP_SIZE_MB}M` |
| `JAVA_GC_OPTS`    | JVM GC args          | JVM args | `-XX:+UseG1GC` plus configuration           |
| `JAVA_USER_OPTS`  | JVM other args       | JVM args |                                             |
| `JAVA_OPTS`       | JVM args             | JVM args | See below                                   |

If not explicitly set, `JAVA_OPTS` is defaulted to:

   ```
   JAVA_OPTS:=-showversion \
              ${DBG_AGENT} \
              ${PROFILER_AGENT} \
              ${JAVA_HEAP_OPTS} \
              ${JAVA_GC_OPTS} \
              ${JAVA_USER_OPTS}
   ```

When `CPROF_ENABLE` is true, the default entrypoint adds the `PROFILER_AGENT` as:

`-agentpath:/opt/cprof/profiler_java_agent.so=--logtostderr`

For example, if your application code needs more `-add-opens` flags, you can use the `JAVA_USER_OPTS` environment variable defined in the `appengine-web.xml` file:

  ```
    <env-variables>
       <env-var name="JAVA_USER_OPTS" value="--add-opens java.base/java.util=ALL-UNNAMED" />
     </env-variables>
  ```

**Note:**

*   Only one of `appengine.use.EE8`, `appengine.use.EE10`, or `appengine.use.EE11` can be set to `true` at a time.
*   Flags can be set in `WEB-INF/appengine-web.xml` or via Java system properties (e.g., `-Dappengine.use.EE10=true`). System properties override `appengine-web.xml`.
*   EE6 = Servlet 3.1 (`javax.*`), EE8 = Servlet 4.0 (`javax.*`), EE10 = Servlet 5.0 (`jakarta.*`), EE11 = Servlet 6.0 (`jakarta.*`).
*   Jetty 12.1 should be fully backward compatible with Jetty 12.0 and EE11 version should also be backward compatible with EE10.
*   EE8 should also be backward compatible with EE6.




### Java 17 (`<runtime>java17</runtime>`)

| Flag(s) Set in `appengine-web.xml` or System Properties | Resulting Jetty | Support | Resulting EE Version | Notes |
| :--- | :--- | :--- | :--- | :--- |
| _None (default)_ | 9.4 | GA | 6 | |
| `appengine.use.EE8=true` | 12.0 | GA | 8 | |
| `appengine.use.EE10=true` | 12.0 | GA | 10 | |
| `appengine.use.EE8=true`, `appengine.use.jetty121=true` | 12.1 | Early Access | 8 | |
| `appengine.use.EE10=true`, `appengine.use.jetty121=true` | 12.1 | Early Access | 11 | **Upgraded**: EE10 is upgraded to EE11 on Jetty 12.1 |
| `appengine.use.EE11=true` | 12.1 | Early Access | 11 | `appengine.use.jetty121=true` is used automatically |

##

### Java 21 (`<runtime>java21</runtime>`)

| Flag(s) Set in `appengine-web.xml` or System Properties | Resulting Jetty | Support | Resulting EE Version | Notes |
| :--- | :--- | :--- | :--- | :--- |
| _None (default)_ | 12.0 | GA | 10 | |
| `appengine.use.EE8=true` | 12.0 | GA | 8 | |
| `appengine.use.EE10=true` | 12.0 | GA | 10 | |
| `appengine.use.jetty121=true` | 12.1 | Early Access | 11 | If no EE flag is set, `jetty121` defaults to EE11 |
| `appengine.use.EE8=true`, `appengine.use.jetty121=true` | 12.1 | Early Access | 8 | |
| `appengine.use.EE10=true`, `appengine.use.jetty121=true` | 12.1 | Early Access | 11 | **Upgraded**: EE10 is upgraded to EE11 on Jetty 12.1 |
| `appengine.use.EE11=true` | 12.1 | Early Access | 11 | `appengine.use.jetty121=true` is used automatically |

##

### Java 25 (`<runtime>java25</runtime>`)

| Flag(s) Set in `appengine-web.xml` or System Properties | Resulting Jetty | Support | Resulting EE Version | Notes |
| :--- | :--- | :--- | :--- | :--- |
| _None (default)_ | 12.1 | Early Access | 11 | `appengine.use.jetty121=true` is used |
| `appengine.use.EE8=true` | 12.1 | Early Access | 8 | `appengine.use.jetty121=true` is used |
| `appengine.use.EE11=true` | 12.1 | Early Access | 11 | `appengine.use.jetty121=true` is used |
| `appengine.use.EE10=true` | **ERROR** | Unsupported | **ERROR** | EE10 is not supported, use compatible version EE11 instead|

##

## Contributing

Check out the [contributing guide](CONTRIBUTING.md) to learn how you can report issues and help make changes.

Always be sure to follow the [Code of Conduct](CODE_OF_CONDUCT.md).

[maven-version-image]: https://img.shields.io/maven-central/v/com.google.appengine/appengine-api-1.0-sdk.svg
[maven-version-link]: https://search.maven.org/search?q=g:com.google.appengine%20AND%20a:appengine-api-1.0-sdk&core=gav
