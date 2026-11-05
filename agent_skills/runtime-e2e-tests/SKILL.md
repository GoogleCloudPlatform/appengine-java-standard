---
name: runtime-e2e-tests
description: Use when building the runtime deployment or running or writing end-to-end tests in runtime/test (JavaRuntimeViaHttpBase, JavaRuntimeAllInOneTest, CloudTasksSessionTest, RuntimeJarDependencyClosureTest, ...). Covers the build commands, JDK requirements, how test parameters depend on the JDK, and common local failures that are not real regressions.
---

<!--
 Copyright 2026 Google LLC

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

# Runtime end-to-end tests (runtime/test)

Tests in `runtime/test` start the real runtime as a separate process from
`runtime/deployment/target/runtime-deployment-<version>/` (the `appengine.runtime.dir` system
property, set by the root pom) and talk to it over HTTP, with a fake API server
(`HttpApiServer` / `DummyApiServer`).

## Build

- A full JDK is required (`javac` and `lib/ct.sym`); a JRE fails with
  `release version 17 not supported`. Set `JAVA_HOME` to the JDK.
- Build what the tests need, skipping tests and integration tests:

  ```
  ./mvnw install -DskipTests -DskipITs -pl runtime/deployment,runtime/test -am
  ```

  `-pl runtime/test -am` alone does **not** rebuild the runtime jars or the deployment, so the
  tests would run an old runtime. After changing a runtime_impl pom, rebuild with
  `-pl runtime/runtime_impl_jetty12,runtime/runtime_impl_jetty121,runtime/deployment`.
- `-DskipTests` does not skip failsafe ITs (e.g. `remoteapi` `ShadingIT`); add `-DskipITs`.
- Do not resume a parallel (`-T`) build with `-rf`: modules that were not built yet are skipped.

## Run

```
./mvnw test -pl runtime/test -Dtest=CloudTasksSessionTest -Dsurefire.failIfNoSpecifiedTests=false
```

`JavaRuntimeViaHttpBase.allVersions()` only returns the runtime/Jetty/EE combinations supported by
the JDK running the tests: on JDK 25 only `java25` (Jetty 12.1, EE8/EE11), which uses
`runtime-impl-jetty121.jar`. `runtime-impl-jetty12.jar` (Jetty 12.0) is only exercised on JDK 17
and 21 (CI runs 17, 21, 25 and 28-ea). On JDK 24+ the build adds JVM flags that JDK 21 rejects, so
`-Djvm=<jdk21>/bin/java` does not work; rely on CI or build with JDK 21.

## Local failures that are not regressions

- `FailureFilterTest`, `AnnotationScanningTest`, `NoGaeApisTest`, `AsyncServletAppTest`:
  `expected to be true` on `appRoot.isDirectory()` means their test web app (e.g.
  `runtime/failinitfilterwebapp/target/failinitfilterwebapp-<version>`) was not built for the
  current version. Build those modules too, or run the full build.
- `GuestBookTest` runs a nested `mvnw clean install` and needs `sdk_assembly`.
- Cloud Tasks: there is no endpoint override, so with `APPENGINE_USE_CLOUDTASK_PUSH_QUEUE=true` the
  client calls the real `cloudtasks.googleapis.com` and gets an auth error (`UNAUTHENTICATED` in
  CI, `PERMISSION_DENIED` with local credentials). Tests must not depend on the task being
  delivered.

## Writing a test

Extend `JavaRuntimeViaHttpBase`, copy an app with `copyAppToDir(...)`, patch its
`WEB-INF/appengine-web.xml` if needed, pass environment variables with
`RuntimeContext.Config.builder(...).setEnvironmentEntries(...)`, and check the runtime's
stdout/stderr with `RuntimeContext.outputLinesContaining(Pattern)` or
`awaitStdoutLineMatching`/`awaitStderrLineMatching`. `CloudTasksSessionTest` is a short example.
