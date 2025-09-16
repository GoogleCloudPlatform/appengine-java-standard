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

src_dir="${KOKORO_ARTIFACTS_DIR}/git/appengine-java-standard"
cd $src_dir

sudo apt-get update
sudo apt-get install -y openjdk-21-jdk
sudo update-java-alternatives --set java-1.21.0-openjdk-amd64
export JAVA_HOME="$(update-java-alternatives -l | grep "1.21" | head -n 1 | tr -s " " | cut -d " " -f 3)"

# Make sure `JAVA_HOME` is set.
echo "JAVA_HOME = $JAVA_HOME"
./mvnw -v

# Enable correct evaluation of git buildnumber value for git on borg.
git config --global --add safe.directory /tmpfs/src/git/appengine-java-standard

# Force usage of the aoss profile to point to google artifacts repository to be MOSS compliant.
./mvnw -e -X clean install  spdx:createSPDX -Paoss -Dorg.slf4j.simpleLogger.showDateTime=true -Dorg.slf4j.simpleLogger.dateTimeFormat=HH:mm:ss,SSS

# The artifacts under `${KOKORO_ARTIFACTS_DIR}/maven-artifacts` will be uploaded as a zip file named maven_jars.binary
TMP_STAGING_LOCATION=${KOKORO_ARTIFACTS_DIR}/tmp
PUBLISHED_LOCATION=${KOKORO_ARTIFACTS_DIR}/maven-artifacts
mkdir ${TMP_STAGING_LOCATION}
mkdir ${PUBLISHED_LOCATION}
# Remove jars we do not need in google3.
ls **/*.jar
rm **/target/*sources.jar || true
rm **/target/*tests.jar || true
rm **/target/*javadoc.jar || true

# LINT.IfChange
cp appengine-api-1.0-sdk/target/appengine-api-1.0-sdk*.jar ${TMP_STAGING_LOCATION}/appengine-api-1.0-sdk.jar
cp appengine_jsr107/target/appengine-jsr107*.jar ${TMP_STAGING_LOCATION}/appengine-jsr107.jar

cp -rf sdk_assembly/target/appengine-java-sdk ${TMP_STAGING_LOCATION}/
# Make binaries executable.
chmod a+x ${TMP_STAGING_LOCATION}/appengine-java-sdk/bin/*
# LINT.ThenChange(//depot/google3/third_party/java_src/appengine_standard/check_build.sh)
cp sdk_assembly/target/google_appengine_java_delta*.zip ${TMP_STAGING_LOCATION}/google_appengine_java_delta_from_maven.zip

# Add SBOM files:
cp target/site/com.google.appengine_parent-*.json ${TMP_STAGING_LOCATION}/com.google.appengine_parent.spdx.json

cd ${TMP_STAGING_LOCATION}
zip -r ${PUBLISHED_LOCATION}/maven_jars.binary .
# cleanup staging area
cd ..
rm -rf ${TMP_STAGING_LOCATION}
