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
  gpg -h
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

setup_docuploader() {
 curl -fsSL --retry 10 -o /tmp/jar1.jar https://github.com/googleapis/java-docfx-doclet/releases/download/1.5.0/java-docfx-doclet-1.5.0-jar-with-dependencies.jar
 curl -fsSL --retry 10 -o /tmp/jar2.jar https://github.com/googleapis/java-docfx-doclet/releases/download/1.5.0/java-docfx-doclet-1.5.0.jar
 # By default Ubuntu 16.04 uses Python 3.5 which is too old.
 pyenv global 3.6.1
 # install docuploader package with upgrade to get latest correct versions.
 echo "Trying to install gcp-docuploader."
 python3 -m pip install --upgrade pip --user
 python3 -m pip install gcp-docuploader --user
 python3 -m pip install --upgrade protobuf --user
}

if [[ -z "${CREDENTIALS}" ]]; then
  CREDENTIALS=${KOKORO_KEYSTORE_DIR}/73713_docuploader_service_account
fi

if [[ -z "${STAGING_BUCKET_V2}" ]]; then
  echo "Setting STAGING_BUCKET_V2 environment variable to default value."
  STAGING_BUCKET_V2=docs-staging-v2
  # exit 1
fi

setup_environment_secrets
create_settings_xml_file "settings.xml"

git clone https://github.com/GoogleCloudPlatform/appengine-java-standard.git
cd appengine-java-standard
# Work in a release branch, not main.
git checkout -b release_branch

# Make sure `JAVA_HOME` is set.
echo "JAVA_HOME = $JAVA_HOME"
# compile all packages
echo "Calling release:prepare"
./mvnw release:prepare -B -q --settings=../settings.xml -DskipTests=true -Dgpg.homedir=${GNUPGHOME} -Dgpg.passphrase=${GPG_PASSPHRASE}
echo "Calling release:perform"
./mvnw release:perform -B -q --settings=../settings.xml -DskipTests=true -Dgpg.homedir=${GNUPGHOME} -Dgpg.passphrase=${GPG_PASSPHRASE}

# Not ready, need to get the credentials first from keystorep
# git config user.email gae-java-bot@google.com
# git config user.name gae-java-bot
# git push origin--repo https://gae-java-bot:${PASS}@github.com/GoogleCloudPlatform/appengine-java-standard
# git push origin v${RELEASE_VERSION}

# Now create the API docs and upload it for processing.
setup_docuploader

export NAME=appengine-java11-bundled-services
export VERSION=11

# Use Java 11 for javadoc plugin usage.
sudo update-java-alternatives --set java-1.11.0-openjdk-amd64
export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64
echo "JAVA_HOME = $JAVA_HOME"

# cloud RAD generation
cd api
../mvnw javadoc:aggregate -B -q -P docFX -DdocletPath=/tmp/jar1.jar:/tmp/jar2.jar

# include CHANGELOG
#cp CHANGELOG.md target/docfx-yml/history.md

pushd target/docfx-yml

# create metadata
python3 -m docuploader create-metadata \
 --name ${NAME} \
 --version ${VERSION} \
 --language java
echo "Done creating metadata."

# upload yml to production bucket
python3 -m docuploader upload . \
 --credentials ${CREDENTIALS} \
 --staging-bucket ${STAGING_BUCKET_V2} \
 --destination-prefix docfx

echo "Done doing a release."

