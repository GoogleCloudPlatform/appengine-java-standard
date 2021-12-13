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

import com.google.appengine.api.blobstore.BlobKey;
import com.google.common.collect.Sets;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ServingUrlOptionsTest {

  @Test
  public void testToString() {
    ServingUrlOptions options = ServingUrlOptions.Builder.withGoogleStorageFileName("/gs/bucket/o");
    assertThat(
            options
                .toString()
                .equals(
                    "ServingUrlOptions: blobKey=None, googleStorageFileName=/gs/bucket/o, "
                        + "secureUrl=false, hasCrop=false, imageSize=Not Set."))
        .isTrue();
    options.secureUrl(true);
    assertThat(
            options
                .toString()
                .equals(
                    "ServingUrlOptions: blobKey=None, googleStorageFileName=/gs/bucket/o, "
                        + "secureUrl=true, hasCrop=false, imageSize=Not Set."))
        .isTrue();
    options.crop(true);
    assertThat(
            options
                .toString()
                .equals(
                    "ServingUrlOptions: blobKey=None, googleStorageFileName=/gs/bucket/o, "
                        + "secureUrl=true, hasCrop=true, imageSize=Not Set."))
        .isTrue();
    options.imageSize(1);
    assertThat(
            options
                .toString()
                .equals(
                    "ServingUrlOptions: blobKey=None, googleStorageFileName=/gs/bucket/o, "
                        + "secureUrl=true, hasCrop=true, imageSize=1."))
        .isTrue();

    options = ServingUrlOptions.Builder.withBlobKey(new BlobKey("some_blobkey"));
    assertThat(
            options
                .toString()
                .equals(
                    "ServingUrlOptions: blobKey=<BlobKey: some_blobkey>,"
                        + " googleStorageFileName=None, secureUrl=false, hasCrop=false,"
                        + " imageSize=Not Set."))
        .isTrue();
  }

  @Test
  public void testEquals() {
    assertThat(ServingUrlOptions.Builder.withGoogleStorageFileName("/gs/bucket/foo"))
        .isEqualTo(ServingUrlOptions.Builder.withGoogleStorageFileName("/gs/bucket/foo"));
    assertThat(ServingUrlOptions.Builder.withBlobKey(new BlobKey("foo")))
        .isEqualTo(ServingUrlOptions.Builder.withBlobKey(new BlobKey("foo")));
    assertThat(
            ServingUrlOptions.Builder.withGoogleStorageFileName("/gs/bucket/foo")
                .getGoogleStorageFileName())
        .isEqualTo("/gs/bucket/foo");
    assertThat(ServingUrlOptions.Builder.withBlobKey(new BlobKey("foo")).getBlobKey())
        .isEqualTo(new BlobKey("foo"));
    assertThat(
            ServingUrlOptions.Builder.withGoogleStorageFileName("/gs/bucket/foo")
                .crop(true)
                .getCrop())
        .isTrue();
    assertThat(
            ServingUrlOptions.Builder.withGoogleStorageFileName("/gs/bucket/foo")
                .secureUrl(true)
                .getSecureUrl())
        .isTrue();
    assertThat(
            ServingUrlOptions.Builder.withGoogleStorageFileName("/gs/bucket/foo")
                .imageSize(5)
                .getImageSize())
        .isEqualTo(5);

    assertThat(
            ServingUrlOptions.Builder.withGoogleStorageFileName("/gs/bucket/foo")
                .equals(ServingUrlOptions.Builder.withGoogleStorageFileName("/gs/bucket/bar")))
        .isFalse();
    assertThat(
            ServingUrlOptions.Builder.withGoogleStorageFileName("/gs/bucket/foo")
                .equals(ServingUrlOptions.Builder.withBlobKey(new BlobKey("blobkey1"))))
        .isFalse();
    assertThat(
            ServingUrlOptions.Builder.withBlobKey(new BlobKey("blobkey2"))
                .equals(ServingUrlOptions.Builder.withBlobKey(new BlobKey("blobkey1"))))
        .isFalse();
    assertThat(
            ServingUrlOptions.Builder.withGoogleStorageFileName("/gs/bucket/foo")
                .equals(
                    ServingUrlOptions.Builder.withGoogleStorageFileName("/gs/bucket/foo")
                        .imageSize(5)))
        .isFalse();
    assertThat(
            ServingUrlOptions.Builder.withGoogleStorageFileName("/gs/bucket/foo")
                .imageSize(1)
                .equals(
                    ServingUrlOptions.Builder.withGoogleStorageFileName("/gs/bucket/foo")
                        .imageSize(5)))
        .isFalse();
    assertThat(
            ServingUrlOptions.Builder.withGoogleStorageFileName("/gs/bucket/foo")
                .equals(
                    ServingUrlOptions.Builder.withGoogleStorageFileName("/gs/bucket/foo")
                        .crop(true)))
        .isFalse();
    assertThat(
            ServingUrlOptions.Builder.withGoogleStorageFileName("/gs/bucket/foo")
                .crop(false)
                .equals(
                    ServingUrlOptions.Builder.withGoogleStorageFileName("/gs/bucket/foo")
                        .crop(true)))
        .isFalse();
    assertThat(
            ServingUrlOptions.Builder.withGoogleStorageFileName("/gs/bucket/foo")
                .equals(
                    ServingUrlOptions.Builder.withGoogleStorageFileName("/gs/bucket/foo")
                        .secureUrl(true)))
        .isFalse();
    assertThat(
            ServingUrlOptions.Builder.withGoogleStorageFileName("/gs/bucket/foo")
                .secureUrl(false)
                .equals(
                    ServingUrlOptions.Builder.withGoogleStorageFileName("/gs/bucket/foo")
                        .secureUrl(true)))
        .isFalse();
  }

  @Test
  public void testHashCode() {
    Set<ServingUrlOptions> set = Sets.newHashSet();

    set.add(ServingUrlOptions.Builder.withGoogleStorageFileName("/gs/bucket/foo"));
    set.add(ServingUrlOptions.Builder.withBlobKey(new BlobKey("blobkey2")));
    set.add(ServingUrlOptions.Builder.withBlobKey(new BlobKey("blobkey2")).secureUrl(true));
    set.add(ServingUrlOptions.Builder.withBlobKey(new BlobKey("blobkey2")).crop(true));
    set.add(ServingUrlOptions.Builder.withBlobKey(new BlobKey("blobkey2")).imageSize(1));
    set.add(ServingUrlOptions.Builder.withBlobKey(new BlobKey("blobkey2")));

    assertThat(set).hasSize(5);
    assertThat(set).contains(ServingUrlOptions.Builder.withGoogleStorageFileName("/gs/bucket/foo"));
    assertThat(set).contains(ServingUrlOptions.Builder.withBlobKey(new BlobKey("blobkey2")));
    assertThat(set)
        .contains(ServingUrlOptions.Builder.withBlobKey(new BlobKey("blobkey2")).secureUrl(true));
    assertThat(set)
        .contains(ServingUrlOptions.Builder.withBlobKey(new BlobKey("blobkey2")).crop(true));
    assertThat(set)
        .contains(ServingUrlOptions.Builder.withBlobKey(new BlobKey("blobkey2")).imageSize(1));
    assertThat(set)
        .doesNotContain(
            ServingUrlOptions.Builder.withBlobKey(new BlobKey("blobkey2")).imageSize(10));
  }

  @Test
  public void testInvalidImageSize() {
    assertThrows(
        IllegalArgumentException.class,
        () -> ServingUrlOptions.Builder.withBlobKey(new BlobKey("blobkey2")).imageSize(-1));
  }

  @Test
  public void testSetBlobKeyAndGoogleStorageFileName() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            ServingUrlOptions.Builder.withBlobKey(new BlobKey("foo")).googleStorageFileName("bar"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            ServingUrlOptions.Builder.withGoogleStorageFileName("bar").blobKey(new BlobKey("foo")));
  }

  @Test
  public void testBlobKeyNotSet() {
    assertThrows(
        IllegalStateException.class,
        () -> ServingUrlOptions.Builder.withGoogleStorageFileName("/gs/bucket/foo").getBlobKey());
  }

  @Test
  public void testGoogleStorageFileNameNotSet() {
    assertThrows(
        IllegalStateException.class,
        () ->
            ServingUrlOptions.Builder.withBlobKey(new BlobKey("blobkey2"))
                .getGoogleStorageFileName());
  }

  @Test
  public void testSecureUrlNotSet() {
    assertThrows(
        IllegalStateException.class,
        () -> ServingUrlOptions.Builder.withGoogleStorageFileName("/gs/bucket/foo").getSecureUrl());
  }

  @Test
  public void testCropNotSet() {
    assertThrows(
        IllegalStateException.class,
        () -> ServingUrlOptions.Builder.withGoogleStorageFileName("/gs/bucket/foo").getCrop());
  }

  @Test
  public void testImageSizeSet() {
    assertThrows(
        IllegalStateException.class,
        () -> ServingUrlOptions.Builder.withGoogleStorageFileName("/gs/bucket/foo").getImageSize());
  }
}
