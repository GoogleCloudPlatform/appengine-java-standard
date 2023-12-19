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

package com.google.apphosting.runtime.jetty.delegate.impl;

import java.nio.ByteBuffer;
import java.util.Objects;

import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.io.Retainable;
import org.eclipse.jetty.util.BufferUtil;

public class ContentChunk extends Retainable.ReferenceCounter implements Content.Chunk {
  private final ByteBuffer byteBuffer;
  private final boolean last;

  public ContentChunk(byte[] bytes) {
    this(BufferUtil.toBuffer(bytes), true);
  }

  public ContentChunk(ByteBuffer byteBuffer, boolean last) {
    this.byteBuffer = Objects.requireNonNull(byteBuffer);
    this.last = last;
  }

  @Override
  public ByteBuffer getByteBuffer() {
    return byteBuffer;
  }

  @Override
  public boolean isLast() {
    return last;
  }

  @Override
  public String toString() {

    return String.format(
        "%s@%x[l=%b,b=%s]",
        getClass().getSimpleName(),
        hashCode(),
        isLast(),
        BufferUtil.toDetailString(getByteBuffer()));
  }
}