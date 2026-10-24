---
name: internal-runtime-usage
description: Use when changing the behavior, dependencies or feature flags of public App Engine API classes (QueueImpl, QueueFactory, DatastoreServiceImpl, MemcacheServiceImpl, URLFetchService, UserService, ApiProxy, ...), or when adding an environment-variable switch to them such as APPENGINE_USE_CLOUDTASK_PUSH_QUEUE. Finds the places where the runtime itself calls these APIs from its private class loader and makes sure they are tested end to end in both modes.
---

# API classes used by the runtime itself

Applications use the App Engine APIs through `appengine-api-1.0-sdk.jar`. The **runtime** also
uses some of them, from its own class loader, with the copies bundled in
`runtime-impl-jetty12.jar` / `runtime-impl-jetty121.jar`. A change that works for applications can
therefore still break the runtime, and unit tests in `api/` do not cover it.

## 1. Find runtime callers

Search the code that runs in the runtime class loader:

```
grep -rln --include=*.java -e 'QueueFactory\|QueueImpl\|DatastoreServiceFactory\|MemcacheServiceFactory\|URLFetchServiceFactory\|UserServiceFactory\|ApiProxy' \
  shared_sdk shared_sdk_jetty12 shared_sdk_jetty121 runtime/impl runtime/runtime_impl_jetty12 runtime/runtime_impl_jetty121 runtime/lite runtime/util api/src/main/java/com/google/apphosting/utils/servlet
```

Narrow the pattern to the class you changed. Known runtime users include:

| Runtime code | API used | Enabled by |
|---|---|---|
| `DeferredDatastoreSessionStore` (shared_sdk_jetty12/121) | Task Queue (`QueueFactory`, `DeferredTask`) | `<sessions-enabled>` + `<async-session-persistence enabled="true"/>` |
| `DatastoreSessionStore`, `MemcacheSessionStore` | Datastore, Memcache | `<sessions-enabled>true</sessions-enabled>` |
| `DeferredTaskServlet` (bundled in the runtime jars) | runs `DeferredTask` payloads (Task Queue) | deferred tasks, including async session writes |
| `SessionCleanupServlet` (bundled in the runtime jars) | Datastore | expired session cleanup |
| `SweeperServlet` (bundled in the runtime jars) | `TaskProcessor` → Cloud Tasks | `APPENGINE_USE_CLOUDTASK_PUSH_QUEUE` |
| `AppEngineLoginService`, `AppEngineAuthentication`, `AppEngineUserIdentity` | Users API | `<security-constraint>` in `web.xml` |

Also check which API packages are bundled: the `<filter>` for `com.google.appengine:appengine-apis`
in `runtime/runtime_impl_jetty12/pom.xml` and `runtime/runtime_impl_jetty121/pom.xml`. Any class in
those packages, including new ones you add, ends up in the runtime jars.

## 2. Test the runtime path end to end

Unit tests are not enough: add or extend a test in `runtime/test` that starts the real runtime
(`JavaRuntimeViaHttpBase`) with an app that triggers the runtime caller. If the change is behind a
flag, run the test **with the flag on** (and keep the existing test for the flag off).
`CloudTasksSessionTest` is the template: it copies the allinone app, enables
`<async-session-persistence>` in its `appengine-web.xml`, sets
`APPENGINE_USE_CLOUDTASK_PUSH_QUEUE=true` through `setEnvironmentEntries`, and asserts that the
requests succeed and that `outputLinesContaining(...)` finds no `ClassNotFoundException` /
`NoClassDefFoundError`. See the `runtime-e2e-tests` skill to build and run it.

## 3. Check the bundled dependencies

If the change makes the API classes use a new library, follow the `runtime-jar-deps` skill.

## 4. Say what the internal build needs

The internal (google3) build uses BUILD files, not these poms. In the PR, list the libraries the
runtime jars now need so the matching BUILD deps can be added internally, otherwise the change
can pass here and fail internally.
