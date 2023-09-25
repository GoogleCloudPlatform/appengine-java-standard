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

package com.google.apphosting.runtime.jetty9;

import com.google.apphosting.base.protos.RuntimePb.UPRequest;
import com.google.apphosting.base.protos.RuntimePb.UPResponse;
import com.google.apphosting.runtime.MutableUpResponse;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.util.Callback;

/**
 * Jetty Endpoint implementation for our RuntimePB protocol buffer.
 */
public class RpcEndPoint implements EndPoint {

  private final long created = System.currentTimeMillis();
  private final UPRequest upRequest;
  private final MutableUpResponse upResponse;
  private volatile boolean closed;
  private volatile Connection connection;
  private volatile long idleTimeout;

  public RpcEndPoint(UPRequest upRequest, MutableUpResponse upResponse) {
    super();
    this.upRequest = upRequest;
    this.upResponse = upResponse;
    upResponse.setError(UPResponse.ERROR.OK_VALUE);
  }

  public UPRequest getUpRequest() {
    return upRequest;
  }

  public MutableUpResponse getUpResponse() {
    return upResponse;
  }

  @Override
  public InetSocketAddress getLocalAddress() {
    return InetSocketAddress.createUnresolved("0.0.0.0", 0);
  }

  @Override
  public InetSocketAddress getRemoteAddress() {
    return InetSocketAddress.createUnresolved(upRequest.getRequest().getUserIp(), 0);
  }

  @Override
  public boolean isOpen() {
    return !closed;
  }

  @Override
  public long getCreatedTimeStamp() {
    return created;
  }

  @Override
  public void shutdownOutput() {
    closed = true;
  }

  @Override
  public boolean isOutputShutdown() {
    return closed;
  }

  @Override
  public boolean isInputShutdown() {
    return closed;
  }

  @Override
  public void close() {
    closed = true;
  }

  @Override
  public int fill(ByteBuffer buffer) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean flush(ByteBuffer... buffer) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public Object getTransport() {

    return this;
  }

  @Override
  public long getIdleTimeout() {

    return idleTimeout;
  }

  @Override
  public void setIdleTimeout(long idleTimeout) {

    this.idleTimeout = idleTimeout;
  }

  @Override
  public void fillInterested(Callback callback) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean tryFillInterested(Callback callback) {
    return false;
  }

  @Override
  public void write(Callback callback, ByteBuffer... buffers) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Connection getConnection() {

    return connection;
  }

  @Override
  public void setConnection(Connection connection) {

    this.connection = connection;
  }

  @Override
  public void onOpen() {}

  @Override
  public void onClose() {}

  @Override
  public void upgrade(Connection a) {}

  @Override
  public boolean isFillInterested() {
    return false;
  }

  @Override
  public boolean isOptimizedForDirectBuffers() {
    return false;
  }


}
