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
# App Engine Java standard modules

This document describes the Maven modules in the App Engine Java standard project, their relationships, and key characteristics.

## Overview

The project is divided into several categories of modules:

*   **API**: Modules defining the App Engine APIs available to applications.
*   **SDK**: Modules providing implementations for local development and testing.
*   **Runtime**: Modules related to the App Engine runtime environment.
*   **Testing**: Modules for testing App Engine applications.
*   **Assembly**: Modules for packaging the SDK and runtime distributions.
*   **Utilities**: Helper modules used by other parts of the project.

The project supports multiple Jetty versions and servlet API specifications:
*   **Jetty 9.4** with **Servlet 3.1** (`javax.*`)
*   **Jetty 12.0** with **EE8** (`javax.*` Servlet 4.0) and **EE10** (`jakarta.*` Servlet 6.0)
*   **Jetty 12.1** with **EE8** (`javax.*` Servlet 4.0) and **EE11** (`jakarta.*` Servlet 6.1)

---

## Core modules

### [`protobuf`](protobuf/)
*   **Description**: Compiles protocol buffer definitions used by the APIs and runtime.
*   **Dependencies**: `protobuf-java`.

### [`sessiondata`](sessiondata/)
*   **Description**: Defines data structures for session management.
*   **Dependencies**: None.

### [`appengine_init`](appengine_init/)
*   **Description**: Contains code for App Engine environment initialization.
*   **Dependencies**: None.

### [`utils`](utils/)
*   **Description**: Provides common utility classes used across various modules.
*   **Dependencies**: `yamlbeans`, `flogger`, `antlr-runtime`, `javax.servlet-api`.

---

## API modules

### [`api` (`appengine-apis`)](api/)
*   **Description**: Core App Engine APIs for bundled services like Datastore, Users, Mail, URL Fetch, etc.
*   **Dependencies**: `protos`, `runtime-shared`, `appengine-utils`, `geronimo-javamail_1.4_spec`, `javax.servlet-api`, `jakarta.servlet-api`.

### [`appengine-api-1.0-sdk`](appengine-api-1.0-sdk/)
*   **Description**: The primary API artifact for users. It is a **shaded JAR** that bundles `appengine-apis` and its dependencies (like Guava, Protobuf, HTTP client libraries, etc.), relocating them to `com.google.appengine.repackaged.*` namespaces to prevent dependency conflicts in user applications. It keeps the "1.0-sdk" name as it is backward compatible with the initial version of AppEngine.
*   **Dependencies**: `appengine-apis`.

### [`api_legacy` (`appengine-api-legacy`)](api_legacy/)
*   **Description**: Provides legacy APIs (e.g., JCache) that were removed from `appengine-api-1.0-sdk`. This is a **shaded JAR** including `jcache-api`.
*   **Dependencies**: `appengine-api-1.0-sdk`.

### [`appengine_jsr107`](appengine_jsr107/)
*   **Description**: JSR-107 (JCache) implementation using App Engine Memcache.
*   **Dependencies**: `appengine-api-1.0-sdk`, `jsr107cache`.

### [`external/geronimo_javamail` (`geronimo-javamail_1.4_spec`)](external/geronimo_javamail/)
*   **Description**: Provides the JavaMail 1.4 API implementation used by the Mail API.
*   **Dependencies**: `javax.activation`.

---

## SDK and local development modules

Modules used for local application testing via `dev_appserver`.

### [`api_dev` (`appengine-apis-dev`)](api_dev/)
*   **Description**: Contains local implementations (stubs) of the App Engine services for use in the local development server.
*   **Dependencies**: `appengine-apis`, `protos`, `appengine-init`, `appengine-utils`, `shared-sdk`, `appengine-resources`.

### [`appengine-api-stubs`](appengine-api-stubs/)
*   **Description**: A **shaded JAR** containing the local service implementations from `appengine-apis-dev` and its dependencies, with package relocations to prevent conflicts. This is used for local testing.
*   **Dependencies**: `appengine-apis-dev`, `shared-sdk`, `appengine-api-1.0-sdk`.

### [`shared_sdk`](shared_sdk/)
*   **Description**: Code shared between different versions of the SDK.
*   **Dependencies**: `appengine-api-1.0-sdk`, `sessiondata`.

### [`shared_sdk_jetty9`](shared_sdk_jetty9/), [`shared_sdk_jetty12`](shared_sdk_jetty12/), [`shared_sdk_jetty121`](shared_sdk_jetty121/)
*   **Description**: Jetty-version-specific SDK code for Jetty 9, 12.0, and 12.1 respectively.
*   **Dependencies**: `shared-sdk`.

### [`local_runtime_shared_jetty9`](local_runtime_shared_jetty9/)
*   **Description**: Provides components for the local runtime based on Jetty 9 / javax Servlet. This is a **shaded JAR**. This module depends on the javax.servlet API, not on internal Jetty APIs.

*   **Dependencies**: `appengine-apis-dev`, `runtime-shared`.

### [`local_runtime_shared_jetty12`](local_runtime_shared_jetty12/)
*   **Description**: Provides components for the local runtime based on Jetty 12 / jakarta Servlet (EE10 or EE11). This is a **shaded JAR**.  This module depends on the jakarta.servlet API, not on internal Jetty APIs.
*   **Dependencies**: `appengine-apis-dev`, `runtime-shared`.

---

## Runtime modules

Modules used for the deployed application runtime.

### [`runtime_shared`](runtime_shared/)
*   **Description**: Code shared across different runtime implementations.
*   **Dependencies**: `sessiondata`.

### [`runtime-shared-jetty9`](runtime_shared_jetty9/)
*   **Description**: Jetty 9 / EE6 specific runtime components, including JSP/EL APIs. This is a **shaded JAR**.
*   **Dependencies**: `runtime-shared`.

### [`runtime-shared-jetty12`](runtime_shared_jetty12/)
*   **Description**: Jetty 12.0 / EE8 specific runtime components (Servlet 4.0), including JSP/EL APIs. This is a **shaded JAR**.
*   **Dependencies**: `runtime-shared`.

### [`runtime-shared-jetty12-ee10`](runtime_shared_jetty12_ee10/)
*   **Description**: Jetty 12.0 / EE10 specific runtime components (Servlet 6.0), including JSP/EL APIs. This is a **shaded JAR**.
*   **Dependencies**: `runtime-shared`.

### [`runtime-shared-jetty121-ee8`](runtime_shared_jetty121_ee8/)
*   **Description**: Jetty 12.1 / EE8 specific runtime components (Servlet 4.0), including JSP/EL APIs. This is a **shaded JAR**.
*   **Dependencies**: `runtime-shared`.

### [`runtime-shared-jetty121-ee11`](runtime_shared_jetty121_ee11/)
*   **Description**: Jetty 12.1 / EE11 specific runtime components (Servlet 6.1), including JSP/EL APIs. This is a **shaded JAR**.
*   **Dependencies**: `runtime-shared`.

### [`runtime`](runtime/)
*   **Description**: Parent POM for runtime implementation modules (`impl`, `main`, `deployment`, `local_*`, etc.).

---

## Quickstart Generator Modules

These modules generate Jetty quickstart `web.xml` file configurations to speed up server startup. Each module corresponds to a specific Jetty and EE version combination.

*   [`quickstartgenerator`](quickstartgenerator/): Jetty 9
*   [`quickstartgenerator-jetty12`](quickstartgenerator_jetty12/): Jetty 12.0 / EE8
*   [`quickstartgenerator-jetty12-ee10`](quickstartgenerator_jetty12_ee10/): Jetty 12.0 / EE10
*   [`quickstartgenerator-jetty121-ee8`](quickstartgenerator_jetty121_ee8/): Jetty 12.1 / EE8
*   [`quickstartgenerator-jetty121-ee11`](quickstartgenerator_jetty121_ee11/): Jetty 12.1 / EE11

---

## Testing modules

### [`appengine_testing`](appengine_testing/)
*   **Description**: Provides helper classes for testing App Engine applications, for example, `LocalServiceTestHelper`. This is a **shaded JAR**.
*   **Dependencies**: `appengine-api-stubs`, `appengine-tools-sdk`.

---

## Remote API module

### [`remoteapi`](remoteapi/)
*   **Description**: Client library for remotely accessing App Engine services from other applications. This is a **shaded JAR**.
*   **Dependencies**: `appengine-api-1.0-sdk`.

---

## Assembly Modules

These modules use `maven-assembly-plugin` and `maven-dependency-plugin` to build the final SDK distribution ZIP file.

### [`jetty12_assembly`](jetty12_assembly/)
*   **Description**: Unpacks and customizes a Jetty 12.0 distribution for inclusion in the SDK.

### [`jetty121_assembly`](jetty121_assembly/)
*   **Description**: Unpacks and customizes a Jetty 12.1 distribution for inclusion in the SDK.

### [`sdk_assembly`](sdk_assembly/)
*   **Description**: The main assembly module. It gathers artifacts from all other modules, such as `appengine-api-1.0-sdk`, `appengine-api-stubs`, `appengine-tools-sdk`, runtime modules, or Jetty assemblies, and packages them into the `appengine-java-sdk.zip` distribution file.
