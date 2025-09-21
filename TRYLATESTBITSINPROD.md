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

# How to test the latest App Engine Java runtime in prod.

The Google App Engine Java runtime is pushed almost every 2 weeks in production
after an internal QA validation. The runtime jars come from this github repository.
Since all the runtime code is now in open source, what if you could add the runtime jars
somewhere in your App Engine Application and use these jars instead of the one in prod?
You could either use a custom build of the runtime of pin to a version you like of the runtime,
without being impacted with a scheduled new runtime push.

Well, it is possible, by changing just a little bit your application configuration and your
pom.xml file.

First, you need to decide which App Engine Java runtime jars version you want to use. There are 6 runtime jars that
are bundled as a Maven assembly under `<artifactId>runtime-deployment</artifactId>`:

  * runtime-impl-jetty9.jar
  * runtime-impl-jetty12.jar
  * runtime-impl-jetty121.jar
  * runtime-shared-jetty9.jar
  * runtime-shared-jetty12.jar
  * runtime-shared-jetty121-ee8.jar
  * runtime-shared-jetty12-ee10.jar
  * runtime-shared-jetty121-ee11.jar
  * runtime-main.jar

Let's say you want the latest from head in this github repository. You could built the 9 jars, add them at the
top of your web application and change the entrypoint to boot with these jars instead of the one maintained in production.


```
 git clone https://github.com/GoogleCloudPlatform/appengine-java-standard.git
 cd appengine-java-standard
 ./mvnw clean install
```

Let's assume the current build version is `3.0.0-SNAPSHOT`.

See the output of the runtime deployment module which contains all the jars needed by the runtime:


```
ls  runtime/deployment/target/runtime-deployment-*/
runtime-impl-jetty12.jar	runtime-impl-jetty121.jar	runtime-main.jar		runtime-shared-jetty12.jar
runtime-shared-jetty121-ee8.jar  runtime-shared-jetty121-ee11.jar
runtime-impl-jetty9.jar		runtime-shared-jetty12-ee10.jar	runtime-shared-jetty9.jar
```

These jars are pushed in Maven Central as well under artifact com.google.appengine:runtime-deployment.
For example, look at all the pushed versions in https://repo1.maven.org/maven2/com/google/appengine/runtime-deployment

The idea is to add these runtime jars inside your web application during deployment and change the entry point to start using these runtime jars instead of the ones provided by default by the App Engine runtime.

Add the dependency for the GAE runtime jars in your application pom.xml file:

```
 <properties>
        <appengine.runtime.version>3.0.0-SNAPSHOT</appengine.runtime.version>
        <appengine.runtime.location>target/${project.artifactId}-${project.version}</appengine.runtime.location>
 <properties>
 ...
<dependency>
    <groupId>com.google.appengine</groupId>
    <artifactId>runtime-deployment</artifactId>
    <version>${appengine.runtime.version}</version>
    <type>zip</type>
</dependency>
```

And in the pom.xml plugin section, copy the runtime jars at the top of the
deployed web application.

```
<plugin>
    <groupId>com.coderplus.maven.plugins</groupId>
    <artifactId>copy-rename-maven-plugin</artifactId>
    <version>1.0</version>
    <executions>
        <execution>
            <id>rename-file</id>
            <phase>pre-integration-test</phase>
            <goals>
                <goal>rename</goal>
            </goals>
            <configuration>
                <fileSets>
                    <fileSet>
                        <sourceFile>${appengine.runtime.location}/WEB-INF/lib/runtime-impl-jetty9-${appengine.runtime.version}.jar</sourceFile>
                        <destinationFile>${appengine.runtime.location}/runtime-impl-jetty9.jar</destinationFile>
                    </fileSet>
                    <fileSet>
                        <sourceFile>${appengine.runtime.location}/WEB-INF/lib/runtime-shared-jetty9-${appengine.runtime.version}.jar</sourceFile>
                        <destinationFile>${appengine.runtime.location}/runtime-shared-jetty9.jar</destinationFile>
                    </fileSet>
                    <fileSet>
                        <sourceFile>${appengine.runtime.location}/WEB-INF/lib/runtime-impl-jetty12-${appengine.runtime.version}.jar</sourceFile>
                        <destinationFile>${appengine.runtime.location}/runtime-impl-jetty12.jar</destinationFile>
                    </fileSet>
                     <fileSet>
                        <sourceFile>${appengine.runtime.location}/WEB-INF/lib/runtime-impl-jetty121-${appengine.runtime.version}.jar</sourceFile>
                        <destinationFile>${appengine.runtime.location}/runtime-impl-jetty121.jar</destinationFile>
                    </fileSet>
                    <fileSet>
                        <sourceFile>${appengine.runtime.location}/WEB-INF/lib/runtime-shared-jetty12-${appengine.runtime.version}.jar</sourceFile>
                        <destinationFile>${appengine.runtime.location}/runtime-shared-jetty12.jar</destinationFile>
                    </fileSet>
                     <fileSet>
                        <sourceFile>${appengine.runtime.location}/WEB-INF/lib/runtime-shared-jetty12-ee10-${appengine.runtime.version}.jar</sourceFile>
                        <destinationFile>${appengine.runtime.location}/runtime-shared-jetty12-ee10.jar</destinationFile>
                    </fileSet>
                     <fileSet>
                        <sourceFile>${appengine.runtime.location}/WEB-INF/lib/runtime-shared-jetty121-ee8-${appengine.runtime.version}.jar</sourceFile>
                        <destinationFile>${appengine.runtime.location}/runtime-shared-jetty121-ee8.jar</destinationFile>
                    </fileSet>
                     <fileSet>
                        <sourceFile>${appengine.runtime.location}/WEB-INF/lib/runtime-shared-jetty121-ee11-${appengine.runtime.version}.jar</sourceFile>
                        <destinationFile>${appengine.runtime.location}/runtime-shared-jetty121-ee11.jar</destinationFile>
                    </fileSet>
                    <fileSet>
                        <sourceFile>${appengine.runtime.location}/WEB-INF/lib/runtime-main-${appengine.runtime.version}.jar</sourceFile>
                        <destinationFile>${appengine.runtime.location}/runtime-main.jar</destinationFile>
                    </fileSet>
                </fileSets>
            </configuration>
        </execution>
    </executions>
</plugin>
```

In the appengine-web.xml, modify the entrypoint to use the bundled runtime jars instead of the ones that are part of the base image:


```
<?xml version="1.0" encoding="utf-8"?>
<appengine-web-app xmlns="http://appengine.google.com/ns/1.0">
   <runtime>java21</runtime>
   <app-engine-apis>true</app-engine-apis>
   <system-properties>
        <property name="java.util.logging.config.file" value="WEB-INF/logging.properties"/>
    </system-properties>
    <entrypoint>
  java
  --add-opens java.base/java.lang=ALL-UNNAMED
  --add-opens java.base/java.nio.charset=ALL-UNNAMED
  --add-opens java.base/java.util.concurrent=ALL-UNNAMED
  --add-opens java.logging/java.util.logging=ALL-UNNAMED
  -showversion -XX:+PrintCommandLineFlags
  -Djava.class.path=runtime-main.jar
  -Dclasspath.runtimebase=.:
  com/google/apphosting/runtime/JavaRuntimeMainWithDefaults
  --fixed_application_path=.
  .
    </entrypoint>
</appengine-web-app>
```

You have now a GAE Web App project which is configured to use at runtime the jars that were just built locally via github.
You can deploy the application the way do and see it running in prod with the latest runtime jars.

