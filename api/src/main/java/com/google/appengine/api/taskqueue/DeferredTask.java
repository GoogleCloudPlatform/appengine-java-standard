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

import java.io.Serializable;

/**
 * Interface for deferred tasks. Classes implementing this interface may use {@link
 * TaskOptions#payload(DeferredTask)} to serialize the {@link DeferredTask} into the payload of the
 * task definition. The {@link DeferredTask#run()} method will be called when the task is received
 * by the built in DeferredTask servlet.
 *
 * <p>Normal return from this method is considered success and will not retry unless {@link
 * DeferredTaskContext#markForRetry} is called. Exceptions thrown from this method will indicate a
 * failure and will be processed as a retry attempt unless {@link
 * DeferredTaskContext#setDoNotRetry(boolean)} was set to {@code true}.
 *
 */
public interface DeferredTask extends Runnable, Serializable {}
