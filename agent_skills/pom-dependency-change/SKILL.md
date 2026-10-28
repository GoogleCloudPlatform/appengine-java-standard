---
name: pom-dependency-change
description: Use before removing or adding a Maven dependency, an artifactSet include, a relocation or a filter in any pom.xml of this repo, or when bumping a library version (including reviewing Dependabot PRs). Checks why the entry exists, what depends on it transitively, and how the shaded jars (appengine-api-1.0-sdk, remoteapi, runtime_impl) change, including their size.
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

# Changing Maven dependencies safely

Many pom entries look unused but exist for a shaded jar or a runtime-only code path. For example
`io.grpc:grpc-api` in `appengine-api-1.0-sdk/pom.xml` was added for issue #381 (shaded OpenCensus
in remote_api needs `io.grpc.Context`); removing it later only kept working because another
library happened to pull it in.

## 1. Find why the entry exists

```
git log --format='%h %ad %an %s' --date=short -S'<artifactId>ARTIFACT</artifactId>' -- '*pom.xml'
git log --format='%h %ad %s' --date=short -G'ARTIFACT' -- '*pom.xml'
```

Read the commit that added it (`git show <sha>`), and any XML comment next to the entry. Search the
issue tracker for referenced issue numbers.

## 2. Compare the dependency trees

Run before and after the change, for every module whose pom you touch and for the shaded modules
that depend on it:

```
./mvnw -q dependency:tree -pl MODULE -Dincludes=GROUP_ID -DoutputFile=/tmp/tree-before.txt
```

The tree omits duplicates, so a library that disappears from one branch may still be resolved
elsewhere; check the final resolved version, not only the node you edited.

## 3. Check the shaded jars

| Jar | Pom | What to check |
|---|---|---|
| `appengine-api-1.0-sdk.jar` (used by apps) | `appengine-api-1.0-sdk/pom.xml` | `<artifactSet>` includes and `com.google.appengine.repackaged.*` relocations |
| `appengine-remote-api.jar` | `remoteapi/pom.xml` | `./mvnw verify -pl remoteapi` (runs `ShadingIT`) |
| `runtime-impl-jetty12.jar`, `runtime-impl-jetty121.jar` | `runtime/runtime_impl_jetty12*/pom.xml` | the `runtime-jar-deps` skill (`RuntimeJarDependencyClosureTest`) |

Shade only bundles artifacts listed in an `artifactSet`; a library that is resolved but not listed
is silently left out of the jar.

## 4. Report the size impact

Build the jar before and after (use a `git worktree` of `origin/main` for "before", with
`./mvnw package` so `~/.m2` is not overwritten), then:

```
agent_skills/pom-dependency-change/scripts/jar_size_diff.sh OLD.jar NEW.jar
```

Put the totals and the largest packages in the PR description or a PR comment.

## 5. Version pins with constraints

Some versions are deliberately held back and guarded, for example `okio.version` must stay 1.x
(okio 2+ needs kotlin-stdlib, which the runtime jars do not bundle): it is enforced by a
`requireProperty` rule in the runtime_impl poms and ignored for `>= 2.0.0` in
`.github/dependabot.yml`. When you add such a pin, add the same guard and the Dependabot ignore,
and do not "fix" a failing guard by loosening it.

## 6. Internal build

The internal (google3) build uses BUILD files, not these poms. When a shaded jar gains or loses a
library, say so in the PR so the internal BUILD deps can be updated.
