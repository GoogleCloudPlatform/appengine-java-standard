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

# Make sure `JAVA_HOME` is set.
echo "JAVA_HOME = $JAVA_HOME"
./mvnw -v

./mvnw -e clean install

# The artifacts under `${KOKORO_ARTIFACTS_DIR}/maven-artifacts` will be uploaded as a zip file named maven_jars.binary
TMP_STAGING_LOCATION=${KOKORO_ARTIFACTS_DIR}/tmp
PUBLISHED_LOCATION=${KOKORO_ARTIFACTS_DIR}/maven-artifacts
mkdir ${TMP_STAGING_LOCATION}
mkdir ${PUBLISHED_LOCATION}
# Remove jars we do not need in google3.
ls **/*.jar
rm **/target/*sources.jar || true
rm **/target/*tests.jar || true

# LINT.IfChange
cp api_legacy/target/appengine-api-legacy*.jar ${TMP_STAGING_LOCATION}/appengine-api-legacy.jar
cp appengine-api-1.0-sdk/target/appengine-api-1.0-sdk*.jar ${TMP_STAGING_LOCATION}/appengine-api-1.0-sdk.jar
cp appengine-api-stubs/target/appengine-api-stubs*.jar ${TMP_STAGING_LOCATION}/appengine-api-stubs.jar
cp appengine_testing/target/appengine-testing*.jar ${TMP_STAGING_LOCATION}/appengine-testing.jar
cp remoteapi/target/appengine-remote-api*.jar ${TMP_STAGING_LOCATION}/appengine-remote-api.jar
cp appengine_jsr107/target/appengine-jsr107*.jar ${TMP_STAGING_LOCATION}/appengine-jsr107.jar
cp runtime_shared/target/runtime-shared*.jar ${TMP_STAGING_LOCATION}/runtime-shared.jar
cp lib/tools_api/target/appengine-tools-sdk*.jar ${TMP_STAGING_LOCATION}/appengine-tools-api.jar
cp lib/xml_validator/target/libxmlvalidator*.jar ${TMP_STAGING_LOCATION}/libxmlvalidator.jar
cp runtime/impl/target/runtime-impl*.jar ${TMP_STAGING_LOCATION}/runtime-impl.jar
cp runtime/local/target/appengine-local-runtime*.jar ${TMP_STAGING_LOCATION}/appengine-local-runtime.jar
cp runtime/main/target/runtime-main*.jar ${TMP_STAGING_LOCATION}/runtime-main.jar
cp local_runtime_shared/target/appengine-local-runtime-shared*.jar ${TMP_STAGING_LOCATION}/appengine-local-runtime-shared.jar
cp quickstartgenerator/target/quickstartgenerator*.jar ${TMP_STAGING_LOCATION}/quickstartgenerator.jar

cp -rf sdk_assembly/target/appengine-java-sdk ${TMP_STAGING_LOCATION}/
# Make binaries executable.
chmod a+x ${TMP_STAGING_LOCATION}/appengine-java-sdk/bin/*
# LINT.ThenChange(//depot/google3/third_party/java_src/appengine_standard/check_build.sh)
cp sdk_assembly/target/google_appengine_java_delta*.zip ${TMP_STAGING_LOCATION}/google_appengine_java_delta_from_maven.zip
cd ${TMP_STAGING_LOCATION}
zip -r ${PUBLISHED_LOCATION}/maven_jars.binary .
# cleanup staging area
cd ..
rm -rf ${TMP_STAGING_LOCATION}
