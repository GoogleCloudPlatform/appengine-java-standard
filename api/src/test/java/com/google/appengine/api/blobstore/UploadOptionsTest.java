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

package com.google.appengine.api.blobstore;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.Sets;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class UploadOptionsTest {

  @Test
  public void testToString() {
    assertThat(UploadOptions.Builder.withMaxUploadSizeBytesPerBlob(10).toString())
        .isEqualTo(
            "UploadOptions: maxUploadSizeBytes=unlimited, maxUploadSizeBytesPerBlob=10, "
                + "gsBucketName=None.");
    assertThat(UploadOptions.Builder.withMaxUploadSizeBytes(1).toString())
        .isEqualTo(
            "UploadOptions: maxUploadSizeBytes=1, maxUploadSizeBytesPerBlob=unlimited, "
                + "gsBucketName=None.");
    assertThat(UploadOptions.Builder.withDefaults().toString())
        .isEqualTo(
            "UploadOptions: maxUploadSizeBytes=unlimited, maxUploadSizeBytesPerBlob=unlimited, "
                + "gsBucketName=None.");
    assertThat(UploadOptions.Builder.withMaxUploadSizeBytesPerBlob(1000000).toString())
        .isEqualTo(
            "UploadOptions: maxUploadSizeBytes=unlimited, maxUploadSizeBytesPerBlob=1000000, "
                + "gsBucketName=None.");
    assertThat(UploadOptions.Builder.withGoogleStorageBucketName("foo").toString())
        .isEqualTo(
            "UploadOptions: maxUploadSizeBytes=unlimited, maxUploadSizeBytesPerBlob=unlimited, "
                + "gsBucketName=foo.");
  }

  @Test
  public void testEquals() {
    assertThat(UploadOptions.Builder.withDefaults())
        .isEqualTo(UploadOptions.Builder.withDefaults());
    assertThat(UploadOptions.Builder.withMaxUploadSizeBytesPerBlob(10))
        .isEqualTo(UploadOptions.Builder.withMaxUploadSizeBytesPerBlob(10));
    assertThat(UploadOptions.Builder.withMaxUploadSizeBytes(10))
        .isEqualTo(UploadOptions.Builder.withMaxUploadSizeBytes(10));
    assertThat(UploadOptions.Builder.withMaxUploadSizeBytes(10).maxUploadSizeBytesPerBlob(20))
        .isEqualTo(UploadOptions.Builder.withMaxUploadSizeBytes(10).maxUploadSizeBytesPerBlob(20));
    assertThat(UploadOptions.Builder.withMaxUploadSizeBytes(10).getMaxUploadSizeBytes())
        .isEqualTo(10);
    assertThat(
            UploadOptions.Builder.withMaxUploadSizeBytesPerBlob(10).getMaxUploadSizeBytesPerBlob())
        .isEqualTo(10);
    assertThat(
            UploadOptions.Builder.withGoogleStorageBucketName("foo").getGoogleStorageBucketName())
        .isEqualTo("foo");
    assertThat(UploadOptions.Builder.withGoogleStorageBucketName("foo"))
        .isEqualTo(UploadOptions.Builder.withGoogleStorageBucketName("foo"));
    assertThat(
            UploadOptions.Builder.withGoogleStorageBucketName("foo").maxUploadSizeBytesPerBlob(20))
        .isEqualTo(
            UploadOptions.Builder.withGoogleStorageBucketName("foo").maxUploadSizeBytesPerBlob(20));

    assertThat(UploadOptions.Builder.withMaxUploadSizeBytesPerBlob(10))
        .isNotEqualTo(UploadOptions.Builder.withMaxUploadSizeBytesPerBlob(20));
    assertThat(UploadOptions.Builder.withMaxUploadSizeBytes(10))
        .isNotEqualTo(UploadOptions.Builder.withMaxUploadSizeBytes(20));
    assertThat(UploadOptions.Builder.withMaxUploadSizeBytes(10))
        .isNotEqualTo(UploadOptions.Builder.withDefaults());
    assertThat(UploadOptions.Builder.withMaxUploadSizeBytesPerBlob(10))
        .isNotEqualTo(UploadOptions.Builder.withDefaults());
    assertThat(UploadOptions.Builder.withGoogleStorageBucketName("foo"))
        .isNotEqualTo(UploadOptions.Builder.withGoogleStorageBucketName("bar"));
    assertThat(UploadOptions.Builder.withGoogleStorageBucketName("foo"))
        .isNotEqualTo(UploadOptions.Builder.withDefaults());
  }

  @Test
  public void testHashCode() {
    Set<UploadOptions> set = Sets.newHashSet();

    set.add(UploadOptions.Builder.withMaxUploadSizeBytesPerBlob(10));
    set.add(UploadOptions.Builder.withMaxUploadSizeBytesPerBlob(10));
    set.add(UploadOptions.Builder.withMaxUploadSizeBytes(20));
    set.add(UploadOptions.Builder.withMaxUploadSizeBytesPerBlob(10).maxUploadSizeBytes(20));
    set.add(UploadOptions.Builder.withGoogleStorageBucketName("foo"));

    assertThat(set)
        .containsExactly(
            UploadOptions.Builder.withMaxUploadSizeBytesPerBlob(10),
            UploadOptions.Builder.withMaxUploadSizeBytes(20),
            UploadOptions.Builder.withMaxUploadSizeBytesPerBlob(10).maxUploadSizeBytes(20),
            UploadOptions.Builder.withGoogleStorageBucketName("foo"));
  }

  @Test
  public void testInvalidMaxUploadSizeBytes() {
    assertThrows(
        IllegalArgumentException.class, () -> UploadOptions.Builder.withMaxUploadSizeBytes(-1));
  }

  @Test
  public void testInvalidMaxUploadSizeBytesPerBlob() {
    assertThrows(
        IllegalArgumentException.class,
        () -> UploadOptions.Builder.withMaxUploadSizeBytesPerBlob(-1));
  }

  @Test
  public void testMaxUploadSizeBytesNotSet() {
    assertThrows(
        IllegalStateException.class,
        () -> UploadOptions.Builder.withDefaults().getMaxUploadSizeBytes());
  }

  @Test
  public void testMaxUploadSizeBytesPerBlobNotSet() {
    assertThrows(
        IllegalStateException.class,
        () -> UploadOptions.Builder.withDefaults().getMaxUploadSizeBytesPerBlob());
  }

  @Test
  public void testGoogleStorageBucketNameNotSet() {
    assertThrows(
        IllegalStateException.class,
        () -> UploadOptions.Builder.withDefaults().getGoogleStorageBucketName());
  }
}
