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

# Agent Skills

This directory holds skills for AI coding agents (Claude Code, Gemini CLI, and
others) working on this repository. Each subdirectory is one skill, with a
`SKILL.md` file that describes when to use it and the steps to follow.

| Skill | Use it when |
| --- | --- |
| [internal-runtime-usage](internal-runtime-usage/SKILL.md) | Changing public API classes that the runtime itself calls, or their feature flags. |
| [pom-dependency-change](pom-dependency-change/SKILL.md) | Changing a Maven dependency, shade include, relocation or filter, or bumping a version. |
| [runtime-e2e-tests](runtime-e2e-tests/SKILL.md) | Building the runtime deployment, or running or writing tests in `runtime/test`. |
| [runtime-jar-deps](runtime-jar-deps/SKILL.md) | Changing API classes bundled into the runtime jars, or their shading. |

## Repository layout and Copybara synchronization

Google's internal source tree (`google3`) does not use leading dot directories for skills.
However, coding agents on GitHub (Claude Code, Antigravity, Gemini CLI) expect skills under
their respective dot directories (`.claude/skills/`, `.agent/skills/`, `.gemini/skills/`).

To bridge this:
- **Phase 1 (Initial import)**: Skills are temporarily placed in this regular `agent_skills/`
  directory without a leading dot so that Copybara can import the files into `google3` without
  failing the import check.
- **Phase 2 (Bi-directional Copybara rule)**: A Copybara transformation rule in `google3` will
  manage synchronization:
  - **Export (`google3` -> GitHub)**: Rewrites the non-dot directories in `google3` to `.claude/`,
    `.agent/`, `.gemini/` on GitHub so the skills appear natively in the right place.
  - **Import (GitHub -> `google3`)**: Reverses the mapping, moving files from `.claude/`, `.agent/`,
    `.gemini/` back to the corresponding non-dot directories in `google3`.

Because these dot directories will be version-controlled, tracked files in Git on GitHub,
they **must not** be listed in `.gitignore`.

### Local usage before the Copybara rule is deployed

Until the bidirectional Copybara rule is in place in `google3`, you can point your local tools
at `agent_skills/` (for example, with a local symlink, without committing it):

- **Claude Code**:
  ```shell
  mkdir -p .claude && ln -s ../agent_skills .claude/skills
  ```
- **Gemini CLI / Antigravity**:
  ```shell
  mkdir -p .agent && ln -s ../agent_skills .agent/skills
  ```
