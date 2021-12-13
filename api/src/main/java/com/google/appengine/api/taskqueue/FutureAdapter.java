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

package com.google.appengine.api.taskqueue;

import com.google.appengine.api.utils.FutureWrapper;
import java.util.concurrent.Future;

/**
 * {@code FutureAdapter} is a simple {@link FutureWrapper} for situations where no conversion of
 * exceptions is required. This class is thread-safe.
 *
 * @param <K> The type of this {@link Future}
 * @param <V> The type of the wrapped {@link Future}
 */
abstract class FutureAdapter<K, V> extends FutureWrapper<K, V> {

  FutureAdapter(Future<K> parent) {
    super(parent);
  }

  @Override
  protected final Throwable convertException(Throwable cause) {
    return cause;
  }
}
