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
[![Java8/11/17](https://github.com/GoogleCloudPlatform/appengine-java-standard/actions/workflows/maven.yml/badge.svg)](https://github.com/GoogleCloudPlatform/appengine-java-standard/actions/workflows/maven.yml)
[![Maven][maven-version-image]][maven-version-link]
[![Code of conduct](https://img.shields.io/badge/%E2%9D%A4-code%20of%20conduct-blue.svg)](https://github.com/GoogleCloudPlatform/appengine-java-standard/blob/main/CODE_OF_CONDUCT.md)

# Google App Engine Standard Environment Source Code for Java 8, Java 11 and Java 17.


This repository contains the Java Source Code for [Google App Engine
standard environment][ae-docs], the production runtime, the AppEngine APIs, and the local SDK.

[ae-docs]: https://cloud.google.com/appengine/docs/standard/java

## Prerequisites

### Use a JDK8 environment, so it can build the Java8 GAE runtime.

[jdk8](https://adoptium.net/), but using a JDK11 or JDK17 is also possible.

The shared code base is also used for GAE Java 11 and Java 17 build and test targets, using GitHub actions:

- [Java 8/11/17 Continuous Integration](https://github.com/GoogleCloudPlatform/appengine-java-standard/actions/workflows/maven.yml)

## Releases

This repository is the open source mirror of the Google App Engine Java source code that was used to produce Maven artifacts and runtime jars.
On Maven Central, the released artifacts from the internal source repository are using the versions like 1.9.xx.
The open source release mechanism used with this GitHub repository is using the version starting at 2.0.x.
Soon we will stop entirely pushing internal 1.9.xx artifacts and encourage all App Engine customers to use the new artifacts built from the GitHub project.


## Modules

Orange items are public modules artifacts and yellow are internal ones.
Modules ending with * are only used on the production server side.

<img width="964" alt="pom_dependencies" src="https://github.com/GoogleCloudPlatform/appengine-java-standard/blob/main/pom%20dependencies.png">

### App Engine Java APIs

Source code for all public APIs for com.google.appengine.api.* packages.

- [Public Documentation][ae-docs]
- [Javadocs](https://cloud.google.com/appengine/docs/standard/java/javadoc)
- [Source Code](https://github.com/GoogleCloudPlatform/appengine-java-standard/tree/master/api)
- [Source Code for repackaged API jar](https://github.com/GoogleCloudPlatform/appengine-java-standard/tree/master/appengine-api-1.0-sdk)

*  Maven pom.xml

    ```
    <packaging>war</packaging><!-- Servlet 3.1 WAR packaging-->
 ...
    <dependencies>
        <dependency>
            <groupId>com.google.appengine</groupId>
            <artifactId>appengine-api-1.0-sdk</artifactId>
            <version>2.0.4</version><!-- or later-->
        </dependency>
        <dependency>
          <groupId>javax.servlet</groupId>
          <artifactId>javax.servlet-api</artifactId>
          <version>3.1</version>
          <scope>provided</scope>
    </dependency>
        ...
    ```

*  Java 17 appengine-web.xml

    ```
    <?xml version="1.0" encoding="utf-8"?>
    <appengine-web-app xmlns="http://appengine.google.com/ns/1.0">
      <runtime>java17</runtime>
      <app-engine-apis>true</app-engine-apis>
    </appengine-web-app>
    ```

*  Java 11  appengine-web.xml

    ```
    <?xml version="1.0" encoding="utf-8"?>
    <appengine-web-app xmlns="http://appengine.google.com/ns/1.0">
      <runtime>java11</runtime>
      <app-engine-apis>true</app-engine-apis>
    </appengine-web-app>
    ```

### App Engine Java Remote APIs

Source code for remote APIs for App Engine.

- [Public Documentation](https://cloud.google.com/appengine/docs/standard/java/tools/remoteapi)
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

*  Maven pom.xml

```
    <dependency>
       <groupId>com.google.appengine</groupId>
       <artifactId>appengine-remote-api</artifactId>
       <version>2.0.4</version><!-- Or later-->
    </dependency>
```

#### User Visible Changes With Maven Builds

We moved `com.google.appengine.api.memcache.stdimpl` and its old dependency
`javax.cache` from `appengine-api-1.0-sdk.jar` to  a new jar `appengine-api-legacy.jar`.
  
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
       <version>2.0.4</version><!-- Or later-->
    </dependency>
```

###  Local Unit Testing for Java 8, 11, 17

[Code Sample](https://github.com/GoogleCloudPlatform/java-docs-samples/tree/main/unittests)

  *  Maven pom.xml
  
```
    <dependency>
      <groupId>com.google.appengine</groupId>
      <artifactId>appengine-testing</artifactId>
       <version>2.0.4</version><!-- Or later-->
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>com.google.appengine</groupId>
      <artifactId>appengine-api-stubs</artifactId>
       <version>2.0.4</version><!-- Or later-->
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>com.google.appengine</groupId>
      <artifactId>appengine-tools-sdk</artifactId>
       <version>2.0.4</version><!-- Or later-->
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

## Default entrypoint used by Java11 and Java17

The Java 11, Java 17 runtimes can benefit from extra user configuration when starting the JVM for web apps.

The default entrypoint used to boot the JVM is generated by App Engine Buildpacks.
Essentially, it is equivalent to define this entrypoint in the `appengine-web.xml` file. For example:

<pre><entrypoint>java --add-opens java.base/java.lang=ALL-UNNAMED  --add-opens java.base/java.nio.charset=ALL-UNNAMED -showversion -Xms32M -Xmx204M -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:+PrintCommandLineFlags -agentpath:/opt/cdbg/cdbg_java_agent.so=--log_dir=/tmp,--alsologtostderr=true,--cdbg_extra_class_path=/workspace/WEB-INF/classes:/workspace/WEB-INF/lib -Dclasspath.runtimebase=/base/java_runtime -Djava.class.path=/base/java_runtime/runtime-main.jar -Djava.library.path=/base/java_runtime: com/google/apphosting/runtime/JavaRuntimeMainWithDefaults --fixed_application_path=/workspace /base/java_runtime</entrypoint></pre>

We do not recommend changing this default entrypoint as the memory settings are calculated based on the instance type (F1, F2, F4) and memory available.

By default, we use `--add-opens java.base/java.lang=ALL-UNNAMED  --add-opens java.base/java.nio.charset=ALL-UNNAMED` to open some necessary JDK APIs.


## Entry Point Features

The entry point for the Java 11, Java 17 runtimes can be customized with user-defined environment variables added in the `appengine-web.xml` configuration file.

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

## Contributing

Check out the [contributing guide](CONTRIBUTING.md) to learn how you can report issues and help make changes.

Always be sure to follow the [Code of Conduct](CODE_OF_CONDUCT.md).

[maven-version-image]: https://img.shields.io/maven-central/v/com.google.appengine/appengine-api-1.0-sdk.svg
[maven-version-link]: https://search.maven.org/search?q=g:com.google.appengine%20AND%20a:appengine-api-1.0-sdk&core=gav

