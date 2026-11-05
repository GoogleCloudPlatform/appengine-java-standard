#!/usr/bin/env bash
# Copyright 2026 Google LLC
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

# Compares two builds of the same jar: total size, entry and class counts, and the compressed
# bytes of added and removed entries grouped by package (first three path segments).
#
# Usage: jar_size_diff.sh OLD.jar NEW.jar
set -euo pipefail
export LC_ALL=C

if [[ $# -ne 2 ]]; then
  echo "Usage: $0 OLD.jar NEW.jar" >&2
  exit 1
fi
old=$1
new=$2
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

# Writes "path<TAB>compressed-size" for each file entry of a jar, sorted by path.
entries() {
  zipinfo -l "$1" | awk 'NF >= 10 && $1 ~ /^-/ { print $NF "\t" $6 }' | sort
}
entries "$old" > "$tmp/old"
entries "$new" > "$tmp/new"

size_old=$(stat -c%s "$old")
size_new=$(stat -c%s "$new")
classes_old=$(grep -c '\.class	' "$tmp/old" || true)
classes_new=$(grep -c '\.class	' "$tmp/new" || true)
printf '%-10s %12s %12s %12s\n' "" "old" "new" "delta"
printf '%-10s %12d %12d %+12d\n' "bytes" "$size_old" "$size_new" $((size_new - size_old))
printf '%-10s %12d %12d %+12d\n' "entries" "$(wc -l < "$tmp/old")" "$(wc -l < "$tmp/new")" \
  $(($(wc -l < "$tmp/new") - $(wc -l < "$tmp/old")))
printf '%-10s %12d %12d %+12d\n' "classes" "$classes_old" "$classes_new" \
  $((classes_new - classes_old))

# Prints compressed bytes and entry counts per package for entries only in the first file.
by_package() {
  join -t "$(printf '\t')" -v 1 "$1" <(cut -f1 "$2") |
    awk -F'\t' '{ n = split($1, p, "/"); k = p[1]; for (i = 2; i <= 3 && i < n; i++) k = k "/" p[i];
                  bytes[k] += $2; count[k]++ }
                END { for (k in bytes) printf "%10d B %6d entries  %s\n", bytes[k], count[k], k }' |
    sort -rn
}
echo
echo "Added (compressed bytes by package):"
by_package "$tmp/new" "$tmp/old"
echo
echo "Removed (compressed bytes by package):"
by_package "$tmp/old" "$tmp/new"
