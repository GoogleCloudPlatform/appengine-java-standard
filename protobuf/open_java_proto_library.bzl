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

load("//tools/jdk:not_widely_loaded_public_constants.bzl", "JAVA8_JAVACOPTS")

def open_java_proto_library(
        name,
        srcs,
        deps = [],
        imports = [],
        compatible_with = []):
    """Builds a Java proto library using the open-source proto compiler.

    Uses the open-source protoc to generate Java source files from input protos,
    rewrites that source code so it can compile in google3, then compiles it.

    Args:
      name: Name of the output java_library.
      srcs: Relative paths of input .proto files.
      deps: Java libraries that this one depends on, typically defined by other
          instances of open_java_proto_library.
      imports: Other proto files that this one imports.
      compatible_with: Clause for the rules used by this macro.
    """

    # First make a jar containing the generated Java source code for the protos.
    # This will be used in the next step, and is also of interest to check what
    # the generated code looks like.
    gensrc = name + "-gensrc"
    native.genrule(
        name = gensrc,
        srcs = srcs + imports,
        outs = [gensrc + ".srcjar"],
        cmd = """
        # The root output directory is blaze-whatever/.../java
        # but our RULEDIR adds the relative path of the directory containing
        # the open_java_proto_library invocation.
        out_dir=$$(sed 's,/java/.*,/java,' <<<$(RULEDIR))
        target="$$(pwd)"/$@
        jar="$$(pwd)"/$(location //third_party/java/jdk/jar)
        rewrite_script="$$(pwd)"/$(location <internal12>)
        srcs=({srcs})
        $(location //third_party/protobuf_legacy_opensource:protoc) \
            -I third_party/java_src/appengine_standard/protobuf -I third_party/java_src/appengine_standard/protobuf/api \
            --java_out="$${{out_dir}}" "$${{srcs[@]}}"
        cd "$${{out_dir}}"
        javasrc=$$(find * -name \\*.java)
        sed -i -f "$${{rewrite_script}}" $${{javasrc}}
        "$${{jar}}" cf "$${{target}}" $${{javasrc}}
        """.format(srcs = " ".join(srcs)),
        tools = [
            "<internal12>",
            "//third_party/java/jdk/jar",
            "//third_party/protobuf_legacy_opensource:protoc",
        ],
        compatible_with = compatible_with,
    )

    native.java_library(
        name = name,
        srcs = [gensrc],
        javacopts = [
            # ErrorProne doesn't like that we implement com.google.protobuf.Internal.EnumLite:
            "-Xep:ShouldNotSubclass:OFF",
        ] + JAVA8_JAVACOPTS,
        deps = deps + [
            "//java/com/google/protobuf",
        ],
        compatible_with = compatible_with,
    )
