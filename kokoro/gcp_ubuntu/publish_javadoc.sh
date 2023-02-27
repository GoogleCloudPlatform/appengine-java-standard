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

setup_docuploader() {
 curl -fsSL --retry 10 -o /tmp/jar1.jar https://github.com/googleapis/java-docfx-doclet/releases/download/1.8.0/docfx-doclet-1.8.0-jar-with-dependencies.jar
 # Update Python 3 and Maven
 sudo apt-get update
 sudo apt-get install -y python3 python3-pip maven
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
fi

git clone https://github.com/GoogleCloudPlatform/appengine-java-standard.git
cd appengine-java-standard

# Setup the doc uploader environment.
setup_docuploader

export NAME=appengine-java11-bundled-services
export VERSION=11

# Use Java 11 for javadoc plugin usage.
sudo update-java-alternatives --set java-1.11.0-openjdk-amd64
export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64
echo "JAVA_HOME = $JAVA_HOME"
 # Setting up maven wrapper for the project. https://maven.apache.org/wrapper/
 mvn wrapper:wrapper
# Do a build of all dependent modules first.
./mvnw install -B -q -DskipTests=true

# Then do a build in api/ for cloud RAD generation.
cd api
../mvnw javadoc:aggregate -B -q -P docFX -DdocletPath=/tmp/jar1.jar

# include CHANGELOG
#cp CHANGELOG.md target/docfx-yml/history.md

pushd target/docfx-yml

# create metadata for Java11/17
python3 -m docuploader create-metadata \
 --name appengine-java-gen2-bundled-services \
 --version 2.0.0 \
 --stem /appengine/docs/standard/java-gen2/reference/services/bundled \
 --language java


 echo "Done creating metadata."

# upload yml to production bucket
python3 -m docuploader upload . \
 --credentials ${CREDENTIALS} \
 --staging-bucket ${STAGING_BUCKET_V2} \
 --destination-prefix docfx

echo "Done publishing Javadocs."

