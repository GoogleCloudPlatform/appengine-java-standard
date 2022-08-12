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

# The artifacts under `${KOKORO_ARTIFACTS_DIR}/maven-artifacts` will be uploaded.
mkdir ${KOKORO_ARTIFACTS_DIR}/maven-artifacts
# Remove jars we do not need in google3.
ls **/*.jar
rm **/target/*sources.jar || true

# LINT.IfChange
cp api_legacy/target/appengine-api-legacy*.jar ${KOKORO_ARTIFACTS_DIR}/maven-artifacts/appengine-api-legacy.jar
cp appengine-api-1.0-sdk/target/appengine-api-1.0-sdk*.jar ${KOKORO_ARTIFACTS_DIR}/maven-artifacts/appengine-api-1.0-sdk.jar
cp appengine-api-stubs/target/appengine-api-stubs*.jar ${KOKORO_ARTIFACTS_DIR}/maven-artifacts/appengine-api-stubs.jar
cp appengine_testing/target/appengine-testing*.jar ${KOKORO_ARTIFACTS_DIR}/maven-artifacts/appengine-testing.jar
cp remoteapi/target/appengine-remote-api*.jar ${KOKORO_ARTIFACTS_DIR}/maven-artifacts/appengine-remote-api.jar
cp appengine_jsr107/target/appengine-jsr107*.jar ${KOKORO_ARTIFACTS_DIR}/maven-artifacts/appengine-jsr107.jar
cp runtime_shared/target/runtime-shared*.jar ${KOKORO_ARTIFACTS_DIR}/maven-artifacts/runtime-shared.jar
cp lib/tools_api/target/appengine-tools-sdk*.jar ${KOKORO_ARTIFACTS_DIR}/maven-artifacts/appengine-tools-api.jar
cp lib/xml_validator/target/libxmlvalidator*.jar ${KOKORO_ARTIFACTS_DIR}/maven-artifacts/libxmlvalidator.jar
cp runtime/impl/target/runtime-impl*.jar ${KOKORO_ARTIFACTS_DIR}/maven-artifacts/runtime-impl.jar
cp runtime/local/target/appengine-local-runtime*.jar ${KOKORO_ARTIFACTS_DIR}/maven-artifacts/appengine-local-runtime.jar
cp runtime/main/target/runtime-main*.jar ${KOKORO_ARTIFACTS_DIR}/maven-artifacts/runtime-main.jar
cp local_runtime_shared/target/appengine-local-runtime-shared*.jar ${KOKORO_ARTIFACTS_DIR}/maven-artifacts/appengine-local-runtime-shared.jar
cp quickstartgenerator/target/quickstartgenerator*.jar ${KOKORO_ARTIFACTS_DIR}/maven-artifacts/quickstartgenerator.jar

cp -rf sdk_assembly/target/appengine-java-sdk ${KOKORO_ARTIFACTS_DIR}/maven-artifacts/
# Make binaries executable.
chmod a+x ${KOKORO_ARTIFACTS_DIR}/maven-artifacts/appengine-java-sdk/bin/*
# Also create the area for the Cloud SDK deliverable in google_appengine_java_delta.
mkdir -p ${KOKORO_ARTIFACTS_DIR}/maven-artifacts/google_appengine_java_delta/google/appengine/tools/java/
cp -pr ${KOKORO_ARTIFACTS_DIR}/maven-artifacts/appengine-java-sdk/* ${KOKORO_ARTIFACTS_DIR}/maven-artifacts/google_appengine_java_delta/google/appengine/tools/java
# LINT.ThenChange(//depot/google3/third_party/java_src/appengine_standard/check_build.sh)
