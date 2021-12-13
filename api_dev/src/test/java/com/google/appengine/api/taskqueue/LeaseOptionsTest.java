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

import static com.google.appengine.api.taskqueue.LeaseOptions.Builder.withCountLimit;
import static com.google.appengine.api.taskqueue.LeaseOptions.Builder.withDeadlineInSeconds;
import static com.google.appengine.api.taskqueue.LeaseOptions.Builder.withLeasePeriod;
import static com.google.appengine.api.taskqueue.LeaseOptions.Builder.withTag;
import static com.google.common.truth.Truth.assertThat;

import java.util.concurrent.TimeUnit;
import junit.framework.TestCase;

/**
 * Unit tests for {@link LeaseOptions}.
 *
 */
public class LeaseOptionsTest extends TestCase {

  public void testLeaseOptions() throws Exception {
    assertEquals(Long.valueOf(100), withLeasePeriod(100, TimeUnit.SECONDS).getLease());
    assertEquals(TimeUnit.SECONDS, withLeasePeriod(200, TimeUnit.SECONDS).getUnit());
    assertEquals(Long.valueOf(300), withCountLimit(300).getCountLimit());
    assertEquals(null, withCountLimit(1).getDeadlineInSeconds());
    assertEquals(0.40, withDeadlineInSeconds(0.40).getDeadlineInSeconds(), 0.0);
    assertEquals(null, withDeadlineInSeconds(null).getDeadlineInSeconds());
    assertEquals(false, withDeadlineInSeconds(null).getGroupByTag());
    assertEquals(true, withDeadlineInSeconds(null).groupByTag().getGroupByTag());
    assertEquals(true, withTag(new byte[] {}).getGroupByTag());
    assertEquals(true, withTag("").getGroupByTag());
    byte[] tag = new byte[] {1, 2, 3};
    assertEquals(tag, withTag(tag).getTag());
    assertEquals("ABC", new String(withTag("ABC").getTag()));
  }

  public void testCopyConstructor() throws Exception {
    LeaseOptions options1 =
        withLeasePeriod(100, TimeUnit.SECONDS).countLimit(200).deadlineInSeconds(0.30);
    assertThat(new LeaseOptions(options1)).isEqualTo(options1);

    LeaseOptions options2 = new LeaseOptions(options1).leasePeriod(400, TimeUnit.HOURS);
    LeaseOptions options3 = new LeaseOptions(options1).countLimit(500);
    LeaseOptions options4 = new LeaseOptions(options1).deadlineInSeconds(0.60);
    LeaseOptions options5 = new LeaseOptions(options1).groupByTag();
    LeaseOptions options6 = new LeaseOptions(options1).tag("TAG");

    assertThat(options2.getLease()).isNotEqualTo(options1.getLease());
    assertThat(options2.getUnit()).isNotEqualTo(options1.getUnit());
    assertThat(options3.getCountLimit()).isNotEqualTo(options1.getCountLimit());
    assertThat(options4.getDeadlineInSeconds()).isNotEqualTo(options1.getDeadlineInSeconds());
    assertThat(options5.getGroupByTag()).isNotEqualTo(options1.getGroupByTag());
    assertThat(options6.getTag()).isNotEqualTo(options1.getTag());

    assertEquals(options1.getLease(), options3.getLease());
    assertEquals(options1.getUnit(), options3.getUnit());
    assertEquals(options1.getCountLimit(), options4.getCountLimit());
    assertEquals(options1.getDeadlineInSeconds(), options5.getDeadlineInSeconds());
    assertEquals(options1.getGroupByTag(), options2.getGroupByTag());
    assertEquals(options1.getTag(), options2.getTag());
  }

  public void testEquals() {
    assertThat(withLeasePeriod(2, TimeUnit.SECONDS))
        .isNotEqualTo(withLeasePeriod(1, TimeUnit.SECONDS));
    assertThat(withLeasePeriod(1, TimeUnit.MINUTES))
        .isNotEqualTo(withLeasePeriod(1, TimeUnit.MILLISECONDS));
    assertThat(withCountLimit(2)).isNotEqualTo(withCountLimit(1));
    assertThat(withDeadlineInSeconds(2.0)).isNotEqualTo(withDeadlineInSeconds(1.0));
    assertThat(withDeadlineInSeconds(null)).isNotEqualTo(withDeadlineInSeconds(1.0));
    assertThat(withDeadlineInSeconds(2.0)).isNotEqualTo(withDeadlineInSeconds(null));

    assertThat(withDeadlineInSeconds(null)).isNotEqualTo(withDeadlineInSeconds(null).groupByTag());
    assertThat(withTag("TAG2")).isNotEqualTo(withTag("TAG1"));
    assertThat(withDeadlineInSeconds(null)).isNotEqualTo(withDeadlineInSeconds(null).tag("TAG1"));
    assertThat(withDeadlineInSeconds(null).groupByTag())
        .isNotEqualTo(withDeadlineInSeconds(null).tag("TAG1"));
    assertThat(withDeadlineInSeconds(null).tag("TAG2")).isNotEqualTo(withDeadlineInSeconds(null));
    assertThat(withDeadlineInSeconds(null).tag("TAG2"))
        .isNotEqualTo(withDeadlineInSeconds(null).groupByTag());

    assertEquals(
        withLeasePeriod(10, TimeUnit.SECONDS).countLimit(20),
        withCountLimit(20).leasePeriod(10, TimeUnit.SECONDS));
    assertEquals(
        withCountLimit(30).deadlineInSeconds(40.0), withDeadlineInSeconds(40.0).countLimit(30));
    assertEquals(
        withDeadlineInSeconds(50.0).leasePeriod(60, TimeUnit.HOURS),
        withLeasePeriod(60, TimeUnit.HOURS).deadlineInSeconds(50.0));
  }

  public void testHashCode() {
    // Make sure hashCode() doesn't npe in the presence of absent properties.
    withLeasePeriod(10, TimeUnit.SECONDS).hashCode();
    withCountLimit(1).hashCode();
    withDeadlineInSeconds(null).hashCode();

    assertThat(withLeasePeriod(2, TimeUnit.SECONDS).hashCode())
        .isNotEqualTo(withLeasePeriod(1, TimeUnit.SECONDS).hashCode());
    assertThat(withLeasePeriod(1, TimeUnit.MINUTES).hashCode())
        .isNotEqualTo(withLeasePeriod(1, TimeUnit.MILLISECONDS).hashCode());
    assertThat(withCountLimit(2).hashCode()).isNotEqualTo(withCountLimit(1).hashCode());
    assertThat(withDeadlineInSeconds(2.0).hashCode())
        .isNotEqualTo(withDeadlineInSeconds(1.0).hashCode());
    assertThat(withDeadlineInSeconds(null).hashCode())
        .isNotEqualTo(withDeadlineInSeconds(1.0).hashCode());
    assertThat(withDeadlineInSeconds(2.0).hashCode())
        .isNotEqualTo(withDeadlineInSeconds(null).hashCode());

    assertThat(withDeadlineInSeconds(null).hashCode())
        .isNotEqualTo(withDeadlineInSeconds(null).groupByTag().hashCode());
    assertThat(withTag("TAG2").hashCode()).isNotEqualTo(withTag("TAG1").hashCode());
    assertThat(withDeadlineInSeconds(null).hashCode())
        .isNotEqualTo(withDeadlineInSeconds(null).tag("TAG1").hashCode());
    assertThat(withDeadlineInSeconds(null).groupByTag().hashCode())
        .isNotEqualTo(withDeadlineInSeconds(null).tag("TAG1").hashCode());
    assertThat(withDeadlineInSeconds(null).tag("TAG2").hashCode())
        .isNotEqualTo(withDeadlineInSeconds(null).hashCode());
    assertThat(withDeadlineInSeconds(null).tag("TAG2").hashCode())
        .isNotEqualTo(withDeadlineInSeconds(null).groupByTag().hashCode());

    assertEquals(
        withLeasePeriod(10, TimeUnit.SECONDS).countLimit(20).hashCode(),
        withCountLimit(20).leasePeriod(10, TimeUnit.SECONDS).hashCode());
    assertEquals(
        withCountLimit(30).deadlineInSeconds(40.0).hashCode(),
        withDeadlineInSeconds(40.0).countLimit(30).hashCode());
    assertEquals(
        withDeadlineInSeconds(50.0).leasePeriod(60, TimeUnit.HOURS).hashCode(),
        withLeasePeriod(60, TimeUnit.HOURS).deadlineInSeconds(50.0).hashCode());
  }

  public void testToString() {
    // We don't test the actual content of the string, since the API doesn't really
    // define a contract about what it should be, and we don't want to make the tests that
    // fragile. But we'll make sure toString() doesn't npe in the presence of absent properties.
    withLeasePeriod(1, TimeUnit.MILLISECONDS).toString();
    withCountLimit(1).toString();
    withDeadlineInSeconds(null).toString();
    withTag(new byte[] {-1}).toString();
  }

  public void testIllegalLeasePeriod() throws Exception {
    try {
      withLeasePeriod(-1, TimeUnit.SECONDS);
      fail("IllegalArgumentException should be thrown");
    } catch (IllegalArgumentException e) {
      // Expected
    }

    try {
      withLeasePeriod(0, TimeUnit.SECONDS);
      fail("IllegalArgumentException should be thrown");
    } catch (IllegalArgumentException e) {
      // Expected
    }

    try {
      withLeasePeriod(1, null);
      fail("IllegalArgumentException should be thrown");
    } catch (IllegalArgumentException e) {
      // Expected
    }
  }

  public void testIllegalCountLimit() throws Exception {
    try {
      withCountLimit(-1);
      fail("IllegalArgumentException should be thrown");
    } catch (IllegalArgumentException e) {
      // Expected
    }

    try {
      withCountLimit(0);
      fail("IllegalArgumentException should be thrown");
    } catch (IllegalArgumentException e) {
      // Expected
    }
  }

  public void testIllegalDeadlineInSeconds() throws Exception {
    try {
      withDeadlineInSeconds(-1.0);
      fail("IllegalArgumentException should be thrown");
    } catch (IllegalArgumentException e) {
      // Expected
    }

    try {
      withDeadlineInSeconds(0.0);
      fail("IllegalArgumentException should be thrown");
    } catch (IllegalArgumentException e) {
      // Expected
    }
  }

  public void testIllegalTag() throws Exception {
    try {
      byte[] nullArray = null;
      withTag(nullArray);
      fail("IllegalArgumentException should be thrown");
    } catch (IllegalArgumentException e) {
      // Expected
    }

    try {
      String nullString = null;
      withTag(nullString);
      fail("IllegalArgumentException should be thrown");
    } catch (IllegalArgumentException e) {
      // Expected
    }
  }
}
