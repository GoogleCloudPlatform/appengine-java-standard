#!/bin/bash
# Copyright 2021 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# Fail on any error.
set -e
shopt -s globstar

# Get secrets from keystore and set and environment variables
setup_environment_secrets() {
  export GNUPGHOME=/tmp/gpg
  mkdir $GNUPGHOME
  mv ${KOKORO_KEYSTORE_DIR}/70247_maven-gpg-pubkeyring $GNUPGHOME/pubring.gpg
  mv ${KOKORO_KEYSTORE_DIR}/70247_maven-gpg-keyring $GNUPGHOME/secring.gpg
  # See https://linuxhint.com/solve-gpg-decryption-failed-no-secret-key-error/
  gpg -k
}

create_settings_xml_file() {
  echo "<settings>
   <profiles>
     <profile>
         <activation>
             <activeByDefault>true</activeByDefault>
         </activation>
         <properties>
             <gpg.passphrase>${GPG_PASSPHRASE}</gpg.passphrase>
         </properties>
         <repositories>
          <repository>
             <id>aoss-artifact-registry</id>
             <name>AOSS Repository</name>
             <url>artifactregistry://us-maven.pkg.dev/cloud-aoss-1p/cloud-aoss-1p-java</url>
           </repository>
        </repositories>
     </profile>
  </profiles>
  <servers>
    <server>
      <id>ossrh</id>
      <username>${SONATYPE_USERNAME}</username>
      <password>${SONATYPE_PASSWORD}</password>
    </server>
    <server>
      <id>sonatype-nexus-staging</id>
      <username>${SONATYPE_USERNAME}</username>
      <password>${SONATYPE_PASSWORD}</password>
    </server>
    <server>
      <id>sonatype-nexus-snapshots</id>
      <username>${SONATYPE_USERNAME}</username>
      <password>${SONATYPE_PASSWORD}</password>
    </server>
  </servers>
</settings>" > $1
}


setup_environment_secrets
## double commented lines are for attempting to use the git on borg repository.
## cd ${KOKORO_ARTIFACTS_DIR}/git
create_settings_xml_file "settings.xml"

git clone https://github.com/GoogleCloudPlatform/appengine-java-standard.git
cd appengine-java-standard
git checkout "${BRANCH_NAME}"

## src_dir="${KOKORO_ARTIFACTS_DIR}/git/appengine-java-standard"
## cd $src_dir

# Enable correct evaluation of git buildnumber value for git on borg.
## git config --global --add safe.directory /tmpfs/src/git/appengine-java-standard

# Get the current version from pom.xml
POM_VERSION=$(
 awk '
  /<dependenc/{exit}
  /<parent>/{parent++};
  /<version>/{
    if (parent == 1) {
      sub(/.*<version>/, "");
      sub(/<.*/, "");
      parent_version = $0;
    } else {
      sub(/.*<version>/, "");
      sub(/<.*/, "");
      version = $0;
      exit
    }
  }
  /<\/parent>/{parent--};
  END {
    print (version == "") ? parent_version : version
  }' pom.xml
)

# Remove trailing -SNAPSHOT
RELEASE_NUMBER=$(echo "$POM_VERSION" | sed 's/-SNAPSHOT//g')
echo "Using release number="$RELEASE_NUMBER
# Work in a $RELEASE_NUMBER branch, not main.
git checkout -b $RELEASE_NUMBER

git config user.email gae-java-bot@google.com
git config user.name gae-java-bot

sudo apt-get update
sudo apt-get install -y openjdk-21-jdk
sudo update-java-alternatives --set java-1.21.0-openjdk-amd64
export JAVA_HOME="$(update-java-alternatives -l | grep "1.21" | head -n 1 | tr -s " " | cut -d " " -f 3)"

# Make sure `JAVA_HOME` is set.
echo "JAVA_HOME = $JAVA_HOME"

# compile all packages
MVN_COMMON_OPTIONS="-B -q --settings=../settings.xml -Paoss -DskipTests -Darguments=-DskipTests -Dgpg.homedir=${GNUPGHOME} -Dgpg.passphrase=${GPG_PASSPHRASE}"
if [[ "${DRY_RUN}" == "true" ]]; then
  echo "DRY_RUN is true, only calling release:prepare."
  ./mvnw release:prepare ${MVN_COMMON_OPTIONS}
else
  echo "Cleaning up previous OSS Exit Gate staging attempt for version ${RELEASE_NUMBER} (if any)..."
  if [[ -n "${RELEASE_NUMBER}" ]]; then
    while IFS= read -r pkg; do
      pkg_name="${pkg##*/}"
      [[ -n "${pkg_name}" ]] || continue
      if gcloud artifacts versions describe "${RELEASE_NUMBER}" \
        --package="${pkg_name}" \
        --project="oss-exit-gate-prod" \
        --location="us" \
        --repository="appengine-java--mavencentral" >/dev/null 2>&1; then
        gcloud artifacts versions delete "${RELEASE_NUMBER}" \
          --package="${pkg_name}" \
          --project="oss-exit-gate-prod" \
          --location="us" \
          --repository="appengine-java--mavencentral" \
          --quiet
      fi
    done < <(gcloud artifacts packages list \
      --project="oss-exit-gate-prod" \
      --location="us" \
      --repository="appengine-java--mavencentral" \
      --format="value(name)" 2>/dev/null || true)
  fi

  echo "Calling release:prepare and release:perform via OSS Exit Gate staging..."
  # Force usage of the aoss profile to point to google artifacts repository to be MOSS compliant.
  MVN_EXIT_CODE=0
  ./mvnw release:prepare release:perform -B -q --settings=../settings.xml -Paoss,oss-exit-gate -DskipTests -Darguments="-Psonatype-release,oss-exit-gate -DskipTests" -Dgpg.homedir=${GNUPGHOME} -Dgpg.passphrase=${GPG_PASSPHRASE} -l /tmp/mvn_log.txt || MVN_EXIT_CODE=$?
  grep -v "Using Usertoken auth" /tmp/mvn_log.txt
  if [[ ${MVN_EXIT_CODE} -ne 0 ]]; then exit ${MVN_EXIT_CODE}; fi

  python3 - "${RELEASE_NUMBER}" <<'PY'
import collections, json, os, subprocess, sys
version = os.environ.get("RELEASE_NUMBER") or (sys.argv[1] if len(sys.argv) > 1 else "")
if not version:
  raise SystemExit("ERROR: missing release version for manifest generation")
repo = "appengine-java--mavencentral"
pkgs_out = subprocess.check_output([
    "gcloud", "artifacts", "packages", "list",
    "--project=oss-exit-gate-prod", "--location=us",
    f"--repository={repo}", "--format=value(name)",
], text=True).splitlines()
groups = collections.defaultdict(list)
for pkg_path in sorted(pkgs_out):
  pkg_id = pkg_path.strip().split("/")[-1]
  if ":" not in pkg_id:
    continue
  rc = subprocess.run([
      "gcloud", "artifacts", "versions", "describe", version,
      f"--package={pkg_id}", "--project=oss-exit-gate-prod",
      "--location=us", f"--repository={repo}",
  ], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode
  if rc == 0:
    ns, name = pkg_id.split(":", 1)
    groups[ns].append({"name": name, "version": version})
if not groups:
  raise SystemExit(f"ERROR: No staged Maven packages found in {repo} for version {version}")
manifest = {
    "publish_all": False,
    "publishing_groups": [
        {"namespace": ns, "packages": pkgs}
        for ns, pkgs in sorted(groups.items())
    ],
}
with open("/tmp/oss-exit-gate-manifest.json", "w", encoding="utf-8") as fh:
  json.dump(manifest, fh, indent=2)
  fh.write("\n")
PY
  gcloud storage cp /tmp/oss-exit-gate-manifest.json \
      "gs://oss-exit-gate-prod-projects-bucket/appengine-java/mavencentral/manifests/manifest-${RELEASE_NUMBER}-$(date +%s).json"

  git remote set-url origin https://gae-java-bot:${GAE_JAVA_BOT_GITHUB_TOKEN}@github.com/GoogleCloudPlatform/appengine-java-standard
  echo "Doing git tag and push."
  git tag -a v$RELEASE_NUMBER -m v$RELEASE_NUMBER
  git push --set-upstream origin $RELEASE_NUMBER
  # Push the tag.
  git push origin v$RELEASE_NUMBER
fi

echo "Done doing a release."
