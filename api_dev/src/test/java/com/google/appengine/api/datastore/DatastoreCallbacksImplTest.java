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

package com.google.appengine.api.datastore;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import com.google.common.collect.ImmutableList;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Properties;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.stubbing.Answer;

@RunWith(JUnit4.class)
public class DatastoreCallbacksImplTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  // This is a little tricky. Because the callback class is instantiated by reflection given just
  // the class name, we need some way to observe the callbacks from the instantiated class. So
  // the instantiated HasCallbacks will call this mock HasCallbacks (via a ThreadLocal), and we'll
  // check that those calls happened using Mockito's doAnswer. We have to be a bit careful about
  // exactly when we make the check, because the various FooContext classes have a currentIndex
  // field that gets incremented after the callback. So the check has to happen *during* the
  // callback, before currentIndex is incremented. That's why we use doAnswer rather than
  // verify(mock) with an argThat, or ArgumentCaptor.
  @Mock private HasCallbacks mock;
  private static final ThreadLocal<HasCallbacks> threadLocalMock = new ThreadLocal<>();

  @Mock private CurrentTransactionProvider currentTransactionProvider;

  // Necessary because the Key constructor reaches into the environment to
  // figure out the app id.
  private final LocalServiceTestHelper helper = new LocalServiceTestHelper();
  private Key yarKey;
  private Key notYarKey;
  private Entity yarEntity;
  private Entity notYarEntity;

  private static class HasCallbacks {
    private static HasCallbacks mock() {
      return threadLocalMock.get();
    }

    @PrePut
    public void prePut1(PutContext context) {
      mock().prePut1(context);
    }

    @PrePut
    public void prePut2(PutContext context) {
      mock().prePut2(context);
    }

    @PostPut
    public void postPut1(PutContext context) {
      mock().postPut1(context);
    }

    @PostPut
    public void postPut2(PutContext context) {
      mock().postPut2(context);
    }

    @PreDelete
    public void preDelete1(DeleteContext context) {
      mock().preDelete1(context);
    }

    @PreDelete
    public void preDelete2(DeleteContext context) {
      mock().preDelete2(context);
    }

    @PostDelete
    public void postDelete1(DeleteContext context) {
      mock().postDelete1(context);
    }

    @PostDelete
    public void postDelete2(DeleteContext context) {
      mock().postDelete2(context);
    }
  }

  private static final String HAS_CALLBACKS = HasCallbacks.class.getName();

  @Before
  public void setUp() throws Exception {
    helper.setUp();

    threadLocalMock.set(mock);

    yarKey = KeyFactory.createKey("yar", 23);
    notYarKey = KeyFactory.createKey("notyar", 24);
    yarEntity = new Entity(yarKey);
    notYarEntity = new Entity(notYarKey);
  }

  @After
  public void tearDown() {
    helper.tearDown();
  }

  private static <T extends CallbackContext<?>> Answer<?> checkContext(T expectedContext) {
    return invocation -> {
      @SuppressWarnings("unchecked")
      T actualContext = (T) invocation.getArgument(0);
      assertThat(actualContext.getClass()).isEqualTo(expectedContext.getClass());
      assertThat(actualContext.getCurrentIndex()).isEqualTo(expectedContext.getCurrentIndex());
      assertThat(actualContext.getElements()).isEqualTo(expectedContext.getElements());
      return null;
    };
  }

  @Test
  public void testPrePut_1KindCallback() throws IOException {
    doAnswer(checkContext(newPutContext(yarEntity))).when(mock).prePut2(any());
    DatastoreCallbacksImpl callbacks = newCallbacks("yar.PrePut", HAS_CALLBACKS + ":prePut2");
    callbacks.executePrePutCallbacks(newPutContext(yarEntity));
    callbacks.executePrePutCallbacks(newPutContext(notYarEntity));
    verify(mock).prePut2(any());
  }

  @Test
  public void testPrePut_2KindCallbacks() throws IOException {
    doAnswer(checkContext(newPutContext(yarEntity))).when(mock).prePut2(any());
    doAnswer(checkContext(newPutContext(yarEntity))).when(mock).prePut1(any());
    DatastoreCallbacksImpl callbacks =
        newCallbacks("yar.PrePut", HAS_CALLBACKS + ":prePut2," + HAS_CALLBACKS + ":prePut1");
    callbacks.executePrePutCallbacks(newPutContext(yarEntity));
    callbacks.executePrePutCallbacks(newPutContext(new Entity("notyar", 234)));
    verify(mock).prePut2(any());
    verify(mock).prePut1(any());
  }

  @Test
  public void testPrePut_1GlobalCallback() throws IOException {
    doAnswer(checkContext(newPutContext(yarEntity)))
        .doAnswer(checkContext(newPutContext(notYarEntity)))
        .when(mock)
        .prePut2(any());
    DatastoreCallbacksImpl callbacks = newCallbacks(".PrePut", HAS_CALLBACKS + ":prePut2");
    callbacks.executePrePutCallbacks(newPutContext(yarEntity));
    callbacks.executePrePutCallbacks(newPutContext(notYarEntity));
    verify(mock, times(2)).prePut2(any());
  }

  @Test
  public void testPrePut_2GlobalCallbacks() throws IOException {
    doAnswer(checkContext(newPutContext(yarEntity)))
        .doAnswer(checkContext(newPutContext(notYarEntity)))
        .when(mock)
        .prePut2(any());
    doAnswer(checkContext(newPutContext(yarEntity)))
        .doAnswer(checkContext(newPutContext(notYarEntity)))
        .when(mock)
        .prePut1(any());
    DatastoreCallbacksImpl callbacks =
        newCallbacks(".PrePut", HAS_CALLBACKS + ":prePut2," + HAS_CALLBACKS + ":prePut1");
    callbacks.executePrePutCallbacks(newPutContext(yarEntity));
    callbacks.executePrePutCallbacks(newPutContext(notYarEntity));
    verify(mock, times(2)).prePut2(any());
    verify(mock, times(2)).prePut1(any());
  }

  @Test
  public void testPrePut_2KindCallbacks_2GlobalCallbacks() throws IOException {
    doAnswer(checkContext(newPutContext(yarEntity)))
        .doAnswer(checkContext(newPutContext(yarEntity)))
        .doAnswer(checkContext(newPutContext(notYarEntity)))
        .when(mock)
        .prePut2(any());
    doAnswer(checkContext(newPutContext(yarEntity)))
        .doAnswer(checkContext(newPutContext(yarEntity)))
        .doAnswer(checkContext(newPutContext(notYarEntity)))
        .when(mock)
        .prePut1(any());
    DatastoreCallbacksImpl callbacks =
        newCallbacks(
            ".PrePut",
            HAS_CALLBACKS + ":prePut2," + HAS_CALLBACKS + ":prePut1",
            "yar.PrePut",
            HAS_CALLBACKS + ":prePut2," + HAS_CALLBACKS + ":prePut1");
    callbacks.executePrePutCallbacks(newPutContext(yarEntity));
    callbacks.executePrePutCallbacks(newPutContext(notYarEntity));
    verify(mock, times(3)).prePut2(any());
    verify(mock, times(3)).prePut1(any());
  }

  @Test
  public void testPostPut_1KindCallback() throws IOException {
    doAnswer(checkContext(newPutContext(yarEntity))).when(mock).postPut2(any());
    DatastoreCallbacksImpl callbacks = newCallbacks("yar.PostPut", HAS_CALLBACKS + ":postPut2");
    callbacks.executePostPutCallbacks(newPutContext(yarEntity));
    callbacks.executePostPutCallbacks(newPutContext(notYarEntity));
    verify(mock).postPut2(any());
  }

  @Test
  public void testPostPut_2KindCallbacks() throws IOException {
    doAnswer(checkContext(newPutContext(yarEntity))).when(mock).postPut2(any());
    doAnswer(checkContext(newPutContext(yarEntity))).when(mock).postPut1(any());
    DatastoreCallbacksImpl callbacks =
        newCallbacks("yar.PostPut", HAS_CALLBACKS + ":postPut2," + HAS_CALLBACKS + ":postPut1");
    callbacks.executePostPutCallbacks(newPutContext(yarEntity));
    callbacks.executePostPutCallbacks(newPutContext(notYarEntity));
    verify(mock).postPut2(any());
    verify(mock).postPut1(any());
  }

  @Test
  public void testPostPut_1GlobalCallback() throws IOException {
    doAnswer(checkContext(newPutContext(yarEntity)))
        .doAnswer(checkContext(newPutContext(notYarEntity)))
        .when(mock)
        .postPut2(any());
    DatastoreCallbacksImpl callbacks = newCallbacks(".PostPut", HAS_CALLBACKS + ":postPut2");
    callbacks.executePostPutCallbacks(newPutContext(yarEntity));
    callbacks.executePostPutCallbacks(newPutContext(notYarEntity));
    verify(mock, times(2)).postPut2(any());
  }

  @Test
  public void testPostPut_2GlobalCallbacks() throws IOException {
    doAnswer(checkContext(newPutContext(yarEntity)))
        .doAnswer(checkContext(newPutContext(notYarEntity)))
        .when(mock)
        .postPut2(any());
    doAnswer(checkContext(newPutContext(yarEntity)))
        .doAnswer(checkContext(newPutContext(notYarEntity)))
        .when(mock)
        .postPut1(any());
    DatastoreCallbacksImpl callbacks =
        newCallbacks(".PostPut", HAS_CALLBACKS + ":postPut2," + HAS_CALLBACKS + ":postPut1");
    callbacks.executePostPutCallbacks(newPutContext(yarEntity));
    callbacks.executePostPutCallbacks(newPutContext(notYarEntity));
    verify(mock, times(2)).postPut2(any());
    verify(mock, times(2)).postPut1(any());
  }

  @Test
  public void testPostPut_2KindCallbacks_2GlobalCallbacks() throws IOException {
    doAnswer(checkContext(newPutContext(yarEntity)))
        .doAnswer(checkContext(newPutContext(yarEntity)))
        .doAnswer(checkContext(newPutContext(notYarEntity)))
        .when(mock)
        .postPut2(any());
    doAnswer(checkContext(newPutContext(yarEntity)))
        .doAnswer(checkContext(newPutContext(yarEntity)))
        .doAnswer(checkContext(newPutContext(notYarEntity)))
        .when(mock)
        .postPut1(any());
    DatastoreCallbacksImpl callbacks =
        newCallbacks(
            ".PostPut",
            HAS_CALLBACKS + ":postPut2," + HAS_CALLBACKS + ":postPut1",
            "yar.PostPut",
            HAS_CALLBACKS + ":postPut2," + HAS_CALLBACKS + ":postPut1");
    callbacks.executePostPutCallbacks(newPutContext(yarEntity));
    callbacks.executePostPutCallbacks(newPutContext(notYarEntity));
    verify(mock, times(3)).postPut2(any());
    verify(mock, times(3)).postPut1(any());
  }

  @Test
  public void testPreDelete_1KindCallback() throws IOException {
    doAnswer(checkContext(newDeleteContext(yarKey))).when(mock).preDelete2(any());
    DatastoreCallbacksImpl callbacks = newCallbacks("yar.PreDelete", HAS_CALLBACKS + ":preDelete2");
    callbacks.executePreDeleteCallbacks(newDeleteContext(yarKey));
    callbacks.executePreDeleteCallbacks(newDeleteContext(notYarKey));
    verify(mock).preDelete2(any());
  }

  @Test
  public void testPreDelete_2KindCallbacks() throws IOException {
    doAnswer(checkContext(newDeleteContext(yarKey))).when(mock).preDelete2(any());
    doAnswer(checkContext(newDeleteContext(yarKey))).when(mock).preDelete1(any());
    DatastoreCallbacksImpl callbacks =
        newCallbacks(
            "yar.PreDelete", HAS_CALLBACKS + ":preDelete2," + HAS_CALLBACKS + ":preDelete1");
    callbacks.executePreDeleteCallbacks(newDeleteContext(yarKey));
    callbacks.executePreDeleteCallbacks(newDeleteContext(notYarKey));
    verify(mock).preDelete2(any());
    verify(mock).preDelete1(any());
  }

  @Test
  public void testPreDelete_1GlobalCallback() throws IOException {
    doAnswer(checkContext(newDeleteContext(yarKey)))
        .doAnswer(checkContext(newDeleteContext(notYarKey)))
        .when(mock)
        .preDelete2(any());
    DatastoreCallbacksImpl callbacks = newCallbacks(".PreDelete", HAS_CALLBACKS + ":preDelete2");
    callbacks.executePreDeleteCallbacks(newDeleteContext(yarKey));
    callbacks.executePreDeleteCallbacks(newDeleteContext(notYarKey));
    verify(mock, times(2)).preDelete2(any());
  }

  @Test
  public void testPreDelete_2GlobalCallbacks() throws IOException {
    doAnswer(checkContext(newDeleteContext(yarKey)))
        .doAnswer(checkContext(newDeleteContext(notYarKey)))
        .when(mock)
        .preDelete2(any());
    doAnswer(checkContext(newDeleteContext(yarKey)))
        .doAnswer(checkContext(newDeleteContext(notYarKey)))
        .when(mock)
        .preDelete1(any());
    DatastoreCallbacksImpl callbacks =
        newCallbacks(".PreDelete", HAS_CALLBACKS + ":preDelete2," + HAS_CALLBACKS + ":preDelete1");
    callbacks.executePreDeleteCallbacks(newDeleteContext(yarKey));
    callbacks.executePreDeleteCallbacks(newDeleteContext(notYarKey));
    verify(mock, times(2)).preDelete2(any());
    verify(mock, times(2)).preDelete1(any());
  }

  @Test
  public void testPreDelete_2KindCallbacks_2GlobalCallbacks() throws IOException {
    doAnswer(checkContext(newDeleteContext(yarKey)))
        .doAnswer(checkContext(newDeleteContext(yarKey)))
        .doAnswer(checkContext(newDeleteContext(notYarKey)))
        .when(mock)
        .preDelete2(any());
    doAnswer(checkContext(newDeleteContext(yarKey)))
        .doAnswer(checkContext(newDeleteContext(yarKey)))
        .doAnswer(checkContext(newDeleteContext(notYarKey)))
        .when(mock)
        .preDelete1(any());
    DatastoreCallbacksImpl callbacks =
        newCallbacks(
            ".PreDelete",
            HAS_CALLBACKS + ":preDelete2," + HAS_CALLBACKS + ":preDelete1",
            "yar.PreDelete",
            HAS_CALLBACKS + ":preDelete2," + HAS_CALLBACKS + ":preDelete1");
    callbacks.executePreDeleteCallbacks(newDeleteContext(yarKey));
    callbacks.executePreDeleteCallbacks(newDeleteContext(notYarKey));
    verify(mock, times(3)).preDelete2(any());
    verify(mock, times(3)).preDelete1(any());
  }

  @Test
  public void testPostDelete_1KindCallback() throws IOException {
    doAnswer(checkContext(newDeleteContext(yarKey))).when(mock).postDelete2(any());
    DatastoreCallbacksImpl callbacks =
        newCallbacks("yar.PostDelete", HAS_CALLBACKS + ":postDelete2");
    callbacks.executePostDeleteCallbacks(newDeleteContext(yarKey));
    callbacks.executePostDeleteCallbacks(newDeleteContext(notYarKey));
    verify(mock).postDelete2(any());
  }

  @Test
  public void testPostDelete_2KindCallbacks() throws IOException {
    doAnswer(checkContext(newDeleteContext(yarKey))).when(mock).postDelete2(any());
    doAnswer(checkContext(newDeleteContext(yarKey))).when(mock).postDelete1(any());
    DatastoreCallbacksImpl callbacks =
        newCallbacks(
            "yar.PostDelete", HAS_CALLBACKS + ":postDelete2," + HAS_CALLBACKS + ":postDelete1");
    callbacks.executePostDeleteCallbacks(newDeleteContext(yarKey));
    callbacks.executePostDeleteCallbacks(newDeleteContext(notYarKey));
    verify(mock).postDelete2(any());
    verify(mock).postDelete1(any());
  }

  @Test
  public void testPostDelete_1GlobalCallback() throws IOException {
    doAnswer(checkContext(newDeleteContext(yarKey)))
        .doAnswer(checkContext(newDeleteContext(notYarKey)))
        .when(mock)
        .postDelete2(any());
    DatastoreCallbacksImpl callbacks = newCallbacks(".PostDelete", HAS_CALLBACKS + ":postDelete2");
    callbacks.executePostDeleteCallbacks(newDeleteContext(yarKey));
    callbacks.executePostDeleteCallbacks(newDeleteContext(notYarKey));
    verify(mock, times(2)).postDelete2(any());
  }

  @Test
  public void testPostDelete_2GlobalCallbacks() throws IOException {
    doAnswer(checkContext(newDeleteContext(yarKey)))
        .doAnswer(checkContext(newDeleteContext(notYarKey)))
        .when(mock)
        .postDelete2(any());
    doAnswer(checkContext(newDeleteContext(yarKey)))
        .doAnswer(checkContext(newDeleteContext(notYarKey)))
        .when(mock)
        .postDelete1(any());
    DatastoreCallbacksImpl callbacks =
        newCallbacks(
            ".PostDelete", HAS_CALLBACKS + ":postDelete2," + HAS_CALLBACKS + ":postDelete1");
    callbacks.executePostDeleteCallbacks(newDeleteContext(yarKey));
    callbacks.executePostDeleteCallbacks(newDeleteContext(notYarKey));
    verify(mock, times(2)).postDelete2(any());
    verify(mock, times(2)).postDelete1(any());
  }

  @Test
  public void testPostDelete_2KindCallbacks_2GlobalCallbacks() throws IOException {
    doAnswer(checkContext(newDeleteContext(yarKey)))
        .doAnswer(checkContext(newDeleteContext(yarKey)))
        .doAnswer(checkContext(newDeleteContext(notYarKey)))
        .when(mock)
        .postDelete2(any());
    doAnswer(checkContext(newDeleteContext(yarKey)))
        .doAnswer(checkContext(newDeleteContext(yarKey)))
        .doAnswer(checkContext(newDeleteContext(notYarKey)))
        .when(mock)
        .postDelete1(any());
    DatastoreCallbacksImpl callbacks =
        newCallbacks(
            ".PostDelete",
            HAS_CALLBACKS + ":postDelete2," + HAS_CALLBACKS + ":postDelete1",
            "yar.PostDelete",
            HAS_CALLBACKS + ":postDelete2," + HAS_CALLBACKS + ":postDelete1");
    callbacks.executePostDeleteCallbacks(newDeleteContext(yarKey));
    callbacks.executePostDeleteCallbacks(newDeleteContext(notYarKey));
    verify(mock, times(3)).postDelete2(any());
    verify(mock, times(3)).postDelete1(any());
  }

  @Test
  public void testEmptyConfig() throws IOException {
    DatastoreCallbacksImpl callbacks = newCallbacks();
    callbacks.executePreDeleteCallbacks(newDeleteContext(yarKey));
    callbacks.executePreDeleteCallbacks(newDeleteContext(notYarKey));
    verifyNoMoreInteractions(mock);
  }

  @Test
  public void testBadConfigs() throws IOException {
    DatastoreCallbacksImpl.InvalidCallbacksConfigException e1 =
        assertThrows(
            DatastoreCallbacksImpl.InvalidCallbacksConfigException.class,
            () -> newCallbacks("no period", "yar"));
    assertThat(e1)
        .hasMessageThat()
        .isEqualTo("Could not extract kind and callback type from 'no period'");

    DatastoreCallbacksImpl.InvalidCallbacksConfigException e2 =
        assertThrows(
            DatastoreCallbacksImpl.InvalidCallbacksConfigException.class,
            () -> newCallbacks("yam.InvalidCallbackType", "yar"));
    assertThat(e2).hasMessageThat().isEqualTo("Received unknown callback type InvalidCallbackType");

    DatastoreCallbacksImpl.InvalidCallbacksConfigException e3 =
        assertThrows(
            DatastoreCallbacksImpl.InvalidCallbacksConfigException.class,
            () -> newCallbacks("yam.PrePut", "no colon"));
    assertThat(e3)
        .hasMessageThat()
        .isEqualTo("Could not extract fully-qualified classname and method from 'no colon'");

    // Wrong signature for the callback type
    DatastoreCallbacksImpl.InvalidCallbacksConfigException e4 =
        assertThrows(
            DatastoreCallbacksImpl.InvalidCallbacksConfigException.class,
            () -> newCallbacks("yar.PostDelete", HAS_CALLBACKS + ":prePut1"));
    assertThat(e4)
        .hasMessageThat()
        .isEqualTo(
            "Unable to initialize datastore callbacks because of reference to missing method.");

    // Correct signature but wrong callback type
    DatastoreCallbacksImpl.InvalidCallbacksConfigException e5 =
        assertThrows(
            DatastoreCallbacksImpl.InvalidCallbacksConfigException.class,
            () -> newCallbacks("yar.PostPut", HAS_CALLBACKS + ":prePut1"));
    assertThat(e5)
        .hasMessageThat()
        .isEqualTo(
            "Unable to initialize datastore callbacks because method "
                + HAS_CALLBACKS
                + ".prePut1("
                + "com.google.appengine.api.datastore.PutContext) is missing annotation PostPut.");
  }

  @Test
  public void testTrickyConfigs() throws IOException {
    DatastoreCallbacksImpl callbacks =
        newCallbacks("kind with = in it.PrePut", HAS_CALLBACKS + ":prePut2");
    callbacks.executePrePutCallbacks(newPutContext(new Entity("kind with = in it", 23)));
    callbacks.executePrePutCallbacks(newPutContext(notYarEntity));
    verify(mock).prePut2(any());
    reset(mock);

    callbacks = newCallbacks("kind with . in it.PrePut", HAS_CALLBACKS + ":prePut2");
    callbacks.executePrePutCallbacks(newPutContext(new Entity("kind with . in it", 23)));
    callbacks.executePrePutCallbacks(newPutContext(notYarEntity));
    verify(mock).prePut2(any());
    reset(mock);

    doAnswer(checkContext(newPutContext(new Entity("kind with . and = and = and . in it", 23))))
        .when(mock)
        .prePut2(any());
    callbacks =
        newCallbacks("kind with . and = and = and . in it.PrePut", HAS_CALLBACKS + ":prePut2");
    callbacks.executePrePutCallbacks(
        newPutContext(new Entity("kind with . and = and = and . in it", 23)));
    callbacks.executePrePutCallbacks(newPutContext(notYarEntity));
  }

  @Test
  public void testConstructor() {
    assertThrows(NullPointerException.class, () -> new DatastoreCallbacksImpl(null, false));
  }

  @Test
  public void testRefsToNonexistentMethods() throws IOException {
    DatastoreCallbacksImpl.InvalidCallbacksConfigException e1 =
        assertThrows(
            DatastoreCallbacksImpl.InvalidCallbacksConfigException.class,
            () -> newCallbacks("yar.PreDelete", "com.google.appengine.doesnotexist:dne"));
    assertThat(e1)
        .hasMessageThat()
        .isEqualTo("Unable to initialize datastore callbacks due to missing class.");
    newCallbacks(true, "yar.PreDelete", "com.google.appengine.doesnotexist:dne");

    DatastoreCallbacksImpl.InvalidCallbacksConfigException e2 =
        assertThrows(
            DatastoreCallbacksImpl.InvalidCallbacksConfigException.class,
            () -> newCallbacks("yar.PreDelete", HAS_CALLBACKS + ":doesNotExist"));
    assertThat(e2)
        .hasMessageThat()
        .isEqualTo(
            "Unable to initialize datastore callbacks because of reference to missing method.");
    newCallbacks(true, "yar.PreDelete", HAS_CALLBACKS + ":doesNotExist");
  }

  /**
   * Creates a {@link DatastoreCallbacksImpl} instance from the provided key/value pairs. The
   * arguments alternate between key and value.
   */
  private DatastoreCallbacksImpl newCallbacks(String... args) throws IOException {
    return newCallbacks(false, args);
  }

  /**
   * Creates a {@link DatastoreCallbacksImpl} instance from the provided key/value pairs. The
   * arguments alternate between key and value.
   */
  private DatastoreCallbacksImpl newCallbacks(boolean ignoreMissingMethods, String... args)
      throws IOException {
    Properties props = new Properties();
    props.setProperty(DatastoreCallbacksImpl.FORMAT_VERSION_PROPERTY, "1");
    String key = null;
    for (String arg : args) {
      if (key == null) {
        key = arg;
      } else {
        props.setProperty(key, arg);
        key = null;
      }
    }
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    props.storeToXML(baos, "");
    try (ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray())) {
      return new DatastoreCallbacksImpl(bais, ignoreMissingMethods);
    }
  }

  private PutContext newPutContext(Entity... entities) {
    return new PutContext(currentTransactionProvider, ImmutableList.copyOf(entities));
  }

  private DeleteContext newDeleteContext(Key... keys) {
    return new DeleteContext(currentTransactionProvider, ImmutableList.copyOf(keys));
  }
}
