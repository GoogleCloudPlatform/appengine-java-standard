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

package com.google.appengine.tools.remoteapi;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.apphosting.base.protos.api_bytes.RemoteApiPb;
import com.google.protobuf.ByteString;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.InvalidProtocolBufferException;
import java.io.ByteArrayOutputStream;
import java.io.Externalizable;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import javax.naming.NamingException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Test for {@link RemoteRpc}. */
@RunWith(JUnit4.class)
public class RemoteRpcTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Mock private AppEngineClient appEngineClient;

  /** Test that every RPC has a request id and that these ids differ. */
  @Test
  public void testRequestId() throws IOException {
    RemoteRpc remoteRpc = new RemoteRpc(appEngineClient);
    byte[] dummyRequest = {1, 2, 3, 4};
    RemoteApiPb.Response responseProto = RemoteApiPb.Response.getDefaultInstance();
    AppEngineClient.Response dummyResponse =
        new AppEngineClient.Response(200, responseProto.toByteArray(), UTF_8);
    when(appEngineClient.getRemoteApiPath()).thenReturn("/path/one");
    when(appEngineClient.post(eq("/path/one"), eq("application/octet-stream"), any(byte[].class)))
        .thenReturn(dummyResponse);

    ArgumentCaptor<byte[]> byteArrayCaptor = ArgumentCaptor.forClass(byte[].class);

    remoteRpc.call("foo", "bar", "logSuffix", dummyRequest);

    verify(appEngineClient)
        .post(eq("/path/one"), eq("application/octet-stream"), byteArrayCaptor.capture());
    RemoteApiPb.Request request1 = parseRequest(byteArrayCaptor.getValue());
    assertThat(request1.getServiceName()).isEqualTo("foo");
    assertThat(request1.getMethod()).isEqualTo("bar");
    assertThat(request1.getRequest().toByteArray()).isEqualTo(dummyRequest);
    String id1 = request1.getRequestId();

    when(appEngineClient.getRemoteApiPath()).thenReturn("/path/two");
    when(appEngineClient.post(eq("/path/two"), eq("application/octet-stream"), any(byte[].class)))
        .thenReturn(dummyResponse);

    remoteRpc.call("foo", "bar", "logSuffix", dummyRequest);

    verify(appEngineClient)
        .post(eq("/path/two"), eq("application/octet-stream"), byteArrayCaptor.capture());
    RemoteApiPb.Request request2 = parseRequest(byteArrayCaptor.getValue());
    assertThat(request2.getServiceName()).isEqualTo("foo");
    assertThat(request2.getMethod()).isEqualTo("bar");
    assertThat(request2.getRequest().toByteArray()).isEqualTo(dummyRequest);
    String id2 = request2.getRequestId();
    assertWithMessage("Expected '%s' != '%s[", id1, id2).that(id1.equals(id2)).isFalse();
  }

  @Test
  public void allowedExceptionDeserialization_succeeds() throws Exception {
    RemoteRpc remoteRpc = new RemoteRpc(appEngineClient);
    IllegalArgumentException nestedCause = new IllegalArgumentException("bad key");
    nestedCause.addSuppressed(new IOException("suppressed io failure"));
    ConcurrentModificationException serverException =
        new ConcurrentModificationException("entity modified");
    serverException.initCause(nestedCause);

    mockJavaExceptionResponse(serializeToByteString(serverException));

    ConcurrentModificationException thrown =
        assertThrows(
            ConcurrentModificationException.class,
            () -> remoteRpc.call("datastore_v3", "Put", "", new byte[] {1}));
    assertThat(thrown).hasMessageThat().isEqualTo("entity modified");
    assertThat(thrown).hasCauseThat().isInstanceOf(ConcurrentModificationException.class);
    assertThat(thrown).hasCauseThat().hasCauseThat().isInstanceOf(IllegalArgumentException.class);
    assertThat(thrown.getCause()).hasCauseThat().hasMessageThat().isEqualTo("bad key");
    assertThat(thrown.getCause().getCause().getSuppressed()).hasLength(1);
    assertThat(thrown.getCause().getCause().getSuppressed()[0]).isInstanceOf(IOException.class);
  }

  @Test
  public void allowedAppEngineException_succeeds() throws Exception {
    RemoteRpc remoteRpc = new RemoteRpc(appEngineClient);
    RemoteApiException appException =
        new RemoteApiException("datastore timeout", "datastore_v3", "Get", null);

    mockJavaExceptionResponse(serializeToByteString(appException));

    RemoteApiException thrown =
        assertThrows(
            RemoteApiException.class,
            () -> remoteRpc.call("datastore_v3", "Get", "", new byte[] {1}));
    assertThat(thrown).hasMessageThat().isEqualTo("datastore timeout");
  }

  @Test
  public void checkedExceptionDeserialization_wrappedInRemoteApiException() throws Exception {
    RemoteRpc remoteRpc = new RemoteRpc(appEngineClient);
    IOException checkedException = new IOException("disk full");

    mockJavaExceptionResponse(serializeToByteString(checkedException));

    RemoteApiException thrown =
        assertThrows(
            RemoteApiException.class,
            () -> remoteRpc.call("datastore_v3", "Get", "", new byte[] {1}));
    assertThat(thrown).hasMessageThat().contains("response was an exception");
    assertThat(thrown).hasCauseThat().isInstanceOf(IOException.class);
    assertThat(thrown).hasCauseThat().hasMessageThat().isEqualTo("disk full");
  }

  @Test
  public void nullJavaExceptionDeserialization_throwsUnexpectedResponseType() throws Exception {
    RemoteRpc remoteRpc = new RemoteRpc(appEngineClient);

    mockJavaExceptionResponse(serializeToByteString(null));

    RemoteApiException thrown =
        assertThrows(
            RemoteApiException.class,
            () -> remoteRpc.call("datastore_v3", "Get", "", new byte[] {1}));
    assertThat(thrown).hasMessageThat().contains("unexpected response type: null");
  }

  @Test
  public void non200HttpResponse_throwsRemoteApiException() throws Exception {
    RemoteRpc remoteRpc = new RemoteRpc(appEngineClient);
    AppEngineClient.Response errorResponse = new AppEngineClient.Response(500, new byte[0], UTF_8);
    when(appEngineClient.getRemoteApiPath()).thenReturn("/remote_api");
    when(appEngineClient.post(eq("/remote_api"), eq("application/octet-stream"), any()))
        .thenReturn(errorResponse);

    RemoteApiException thrown =
        assertThrows(
            RemoteApiException.class,
            () -> remoteRpc.call("datastore_v3", "Get", "", new byte[] {1}));
    assertThat(thrown).hasMessageThat().contains("unexpected HTTP response: 500");
  }

  @Test
  public void oversizedStackTraceArray_rejected() throws Exception {
    RemoteRpc remoteRpc = new RemoteRpc(appEngineClient);
    IllegalStateException oversizedException = new IllegalStateException("oversized stack");
    // A shared frame keeps references (~1000) and bytes (~5 KB) under their limits, so only the
    // array length bound (1000) can reject.
    StackTraceElement[] hugeStack = new StackTraceElement[1001];
    Arrays.fill(hugeStack, new StackTraceElement("Class", "method", "File.java", 1));
    oversizedException.setStackTrace(hugeStack);

    mockJavaExceptionResponse(serializeToByteString(oversizedException));

    RemoteApiException thrown =
        assertThrows(
            RemoteApiException.class,
            () -> remoteRpc.call("datastore_v3", "Get", "", new byte[] {1}));
    assertThat(thrown)
        .hasMessageThat()
        .contains("remote API call: can't deserialize server-side exception");
    assertThat(thrown).hasCauseThat().isInstanceOf(InvalidClassException.class);
    assertThat(thrown).hasCauseThat().hasMessageThat().contains("filter status: REJECTED");
  }

  @Test
  public void stackTraceArrayAtLimit_succeeds() throws Exception {
    RemoteRpc remoteRpc = new RemoteRpc(appEngineClient);
    IllegalStateException boundaryException = new IllegalStateException("stack at limit");
    StackTraceElement[] maxStack = new StackTraceElement[1000];
    Arrays.fill(maxStack, new StackTraceElement("Cls", "run", "Boundary.java", 42));
    boundaryException.setStackTrace(maxStack);

    mockJavaExceptionResponse(serializeToByteString(boundaryException));

    IllegalStateException thrown =
        assertThrows(
            IllegalStateException.class,
            () -> remoteRpc.call("datastore_v3", "Get", "", new byte[] {10}));
    assertThat(thrown).hasMessageThat().isEqualTo("stack at limit");
    assertThat(thrown.getStackTrace()).hasLength(1000);
  }

  @Test
  public void deepCauseChain_rejected() throws Exception {
    RemoteRpc remoteRpc = new RemoteRpc(appEngineClient);
    // Each cause adds one level of object-graph depth. Empty stack traces keep the reference
    // count and stream size far below their limits, so only the depth bound (20) can reject.
    IllegalStateException deepException = new IllegalStateException("depth 0");
    deepException.setStackTrace(new StackTraceElement[0]);
    IllegalStateException current = deepException;
    for (int i = 1; i <= 25; i++) {
      IllegalStateException cause = new IllegalStateException("depth " + i);
      cause.setStackTrace(new StackTraceElement[0]);
      current.initCause(cause);
      current = cause;
    }

    mockJavaExceptionResponse(serializeToByteString(deepException));

    RemoteApiException thrown =
        assertThrows(
            RemoteApiException.class,
            () -> remoteRpc.call("datastore_v3", "Get", "", new byte[] {6}));
    assertThat(thrown)
        .hasMessageThat()
        .contains("remote API call: can't deserialize server-side exception");
    assertThat(thrown).hasCauseThat().isInstanceOf(InvalidClassException.class);
    assertThat(thrown).hasCauseThat().hasMessageThat().contains("filter status: REJECTED");
  }

  @Test
  public void excessiveObjectReferences_rejected() throws Exception {
    RemoteRpc remoteRpc = new RemoteRpc(appEngineClient);
    // Every stack frame points at the same StackTraceElement, so each array slot is written as a
    // 5-byte back-reference. Four 1000-element stacks yield >4000 references in roughly 20 KB,
    // staying within the array length (1000), depth (20), and byte (65536) limits.
    StackTraceElement sharedFrame = new StackTraceElement("Cls", "m", "F.java", 7);
    StackTraceElement[] repeatedFrames = new StackTraceElement[1000];
    Arrays.fill(repeatedFrames, sharedFrame);
    IllegalStateException rootException = new IllegalStateException("references 0");
    rootException.setStackTrace(repeatedFrames);
    IllegalStateException current = rootException;
    for (int i = 1; i <= 3; i++) {
      IllegalStateException cause = new IllegalStateException("references " + i);
      cause.setStackTrace(repeatedFrames);
      current.initCause(cause);
      current = cause;
    }

    mockJavaExceptionResponse(serializeToByteString(rootException));

    RemoteApiException thrown =
        assertThrows(
            RemoteApiException.class,
            () -> remoteRpc.call("datastore_v3", "Get", "", new byte[] {7}));
    assertThat(thrown)
        .hasMessageThat()
        .contains("remote API call: can't deserialize server-side exception");
    assertThat(thrown).hasCauseThat().isInstanceOf(InvalidClassException.class);
    assertThat(thrown).hasCauseThat().hasMessageThat().contains("filter status: REJECTED");
  }

  @Test
  public void oversizedStream_rejected() throws Exception {
    RemoteRpc remoteRpc = new RemoteRpc(appEngineClient);
    // A single 70,000-character message pushes the stream past MAX_BYTES (65536) while depth,
    // references, and array lengths stay small.
    IllegalStateException largeException = new IllegalStateException("x".repeat(70_000));
    largeException.setStackTrace(new StackTraceElement[0]);

    mockJavaExceptionResponse(serializeToByteString(largeException));

    RemoteApiException thrown =
        assertThrows(
            RemoteApiException.class,
            () -> remoteRpc.call("datastore_v3", "Get", "", new byte[] {8}));
    assertThat(thrown)
        .hasMessageThat()
        .contains("remote API call: can't deserialize server-side exception");
    assertThat(thrown).hasCauseThat().isInstanceOf(InvalidClassException.class);
    assertThat(thrown).hasCauseThat().hasMessageThat().contains("filter status: REJECTED");
  }

  @Test
  public void unresolvableClassInAllowedPackage_surfacesClassNotFound() throws Exception {
    RemoteRpc remoteRpc = new RemoteRpc(appEngineClient);
    // Rename the serialized class to a same-length name that does not exist on the classpath.
    // ObjectInputStream then invokes the filter at depth 1 with a null serialClass; the filter
    // must leave the decision UNDECIDED so ClassNotFoundException (not a filter rejection) is
    // reported.
    byte[] serialized = serializeToByteString(new MissingTypeException()).toByteArray();
    byte[] renamed =
        replaceAsciiOnce(
            serialized,
            MissingTypeException.class.getName(),
            MissingTypeException.class
                .getName()
                .replace("MissingTypeException", "AbsentClassException"));

    mockJavaExceptionResponse(ByteString.copyFrom(renamed));

    RemoteApiException thrown =
        assertThrows(
            RemoteApiException.class,
            () -> remoteRpc.call("datastore_v3", "Get", "", new byte[] {9}));
    assertThat(thrown)
        .hasMessageThat()
        .contains("remote API call: can't deserialize server-side exception");
    assertThat(thrown).hasCauseThat().isInstanceOf(ClassNotFoundException.class);
  }

  @Test
  public void disallowedNonThrowableInAllowedPackage_rejected() throws Exception {
    RemoteRpc remoteRpc = new RemoteRpc(appEngineClient);
    HashMap<String, String> gadgetPayload = new HashMap<>();
    gadgetPayload.put("maliciousKey", "maliciousValue");

    mockJavaExceptionResponse(serializeToByteString(gadgetPayload));

    RemoteApiException thrown =
        assertThrows(
            RemoteApiException.class,
            () -> remoteRpc.call("datastore_v3", "Get", "", new byte[] {2}));
    assertThat(thrown)
        .hasMessageThat()
        .contains("remote API call: can't deserialize server-side exception");
    assertThat(thrown).hasCauseThat().isInstanceOf(InvalidClassException.class);
    assertThat(thrown).hasCauseThat().hasMessageThat().contains("Non-Throwable class");
  }

  @Test
  public void topLevelNonThrowableInAllowedSet_rejected() throws Exception {
    RemoteRpc remoteRpc = new RemoteRpc(appEngineClient);
    ArrayList<String> topLevelList = new ArrayList<>();
    topLevelList.add("not a throwable");

    mockJavaExceptionResponse(serializeToByteString(topLevelList));

    RemoteApiException thrown =
        assertThrows(
            RemoteApiException.class,
            () -> remoteRpc.call("datastore_v3", "Get", "", new byte[] {2}));
    assertThat(thrown)
        .hasMessageThat()
        .contains("remote API call: can't deserialize server-side exception");
    assertThat(thrown).hasCauseThat().isInstanceOf(InvalidClassException.class);
    assertThat(thrown).hasCauseThat().hasMessageThat().contains("filter status: REJECTED");
  }

  @Test
  public void disallowedPackageException_rejected() throws Exception {
    RemoteRpc remoteRpc = new RemoteRpc(appEngineClient);
    NamingException unlistedPackageException = new NamingException("jndi lookup");

    mockJavaExceptionResponse(serializeToByteString(unlistedPackageException));

    RemoteApiException thrown =
        assertThrows(
            RemoteApiException.class,
            () -> remoteRpc.call("datastore_v3", "RunQuery", "", new byte[] {3}));
    assertThat(thrown)
        .hasMessageThat()
        .contains("remote API call: can't deserialize server-side exception");
    assertThat(thrown).hasCauseThat().isInstanceOf(InvalidClassException.class);
    assertThat(thrown)
        .hasCauseThat()
        .hasMessageThat()
        .contains("Unauthorized deserialization attempt");
  }

  @Test
  public void protobufException_wrappedInRemoteApiException() throws Exception {
    RemoteRpc remoteRpc = new RemoteRpc(appEngineClient);
    InvalidProtocolBufferException protoException =
        new InvalidProtocolBufferException("truncated request");

    mockJavaExceptionResponse(serializeToByteString(protoException));

    RemoteApiException thrown =
        assertThrows(
            RemoteApiException.class,
            () -> remoteRpc.call("datastore_v3", "Next", "", new byte[] {5}));
    assertThat(thrown).hasMessageThat().contains("response was an exception");
    assertThat(thrown).hasCauseThat().isInstanceOf(InvalidProtocolBufferException.class);
    assertThat(thrown).hasCauseThat().hasMessageThat().isEqualTo("truncated request");
  }

  @Test
  public void dynamicProxyInJavaException_rejected() throws Exception {
    RemoteRpc remoteRpc = new RemoteRpc(appEngineClient);
    Object proxyPayload =
        Proxy.newProxyInstance(
            RemoteRpcTest.class.getClassLoader(),
            new Class<?>[] {Comparable.class, Serializable.class},
            new DummyInvocationHandler());

    mockJavaExceptionResponse(serializeToByteString(proxyPayload));

    RemoteApiException thrown =
        assertThrows(
            RemoteApiException.class,
            () -> remoteRpc.call("datastore_v3", "Delete", "", new byte[] {4}));
    assertThat(thrown)
        .hasMessageThat()
        .contains("remote API call: can't deserialize server-side exception");
    assertThat(thrown).hasCauseThat().isInstanceOf(InvalidClassException.class);
    assertThat(thrown).hasCauseThat().hasMessageThat().contains("Proxy classes are not allowed");
  }

  @Test
  public void externalizableException_rejected() throws Exception {
    RemoteRpc remoteRpc = new RemoteRpc(appEngineClient);
    ExternalizableException externalizableException =
        new ExternalizableException("externalizable payload");

    mockJavaExceptionResponse(serializeToByteString(externalizableException));

    RemoteApiException thrown =
        assertThrows(
            RemoteApiException.class,
            () -> remoteRpc.call("datastore_v3", "Delete", "", new byte[] {4}));
    assertThat(thrown)
        .hasMessageThat()
        .contains("remote API call: can't deserialize server-side exception");
    assertThat(thrown).hasCauseThat().isInstanceOf(InvalidClassException.class);
    assertThat(thrown)
        .hasCauseThat()
        .hasMessageThat()
        .contains("Externalizable class is not allowed");
  }

  @Test
  public void nestedNonThrowableInAllowedPackage_rejected() throws Exception {
    RemoteRpc remoteRpc = new RemoteRpc(appEngineClient);
    ExceptionWithNonThrowableField exception =
        new ExceptionWithNonThrowableField(new NonThrowablePayload());

    mockJavaExceptionResponse(serializeToByteString(exception));

    RemoteApiException thrown =
        assertThrows(
            RemoteApiException.class,
            () -> remoteRpc.call("datastore_v3", "Delete", "", new byte[] {4}));
    assertThat(thrown)
        .hasMessageThat()
        .contains("remote API call: can't deserialize server-side exception");
    assertThat(thrown).hasCauseThat().isInstanceOf(InvalidClassException.class);
    assertThat(thrown).hasCauseThat().hasMessageThat().contains("Non-Throwable class");
  }

  private static final class ExternalizableException extends Exception implements Externalizable {
    // Java serialization only uses a public no-arg constructor for Externalizable classes.
    @SuppressWarnings("EffectivelyPrivate")
    public ExternalizableException() {}

    ExternalizableException(String message) {
      super(message);
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
      out.writeUTF(getMessage() == null ? "" : getMessage());
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
      in.readUTF();
    }
  }

  private static final class NonThrowablePayload implements Serializable {}

  private static final class ExceptionWithNonThrowableField extends Exception {
    // Carries a non-Throwable object into the serialized stream; never read back in tests.
    @SuppressWarnings("UnusedVariable")
    private final Object customField;

    ExceptionWithNonThrowableField(Object customField) {
      super("exception with custom field");
      this.customField = customField;
    }
  }

  private static final class MissingTypeException extends RuntimeException {}

  private static final class DummyInvocationHandler implements InvocationHandler, Serializable {
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
      return 0;
    }
  }

  private void mockJavaExceptionResponse(ByteString serializedJavaException) throws IOException {
    RemoteApiPb.Response responseProto =
        RemoteApiPb.Response.newBuilder().setJavaException(serializedJavaException).build();
    AppEngineClient.Response httpResponse =
        new AppEngineClient.Response(200, responseProto.toByteArray(), UTF_8);
    when(appEngineClient.getRemoteApiPath()).thenReturn("/remote_api");
    when(appEngineClient.post(eq("/remote_api"), eq("application/octet-stream"), any()))
        .thenReturn(httpResponse);
  }

  private static ByteString serializeToByteString(Object obj) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
      oos.writeObject(obj);
    }
    return ByteString.copyFrom(baos.toByteArray());
  }

  private static byte[] replaceAsciiOnce(byte[] haystack, String from, String to) {
    byte[] fromBytes = from.getBytes(UTF_8);
    byte[] toBytes = to.getBytes(UTF_8);
    if (toBytes.length != fromBytes.length) {
      throw new IllegalArgumentException(
          "Replacement must have the same UTF-8 byte length as " + from);
    }
    int matchIndex = -1;
    for (int i = 0; i + fromBytes.length <= haystack.length; i++) {
      if (Arrays.equals(haystack, i, i + fromBytes.length, fromBytes, 0, fromBytes.length)) {
        if (matchIndex != -1) {
          throw new IllegalStateException("Expected a single occurrence of " + from);
        }
        matchIndex = i;
      }
    }
    if (matchIndex < 0) {
      throw new IllegalStateException("Expected to find " + from + " in serialized stream");
    }
    byte[] result = haystack.clone();
    System.arraycopy(toBytes, 0, result, matchIndex, toBytes.length);
    return result;
  }

  private static RemoteApiPb.Request parseRequest(byte[] bytes) {
    RemoteApiPb.Request.Builder parsedRequest = RemoteApiPb.Request.newBuilder();
    try {
      parsedRequest.mergeFrom(bytes, ExtensionRegistry.getEmptyRegistry());
    } catch (InvalidProtocolBufferException e) {
      throw new RuntimeException(e);
    }
    //  assertThat(parsedRequest.mergeFrom(bytes)).isTrue();
    return parsedRequest.build();
  }
}
