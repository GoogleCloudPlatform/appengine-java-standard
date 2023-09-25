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

package com.google.apphosting.runtime.jetty.delegate.internal;

import com.google.apphosting.runtime.jetty.delegate.api.DelegateExchange;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ReadPendingException;
import java.nio.channels.WritePendingException;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.util.Callback;

public class DelegateEndpoint implements EndPoint {
  private final long _creationTime = System.currentTimeMillis();
  private final DelegateExchange _exchange;
  private boolean _closed = false;

  public DelegateEndpoint(DelegateExchange exchange) {
    _exchange = exchange;
  }

  public DelegateExchange getDelegateExchange() {
    return _exchange;
  }

  @Override
  public InetSocketAddress getLocalAddress() {
    return _exchange.getLocalAddr();
  }

  @Override
  public InetSocketAddress getRemoteAddress() {
    return _exchange.getRemoteAddr();
  }

  @Override
  public boolean isOpen() {
    return !_closed;
  }

  @Override
  public long getCreatedTimeStamp() {
    return _creationTime;
  }

  @Override
  public void shutdownOutput() {
    _closed = true;
  }

  @Override
  public boolean isOutputShutdown() {
    return _closed;
  }

  @Override
  public boolean isInputShutdown() {
    return _closed;
  }

  @Override
  public void close() {
    _closed = true;
  }

  @Override
  public void close(Throwable cause) {}

  @Override
  public int fill(ByteBuffer buffer) throws IOException {
    return 0;
  }

  @Override
  public boolean flush(ByteBuffer... buffer) throws IOException {
    return false;
  }

  @Override
  public Object getTransport() {
    return null;
  }

  @Override
  public long getIdleTimeout() {
    return 0;
  }

  @Override
  public void setIdleTimeout(long idleTimeout) {}

  @Override
  public void fillInterested(Callback callback) throws ReadPendingException {}

  @Override
  public boolean tryFillInterested(Callback callback) {
    return false;
  }

  @Override
  public boolean isFillInterested() {
    return false;
  }

  @Override
  public void write(Callback callback, ByteBuffer... buffers) throws WritePendingException {}

  @Override
  public Connection getConnection() {
    return null;
  }

  @Override
  public void setConnection(Connection connection) {}

  @Override
  public void onOpen() {}

  @Override
  public void onClose(Throwable cause) {}

  @Override
  public void upgrade(Connection newConnection) {}
}
