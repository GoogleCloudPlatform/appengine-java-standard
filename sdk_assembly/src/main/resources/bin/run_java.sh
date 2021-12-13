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

# Shared script for appcfg.sh, dev_appserver.sh, and endpoints.sh.
# Checks that the java command reports at least the minimum required version
# before running it. The arguments to this script are, first, the name of
# the script we are doing this for (for example appcfg.sh), then all of the
# arguments to be passed to the java binary.

readonly SCRIPT_NAME="$1"
shift

readonly VERSION=$(java -version 2>&1 \
    | sed -n '1s/.*version.*1\.\([0-9]\).*/\1/p')
case "${VERSION}" in
[1-6])
  cat >&2 <<EOF
${SCRIPT_NAME} requires at least Java 7 (also known as 1.7).

The java executable at $(type -p java) reports:
$(java -version 2>&1)

You can download the latest JDK from:
  https://www.oracle.com/java/technologies/javase-downloads.html
EOF
  exit 1;;

[7-9])
  ;;

*)
  echo "Warning: unable to determine Java version" >&2;;
esac

exec java "$@"
