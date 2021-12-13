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

import static com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueServiceError.ErrorCode.DATASTORE_ERROR_VALUE;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.truth.Truth.assertThat;

import com.google.apphosting.datastore.proto2api.DatastoreV3Pb;
import com.google.common.collect.ImmutableMap;
import com.google.common.truth.Expect;
import com.google.protobuf.ProtocolMessageEnum;
import java.lang.reflect.Method;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class DatastoreErrorsTest {
  @Rule public Expect expect = Expect.create();

  @Test
  public void datastoreErrorsAreListedAsTaskqueueErrors() throws ReflectiveOperationException {
    ImmutableMap<String, Integer> datastoreErrorCodes =
        protoEnumCodes(DatastoreV3Pb.Error.ErrorCode.class);
    ImmutableMap<String, Integer> expectedTaskQueueErrorCodes =
        datastoreErrorCodes.entrySet().stream()
            .collect(
                toImmutableMap(
                    entry -> "DATASTORE_" + entry.getKey(),
                    entry -> entry.getValue() + DATASTORE_ERROR_VALUE));
    ImmutableMap<String, Integer> taskQueueErrorCodes =
        protoEnumCodes(TaskQueuePb.TaskQueueServiceError.ErrorCode.class);
    assertThat(taskQueueErrorCodes).containsAtLeastEntriesIn(expectedTaskQueueErrorCodes);
  }

  private static <E extends Enum<E> & ProtocolMessageEnum>
      ImmutableMap<String, Integer> protoEnumCodes(Class<E> protoEnumClass)
          throws ReflectiveOperationException {
    Method valuesMethod = protoEnumClass.getMethod("values");
    @SuppressWarnings("unchecked") // every enum has a method like this
    E[] values = (E[]) valuesMethod.invoke(null);
    ImmutableMap.Builder<String, Integer> o = new ImmutableMap.Builder<>();
    for (E v : values) {
      o.put(v.name(), v.getNumber());
    }
    return o.build();
  }
}
