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

# Launches the AppCfg utility, which allows Google App Engine
# developers to deploy their application to the cloud.

[[ -z "${DEBUG}" ]] || set -x  # trace if $DEBUG env. var. is non-zero

# Construct the absolute name of the SDK bin directory.
# Use -P so pwd will see the real name, independent of symbolic links.
readonly SDK_BIN="$(cd -P "$(dirname "$0")" && pwd)"
readonly SDK_ROOT="$SDK_BIN/.."
readonly SDK_LIB="$(dirname "${SDK_BIN}")/lib"
readonly JAR_FILE="${SDK_LIB}/appengine-tools-api.jar"

if [[ ! -e "${JAR_FILE}" ]]; then
    echo "${JAR_FILE} not found" >&2
    exit 1
fi

readonly SCRIPT_NAME=$(basename "$0")
JAVA="${JAVA:-java}"
"$JAVA" -Dappengine.sdk.root="${SDK_ROOT}" -Xmx1100m -cp "$JAR_FILE" com.google.appengine.tools.admin.AppCfg "$@"
