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
import static org.mockito.Mockito.when;

import com.google.appengine.api.blobstore.BlobstoreInputStream.ClosedStreamException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/**
 * Unit tests for the {@link BlobstoreInputStream} class.
 *
 */
@RunWith(JUnit4.class)
public class BlobstoreInputStreamTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private BlobstoreService blobstoreService;
  @Mock private BlobInfoFactory blobInfoFactory;

  private BlobstoreInputStream createStream(BlobKey key, int offset) throws IOException {
    return new BlobstoreInputStream(key, offset, blobInfoFactory, blobstoreService);
  }

  @Test
  public void testThrowsExceptionOnInvalidBlobKey() throws Exception {
    BlobKey nonExistent = new BlobKey("foozbar");
    when(blobInfoFactory.loadBlobInfo(nonExistent)).thenReturn(null);
    assertThrows(
        BlobstoreInputStream.BlobstoreIOException.class, () -> createStream(nonExistent, 0));
  }

  private final BlobKey validKey = new BlobKey("hellaValid");

  public void setUpValidBlobInfoFactory() {
    when(blobInfoFactory.loadBlobInfo(validKey))
        .thenReturn(
            new BlobInfo(
                validKey, // blob key
                "text/plain", // content type
                new Date(), // date
                "filename", // file name
                100, // size
                "abcdef" // md5_hash
                ));
  }

  private static byte[] createTestArray(int length) {
    byte[] arr = new byte[length];
    for (int i = 0; i < length; i++) {
      arr[i] = (byte) (i % 255);
    }
    return arr;
  }

  @Test
  public void testCatchesReadPastTheEnd() throws Exception {
    setUpValidBlobInfoFactory();
    BlobstoreInputStream inputStream = createStream(validKey, 0);

    byte[] output = new byte[100];
    assertThrows(IndexOutOfBoundsException.class, () -> inputStream.read(output, 10, 100));
  }

  @Test
  public void testThrowsExceptionOnNegativeOffset() throws Exception {
    assertThrows(IllegalArgumentException.class, () -> createStream(validKey, -1));
  }

  @Test
  public void testReadsPastEndAreEmpty() throws Exception {
    setUpValidBlobInfoFactory();

    BlobstoreInputStream inputStream = createStream(validKey, 1000);
    assertThat(inputStream.read()).isEqualTo(-1);
  }

  /**
   * Regression test: read() should return a value from [0,255] whereas the Java byte type is in the
   * range [-128,127].
   */
  @Test
  public void testCompensatesForByteBeingSigned() throws Exception {
    setUpValidBlobInfoFactory();
    BlobstoreInputStream inputStream = createStream(validKey, 0);
    byte[] testArr = createTestArray(100);
    testArr[0] = -1;
    testArr[1] = -2;
    when(blobstoreService.fetchData(validKey, 0, 99)).thenReturn(testArr);

    // -1 should be coerced into its unsigned equivalent
    assertThat(inputStream.read()).isEqualTo(255);
    // as should -2
    assertThat(inputStream.read()).isEqualTo(254);
  }

  @Test
  public void testSimpleReads() throws Exception {
    setUpValidBlobInfoFactory();
    BlobstoreInputStream inputStream = createStream(validKey, 0);
    byte[] testArr = createTestArray(100);
    when(blobstoreService.fetchData(validKey, 0, 99)).thenReturn(testArr);

    byte[] output = new byte[100];
    assertThat(inputStream.read(output, 0, 100)).isEqualTo(100);
    assertThat(output).isEqualTo(testArr);

    assertThat(inputStream.read()).isEqualTo(-1);
    assertThat(inputStream.read(output, 0, 100)).isEqualTo(-1);
  }

  /**
   * Assert that the subarrays: expected[expectedOffset:expectedOffset + length] and
   * actual[actualOffset:actualOffset + length] are equal.
   */
  private static void assertSubarraysEqual(
      byte[] expected, int expectedOffset, byte[] actual, int actualOffset, int length) {
    assertThat(Arrays.copyOfRange(actual, actualOffset, actualOffset + length))
        .isEqualTo(Arrays.copyOfRange(expected, expectedOffset, expectedOffset + length));
  }

  @Test
  public void testReadToOffset() throws Exception {
    setUpValidBlobInfoFactory();
    int blobOffset = 10;
    int blobSize = 100;
    BlobstoreInputStream inputStream = createStream(validKey, blobOffset);
    byte[] testArr = createTestArray(blobSize);
    when(blobstoreService.fetchData(validKey, blobOffset, blobSize - 1)).thenReturn(testArr);

    int outputOffset = 7;
    int outputSize = 10;
    byte[] output = new byte[outputSize + outputOffset];

    assertThat(inputStream.read(output, outputOffset, outputSize)).isEqualTo(outputSize);
    assertSubarraysEqual(testArr, 0, output, outputOffset, outputSize);
  }

  /**
   * Tests {@code mark()} behavior by doing a read, setting the mark, doing a read, {@code
   * reset()}ing, and doing a final read.
   */
  @Test
  public void testMark() throws Exception {
    setUpValidBlobInfoFactory();
    int blobSize = 100;
    int firstSize = 20;
    int secondSize = 30;
    BlobstoreInputStream inputStream = createStream(validKey, 0);

    byte[] testArr1 = createTestArray(blobSize);

    // Create an array that contains the remainder of the blob after the first
    // read.
    byte[] testArr2 = Arrays.copyOfRange(testArr1, firstSize, blobSize - firstSize);

    when(blobstoreService.fetchData(validKey, 0, blobSize - 1)).thenReturn(testArr1);

    // Second call after we drop the buffer on reset. The first time reading this
    // data is picked up by the buffer from the very first read, so we only
    // read starting at this offset once, after the reset.
    when(blobstoreService.fetchData(validKey, firstSize, blobSize - 1)).thenReturn(testArr2);

    byte[] output1 = new byte[firstSize];
    assertThat(inputStream.read(output1, 0, firstSize)).isEqualTo(firstSize);
    assertSubarraysEqual(testArr1, 0, output1, 0, firstSize);

    assertThat(inputStream.markSupported()).isTrue();
    inputStream.mark(secondSize);

    byte[] output2 = new byte[secondSize];
    assertThat(inputStream.read(output2, 0, secondSize)).isEqualTo(secondSize);
    assertSubarraysEqual(testArr2, 0, output2, 0, secondSize);
    inputStream.reset();

    // Try to call reset twice to make sure it fails.
    assertThrows(IOException.class, inputStream::reset);

    output2 = new byte[secondSize];
    assertThat(inputStream.read(output2, 0, secondSize)).isEqualTo(secondSize);
    assertSubarraysEqual(testArr2, 0, output2, 0, secondSize);
  }

  /** Regression test ensuring that calling mark() before read() yields a valid result. */
  @Test
  public void testMarkBeforeRead() throws IOException {
    setUpValidBlobInfoFactory();
    int blobSize = 100;
    int readSize = 20;
    BlobstoreInputStream inputStream = createStream(validKey, 0);
    byte[] testArr1 = createTestArray(blobSize);

    // Create an array that contains the remainder of the blob after the first
    // read.
    // We read, then reset, dropping the buffer, then read again.
    when(blobstoreService.fetchData(validKey, 0, blobSize - 1)).thenReturn(testArr1);

    assertThat(inputStream.markSupported()).isTrue();
    inputStream.mark(readSize);
    byte[] output1 = new byte[readSize];
    assertThat(inputStream.read(output1, 0, readSize)).isEqualTo(readSize);
    assertSubarraysEqual(testArr1, 0, output1, 0, readSize);

    // reset and reread
    inputStream.reset();
    assertThat(inputStream.read(output1, 0, readSize)).isEqualTo(readSize);
    assertSubarraysEqual(testArr1, 0, output1, 0, readSize);
  }

  /** Test behaviors of the stream after close() is called. */
  @Test
  public void testClose() throws IOException {
    setUpValidBlobInfoFactory();
    try (BlobstoreInputStream inputStream = createStream(validKey, 0)) {
      inputStream.close();
      assertThrows(ClosedStreamException.class, inputStream::read);
      assertThrows(ClosedStreamException.class, () -> inputStream.read(new byte[1]));
      inputStream.mark(1);
      assertThrows(ClosedStreamException.class, inputStream::reset);
    }
  }
}
