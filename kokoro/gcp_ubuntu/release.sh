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

cd ${KOKORO_ARTIFACTS_DIR}/git
create_settings_xml_file "settings.xml"

src_dir="${KOKORO_ARTIFACTS_DIR}/git/appengine-java-standard"
cd $src_dir

# Enable correct evaluation of git buildnumber value for git on borg.
git config --global --add safe.directory ${src_dir}

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
echo "Calling release:prepare and release:perform."
# Force usage of the aoss profile to point to google artifacts repository to be MOSS compliant.
./mvnw release:prepare release:perform -B -q --settings=../settings.xml -Paoss -DskipTests -Darguments=-DskipTests -Dgpg.homedir=${GNUPGHOME} -Dgpg.passphrase=${GPG_PASSPHRASE}

git remote set-url origin https://gae-java-bot:${GAE_JAVA_BOT_GITHUB_TOKEN}@github.com/GoogleCloudPlatform/appengine-java-standard
echo "Doing git tag and push."
git tag -a v$RELEASE_NUMBER -m v$RELEASE_NUMBER
git push --set-upstream origin $RELEASE_NUMBER
# Push the tag.
git push origin v$RELEASE_NUMBER

echo "Done doing a release."
