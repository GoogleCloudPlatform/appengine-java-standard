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

package com.google.appengine.tools.remoteapi;

import com.google.apphosting.api.ApiProxy.Delegate;
import com.google.apphosting.api.ApiProxy.Environment;
import org.jspecify.annotations.Nullable;

/**
 * Handles App Engine API calls by making HTTP requests to a remote server.
 * The exact mechanism by which the requests are made is an implementation
 * detail of subclasses.  Users of this class are expected to call
 * {@link #shutdown()} when they are done with an instance.
 * 
 * <p>This class and its subclasses are thread-safe.
 *
 */
abstract class RemoteApiDelegate implements Delegate<Environment> {
  private final RemoteRpc remoteRpc;
  private final RemoteDatastore remoteDatastore;

  /** Factory method. */
  public static RemoteApiDelegate newInstance(
      RemoteRpc remoteRpc,
      RemoteApiOptions options,
      @Nullable Delegate<Environment> containerDelegate) {
    return containerDelegate != null ?
        new HostedRemoteApiDelegate(remoteRpc, options, containerDelegate) :
        new StandaloneRemoteApiDelegate(remoteRpc, options);
  }

  /**
   * Do not call directly, use
   * {@link #newInstance(RemoteRpc, RemoteApiOptions, Delegate)} instead.
   */
  RemoteApiDelegate(RemoteRpc rpc, RemoteApiOptions options) {
    this.remoteRpc = rpc;
    this.remoteDatastore =
        new RemoteDatastore(remoteRpc.getClient().getAppId(), remoteRpc, options);
  }

  void resetRpcCount() {
    remoteRpc.resetRpcCount();
  }

  int getRpcCount() {
    return remoteRpc.getRpcCount();
  }

  final byte[] makeDefaultSyncCall(
      String serviceName, String methodName, byte[] request) {
    if (serviceName.equals(RemoteDatastore.DATASTORE_SERVICE)
        && !Boolean.getBoolean("com.google.appengine.devappserver2")) {
      // If we're talking to the datastore service, allow for special handling for
      // transaction-related requests. But no special handling is wanted if we are running under
      // devappserver2.
      return remoteDatastore.handleDatastoreCall(methodName, request);
    } else {
      return remoteRpc.call(serviceName, methodName, "", request);
    }
  }

  /**
   * Perform any necessary clean up and shut down.
   */
  public abstract void shutdown();
}
