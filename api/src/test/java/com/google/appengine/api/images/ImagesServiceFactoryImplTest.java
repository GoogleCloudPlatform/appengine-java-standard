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
package com.google.appengine.api.images;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.appengine.api.EnvironmentProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ImagesServiceFactoryImplTest {

  private ImagesServiceFactoryImpl factory;
  private EnvironmentProvider mockEnvironmentProvider;

  @Before
  public void setUp() {
    factory = new ImagesServiceFactoryImpl();
    mockEnvironmentProvider = mock(EnvironmentProvider.class);
    factory.setEnvironmentProvider(mockEnvironmentProvider);
  }

  @Test
  public void makeImageFromFilename_newBehavior_trueEnv_gsPrefix() {
    when(mockEnvironmentProvider.getenv(
            ImagesServiceFactoryImpl.USE_CUSTOM_IMAGES_GRPC_SERVICE_ENV))
        .thenReturn("true");
    String filename = "/gs/bucket/object";

    // Should NOT call BlobstoreServiceFactory (which would fail in this env)
    Image image = factory.makeImageFromFilename(filename);

    assertThat(image.getBlobKey()).isNotNull();
    assertThat(image.getBlobKey().getKeyString()).isEqualTo(filename);
  }

  @Test
  public void makeImageFromFilename_newBehavior_trueEnv_noGsPrefix_throwsException() {
    when(mockEnvironmentProvider.getenv(
            ImagesServiceFactoryImpl.USE_CUSTOM_IMAGES_GRPC_SERVICE_ENV))
        .thenReturn("true");
    String filename = "not/gs/path";

    IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> factory.makeImageFromFilename(filename));
    assertThat(e).hasMessageThat().contains("must be prefixed with /gs/");
  }
}
