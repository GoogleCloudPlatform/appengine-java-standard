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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.apphosting.runtime.anyrpc.CloneControllerServerInterface;
import com.google.apphosting.runtime.anyrpc.EvaluationRuntimeServerInterface;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

/**
 * Test for {@link GrpcPlugin}.
 *
 */
@RunWith(JUnit4.class)
public class GrpcPluginTest {
  @Test
  public void serverNeedsServerPort() {
    GrpcPlugin plugin = new GrpcPlugin();
    plugin.initialize(0);
    EvaluationRuntimeServerInterface evaluationRuntimeServer =
        Mockito.mock(EvaluationRuntimeServerInterface.class);
    CloneControllerServerInterface cloneControllerServer =
        Mockito.mock(CloneControllerServerInterface.class);
    IllegalStateException expected =
        assertThrows(
            IllegalStateException.class,
            () -> plugin.startServer(evaluationRuntimeServer, cloneControllerServer));
    assertThat(expected).hasMessageThat().isEqualTo("No server port has been specified");
  }
}
