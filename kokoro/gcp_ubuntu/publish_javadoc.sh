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
 curl -fsSL --retry 10 -o /tmp/jar1.jar https://github.com/googleapis/java-docfx-doclet/releases/download/1.9.0/docfx-doclet-1.9.0-jar-with-dependencies.jar
 # Update Python 3 and Maven
 sudo apt-get update
 sudo apt-get install -y python3 python3-pip python3-venv maven
 # install docuploader package with upgrade to get latest correct versions.
 echo "Trying to install gcp-docuploader."
 python3 -m venv env
 source env/bin/activate
cat > /tmp/requirements.txt << EOF
pip==25.0.1 --hash=sha256:c46efd13b6aa8279f33f2864459c8ce587ea6a1a59ee20de055868d8f7688f7f
gcp-docuploader==0.7.2 --hash=sha256:b1c37b55c360c7f1f3a60f8b1d3d6110f7b0f0a42f01f4f8b1c411a7a0c8b2c5
protobuf==4.25.3 --hash=sha256:29b0f0119c6e6169c004b068a070c7ab7707c9a9307d8d481dae181febc2e6f3
EOF
 python3 -m pip install --require-hashes -r /tmp/requirements.txt
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

sudo apt-get update
sudo apt-get install -y openjdk-21-jdk
sudo update-java-alternatives --set java-1.21.0-openjdk-amd64
export JAVA_HOME="$(update-java-alternatives -l | grep "1.21" | head -n 1 | tr -s " " | cut -d " " -f 3)"

# Make sure `JAVA_HOME` is set.
echo "JAVA_HOME = $JAVA_HOME"

# Do a build of all dependent modules first.
./mvnw install -B  -DskipTests=true

# Then do a build in api/ for cloud RAD generation.
cd api
../mvnw javadoc:aggregate -B  -P docFX -DdocletPath=/tmp/jar1.jar

# include CHANGELOG
#cp CHANGELOG.md target/docfx-yml/history.md

pushd target/docfx-yml

# create metadata for Java11/17/25
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

