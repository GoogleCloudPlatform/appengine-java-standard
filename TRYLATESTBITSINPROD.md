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

Let's assume the current build version is `5.0.2-SNAPSHOT`.

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
        <appengine.runtime.version>5.0.2-SNAPSHOT</appengine.runtime.version>
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

## Automation Script

Here is a script to automate the `pom.xml` changes described above. 
Save it as `update_pom.sh` and run it with your `pom.xml` path as an argument.

```bash
#!/bin/bash

POM_FILE=$1

if [ -z "$POM_FILE" ]; then
  echo "Usage: $0 <path-to-pom.xml>"
  exit 1
fi

if [ ! -f "$POM_FILE" ]; then
  echo "File not found: $POM_FILE"
  exit 1
fi

# Add Properties
if grep -q "<properties>" "$POM_FILE"; then
    # Inserts the properties before the closing </properties> tag
    sed -i '/<\/properties>/i \
            <appengine.runtime.version>5.0.2-SNAPSHOT<\/appengine.runtime.version>\
            <appengine.runtime.location>target/${project.artifactId}-${project.version}<\/appengine.runtime.location>' "$POM_FILE"
else
    # If no properties tag exists, insert it before dependencies
    sed -i '/<dependencies>/i \
    <properties>\
        <appengine.runtime.version>5.0.2-SNAPSHOT<\/appengine.runtime.version>\
        <appengine.runtime.location>target/${project.artifactId}-${project.version}<\/appengine.runtime.location>\
    <\/properties>' "$POM_FILE"
fi

# Add Dependency
# Inserts the dependency before the closing </dependencies> tag
sed -i '/<\/dependencies>/i \
    <dependency>\
        <groupId>com.google.appengine</groupId>\
        <artifactId>runtime-deployment</artifactId>\
        <version>${appengine.runtime.version}</version>\
        <type>zip</type>\
    </dependency>' "$POM_FILE"

# Add Plugin
# Inserts the plugin before the closing </plugins> tag
sed -i '/<\/plugins>/i \
            <plugin>\
                <groupId>com.coderplus.maven.plugins</groupId>\
                <artifactId>copy-rename-maven-plugin</artifactId>\
                <version>1.0</version>\
                <executions>\
                    <execution>\
                        <id>rename-file</id>\
                        <phase>pre-integration-test</phase>\
                        <goals>\
                            <goal>rename</goal>\
                        </goals>\
                        <configuration>\
                            <fileSets>\
                                <fileSet>\
                                    <sourceFile>${appengine.runtime.location}/WEB-INF/lib/runtime-impl-jetty9-${appengine.runtime.version}.jar</sourceFile>\
                                    <destinationFile>${appengine.runtime.location}/runtime-impl-jetty9.jar</destinationFile>\
                                </fileSet>\
                                <fileSet>\
                                    <sourceFile>${appengine.runtime.location}/WEB-INF/lib/runtime-shared-jetty9-${appengine.runtime.version}.jar</sourceFile>\
                                    <destinationFile>${appengine.runtime.location}/runtime-shared-jetty9.jar</destinationFile>\
                                </fileSet>\
                                <fileSet>\
                                    <sourceFile>${appengine.runtime.location}/WEB-INF/lib/runtime-impl-jetty12-${appengine.runtime.version}.jar</sourceFile>\
                                    <destinationFile>${appengine.runtime.location}/runtime-impl-jetty12.jar</destinationFile>\
                                </fileSet>\
                                <fileSet>\
                                    <sourceFile>${appengine.runtime.location}/WEB-INF/lib/runtime-impl-jetty121-${appengine.runtime.version}.jar</sourceFile>\
                                    <destinationFile>${appengine.runtime.location}/runtime-impl-jetty121.jar</destinationFile>\
                                </fileSet>\
                                <fileSet>\
                                    <sourceFile>${appengine.runtime.location}/WEB-INF/lib/runtime-shared-jetty12-${appengine.runtime.version}.jar</sourceFile>\
                                    <destinationFile>${appengine.runtime.location}/runtime-shared-jetty12.jar</destinationFile>\
                                </fileSet>\
                                <fileSet>\
                                    <sourceFile>${appengine.runtime.location}/WEB-INF/lib/runtime-shared-jetty12-ee10-${appengine.runtime.version}.jar</sourceFile>\
                                    <destinationFile>${appengine.runtime.location}/runtime-shared-jetty12-ee10.jar</destinationFile>\
                                </fileSet>\
                                <fileSet>\
                                    <sourceFile>${appengine.runtime.location}/WEB-INF/lib/runtime-shared-jetty121-ee8-${appengine.runtime.version}.jar</sourceFile>\
                                    <destinationFile>${appengine.runtime.location}/runtime-shared-jetty121-ee8.jar</destinationFile>\
                                </fileSet>\
                                <fileSet>\
                                    <sourceFile>${appengine.runtime.location}/WEB-INF/lib/runtime-shared-jetty121-ee11-${appengine.runtime.version}.jar</sourceFile>\
                                    <destinationFile>${appengine.runtime.location}/runtime-shared-jetty121-ee11.jar</destinationFile>\
                                </fileSet>\
                                <fileSet>\
                                    <sourceFile>${appengine.runtime.location}/WEB-INF/lib/runtime-main-${appengine.runtime.version}.jar</sourceFile>\
                                    <destinationFile>${appengine.runtime.location}/runtime-main.jar</destinationFile>\
                                </fileSet>\
                            </fileSets>\
                        </configuration>\
                    </execution>\
                </executions>\
            </plugin>' "$POM_FILE"

echo "Updated $POM_FILE"
```


## For the Gradle build system

If you are using the Gradle build system, you can apply the following changes to your build script.

These instructions configure the `war` task to do two important things:
1. **Move the jars to the top of the WAR directory:** The `into("")` (or `into('.')`) block combined with `eachFile { path = name }` extracts the jars and flattens the directory structure, placing them directly in the root of the generated WAR.
2. **Rename the jars:** The `rename` block strips the version string (e.g., `-5.0.2-SNAPSHOT`) from the extracted jars so their names exactly match the `-Djava.class.path=runtime-main.jar` argument specified in the `appengine-web.xml` entrypoint.

### Kotlin
```
import org.gradle.api.tasks.bundling.War

// 1. Define the target runtime version
val gaeRuntimeVersion = "5.0.2-SNAPSHOT" // Change this to your desired version

// 2. Create a custom configuration for the runtime zip
val gaeRuntimeZip by configurations.creating

dependencies {
    // 3. Declare the dependency on the App Engine runtime deployment zip
    gaeRuntimeZip("com.google.appengine:runtime-deployment:$gaeRuntimeVersion@zip")

    // ... your other standard dependencies (e.g., implementation(...)) ...
}

// 4. Configure the WAR task to unpack and rename the jars
tasks.named<War>("war") {
    into("") {
        // Extract the contents of the zip file
        from(gaeRuntimeZip.map { zipTree(it) }) {
            // Flatten the directory structure
            eachFile {
                path = name
            }
            includeEmptyDirs = false
        }

        // Strip the version number from the jar files
        rename { fileName: String ->
            fileName.replace("-$gaeRuntimeVersion", "")
        }
    }
}
```

### Groovy

```
// 1. Define the target runtime version
def gaeRuntimeVersion = "5.0.2-SNAPSHOT" // Change this to your desired version

// 2. Create a custom configuration for the runtime zip
configurations {
    gaeRuntimeZip
}

dependencies {
    // 3. Declare the dependency on the App Engine runtime deployment zip
    gaeRuntimeZip "com.google.appengine:runtime-deployment:${gaeRuntimeVersion}@zip"

    // ... your other standard dependencies ...
}

// 4. Configure the WAR task to unpack and rename the jars
war {
    into('.') {
        // Extract the contents of the zip file
        from({ configurations.gaeRuntimeZip.collect { zipTree(it) } }) {
            // Flatten the directory structure just in case the zip has a root folder
            eachFile { file ->
                file.path = file.name
            }
            includeEmptyDirs = false
        }

        // Strip the version number from the jar files
        // (e.g., 'runtime-main-5.0.2-SNAPSHOT.jar' becomes 'runtime-main.jar')
        rename { String fileName ->
            fileName.replace("-${gaeRuntimeVersion}", "")
        }
    }
}
```