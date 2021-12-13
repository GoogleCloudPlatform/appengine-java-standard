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

package com.google.appengine.api.taskqueue.dev;

import static com.google.common.truth.Truth.assertThat;

import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueAddRequest;
import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueAddRequest.RequestMethod;
import com.google.appengine.api.urlfetch.URLFetchServicePb;
import com.google.appengine.api.urlfetch.URLFetchServicePb.URLFetchRequest;
import com.google.appengine.api.urlfetch.URLFetchServicePb.URLFetchRequest.Header;
import com.google.apphosting.utils.config.QueueXml;
import com.google.protobuf.ByteString;
import java.text.DecimalFormat;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class UrlFetchJobTest {

  @Test
  public void testRequestMethodMapping() {
    for (RequestMethod rm : RequestMethod.values()) {
      assertThat(UrlFetchJob.translateRequestMethod(rm)).isNotNull();
    }
  }

  @Test
  public void testNewFetchRequest() {
    QueueXml.Entry entry = new QueueXml.Entry();
    entry.setTarget("target");

    TaskQueueAddRequest.Builder req =
        TaskQueueAddRequest.newBuilder()
            .setMethod(RequestMethod.GET)
            .setUrl(ByteString.copyFromUtf8("/this/is/the/url"))
            .setTaskName(ByteString.copyFromUtf8("name"))
            .setQueueName(ByteString.copyFromUtf8("queuename"))
            .setBody(ByteString.copyFromUtf8("this is the body"))
            .setEtaUsec(10)
            .addHeader(
                TaskQueueAddRequest.Header.newBuilder()
                    .setKey(ByteString.copyFromUtf8("key1"))
                    .setValue(ByteString.copyFromUtf8("value1"))
                    .build())
            .addHeader(
                TaskQueueAddRequest.Header.newBuilder()
                    .setKey(ByteString.copyFromUtf8("key2"))
                    .setValue(ByteString.copyFromUtf8("value2"))
                    .build());

    UrlFetchJob job = new UrlFetchJob();
    URLFetchRequest fetchReq =
        job.newFetchRequest("yar", req, "http://localhost:3344", 1, entry, 300);
    assertThat(fetchReq.getMethod()).isEqualTo(URLFetchServicePb.URLFetchRequest.RequestMethod.GET);
    assertThat(fetchReq.getUrl()).isEqualTo("http://localhost:3344/this/is/the/url");
    assertThat(fetchReq.getPayload()).isEqualTo(ByteString.copyFromUtf8("this is the body"));
    assertThat(fetchReq.getHeaderCount()).isEqualTo(10);
    assertThat(fetchReq.getHeader(0))
        .isEqualTo(Header.newBuilder().setKey("key1").setValue("value1").build());
    assertThat(fetchReq.getHeader(1))
        .isEqualTo(Header.newBuilder().setKey("key2").setValue("value2").build());
    assertThat(fetchReq.getHeader(2))
        .isEqualTo(
            Header.newBuilder()
                .setKey(UrlFetchJob.X_GOOGLE_DEV_APPSERVER_SKIPADMINCHECK)
                .setValue("true")
                .build());
    assertThat(fetchReq.getHeader(3))
        .isEqualTo(
            Header.newBuilder()
                .setKey(UrlFetchJob.X_APPENGINE_QUEUE_NAME)
                .setValue(req.getQueueName().toStringUtf8())
                .build());
    assertThat(fetchReq.getHeader(4))
        .isEqualTo(
            Header.newBuilder().setKey(UrlFetchJob.X_APPENGINE_TASK_NAME).setValue("yar").build());
    assertThat(fetchReq.getHeader(5))
        .isEqualTo(
            Header.newBuilder()
                .setKey(UrlFetchJob.X_APPENGINE_TASK_RETRY_COUNT)
                .setValue("1")
                .build());
    final String eta = new DecimalFormat("0.000000").format(req.getEtaUsec() / 1.0E6);
    assertThat(fetchReq.getHeader(6))
        .isEqualTo(
            Header.newBuilder().setKey(UrlFetchJob.X_APPENGINE_TASK_ETA).setValue(eta).build());
    assertThat(fetchReq.getHeader(7))
        .isEqualTo(
            Header.newBuilder()
                .setKey(UrlFetchJob.X_APPENGINE_SERVER_NAME)
                .setValue("target")
                .build());
    assertThat(fetchReq.getHeader(8))
        .isEqualTo(
            Header.newBuilder()
                .setKey(UrlFetchJob.X_APPENGINE_TASK_EXECUTION_COUNT)
                .setValue("1")
                .build());
    assertThat(fetchReq.getHeader(9))
        .isEqualTo(
            Header.newBuilder()
                .setKey(UrlFetchJob.X_APPENGINE_TASK_PREVIOUS_RESPONSE)
                .setValue("300")
                .build());
  }
}
