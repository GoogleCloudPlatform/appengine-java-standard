#
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

"""Builds Java proto libraries using the open-source proto compiler."""

def _proto_library_name(name):
    ret = name.replace("open_java_proto", "open_proto")
    if ret == name:
        fail("Name must contain open_java_proto: " + name)
    return ret

def open_java_proto_library(
        name,
        srcs,
        deps = [],
        compatible_with = []):
    """Builds a Java proto library using the open-source proto compiler.

    Uses the open-source protoc to generate Java source files from input protos,
    rewrites that source code so it can compile in google3, then compiles it.

    Args:
      name: Name of the output java_library.
      srcs: Relative paths of input .proto files.
      deps: Other open_java_proto_library() rules that we import.
      compatible_with: Clause for the rules used by this macro.
    """

    proto_library_name = _proto_library_name(name)

    native.proto_library(
        name = proto_library_name,
        srcs = srcs,
        deps = [_proto_library_name(dep) for dep in deps],
        compatible_with = compatible_with,
        strip_import_prefix = "/" + native.package_name(),
    )

    native.java_proto_library(
        name = name,
        deps = [":" + proto_library_name],
        compatible_with = compatible_with,
    )
