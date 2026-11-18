---
name: runtime-jar-deps
description: Use when changing App Engine API classes that are bundled into the runtime jars (com.google.appengine.api.* such as taskqueue, datastore, memcache, urlfetch, users, appidentity, oauth; com.google.apphosting.utils.servlet.*), when adding a library dependency to those API classes, or when editing the shade configuration of runtime/runtime_impl_jetty12 or runtime/runtime_impl_jetty121. Verifies that every class the runtime can reach is actually bundled, so the runtime does not fail with NoClassDefFoundError.
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

# Runtime jar dependency closure

The runtime jars `runtime-impl-jetty12.jar` and `runtime-impl-jetty121.jar` are built by the
maven-shade-plugin in `runtime/runtime_impl_jetty12/pom.xml` and
`runtime/runtime_impl_jetty121/pom.xml`. They copy selected App Engine API classes out of
`appengine-apis` (see the `<filter>` for `com.google.appengine:appengine-apis`, e.g.
`com/google/appengine/api/taskqueue/*`) and bundle third-party libraries from an explicit
allowlist (the `<artifactSet><includes>`).

The runtime uses those API classes **from its own private class loader**, not the application's
(for example `DeferredDatastoreSessionStore` adds Task Queue tasks for async session
persistence). Nothing relocates or bundles a library automatically: if an API class starts using a
new library and the library is not in the `artifactSet`, the runtime fails only at run time:

```
NoClassDefFoundError: com/google/auth/Credentials
  at com.google.appengine.api.taskqueue.QueueImpl.isCloudTaskBackendEnabled(...)
  at com.google.apphosting.runtime.jetty.DeferredDatastoreSessionStore.doStore(...)
```

`appengine-api-1.0-sdk.jar` (what applications use) is a different shade with its own
`artifactSet` and relocations; fixing one does not fix the other.

## Check

1. Build the runtime deployment (needs a full JDK with `javac`; see the `runtime-e2e-tests`
   skill for build details):

   ```
   ./mvnw install -DskipTests -DskipITs -pl runtime/deployment,runtime/test -am
   ```

2. Run the closure test:

   ```
   ./mvnw test -pl runtime/test -Dtest=RuntimeJarDependencyClosureTest -Dsurefire.failIfNoSpecifiedTests=false
   ```

   `runtime/test/src/test/java/com/google/apphosting/runtime/RuntimeJarDependencyClosureTest.java`
   runs `jdeps` on each runtime-impl jar with the other deployment jars on the class path, follows
   every reference from `com.google.appengine.*` / `com.google.apphosting.*` classes through the
   bundled libraries, and lists each missing class with the chain that reaches it, e.g.
   `com.google.auth.Credentials  <-  com.google.appengine.api.taskqueue.CloudTasksClientWrapper`.

## Fix a failure

- **The code path is used by the runtime** (default assumption): add the artifact that contains
  the missing class to the `<artifactSet><includes>` of **both** runtime_impl poms. Find the
  artifact with `./mvnw dependency:tree -pl runtime/runtime_impl_jetty121 -Dincludes=<groupId>`
  or by looking at the same library in `appengine-api-1.0-sdk/pom.xml`. Repeat until the test
  passes; libraries often need further libraries (e.g. `CloudTasksClient` needs `gax-httpjson`
  even for gRPC).
- Libraries that register services in `META-INF/services` (gRPC providers, etc.) rely on the
  `ServicesResourceTransformer` in the shade configuration; keep it.
- **The code path is never used by the runtime**: add the package to `KNOWN_MISSING_PACKAGES` in
  the test, with the reason. Do not use this to hide a library the runtime needs.
- Check the size impact with `agent_skills/pom-dependency-change/scripts/jar_size_diff.sh` and
  report it in the PR.
- The internal (google3) build does not use these poms. Say in the PR that the same libraries must
  be added to the internal BUILD deps of the runtime jars.
- Add or extend an end-to-end test for the runtime code path (see the `internal-runtime-usage`
  skill).
