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

# Launches the Development AppServer.  This utility allows developers
# to test a Google App Engine application on their local workstation.

(>&2 echo \
'The dev_appserver shipped with the standalone Java SDK is deprecated. Please
use the java_dev_appserver.sh shipped with the Cloud SDK instead.
https://cloud.google.com/appengine/docs/standard/java/download')

[[ -z "${DEBUG}" ]] || set -x  # trace if $DEBUG env. var. is non-zero

# Construct the absolute name of the SDK bin directory.
# Use -P so pwd will see the real name, independent of symbolic links.
readonly SDK_BIN="$(cd -P "$(dirname "$0")" && pwd)"
readonly SDK_ROOT="$(dirname "${SDK_BIN}")"
readonly SDK_LIB="${SDK_ROOT}/lib"
readonly JAR_FILE="${SDK_LIB}/appengine-tools-api.jar"

if [[ ! -e "${JAR_FILE}" ]]; then
    echo "${JAR_FILE} not found" >&2
    exit 1
fi

readonly SCRIPT_NAME=$(basename "$0")
readonly RUN_JAVA=$(dirname "$0")/run_java.sh
exec "${RUN_JAVA}" "${SCRIPT_NAME}" \
    -ea -cp "${JAR_FILE}" \
    com.google.appengine.tools.KickStart \
    com.google.appengine.tools.development.DevAppServerMain \
    --sdk_root="${SDK_ROOT}" \
    "$@"
