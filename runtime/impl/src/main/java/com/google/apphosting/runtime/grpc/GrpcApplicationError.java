/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.apphosting.runtime.grpc;

import com.google.common.base.Preconditions;
import com.google.common.flogger.GoogleLogger;
import com.google.common.primitives.Ints;
import io.grpc.Status;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manages Stubby-compatible encoding of application errors with gRPC. The background is that
 * the status of a Stubby call uses a Status class that has a namespace and a code. If the
 * namespace is {@code "RPC"} then the code is one of a fixed set of codes. If it the namespace
 * is something else then the code can communicate an application-level error. This is probably
 * not a great design since RPC errors and application errors are fundamentally a different sort
 * of thing, but it is there and {@link com.google.apphosting.runtime.ApiProxyImpl} depends on it.
 * Meanwhile, gRPC defines a fixed set of statuses in {@link io.grpc.Status} which are the only ones
 * that can be returned for a client call. So the methods in this class shoehorn application-level
 * errors into one of these predefined statuses by (ab)using the
 * {@link Status#getDescription() description} string.
 *
 */
class GrpcApplicationError {
  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();

  // The specific encoding we use is to make a Status.INVALID_ARGUMENT with a description
  // that looks like "SPACE<generic> CODE<23> something", to indicate namespace "generic",
  // application error code 23, and error detail "something".

  final String namespace;
  final int appErrorCode;
  final String errorDetail;

  GrpcApplicationError(String namespace, int appErrorCode, String errorDetail) {
    Preconditions.checkArgument(namespace.indexOf('>') < 0);
    this.namespace = namespace;
    this.appErrorCode = appErrorCode;
    this.errorDetail = errorDetail;
  }

  Status encode() {
    return Status.INVALID_ARGUMENT.withDescription(
        String.format("SPACE<%s> CODE<%d> %s", namespace, appErrorCode, errorDetail));
  }

  private static final Pattern ERROR_PATTERN = Pattern.compile(""
      + "SPACE<([^>]+)> "
      + "CODE<(\\d+)> "
      + "(.*)");

  static Optional<GrpcApplicationError> decode(Status status) {
    if (status.getCode().equals(Status.Code.INVALID_ARGUMENT)) {
      Matcher matcher = ERROR_PATTERN.matcher(status.getDescription());
      if (matcher.matches()) {
        String namespace = matcher.group(1);
        Integer appErrorCode = Ints.tryParse(matcher.group(2));
        String errorDetail = matcher.group(3);
        if (appErrorCode == null) {
          logger.atWarning().log("Could not parse app error out of: %s", status.getDescription());
        } else {
          return Optional.of(new GrpcApplicationError(namespace, appErrorCode, errorDetail));
        }
      }
    }
    return Optional.empty();
  }
}
