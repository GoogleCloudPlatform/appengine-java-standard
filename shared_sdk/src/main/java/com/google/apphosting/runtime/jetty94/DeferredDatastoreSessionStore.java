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

package com.google.apphosting.runtime.jetty94;

import static com.google.appengine.api.taskqueue.RetryOptions.Builder.withTaskAgeLimitSeconds;
import static com.google.appengine.api.taskqueue.TaskOptions.Builder.withPayload;

import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.taskqueue.DeferredTask;
import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TransientFailureException;
import com.google.apphosting.runtime.SessionStore.Retryable;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.Optional;
import org.eclipse.jetty.server.session.SessionData;

/**
 * A {@link DatastoreSessionStore.SessionDataStoreImpl} extension that defers all datastore writes
 * via the taskqueue.
 */
class DeferredDatastoreSessionStore extends DatastoreSessionStore.SessionDataStoreImpl {

  /** Try to save the session state for 10 seconds, then give up. */
  private static final int SAVE_TASK_AGE_LIMIT_SECS = 10;

  // The DeferredTask implementations we use to put and delete session data in
  // the datastore are are general-purpose, but we're not ready to expose them
  // in the public api, so we access them via reflection.
  private static final Constructor<DeferredTask> putDeferredTaskConstructor;
  private static final Constructor<DeferredTask> deleteDeferredTaskConstructor;

  static {
    putDeferredTaskConstructor =
        getConstructor(
            DeferredTask.class.getPackage().getName() + ".DatastorePutDeferredTask", Entity.class);
    deleteDeferredTaskConstructor =
        getConstructor(
            DeferredTask.class.getPackage().getName() + ".DatastoreDeleteDeferredTask", Key.class);
  }

  private final Queue queue;

  DeferredDatastoreSessionStore(Optional<String> queueName) {
    this.queue =
        queueName.isPresent()
            ? QueueFactory.getQueue(queueName.get())
            : QueueFactory.getDefaultQueue();
  }

  @Override
  public void doStore(String id, SessionData data, long lastSaveTime)
      throws IOException, Retryable {
    try {
      // Setting a timeout on retries to reduce the likelihood that session
      // state "reverts."  This can happen if a session in state s1 is saved
      // but the write fails.  Then the session in state s2 is saved and the
      // write succeeds.  Then a retry of the save of the session in s1
      // succeeds.  We could use version numbers in the session to detect this
      // scenario, but it doesn't seem worth it.
      // The length of this timeout has been chosen arbitrarily.  Maybe let
      // users set it?
      Entity e = entityFromSession(id, data);

      queue.add(
          withPayload(newDeferredTask(putDeferredTaskConstructor, e))
              .retryOptions(withTaskAgeLimitSeconds(SAVE_TASK_AGE_LIMIT_SECS)));
    } catch (ReflectiveOperationException e) {
      throw new IOException(e);
    } catch (TransientFailureException e) {
      throw new Retryable(e);
    }
  }

  @Override
  public boolean delete(String id) throws IOException {
    try {
      Key key = createKeyForSession(id);
      // We'll let this task retry indefinitely.
      queue.add(withPayload(newDeferredTask(deleteDeferredTaskConstructor, key)));
    } catch (ReflectiveOperationException e) {
      throw new IOException(e);
    }
    return true;
  }

  /**
   * Helper method that returns a 1-arg constructor taking an arg of the given type for the given
   * class name
   */
  private static Constructor<DeferredTask> getConstructor(String clsName, Class<?> argType) {
    try {
      @SuppressWarnings("unchecked")
      Class<DeferredTask> cls = (Class<DeferredTask>) Class.forName(clsName);
      Constructor<DeferredTask> ctor = cls.getConstructor(argType);
      ctor.setAccessible(true);
      return ctor;
    } catch (ReflectiveOperationException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  /**
   * Helper method that constructs a {@link DeferredTask} using the given constructor, passing in
   * the given arg as a parameter.
   *
   * <p>We used to construct an instance of a DeferredTask implementation that lived in
   * runtime-shared.jar, but this resulted in much heartache: http://b/5386803. We tried resolving
   * this in a number of ways, but ultimately the simplest solution was to just create the
   * DeferredTask implementations we needed in the runtime jar and the api jar. We load them from
   * the runtime jar here and we load them from the api jar in the servlet that deserializes the
   * tasks.
   */
  private static DeferredTask newDeferredTask(Constructor<DeferredTask> ctor, Object arg)
      throws ReflectiveOperationException {
      return ctor.newInstance(arg);

  }
}
