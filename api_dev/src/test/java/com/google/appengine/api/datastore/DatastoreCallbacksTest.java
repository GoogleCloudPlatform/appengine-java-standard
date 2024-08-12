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

import static com.google.appengine.api.datastore.FetchOptions.Builder.withDefaults;
import static com.google.apphosting.datastore.proto2api.DatastoreV3Pb.Error.ErrorCode.INTERNAL_ERROR;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.appengine.tools.development.testing.LocalDatastoreServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import com.google.apphosting.api.ApiProxy;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.stubbing.Answer;

/**
 * Tests for {@link DatastoreCallbacks}.
 *
 */
@RunWith(JUnit4.class)
public class DatastoreCallbacksTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private final LocalServiceTestHelper helper =
      new LocalServiceTestHelper(new LocalDatastoreServiceTestConfig());
  // This is a little tricky. Because the callback class is instantiated by reflection given just
  // the class name, we need some way to observe the callbacks from the instantiated class. So
  // the instantiated HasCallbacks will call this mock HasCallbacks (via a ThreadLocal), and we'll
  // check that those calls happened using Mockito's doAnswer. We have to be a bit careful about
  // exactly when we make the check, because the various FooContext classes get modified during
  // execution, for example removing elements from the getElements() list.
  // So the check often has to happen *during* the callback, before those changes happen. That's why
  // we use doAnswer rather than verify(mock) with an argThat, or ArgumentCaptor.
  @Mock private HasCallbacks mock;
  private static final ThreadLocal<HasCallbacks> threadLocalMock = new ThreadLocal<>();

  public static class HasCallbacks {
    private static HasCallbacks mock() {
      return threadLocalMock.get();
    }

    @PrePut
    public void prePut(PutContext context) {
      mock().prePut(context);
    }

    @PrePut
    public void explosivePrePut(PutContext context) {
      mock().explosivePrePut(context);
    }

    @PostPut
    public void postPut(PutContext context) {
      mock().postPut(context);
    }

    @PostPut
    public void explosivePostPut(PutContext context) {
      mock().explosivePostPut(context);
    }

    @PreDelete
    public void preDelete(DeleteContext context) {
      mock().preDelete(context);
    }

    @PreDelete
    public void explosivePreDelete(DeleteContext context) {
      mock().explosivePreDelete(context);
    }

    @PostDelete
    public void postDelete(DeleteContext context) {
      mock().postDelete(context);
    }

    @PostDelete
    public void explosivePostDelete(DeleteContext context) {
      mock().explosivePostDelete(context);
    }

    @PreGet
    public void preGet(PreGetContext context) {
      mock().preGet(context);
      String kind = context.getCurrentElement().getKind();
      if (kind.startsWith("cbOnlyEntity")) {
        Entity cbEntity = new Entity(context.getCurrentElement());
        cbEntity.setProperty("cb_property_only", "cb_entity1");
        context.setResultForCurrentElement(cbEntity);
      }

      if (kind.equals("not yar")) {
        Entity cbEntity = new Entity(context.getCurrentElement());
        cbEntity.setProperty("cb_property_only", "cb_entity2");
        context.setResultForCurrentElement(cbEntity);
      }

      if (kind.equals("putNullValue")) {
        context.setResultForCurrentElement(null);
      }

      if (kind.equals("putEntityOfWrongKind")) {
        context.setResultForCurrentElement(new Entity(KeyFactory.createKey("differentKind", 23)));
      }
    }

    @PreGet
    public void explosivePreGet(PreGetContext context) {
      mock().explosivePreGet(context);
    }

    @PostLoad
    public void postLoad(PostLoadContext context) {
      mock().postLoad(context);
      if (context.getCurrentElement().getKind().equals("yar")) {
        context.getCurrentElement().setProperty("timestamp", 3333333);
      }
    }

    @PostLoad
    public void explosivePostLoad(PostLoadContext context) {
      mock().explosivePostLoad(context);
    }

    @PreQuery
    @SuppressWarnings("deprecation")
    public void preQuery(PreQueryContext context) {
      mock().preQuery(context);
      String kind = context.getCurrentElement().getKind();
      if (kind.startsWith("decorateMe")) {
        context.getCurrentElement().addFilter("extra", Query.FilterOperator.EQUAL, 2);
      }
    }

    @PreQuery
    public void explosivePreQuery(PreQueryContext context) {
      mock().explosivePreQuery(context);
    }
  }

  private static final String HAS_CALLBACKS = HasCallbacks.class.getName();

  @Mock private CurrentTransactionProvider txnProvider;
  private Entity yar;
  private Entity yarChild;
  private Entity notYar;
  private Entity decorateMe1;
  private Entity decorateMe2;
  private Entity decorateMe3;
  private Query yarQuery;
  private Query kindlessAncestorQuery;
  private Query decorateMeQuery;

  @Before
  public void setUp() throws Exception {
    helper.setUp();

    threadLocalMock.set(mock);

    yar = new Entity("yar", 23);
    yarChild = new Entity("yarchild", 24, yar.getKey());
    notYar = new Entity("not yar", 25);
    decorateMe1 = new Entity("decorateMe", 26);
    decorateMe2 = new Entity("decorateMe", 27);
    decorateMe2.setProperty("extra", 2);
    decorateMe3 = new Entity("decorateMe", 28);
    decorateMe3.setProperty("extra", 3);
    yarQuery = new Query("yar");
    kindlessAncestorQuery = new Query();
    kindlessAncestorQuery.setAncestor(yar.getKey());
    decorateMeQuery = new Query("decorateMe");
    DatastoreServiceConfig.CALLBACKS = null;
  }

  @After
  public void tearDown() {
    helper.tearDown();
    System.clearProperty(DatastoreServiceConfig.CALLBACKS_CONFIG_SYS_PROP);

    threadLocalMock.set(null);
  }

  private static <T extends CallbackContext<?>> Answer<?> checkContext(T expectedContext) {
    return invocation -> {
      @SuppressWarnings("unchecked")
      T actualContext = (T) invocation.getArgument(0);
      assertThat(actualContext.getClass()).isEqualTo(expectedContext.getClass());
      assertThat(actualContext.getElements())
          .containsExactlyElementsIn(expectedContext.getElements());
      return null;
    };
  }

  private static <T extends CallbackContext<?>> T eqContext(T expectedContext) {
    return argThat(
        new ArgumentMatcher<T>() {
          @Override
          public boolean matches(T actualContext) {
            return actualContext.getClass().equals(expectedContext.getClass())
                && ImmutableSet.copyOf(actualContext.getElements())
                    .equals(ImmutableSet.copyOf(expectedContext.getElements()));
          }

          @Override
          public String toString() {
            return expectedContext.toString();
          }
        });
  }

  @Test
  public void testPrePut() throws Exception {
    setCallbacksConfig("yar.PrePut", HAS_CALLBACKS + ":prePut");
    AsyncDatastoreService datastore = DatastoreServiceFactory.getAsyncDatastoreService();
    Future<Key> result = datastore.put(yar);
    verify(mock, times(1)).prePut(eqContext(new PutContext(txnProvider, ImmutableList.of(yar))));
    result.get();
    result.get();
    result = datastore.put(notYar);
    result.get();
    result.get();
    // make sure the data was actually written
    assertThat(datastore.get(ImmutableList.of(yar.getKey(), notYar.getKey())).get()).hasSize(2);
    verifyNoMoreInteractions(mock);
  }

  @Test
  public void testPrePut_CommitTxn() throws Exception {
    setCallbacksConfig("yar.PrePut", HAS_CALLBACKS + ":prePut");
    AsyncDatastoreService datastore = DatastoreServiceFactory.getAsyncDatastoreService();
    Transaction txn = datastore.beginTransaction().get();
    doAnswer(checkContext(new PutContext(txnProvider, ImmutableList.of(yar))))
        .when(mock)
        .prePut(any());
    Future<Key> result = datastore.put(yar);
    verify(mock).prePut(any());
    verifyNoMoreInteractions(mock);
    result.get();
    verify(mock, times(1)).prePut(eqContext(new PutContext(txnProvider, ImmutableList.of(yar))));
    result.get();
    result = datastore.put(yarChild);
    result.get();
    result.get();
    txn.commit();
    // make sure the data was actually written
    assertThat(datastore.get(ImmutableList.of(yar.getKey(), yarChild.getKey())).get()).hasSize(2);
  }

  @Test
  public void testPrePut_Batch_CommitTxn() throws Exception {
    setCallbacksConfig("yar.PrePut", HAS_CALLBACKS + ":prePut");
    AsyncDatastoreService datastore = DatastoreServiceFactory.getAsyncDatastoreService();
    Transaction txn = datastore.beginTransaction().get();
    Future<List<Key>> result = datastore.put(ImmutableList.of(yar, yarChild));
    verify(mock, times(1))
        .prePut(eqContext(new PutContext(txnProvider, ImmutableList.of(yar, yarChild))));
    result.get();
    result.get();
    txn.commit();
    // make sure the data was actually written
    assertThat(datastore.get(ImmutableList.of(yar.getKey(), yarChild.getKey())).get()).hasSize(2);
    verifyNoMoreInteractions(mock);
  }

  @Test
  public void testPrePut_RollbackTxn() throws Exception {
    setCallbacksConfig("yar.PrePut", HAS_CALLBACKS + ":prePut");
    AsyncDatastoreService datastore = DatastoreServiceFactory.getAsyncDatastoreService();
    Transaction txn = datastore.beginTransaction().get();
    Future<Key> result = datastore.put(yar);
    result.get();
    verify(mock, times(1)).prePut(eqContext(new PutContext(txnProvider, ImmutableList.of(yar))));
    result.get();
    result = datastore.put(yarChild);
    result.get();
    result.get();
    txn.rollback();
    // make sure the data was not actually written
    assertThat(datastore.get(ImmutableList.of(yar.getKey(), yarChild.getKey())).get()).isEmpty();
    verifyNoMoreInteractions(mock);
  }

  @Test
  public void testPrePut_Batch_RollbackTxn() throws Exception {
    setCallbacksConfig("yar.PrePut", HAS_CALLBACKS + ":prePut");
    AsyncDatastoreService datastore = DatastoreServiceFactory.getAsyncDatastoreService();
    Transaction txn = datastore.beginTransaction().get();
    Future<List<Key>> result = datastore.put(ImmutableList.of(yar, yarChild));
    result.get();
    verify(mock, times(1))
        .prePut(eqContext(new PutContext(txnProvider, ImmutableList.of(yar, yarChild))));
    result.get();
    txn.rollback();
    // make sure the data was not actually written
    assertThat(datastore.get(ImmutableList.of(yar.getKey(), yarChild.getKey())).get()).isEmpty();
    verifyNoMoreInteractions(mock);
  }

  @Test
  public void testPrePut_FailureInCallback() throws Exception {
    setCallbacksConfig("yar.PrePut", HAS_CALLBACKS + ":explosivePrePut");
    doThrow(new RuntimeException("boom"))
        .when(mock)
        .explosivePrePut(eqContext(new PutContext(txnProvider, ImmutableList.of(yar))));
    AsyncDatastoreService datastore = DatastoreServiceFactory.getAsyncDatastoreService();
    RuntimeException e = assertThrows(RuntimeException.class, () -> datastore.put(yar));
    assertThat(e).hasMessageThat().isEqualTo("boom");
    // no preput callback defined for this kind so no exception
    datastore.put(notYar).get();
    // make sure the entity whose preput callback blew up does not get written
    assertThat(datastore.get(ImmutableList.of(yar.getKey())).get()).isEmpty();
    // make sure the entity whose preput callback did not blow up does get written
    assertThat(datastore.get(ImmutableList.of(notYar.getKey())).get()).hasSize(1);
  }

  @Test
  public void testPrePut_FailureInCallback_Txn() throws Exception {
    setCallbacksConfig("yar.PrePut", HAS_CALLBACKS + ":explosivePrePut");
    doThrow(new RuntimeException("boom"))
        .when(mock)
        .explosivePrePut(eqContext(new PutContext(txnProvider, ImmutableList.of(yar))));
    AsyncDatastoreService datastore = DatastoreServiceFactory.getAsyncDatastoreService();
    Transaction txn = datastore.beginTransaction().get();
    RuntimeException e = assertThrows(RuntimeException.class, () -> datastore.put(yar));
    assertThat(e).hasMessageThat().isEqualTo("boom");
    // no preput callback defined for this kind so no exception
    datastore.put(notYar).get();
    txn.commit();
    // make sure the entity whose preput callback blew up does not get written
    assertThat(datastore.get(ImmutableList.of(yar.getKey())).get()).isEmpty();
    // make sure the entity whose preput callback did not blow up does get written
    assertThat(datastore.get(ImmutableList.of(notYar.getKey())).get()).hasSize(1);
  }

  @Test
  public void testPrePut_FailureInCallback_Batch_Txn() throws Exception {
    setCallbacksConfig("yar.PrePut", HAS_CALLBACKS + ":explosivePrePut");
    doThrow(new RuntimeException("boom"))
        .when(mock)
        .explosivePrePut(eqContext(new PutContext(txnProvider, ImmutableList.of(yar, notYar))));
    AsyncDatastoreService datastore = DatastoreServiceFactory.getAsyncDatastoreService();
    Transaction txn = datastore.beginTransaction().get();
    RuntimeException e =
        assertThrows(RuntimeException.class, () -> datastore.put(ImmutableList.of(yar, notYar)));
    assertThat(e).hasMessageThat().isEqualTo("boom");
    txn.commit();
    // make sure neither entity was written
    assertThat(datastore.get(ImmutableList.of(yar.getKey(), notYar.getKey())).get()).isEmpty();
  }

  @Test
  public void testPostPut() throws Exception {
    setCallbacksConfig("yar.PostPut", HAS_CALLBACKS + ":postPut");
    AsyncDatastoreService datastore = DatastoreServiceFactory.getAsyncDatastoreService();
    Future<Key> result = datastore.put(yar);
    // We shouldn't execute any of the postput logic until we call get() on the
    // future so don't expect any calls until that point.
    verifyNoMoreInteractions(mock);
    result.get();
    verify(mock, times(1)).postPut(eqContext(new PutContext(txnProvider, ImmutableList.of(yar))));
    result.get();
    result = datastore.put(notYar);
    result.get();
    result.get();
    // make sure the data was actually written
    assertThat(datastore.get(ImmutableList.of(yar.getKey(), notYar.getKey())).get()).hasSize(2);
    verifyNoMoreInteractions(mock);
  }

  @Test
  public void testPostPut_CommitTxn() throws Exception {
    setCallbacksConfig("yar.PostPut", HAS_CALLBACKS + ":postPut");
    AsyncDatastoreService datastore = DatastoreServiceFactory.getAsyncDatastoreService();
    Transaction txn = datastore.beginTransaction().get();
    Future<Key> result = datastore.put(yar);
    result.get();
    result.get();
    result = datastore.put(yarChild);
    result.get();
    result.get();
    Future<Void> commitResult = txn.commitAsync();
    // We shouldn't execute any of the postput logic until we call get() on the
    // future returned by commitAsync so don't expect any calls until that point.
    verifyNoMoreInteractions(mock);
    commitResult.get();
    verify(mock, times(1))
        .postPut(eqContext(new PutContext(txnProvider, ImmutableList.of(yar, yarChild))));
    // make sure the data was actually written
    assertThat(datastore.get(ImmutableList.of(yar.getKey(), yarChild.getKey())).get()).hasSize(2);
  }

  @Test
  public void testPostPut_Batch_CommitTxn() throws Exception {
    setCallbacksConfig("yar.PostPut", HAS_CALLBACKS + ":postPut");
    AsyncDatastoreService datastore = DatastoreServiceFactory.getAsyncDatastoreService();
    Transaction txn = datastore.beginTransaction().get();
    Future<List<Key>> result = datastore.put(ImmutableList.of(yar, yarChild));
    result.get();
    result.get();
    Future<Void> commitResult = txn.commitAsync();
    // We shouldn't execute any of the postput logic until we call get() on the
    // future returned by commitAsync so don't expect any calls until that point.
    verifyNoMoreInteractions(mock);
    commitResult.get();
    verify(mock, times(1))
        .postPut(eqContext(new PutContext(txnProvider, ImmutableList.of(yar, yarChild))));
    // make sure the data was actually written
    assertThat(datastore.get(ImmutableList.of(yar.getKey(), yarChild.getKey())).get()).hasSize(2);
  }

  @Test
  public void testPostPut_RollbackTxn() throws Exception {
    setCallbacksConfig("yar.PostPut", HAS_CALLBACKS + ":postPut");
    AsyncDatastoreService datastore = DatastoreServiceFactory.getAsyncDatastoreService();
    Transaction txn = datastore.beginTransaction().get();
    Future<Key> result = datastore.put(yar);
    result.get();
    result.get();
    result = datastore.put(yarChild);
    result.get();
    result.get();
    Future<Void> rollbackResult = txn.rollbackAsync();
    // We shouldn't execute any postput callbacks because we're not committing.
    verifyNoMoreInteractions(mock);
    rollbackResult.get();
    // make sure the data was not actually written
    assertThat(datastore.get(ImmutableList.of(yar.getKey(), yarChild.getKey())).get()).isEmpty();
  }

  @Test
  public void testPostPut_Batch_RollbackTxn() throws Exception {
    setCallbacksConfig("yar.PostPut", HAS_CALLBACKS + ":postPut");
    AsyncDatastoreService datastore = DatastoreServiceFactory.getAsyncDatastoreService();
    Transaction txn = datastore.beginTransaction().get();
    Future<List<Key>> result = datastore.put(ImmutableList.of(yar, yarChild));
    result.get();
    result.get();
    Future<Void> rollbackResult = txn.rollbackAsync();
    // We shouldn't execute any postput callbacks because we're not committing.
    verifyNoMoreInteractions(mock);
    rollbackResult.get();
    // make sure the data was not actually written
    assertThat(datastore.get(ImmutableList.of(yar.getKey(), yarChild.getKey())).get()).isEmpty();
  }

  @Test
  public void testPostPut_FailureInDatastore() throws Exception {
    @SuppressWarnings("unchecked")
    ApiProxy.Delegate<ApiProxy.Environment> explosiveDelegate = mock(ApiProxy.Delegate.class);
    setCallbacksConfig("yar.PostPut", HAS_CALLBACKS + ":explosivePostPut");
    AsyncDatastoreService datastore = DatastoreServiceFactory.getAsyncDatastoreService();
    ApiProxy.Delegate<?> original = ApiProxy.getDelegate();
    Future<byte[]> explosiveFuture =
        immediateFailedFuture(new ApiProxy.ApplicationException(INTERNAL_ERROR.getValue()));
    when(explosiveDelegate.makeAsyncCall(any(), any(), any(), any(), any()))
        .thenReturn(explosiveFuture);
    ApiProxy.setDelegate(explosiveDelegate);
    try {
      // If the datastore throws an exception the postput logic should not run,
      // so we do not expect any postput calls
      // We fail the first time because the datastore throws an exception.
      ExecutionException e1 =
          assertThrows(ExecutionException.class, () -> datastore.put(yar).get());
      assertThat(e1).hasCauseThat().isInstanceOf(DatastoreFailureException.class);

      // We fail the second time because the datastore throws an exception.
      ExecutionException e2 =
          assertThrows(ExecutionException.class, () -> datastore.put(yar).get());
      assertThat(e2).hasCauseThat().isInstanceOf(DatastoreFailureException.class);
    } finally {
      ApiProxy.setDelegate(original);
    }
    // make sure the data was not actually written
    assertThat(datastore.get(ImmutableList.of(yar.getKey(), notYar.getKey())).get()).isEmpty();
  }

  @Test
  public void testPostPut_FailureInCallback() throws Exception {
    setCallbacksConfig("yar.PostPut", HAS_CALLBACKS + ":explosivePostPut");
    AsyncDatastoreService datastore = DatastoreServiceFactory.getAsyncDatastoreService();
    Future<Key> result1 = datastore.put(yar);
    // We shouldn't execute any of the postput logic until we call get() on the
    // future so don't expect any calls until that point.
    doThrow(new RuntimeException("boom"))
        .when(mock)
        .explosivePostPut(eqContext(new PutContext(txnProvider, ImmutableList.of(yar))));
    // We fail the first time because the callback throws an exception.
    ExecutionException e1 = assertThrows(ExecutionException.class, result1::get);
    assertThat(e1).hasCauseThat().hasMessageThat().isEqualTo("boom");
    verify(mock, times(1))
        .explosivePostPut(eqContext(new PutContext(txnProvider, ImmutableList.of(yar))));
    // We fail the second time because we cache the exception result.
    ExecutionException e2 = assertThrows(ExecutionException.class, result1::get);
    assertThat(e2).hasCauseThat().hasMessageThat().isEqualTo("boom");
    Future<Key> result2 = datastore.put(notYar);
    result2.get();
    result2.get();
    verifyNoMoreInteractions(mock);
    // make sure the data was actually written
    assertThat(datastore.get(ImmutableList.of(yar.getKey(), notYar.getKey())).get()).hasSize(2);
  }

  @Test
  public void testPostPut_FailureInCallback_Txn() throws Exception {
    setCallbacksConfig("yar.PostPut", HAS_CALLBACKS + ":explosivePostPut");
    AsyncDatastoreService datastore = DatastoreServiceFactory.getAsyncDatastoreService();
    Transaction txn = datastore.beginTransaction().get();
    @SuppressWarnings("unused") // go/futurereturn-lsc
    Future<?> possiblyIgnoredError = datastore.put(yar);
    @SuppressWarnings("unused") // go/futurereturn-lsc
    Future<?> possiblyIgnoredError1 = datastore.put(yarChild);
    Future<Void> commitResult = txn.commitAsync();
    // We shouldn't execute any of the postput logic until we call get() on the
    // commit future so don't expect any calls until that point.
    doThrow(new RuntimeException("boom"))
        .when(mock)
        .explosivePostPut(eqContext(new PutContext(txnProvider, ImmutableList.of(yar, yarChild))));
    // We fail the first time because the callback throws an exception.
    ExecutionException e1 = assertThrows(ExecutionException.class, commitResult::get);
    assertThat(e1).hasCauseThat().hasMessageThat().isEqualTo("boom");
    verify(mock, times(1))
        .explosivePostPut(eqContext(new PutContext(txnProvider, ImmutableList.of(yar, yarChild))));
    // We fail the second time because we cache the exception result.
    ExecutionException e2 = assertThrows(ExecutionException.class, commitResult::get);
    assertThat(e2).hasCauseThat().hasMessageThat().isEqualTo("boom");
    verifyNoMoreInteractions(mock);
    // make sure the data was actually written
    assertThat(datastore.get(ImmutableList.of(yar.getKey(), yarChild.getKey())).get()).hasSize(2);
  }

  @Test
  public void testPostPut_FailureInCallback_Batch_Txn() throws Exception {
    setCallbacksConfig("yar.PostPut", HAS_CALLBACKS + ":explosivePostPut");
    AsyncDatastoreService datastore = DatastoreServiceFactory.getAsyncDatastoreService();
    Transaction txn = datastore.beginTransaction().get();
    @SuppressWarnings("unused") // go/futurereturn-lsc
    Future<?> possiblyIgnoredError = datastore.put(ImmutableList.of(yar, yarChild));
    Future<Void> commitResult = txn.commitAsync();
    // We shouldn't execute any of the postput logic until we call get() on the
    // commit future so don't expect any calls until that point.
    doThrow(new RuntimeException("boom"))
        .when(mock)
        .explosivePostPut(eqContext(new PutContext(txnProvider, ImmutableList.of(yar, yarChild))));
    // We fail the first time because the callback throws an exception.
    ExecutionException e1 = assertThrows(ExecutionException.class, commitResult::get);
    assertThat(e1).hasCauseThat().hasMessageThat().isEqualTo("boom");
    verify(mock, times(1))
        .explosivePostPut(eqContext(new PutContext(txnProvider, ImmutableList.of(yar, yarChild))));
    // We fail the second time because we cache the exception result.
    ExecutionException e2 = assertThrows(ExecutionException.class, commitResult::get);
    assertThat(e2).hasCauseThat().hasMessageThat().isEqualTo("boom");
    // make sure the data was actually written
    assertThat(datastore.get(ImmutableList.of(yar.getKey(), yarChild.getKey())).get()).hasSize(2);
    verifyNoMoreInteractions(mock);
  }

  @Test
  public void testPreDelete() throws Exception {
    setCallbacksConfig("yar.PreDelete", HAS_CALLBACKS + ":preDelete");
    AsyncDatastoreService datastore = DatastoreServiceFactory.getAsyncDatastoreService();
    // Make sure we have something to delete
    datastore.put(ImmutableList.of(yar, notYar)).get();
    Future<Void> result = datastore.delete(yar.getKey());
    result.get();
    result.get();
    result = datastore.delete(notYar.getKey());
    result.get();
    result.get();
    verify(mock, times(1))
        .preDelete(eqContext(new DeleteContext(txnProvider, ImmutableList.of(yar.getKey()))));
    // make sure the data was actually deleted
    assertThat(datastore.get(ImmutableList.of(yar.getKey(), notYar.getKey())).get()).isEmpty();
  }

  @Test
  public void testPreDelete_CommitTxn() throws Exception {
    setCallbacksConfig("yar.PreDelete", HAS_CALLBACKS + ":preDelete");
    AsyncDatastoreService datastore = DatastoreServiceFactory.getAsyncDatastoreService();
    datastore.put(ImmutableList.of(yar, yarChild)).get();
    Transaction txn = datastore.beginTransaction().get();
    Future<Void> result = datastore.delete(yar.getKey());
    result.get();
    result.get();
    result = datastore.delete(yarChild.getKey());
    result.get();
    result.get();
    txn.commit();
    verify(mock, times(1))
        .preDelete(eqContext(new DeleteContext(txnProvider, ImmutableList.of(yar.getKey()))));

    // make sure the data was actually deleted
    assertThat(datastore.get(ImmutableList.of(yar.getKey(), yarChild.getKey())).get()).isEmpty();
  }

  @Test
  public void testPreDelete_Batch_CommitTxn() throws Exception {
    setCallbacksConfig("yar.PreDelete", HAS_CALLBACKS + ":preDelete");
    AsyncDatastoreService datastore = DatastoreServiceFactory.getAsyncDatastoreService();
    datastore.put(ImmutableList.of(yar, yarChild)).get();
    Transaction txn = datastore.beginTransaction().get();
    Future<Void> result = datastore.delete(ImmutableList.of(yar.getKey(), yarChild.getKey()));
    result.get();
    result.get();
    verify(mock, times(1))
        .preDelete(
            eqContext(
                new DeleteContext(txnProvider, ImmutableList.of(yar.getKey(), yarChild.getKey()))));
    txn.commit();
    // make sure the data was actually deleted
    assertThat(datastore.get(ImmutableList.of(yar.getKey(), yarChild.getKey())).get()).isEmpty();
  }

  @Test
  public void testPreDelete_RollbackTxn() throws Exception {
    setCallbacksConfig("yar.PreDelete", HAS_CALLBACKS + ":preDelete");
    AsyncDatastoreService datastore = DatastoreServiceFactory.getAsyncDatastoreService();
    datastore.put(ImmutableList.of(yar, yarChild)).get();
    Transaction txn = datastore.beginTransaction().get();
    Future<Void> result = datastore.delete(yar.getKey());
    result.get();
    result.get();
    result = datastore.delete(yarChild.getKey());
    result.get();
    result.get();
    txn.rollback();
    // make sure the data was not actually deleted
    assertThat(datastore.get(ImmutableList.of(yar.getKey(), yarChild.getKey())).get()).hasSize(2);
    verify(mock, times(1))
        .preDelete(eqContext(new DeleteContext(txnProvider, ImmutableList.of(yar.getKey()))));
  }

  @Test
  public void testPreDelete_batch_rollbackTxn() throws Exception {
    setCallbacksConfig("yar.PreDelete", HAS_CALLBACKS + ":preDelete");
    AsyncDatastoreService datastore = DatastoreServiceFactory.getAsyncDatastoreService();
    datastore.put(ImmutableList.of(yar, yarChild)).get();
    Transaction txn = datastore.beginTransaction().get();
    Future<Void> result = datastore.delete(ImmutableList.of(yar.getKey(), yarChild.getKey()));
    result.get();
    result.get();
    verify(mock, times(1))
        .preDelete(
            eqContext(
                new DeleteContext(txnProvider, ImmutableList.of(yar.getKey(), yarChild.getKey()))));
    txn.rollback();
    // make sure the data was not actually deleted
    assertThat(datastore.get(ImmutableList.of(yar.getKey(), yarChild.getKey())).get()).hasSize(2);
  }

  @Test
  public void testPreDelete_FailureInCallback() throws Exception {
    setCallbacksConfig("yar.PreDelete", HAS_CALLBACKS + ":explosivePreDelete");
    // Make sure we have something to delete
    AsyncDatastoreService datastore = DatastoreServiceFactory.getAsyncDatastoreService();
    datastore.put(ImmutableList.of(yar, notYar)).get();
    doThrow(new RuntimeException("boom"))
        .when(mock)
        .explosivePreDelete(
            eqContext(new DeleteContext(txnProvider, ImmutableList.of(yar.getKey()))));

    RuntimeException e = assertThrows(RuntimeException.class, () -> datastore.delete(yar.getKey()));
    assertThat(e).hasMessageThat().isEqualTo("boom");
    verify(mock, times(1))
        .explosivePreDelete(
            eqContext(new DeleteContext(txnProvider, ImmutableList.of(yar.getKey()))));
    // no predelete callback defined for this kind so no exception
    datastore.delete(notYar.getKey()).get();
    verifyNoMoreInteractions(mock);
    // make sure the entity whose predelete callback blew up does not get deleted
    assertThat(datastore.get(ImmutableList.of(yar.getKey())).get()).hasSize(1);
    // make sure the entity whose predeleted callback did not blow up does get deleted
    assertThat(datastore.get(ImmutableList.of(notYar.getKey())).get()).isEmpty();
  }

  @Test
  public void testPreDelete_FailureInCallback_Txn() throws Exception {
    setCallbacksConfig("yar.PreDelete", HAS_CALLBACKS + ":explosivePreDelete");
    AsyncDatastoreService datastore = DatastoreServiceFactory.getAsyncDatastoreService();
    datastore.put(ImmutableList.of(yar, notYar)).get();
    doThrow(new RuntimeException("boom"))
        .when(mock)
        .explosivePreDelete(
            eqContext(new DeleteContext(txnProvider, ImmutableList.of(yar.getKey()))));
    Transaction txn = datastore.beginTransaction().get();
    RuntimeException e = assertThrows(RuntimeException.class, () -> datastore.delete(yar.getKey()));
    assertThat(e).hasMessageThat().isEqualTo("boom");
    // no predelete callback defined for this kind so no exception
    datastore.delete(notYar.getKey()).get();
    verify(mock).explosivePreDelete(any());
    txn.commit();
    // make sure the entity whose predelete callback blew up does not get deleted
    assertThat(datastore.get(ImmutableList.of(yar.getKey())).get()).hasSize(1);
    // make sure the entity whose predeleted callback did not blow up does get deleted
    assertThat(datastore.get(ImmutableList.of(notYar.getKey())).get()).isEmpty();
    verifyNoMoreInteractions(mock);
  }

  @Test
  public void testPreDelete_FailureInCallback_Batch_Txn() throws Exception {
    setCallbacksConfig("yar.PreDelete", HAS_CALLBACKS + ":explosivePreDelete");
    AsyncDatastoreService datastore = DatastoreServiceFactory.getAsyncDatastoreService();
    datastore.put(ImmutableList.of(yar, notYar)).get();
    doThrow(new RuntimeException("boom"))
        .when(mock)
        .explosivePreDelete(
            eqContext(
                new DeleteContext(txnProvider, ImmutableList.of(yar.getKey(), notYar.getKey()))));
    Transaction txn = datastore.beginTransaction().get();
    RuntimeException e =
        assertThrows(
            RuntimeException.class,
            () -> datastore.delete(ImmutableList.of(yar.getKey(), notYar.getKey())));
    assertThat(e).hasMessageThat().isEqualTo("boom");
    verify(mock, times(1))
        .explosivePreDelete(
            eqContext(
                new DeleteContext(txnProvider, ImmutableList.of(yar.getKey(), notYar.getKey()))));

    txn.commit();
    // make sure neither entity was deleted
    assertThat(datastore.get(ImmutableList.of(yar.getKey(), notYar.getKey())).get()).hasSize(2);
  }

  @Test
  public void testPostDelete() throws Exception {
    setCallbacksConfig("yar.PostDelete", HAS_CALLBACKS + ":postDelete");
    AsyncDatastoreService datastore = DatastoreServiceFactory.getAsyncDatastoreService();
    // Make sure we have something to delete
    datastore.put(ImmutableList.of(yar, notYar)).get();
    Future<Void> result = datastore.delete(yar.getKey());
    // We shouldn't execute any of the postdelete logic until we call get() on the
    // future so don't expect any calls until that point.
    verifyNoMoreInteractions(mock);
    result.get();
    verify(mock, times(1))
        .postDelete(eqContext(new DeleteContext(txnProvider, ImmutableList.of(yar.getKey()))));
    result.get();
    result = datastore.delete(notYar.getKey());
    result.get();
    result.get();
    verifyNoMoreInteractions(mock);
    // make sure the data was actually deleted
    assertThat(datastore.get(ImmutableList.of(yar.getKey(), notYar.getKey())).get()).isEmpty();
  }

  @Test
  public void testPostDelete_CommitTxn() throws Exception {
    setCallbacksConfig("yar.PostDelete", HAS_CALLBACKS + ":postDelete");
    AsyncDatastoreService datastore = DatastoreServiceFactory.getAsyncDatastoreService();
    datastore.put(ImmutableList.of(yar, yarChild)).get();
    Transaction txn = datastore.beginTransaction().get();
    Future<Void> result = datastore.delete(yar.getKey());
    result.get();
    result.get();
    result = datastore.delete(yarChild.getKey());
    result.get();
    result.get();
    Future<Void> commitResult = txn.commitAsync();
    // We shouldn't execute any of the postdelete logic until we call get() on the
    // future returned by commitAsync so don't expect any calls until that point.
    verifyNoMoreInteractions(mock);
    commitResult.get();
    verify(mock, times(1))
        .postDelete(
            eqContext(
                new DeleteContext(txnProvider, ImmutableList.of(yar.getKey(), yarChild.getKey()))));
    // make sure the data was actually deleted
    assertThat(datastore.get(ImmutableList.of(yar.getKey(), yarChild.getKey())).get()).isEmpty();
  }

  @Test
  public void testPostDelete_Batch_CommitTxn() throws Exception {
    setCallbacksConfig("yar.PostDelete", HAS_CALLBACKS + ":postDelete");
    AsyncDatastoreService datastore = DatastoreServiceFactory.getAsyncDatastoreService();
    datastore.put(ImmutableList.of(yar, yarChild)).get();
    Transaction txn = datastore.beginTransaction().get();
    Future<Void> result = datastore.delete(ImmutableList.of(yar.getKey(), yarChild.getKey()));
    result.get();
    result.get();
    Future<Void> commitResult = txn.commitAsync();
    // We shouldn't execute any of the postdelete logic until we call get() on the
    // future returned by commitAsync so don't expect any calls until that point.
    verifyNoMoreInteractions(mock);
    commitResult.get();
    verify(mock, times(1))
        .postDelete(
            eqContext(
                new DeleteContext(txnProvider, ImmutableList.of(yar.getKey(), yarChild.getKey()))));

    // make sure the data was actually deleted
    assertThat(datastore.get(ImmutableList.of(yar.getKey(), yarChild.getKey())).get()).isEmpty();
  }

  @Test
  public void testPostDelete_RollbackTxn() throws Exception {
    setCallbacksConfig("yar.PostDelete", HAS_CALLBACKS + ":postDelete");
    AsyncDatastoreService datastore = DatastoreServiceFactory.getAsyncDatastoreService();
    datastore.put(ImmutableList.of(yar, yarChild)).get();
    Transaction txn = datastore.beginTransaction().get();
    Future<Void> result = datastore.delete(yar.getKey());
    result.get();
    result.get();
    result = datastore.delete(yarChild.getKey());
    result.get();
    result.get();
    Future<Void> rollbackResult = txn.rollbackAsync();
    // We shouldn't execute any postdelete callbacks because we're not committing.
    verifyNoMoreInteractions(mock);
    rollbackResult.get();
    // make sure the data was not actually deleted
    assertThat(datastore.get(ImmutableList.of(yar.getKey(), yarChild.getKey())).get()).hasSize(2);
  }

  @Test
  public void testPostDelete_Batch_RollbackTxn() throws Exception {
    setCallbacksConfig("yar.PostDelete", HAS_CALLBACKS + ":postDelete");
    AsyncDatastoreService datastore = DatastoreServiceFactory.getAsyncDatastoreService();
    datastore.put(ImmutableList.of(yar, yarChild)).get();
    Transaction txn = datastore.beginTransaction().get();
    Future<Void> result = datastore.delete(ImmutableList.of(yar.getKey(), yarChild.getKey()));
    result.get();
    result.get();
    Future<Void> rollbackResult = txn.rollbackAsync();
    // We shouldn't execute any postdelete callbacks because we're not committing.
    verifyNoMoreInteractions(mock);
    rollbackResult.get();

    // make sure the data was not actually deleted
    assertThat(datastore.get(ImmutableList.of(yar.getKey(), yarChild.getKey())).get()).hasSize(2);
  }

  @Test
  public void testPostDelete_FailureInDatastore() throws Exception {
    setCallbacksConfig("yar.PostDelete", HAS_CALLBACKS + ":explosivePostDelete");
    AsyncDatastoreService datastore = DatastoreServiceFactory.getAsyncDatastoreService();
    datastore.put(ImmutableList.of(yar, notYar)).get();
    @SuppressWarnings("unchecked")
    ApiProxy.Delegate<ApiProxy.Environment> explosiveDelegate = mock(ApiProxy.Delegate.class);
    ApiProxy.Delegate<?> original = ApiProxy.getDelegate();
    Future<byte[]> explosiveFuture =
        immediateFailedFuture(new ApiProxy.ApplicationException(INTERNAL_ERROR.getValue()));
    when(explosiveDelegate.makeAsyncCall(any(), any(), any(), any(), any()))
        .thenReturn(explosiveFuture);
    ApiProxy.setDelegate(explosiveDelegate);
    try {
      // If the datastore throws an exception the postdelete logic should not run,
      // so we do not expect any postdelete calls
      // We fail the first time because the datastore throws an exception.
      ExecutionException e1 =
          assertThrows(
              ExecutionException.class,
              () -> datastore.delete(yar.getKey(), notYar.getKey()).get());
      assertThat(e1).hasCauseThat().isInstanceOf(DatastoreFailureException.class);
      // We fail the second time because the datastore throws an exception.
      ExecutionException e2 =
          assertThrows(
              ExecutionException.class,
              () -> datastore.delete(yar.getKey(), notYar.getKey()).get());
      assertThat(e2).hasCauseThat().isInstanceOf(DatastoreFailureException.class);
    } finally {
      ApiProxy.setDelegate(original);
    }
    // make sure the data was not actually deleted
    assertThat(datastore.get(ImmutableList.of(yar.getKey(), notYar.getKey())).get()).hasSize(2);
  }

  @Test
  public void testPostDelete_FailureInCallback() throws Exception {
    setCallbacksConfig("yar.PostDelete", HAS_CALLBACKS + ":explosivePostDelete");
    AsyncDatastoreService datastore = DatastoreServiceFactory.getAsyncDatastoreService();
    // Make sure we have something to delete
    datastore.put(ImmutableList.of(yar, notYar)).get();
    Future<Void> result1 = datastore.delete(yar.getKey());
    // We shouldn't execute any of the postdelete logic until we call get() on the
    // future so don't expect any calls until that point.
    verifyNoMoreInteractions(mock);
    doThrow(new RuntimeException("boom"))
        .when(mock)
        .explosivePostDelete(
            eqContext(new DeleteContext(txnProvider, ImmutableList.of(yar.getKey()))));
    // We fail the first time because the callback throws an exception.
    ExecutionException e1 = assertThrows(ExecutionException.class, result1::get);
    assertThat(e1).hasCauseThat().hasMessageThat().isEqualTo("boom");
    verify(mock, times(1))
        .explosivePostDelete(
            eqContext(new DeleteContext(txnProvider, ImmutableList.of(yar.getKey()))));
    // We fail the second time because we cache the exception result.
    ExecutionException e2 = assertThrows(ExecutionException.class, result1::get);
    assertThat(e2).hasCauseThat().hasMessageThat().isEqualTo("boom");
    Future<Void> result2 = datastore.delete(notYar.getKey());
    result2.get();
    result2.get();
    // make sure the data was actually deleted
    assertThat(datastore.get(ImmutableList.of(yar.getKey(), notYar.getKey())).get()).isEmpty();
  }

  @Test
  public void testPostDelete_FailureInCallback_Txn() throws Exception {
    setCallbacksConfig("yar.PostDelete", HAS_CALLBACKS + ":explosivePostDelete");
    AsyncDatastoreService datastore = DatastoreServiceFactory.getAsyncDatastoreService();
    datastore.put(ImmutableList.of(yar, yarChild)).get();
    Transaction txn = datastore.beginTransaction().get();
    @SuppressWarnings("unused") // go/futurereturn-lsc
    Future<?> possiblyIgnoredError = datastore.delete(yar.getKey());
    @SuppressWarnings("unused") // go/futurereturn-lsc
    Future<?> possiblyIgnoredError1 = datastore.delete(yarChild.getKey());
    Future<Void> commitResult = txn.commitAsync();
    // We shouldn't execute any of the postdelete logic until we call get() on the
    // commit future so don't expect any calls until that point.
    verifyNoMoreInteractions(mock);
    doThrow(new RuntimeException("boom"))
        .when(mock)
        .explosivePostDelete(
            eqContext(
                new DeleteContext(txnProvider, ImmutableList.of(yar.getKey(), yarChild.getKey()))));
    // We fail the first time because the callback throws an exception.
    ExecutionException e1 = assertThrows(ExecutionException.class, commitResult::get);
    assertThat(e1).hasCauseThat().hasMessageThat().isEqualTo("boom");
    verify(mock, times(1))
        .explosivePostDelete(
            eqContext(
                new DeleteContext(txnProvider, ImmutableList.of(yar.getKey(), yarChild.getKey()))));
    // We fail the second time because we cache the exception result.
    ExecutionException e2 = assertThrows(ExecutionException.class, commitResult::get);
    assertThat(e2).hasCauseThat().hasMessageThat().isEqualTo("boom");
    // make sure the data was actually deleted
    assertThat(datastore.get(ImmutableList.of(yar.getKey(), yarChild.getKey())).get()).isEmpty();
  }

  @Test
  public void testPostDelete_FailureInCallback_Batch_Txn() throws Exception {
    setCallbacksConfig("yar.PostDelete", HAS_CALLBACKS + ":explosivePostDelete");
    AsyncDatastoreService datastore = DatastoreServiceFactory.getAsyncDatastoreService();
    datastore.put(ImmutableList.of(yar, yarChild)).get();
    Transaction txn = datastore.beginTransaction().get();
    @SuppressWarnings("unused") // go/futurereturn-lsc
    Future<?> possiblyIgnoredError =
        datastore.delete(ImmutableList.of(yar.getKey(), yarChild.getKey()));
    Future<Void> commitResult = txn.commitAsync();
    // We shouldn't execute any of the postdelete logic until we call get() on the
    // commit future so don't expect any calls until that point.
    verifyNoMoreInteractions(mock);
    doThrow(new RuntimeException("boom"))
        .when(mock)
        .explosivePostDelete(
            eqContext(
                new DeleteContext(txnProvider, ImmutableList.of(yar.getKey(), yarChild.getKey()))));
    // We fail the first time because the callback throws an exception.
    ExecutionException e1 = assertThrows(ExecutionException.class, commitResult::get);
    assertThat(e1).hasCauseThat().hasMessageThat().isEqualTo("boom");
    verify(mock).explosivePostDelete(any());
    // We fail the second time because we cache the exception result.
    ExecutionException e2 = assertThrows(ExecutionException.class, commitResult::get);
    assertThat(e2).hasCauseThat().hasMessageThat().isEqualTo("boom");
    verifyNoMoreInteractions(mock);
    // make sure the data was actually deleted
    assertThat(datastore.get(ImmutableList.of(yar.getKey(), yarChild.getKey())).get()).isEmpty();
  }

  @Test
  public void testPreGet() throws Exception {
    setCallbacksConfig("yar.PreGet", HAS_CALLBACKS + ":preGet");
    AsyncDatastoreService datastore = DatastoreServiceFactory.getAsyncDatastoreService();
    datastore.put(ImmutableList.of(yar, notYar)).get();
    // preGet callback runs as soon as we execute the fetch
    Future<Entity> result = datastore.get(yar.getKey());
    verify(mock, times(1))
        .preGet(
            eqContext(
                new PreGetContext(txnProvider, ImmutableList.of(yar.getKey()), ImmutableMap.of())));
    assertThat(result.get()).isEqualTo(yar);
    assertThat(result.get()).isEqualTo(yar);
    result = datastore.get(notYar.getKey());
    assertThat(result.get()).isEqualTo(notYar);
    assertThat(result.get()).isEqualTo(notYar);
  }

  @Test
  public void testPreGet_CommitTxn() throws Exception {
    setCallbacksConfig("yar.PreGet", HAS_CALLBACKS + ":preGet");
    AsyncDatastoreService datastore = DatastoreServiceFactory.getAsyncDatastoreService();
    datastore.put(ImmutableList.of(yar, yarChild)).get();
    // preGet callback runs as soon as we execute the fetch
    Transaction txn = datastore.beginTransaction().get();
    Future<Entity> result = datastore.get(yar.getKey());
    verify(mock, times(1))
        .preGet(
            eqContext(
                new PreGetContext(txnProvider, ImmutableList.of(yar.getKey()), ImmutableMap.of())));
    assertThat(result.get()).isEqualTo(yar);
    assertThat(result.get()).isEqualTo(yar);
    result = datastore.get(yarChild.getKey());
    assertThat(result.get()).isEqualTo(yarChild);
    assertThat(result.get()).isEqualTo(yarChild);
    txn.commit();
  }

  @Test
  public void testPreGet_Batch_CommitTxn() throws Exception {
    setCallbacksConfig("yar.PreGet", HAS_CALLBACKS + ":preGet");
    AsyncDatastoreService datastore = DatastoreServiceFactory.getAsyncDatastoreService();
    datastore.put(ImmutableList.of(yar, yarChild)).get();
    // preGet callback runs as soon as we execute the fetch
    Transaction txn = datastore.beginTransaction().get();
    Future<Map<Key, Entity>> result =
        datastore.get(ImmutableList.of(yar.getKey(), yarChild.getKey()));
    verify(mock, times(1))
        .preGet(
            eqContext(
                new PreGetContext(
                    txnProvider,
                    ImmutableList.of(yar.getKey(), yarChild.getKey()),
                    ImmutableMap.of())));
    assertThat(result.get()).containsExactly(yar.getKey(), yar, yarChild.getKey(), yarChild);
    assertThat(result.get()).containsExactly(yar.getKey(), yar, yarChild.getKey(), yarChild);
    txn.commit();
  }

  @Test
  public void testPreGet_RollbackTxn() throws Exception {
    setCallbacksConfig("yar.PreGet", HAS_CALLBACKS + ":preGet");
    AsyncDatastoreService datastore = DatastoreServiceFactory.getAsyncDatastoreService();
    datastore.put(ImmutableList.of(yar, yarChild)).get();
    Transaction txn = datastore.beginTransaction().get();
    // preGet callback runs as soon as we execute the fetch
    Future<Entity> result = datastore.get(yar.getKey());
    assertThat(result.get()).isEqualTo(yar);
    verify(mock, times(1))
        .preGet(
            eqContext(
                new PreGetContext(txnProvider, ImmutableList.of(yar.getKey()), ImmutableMap.of())));
    assertThat(result.get()).isEqualTo(yar);
    verifyNoMoreInteractions(mock);
    result = datastore.get(yarChild.getKey());
    assertThat(result.get()).isEqualTo(yarChild);
    assertThat(result.get()).isEqualTo(yarChild);
    txn.rollback();
  }

  @Test
  public void testPreGet_Batch_RollbackTxn() throws Exception {
    setCallbacksConfig("yar.PreGet", HAS_CALLBACKS + ":preGet");
    AsyncDatastoreService datastore = DatastoreServiceFactory.getAsyncDatastoreService();
    datastore.put(ImmutableList.of(yar, yarChild)).get();
    Transaction txn = datastore.beginTransaction().get();
    // preGet callback runs as soon as we execute the fetch
    Future<Map<Key, Entity>> result =
        datastore.get(ImmutableList.of(yar.getKey(), yarChild.getKey()));
    verify(mock, times(1))
        .preGet(
            eqContext(
                new PreGetContext(
                    txnProvider,
                    ImmutableList.of(yar.getKey(), yarChild.getKey()),
                    ImmutableMap.of())));
    assertThat(result.get()).containsExactly(yar.getKey(), yar, yarChild.getKey(), yarChild);
    assertThat(result.get()).containsExactly(yar.getKey(), yar, yarChild.getKey(), yarChild);
    txn.rollback();
  }

  @Test
  public void testPreGet_FailureInCallback() throws Exception {
    setCallbacksConfig("yar.PreGet", HAS_CALLBACKS + ":explosivePreGet");
    AsyncDatastoreService datastore = DatastoreServiceFactory.getAsyncDatastoreService();
    datastore.put(ImmutableList.of(yar, notYar)).get();
    // preGet callback runs as soon as we execute the fetch
    doThrow(new RuntimeException("boom"))
        .when(mock)
        .explosivePreGet(
            eqContext(
                new PreGetContext(txnProvider, ImmutableList.of(yar.getKey()), ImmutableMap.of())));
    RuntimeException e = assertThrows(RuntimeException.class, () -> datastore.get(yar.getKey()));
    assertThat(e).hasMessageThat().isEqualTo("boom");
    // no preGet callback defined for this kind so no exception
    assertThat(datastore.get(notYar.getKey()).get()).isEqualTo(notYar);
  }

  @Test
  public void testPreGet_FailureInCallback_Txn() throws Exception {
    setCallbacksConfig("yar.PreGet", HAS_CALLBACKS + ":explosivePreGet");
    AsyncDatastoreService datastore = DatastoreServiceFactory.getAsyncDatastoreService();
    datastore.put(ImmutableList.of(yar, notYar)).get();
    Transaction txn = datastore.beginTransaction().get();
    // preGet callback runs as soon as we execute the fetch
    doThrow(new RuntimeException("boom"))
        .when(mock)
        .explosivePreGet(
            eqContext(
                new PreGetContext(txnProvider, ImmutableList.of(yar.getKey()), ImmutableMap.of())));
    RuntimeException e = assertThrows(RuntimeException.class, () -> datastore.get(yar.getKey()));
    assertThat(e).hasMessageThat().isEqualTo("boom");
    // no preGet callback defined for this kind so no exception
    assertThat(datastore.get(notYar.getKey()).get()).isEqualTo(notYar);
    txn.commit();
  }

  @Test
  public void testPreGet_FailureInCallback_Batch_Txn() throws Exception {
    setCallbacksConfig("yar.PreGet", HAS_CALLBACKS + ":explosivePreGet");
    AsyncDatastoreService datastore = DatastoreServiceFactory.getAsyncDatastoreService();
    datastore.put(ImmutableList.of(yar, notYar)).get();
    Transaction txn = datastore.beginTransaction().get();
    // preGet callback runs as soon as we execute the fetch
    doThrow(new RuntimeException("boom"))
        .when(mock)
        .explosivePreGet(
            eqContext(
                new PreGetContext(
                    txnProvider,
                    ImmutableList.of(yar.getKey(), notYar.getKey()),
                    ImmutableMap.of())));
    RuntimeException e =
        assertThrows(
            RuntimeException.class,
            () -> datastore.get(ImmutableList.of(yar.getKey(), notYar.getKey())));
    assertThat(e).hasMessageThat().isEqualTo("boom");
    txn.commit();
  }

  private void testPreGet_PopulateCbOnlyEntityInCallback(Key cbOnlyEntityKey, List<Key> keyList)
      throws IOException, ExecutionException, InterruptedException {
    // fetch the cb only entity result to prove that we get an exception if we try to retrieve
    // it from the datastore
    assertThrows(
        EntityNotFoundException.class,
        () -> DatastoreServiceFactory.getDatastoreService().get(cbOnlyEntityKey));
    // clear out the callbacks so we can reset the config
    DatastoreServiceConfig.CALLBACKS = null;
    setCallbacksConfig(".PreGet", HAS_CALLBACKS + ":preGet");
    List<Key> keyListCopy = Lists.newArrayList(keyList);
    AsyncDatastoreService datastore = DatastoreServiceFactory.getAsyncDatastoreService();
    datastore.put(ImmutableList.of(yar)).get();
    // preGet callback runs as soon as we execute the fetch
    PreGetContext context = new PreGetContext(txnProvider, keyList, ImmutableMap.of());
    doAnswer(checkContext(context)).when(mock).preGet(any());
    Future<Map<Key, Entity>> result = datastore.get(keyList);
    assertThat(result.get().get(yar.getKey())).isEqualTo(yar);
    Entity cbOnlyEntity = result.get().get(cbOnlyEntityKey);
    assertThat(cbOnlyEntity.getKey()).isEqualTo(cbOnlyEntityKey);
    assertThat(cbOnlyEntity.getProperty("cb_property_only")).isEqualTo("cb_entity1");
    assertThat(result.get().get(yar.getKey())).isEqualTo(yar);
    cbOnlyEntity = result.get().get(cbOnlyEntityKey);
    assertThat(cbOnlyEntity.getKey()).isEqualTo(cbOnlyEntityKey);
    // Make sure we don't mutate the input list.
    assertThat(keyList).isEqualTo(keyListCopy);
    verify(mock, times(2)).preGet(any());
  }

  @Test
  public void testPreGet_PopulateCbOnlyEntityInCallback_ImmutableInput() throws Exception {
    Key cbOnlyEntityKey = KeyFactory.createKey("cbOnlyEntity", 24);
    testPreGet_PopulateCbOnlyEntityInCallback(
        cbOnlyEntityKey, ImmutableList.of(yar.getKey(), cbOnlyEntityKey));
  }

  @Test
  public void testPreGet_PopulateCbOnlyEntityInCallback_MutableInput() throws Exception {
    Key cbOnlyEntityKey = KeyFactory.createKey("cbOnlyEntity", 24);
    List<Key> keyList = Lists.newArrayList(yar.getKey(), cbOnlyEntityKey);
    testPreGet_PopulateCbOnlyEntityInCallback(cbOnlyEntityKey, keyList);
  }

  @Test
  public void testPreGet_PopulateCbOnlyEntityInCallback_NoDatastoreEntities() throws Exception {
    setCallbacksConfig(".PreGet", HAS_CALLBACKS + ":preGet");
    AsyncDatastoreService datastore = DatastoreServiceFactory.getAsyncDatastoreService();
    Key cbOnlyEntityKey = KeyFactory.createKey("cbOnlyEntity", 24);
    // preGet callback runs as soon as we execute the fetch
    PreGetContext context =
        new PreGetContext(txnProvider, ImmutableList.of(cbOnlyEntityKey), ImmutableMap.of());
    doAnswer(checkContext(context)).when(mock).preGet(any());
    Future<Entity> result = datastore.get(cbOnlyEntityKey);
    verify(mock).preGet(any());
    assertThat(result.get().getKey()).isEqualTo(cbOnlyEntityKey);
    assertThat(result.get().getProperty("cb_property_only")).isEqualTo("cb_entity1");
    assertThat(result.get().getKey()).isEqualTo(cbOnlyEntityKey);
  }

  @Test
  public void testPreGet_PopulateAllCbOnlyEntitiesInCallback() throws Exception {
    setCallbacksConfig(".PreGet", HAS_CALLBACKS + ":preGet");
    AsyncDatastoreService datastore = DatastoreServiceFactory.getAsyncDatastoreService();
    Key cbOnlyEntityKey1 = KeyFactory.createKey("cbOnlyEntity1", 24);
    Key cbOnlyEntityKey2 = KeyFactory.createKey("cbOnlyEntity2", 24);
    // preGet callback runs as soon as we execute the fetch
    PreGetContext context =
        new PreGetContext(
            txnProvider, ImmutableList.of(cbOnlyEntityKey1, cbOnlyEntityKey2), ImmutableMap.of());
    doAnswer(checkContext(context)).when(mock).preGet(any());
    Future<Map<Key, Entity>> resultFuture =
        datastore.get(ImmutableList.of(cbOnlyEntityKey1, cbOnlyEntityKey2));
    Map<Key, Entity> result = resultFuture.get();
    assertThat(result.get(cbOnlyEntityKey1).getKey()).isEqualTo(cbOnlyEntityKey1);
    assertThat(result.get(cbOnlyEntityKey1).getKey()).isEqualTo(cbOnlyEntityKey1);
    assertThat(result.get(cbOnlyEntityKey2).getKey()).isEqualTo(cbOnlyEntityKey2);
    assertThat(result.get(cbOnlyEntityKey2).getKey()).isEqualTo(cbOnlyEntityKey2);
    assertThat(result).hasSize(2);
    verify(mock, times(2)).preGet(any());
  }

  @Test
  public void testPreGet_PopulateDatastoreBackedEntityInCallback() throws Exception {
    // First fetch notYar without any callbacks specified.
    AsyncDatastoreService datastore = DatastoreServiceFactory.getAsyncDatastoreService();
    datastore.put(ImmutableList.of(yar, notYar)).get();
    Entity notYarFetched = datastore.get(notYar.getKey()).get();
    assertThat(notYarFetched.getKey()).isEqualTo(notYar.getKey());
    assertThat(notYarFetched.getPropertyMap()).isEqualTo(notYar.getPropertyMap());
    verifyNoMoreInteractions(mock);

    // Then load the callbacks, and try fetching notYar again.
    DatastoreServiceConfig.CALLBACKS = null;
    setCallbacksConfig(".PreGet", HAS_CALLBACKS + ":preGet");
    PreGetContext context =
        new PreGetContext(txnProvider, ImmutableList.of(notYar.getKey()), ImmutableMap.of());
    doAnswer(checkContext(context)).when(mock).preGet(any());
    notYarFetched = datastore.get(notYar.getKey()).get();
    verify(mock, times(1)).preGet(any());
    assertThat(notYarFetched.getKey()).isEqualTo(notYar.getKey());
    assertThat(notYarFetched.getPropertyMap()).isNotEqualTo(notYar.getPropertyMap());
    assertThat(notYarFetched.getProperty("cb_property_only")).isEqualTo("cb_entity2");
  }

  @Test
  public void testPreGet_PutNullValue() throws Exception {
    setCallbacksConfig(".PreGet", HAS_CALLBACKS + ":preGet");
    AsyncDatastoreService datastore = DatastoreServiceFactory.getAsyncDatastoreService();
    Entity fakeResult = new Entity("putNullValue", 24);
    // preGet callback runs as soon as we execute the fetch
    assertThrows(NullPointerException.class, () -> datastore.get(fakeResult.getKey()));
    PreGetContext context =
        new PreGetContext(txnProvider, ImmutableList.of(fakeResult.getKey()), ImmutableMap.of());
    verify(mock, times(1)).preGet(eqContext(context));
  }

  @Test
  public void testPreGet_PutEntityWrongKind() throws Exception {
    setCallbacksConfig(".PreGet", HAS_CALLBACKS + ":preGet");
    AsyncDatastoreService datastore = DatastoreServiceFactory.getAsyncDatastoreService();
    Entity fakeResult = new Entity("putNullValue", 24);
    // preGet callback runs as soon as we execute the fetch
    KeyFactory.createKey("putEntityOfWrongKing", 24);
    assertThrows(NullPointerException.class, () -> datastore.get(fakeResult.getKey()));
    PreGetContext context =
        new PreGetContext(txnProvider, ImmutableList.of(fakeResult.getKey()), ImmutableMap.of());
    verify(mock, times(1)).preGet(eqContext(context));
  }

  @Test
  public void testPostLoad_Get() throws Exception {
    setCallbacksConfig("yar.PostLoad", HAS_CALLBACKS + ":postLoad");
    AsyncDatastoreService datastore = DatastoreServiceFactory.getAsyncDatastoreService();
    // Make sure we have something to get
    datastore.put(ImmutableList.of(yar, notYar)).get();
    Future<Entity> result = datastore.get(yar.getKey());
    // postLoad callback doesn't run until we call get() on the future.
    verifyNoMoreInteractions(mock);
    assertThat(result.get()).isEqualTo(yar);
    assertThat(result.get()).isEqualTo(yar);
    result = datastore.get(notYar.getKey());
    assertThat(result.get()).isEqualTo(notYar);
    assertThat(result.get()).isEqualTo(notYar);
    verify(mock, times(1))
        .postLoad(eqContext(new PostLoadContext(txnProvider, ImmutableList.of(yar))));
  }

  @Test
  public void testPostLoad_Get_CommitTxn() throws Exception {
    setCallbacksConfig("yar.PostLoad", HAS_CALLBACKS + ":postLoad");
    AsyncDatastoreService datastore = DatastoreServiceFactory.getAsyncDatastoreService();
    datastore.put(ImmutableList.of(yar, yarChild)).get();
    Transaction txn = datastore.beginTransaction().get();
    Future<Entity> result = datastore.get(yar.getKey());
    // postLoad callback doesn't run until we call get() on the future.
    verifyNoMoreInteractions(mock);
    assertThat(result.get()).isEqualTo(yar);
    assertThat(result.get()).isEqualTo(yar);
    result = datastore.get(yarChild.getKey());
    assertThat(result.get()).isEqualTo(yarChild);
    assertThat(result.get()).isEqualTo(yarChild);
    Future<Void> commitResult = txn.commitAsync();
    commitResult.get();
    verify(mock, times(1))
        .postLoad(eqContext(new PostLoadContext(txnProvider, ImmutableList.of(yar))));
  }

  @Test
  public void testPostLoad_Get_Batch_CommitTxn() throws Exception {
    setCallbacksConfig("yar.PostLoad", HAS_CALLBACKS + ":postLoad");
    AsyncDatastoreService datastore = DatastoreServiceFactory.getAsyncDatastoreService();
    datastore.put(ImmutableList.of(yar, yarChild)).get();
    Transaction txn = datastore.beginTransaction().get();
    Future<Map<Key, Entity>> result =
        datastore.get(ImmutableList.of(yar.getKey(), yarChild.getKey()));
    // postLoad callback doesn't run until we call get() on the future.
    verifyNoMoreInteractions(mock);

    doAnswer(checkContext(new PostLoadContext(txnProvider, ImmutableList.of(yar, yarChild))))
        .when(mock)
        .postLoad(any());
    assertThat(result.get()).containsExactly(yar.getKey(), yar, yarChild.getKey(), yarChild);
    verify(mock, times(1)).postLoad(any());
    assertThat(result.get()).containsExactly(yar.getKey(), yar, yarChild.getKey(), yarChild);
    Future<Void> commitResult = txn.commitAsync();
    commitResult.get();
    verifyNoMoreInteractions(mock);
  }

  @Test
  public void testPostLoad_Get_RollbackTxn() throws Exception {
    setCallbacksConfig("yar.PostLoad", HAS_CALLBACKS + ":postLoad");
    AsyncDatastoreService datastore = DatastoreServiceFactory.getAsyncDatastoreService();
    datastore.put(ImmutableList.of(yar, yarChild)).get();
    Transaction txn = datastore.beginTransaction().get();
    Future<Entity> result = datastore.get(yar.getKey());
    // postLoad callback doesn't run until we call get() on the future.
    verifyNoMoreInteractions(mock);
    assertThat(result.get()).isEqualTo(yar);
    assertThat(result.get()).isEqualTo(yar);
    result = datastore.get(yarChild.getKey());
    assertThat(result.get()).isEqualTo(yarChild);
    assertThat(result.get()).isEqualTo(yarChild);
    Future<Void> rollbackResult = txn.rollbackAsync();
    rollbackResult.get();
    verify(mock, times(1))
        .postLoad(eqContext(new PostLoadContext(txnProvider, ImmutableList.of(yar))));
  }

  @Test
  public void testPostLoad_Get_Batch_RollbackTxn() throws Exception {
    setCallbacksConfig("yar.PostLoad", HAS_CALLBACKS + ":postLoad");
    AsyncDatastoreService datastore = DatastoreServiceFactory.getAsyncDatastoreService();
    datastore.put(ImmutableList.of(yar, yarChild)).get();
    Transaction txn = datastore.beginTransaction().get();
    Future<Map<Key, Entity>> result =
        datastore.get(ImmutableList.of(yar.getKey(), yarChild.getKey()));
    verifyNoMoreInteractions(mock);
    // postLoad callback doesn't run until we call get() on the future.
    assertThat(result.get()).containsExactly(yar.getKey(), yar, yarChild.getKey(), yarChild);
    doAnswer(checkContext(new PostLoadContext(txnProvider, ImmutableList.of(yar, yarChild))))
        .when(mock)
        .postLoad(any());
    assertThat(result.get()).containsExactly(yar.getKey(), yar, yarChild.getKey(), yarChild);
    Future<Void> rollbackResult = txn.rollbackAsync();
    rollbackResult.get();
    verify(mock, times(1)).postLoad(any());
  }

  @Test
  public void testPostLoad_Get_FailureInCallback() throws Exception {
    setCallbacksConfig("yar.PostLoad", HAS_CALLBACKS + ":explosivePostLoad");
    AsyncDatastoreService datastore = DatastoreServiceFactory.getAsyncDatastoreService();
    datastore.put(ImmutableList.of(yar, notYar)).get();
    Future<Entity> result1 = datastore.get(yar.getKey());
    // postLoad callback doesn't run until we call get() on the future.
    verifyNoMoreInteractions(mock);
    doThrow(new RuntimeException("boom"))
        .when(mock)
        .explosivePostLoad(eqContext(new PostLoadContext(txnProvider, ImmutableList.of(yar))));
    // We fail the first time because the callback throws an exception.
    ExecutionException e1 = assertThrows(ExecutionException.class, result1::get);
    assertThat(e1).hasCauseThat().hasMessageThat().isEqualTo("boom");
    // We fail the second time because the result is cached
    ExecutionException e2 = assertThrows(ExecutionException.class, result1::get);
    assertThat(e2).hasCauseThat().hasMessageThat().isEqualTo("boom");
    Future<Entity> result2 = datastore.get(notYar.getKey());
    assertThat(result2.get()).isEqualTo(notYar);
    assertThat(result2.get()).isEqualTo(notYar);
    verify(mock, times(1)).explosivePostLoad(any());
  }

  @Test
  public void testPostLoad_Get_FailureInCallback_Txn() throws Exception {
    setCallbacksConfig("yar.PostLoad", HAS_CALLBACKS + ":explosivePostLoad");
    AsyncDatastoreService datastore = DatastoreServiceFactory.getAsyncDatastoreService();
    datastore.put(ImmutableList.of(yar)).get();
    Transaction txn = datastore.beginTransaction().get();
    Future<Map<Key, Entity>> result =
        datastore.get(ImmutableList.of(yar.getKey(), yarChild.getKey()));
    // postLoad callback doesn't run until we call get() on the future.
    verifyNoMoreInteractions(mock);
    doThrow(new RuntimeException("boom"))
        .when(mock)
        .explosivePostLoad(eqContext(new PostLoadContext(txnProvider, ImmutableList.of(yar))));
    // We fail the first time because the callback throws an exception.
    ExecutionException e1 = assertThrows(ExecutionException.class, result::get);
    assertThat(e1).hasCauseThat().hasMessageThat().isEqualTo("boom");
    // We fail the second time because the result is cached
    ExecutionException e2 = assertThrows(ExecutionException.class, result::get);
    assertThat(e2).hasCauseThat().hasMessageThat().isEqualTo("boom");
    txn.commit();
    verify(mock, times(1)).explosivePostLoad(any());
  }

  @Test
  public void testPostLoad_Get_FailureInCallback_Batch_Txn() throws Exception {
    setCallbacksConfig("yar.PostLoad", HAS_CALLBACKS + ":explosivePostLoad");
    AsyncDatastoreService datastore = DatastoreServiceFactory.getAsyncDatastoreService();
    datastore.put(ImmutableList.of(yar, yarChild)).get();
    Transaction txn = datastore.beginTransaction().get();
    Future<Map<Key, Entity>> result =
        datastore.get(ImmutableList.of(yar.getKey(), yarChild.getKey()));
    // postLoad callback doesn't run until we call get() on the future.
    verifyNoMoreInteractions(mock);
    doThrow(new RuntimeException("boom"))
        .when(mock)
        .explosivePostLoad(
            eqContext(new PostLoadContext(txnProvider, ImmutableList.of(yar, yarChild))));
    // We fail the first time because the callback throws an exception.
    ExecutionException e1 = assertThrows(ExecutionException.class, result::get);
    assertThat(e1).hasCauseThat().hasMessageThat().isEqualTo("boom");
    // We fail the second time because the result is cached.
    ExecutionException e2 = assertThrows(ExecutionException.class, result::get);
    assertThat(e2).hasCauseThat().hasMessageThat().isEqualTo("boom");
    txn.commit();
    verify(mock, times(1)).explosivePostLoad(any());
  }

  @Test
  public void testPreQuery() throws Exception {
    setCallbacksConfig("yar.PreQuery", HAS_CALLBACKS + ":preQuery");
    AsyncDatastoreService datastore = DatastoreServiceFactory.getAsyncDatastoreService();
    datastore.put(ImmutableList.of(yar, notYar)).get();
    Query copy = new Query(yarQuery);
    assertThat(datastore.prepare(yarQuery).asSingleEntity()).isEqualTo(yar);
    // make sure the query hasn't changed.
    assertThat(yarQuery).isEqualTo(copy);
    // preQuery callback runs as soon as we prepare the query
    verify(mock).preQuery(eqContext(new PreQueryContext(txnProvider, yarQuery)));
  }

  @Test
  public void testPreQuery_Decorate() throws Exception {
    setCallbacksConfig("decorateMe.PreQuery", HAS_CALLBACKS + ":preQuery");
    AsyncDatastoreService datastore = DatastoreServiceFactory.getAsyncDatastoreService();
    datastore.put(ImmutableList.of(decorateMe1, decorateMe2, decorateMe3)).get();
    // preQuery callback runs as soon as we prepare the query
    doAnswer(checkContext(new PreQueryContext(txnProvider, decorateMeQuery)))
        .when(mock)
        .preQuery(any());
    Query copy = new Query(decorateMeQuery);
    // We only get decorateMe2 because the callback adds "extra == 2" to the query.
    assertThat(datastore.prepare(decorateMeQuery).asSingleEntity()).isEqualTo(decorateMe2);
    verify(mock).preQuery(any());
    // make sure the query hasn't changed.
    assertThat(decorateMeQuery).isEqualTo(copy);
  }

  @Test
  public void testPreQuery_FailureInCallback() throws Exception {
    setCallbacksConfig("yar.PreQuery", HAS_CALLBACKS + ":explosivePreQuery");
    AsyncDatastoreService datastore = DatastoreServiceFactory.getAsyncDatastoreService();
    // preQuery callback runs as soon as we prepare the query
    doThrow(new RuntimeException("boom"))
        .when(mock)
        .explosivePreQuery(eqContext(new PreQueryContext(txnProvider, yarQuery)));
    Query copy = new Query(yarQuery);
    // We fail because the callback throws an exception.
    RuntimeException e = assertThrows(RuntimeException.class, () -> datastore.prepare(yarQuery));
    assertThat(e).hasMessageThat().isEqualTo("boom");
    // make sure the query hasn't changed.
    assertThat(yarQuery).isEqualTo(copy);
  }

  @Test
  public void testPostLoad_Query() throws Exception {
    setCallbacksConfig("yar.PostLoad", HAS_CALLBACKS + ":postLoad");
    DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
    datastore.put(ImmutableList.of(yar, notYar));
    Entity yarCopy = new Entity(yar.getKey());
    yarCopy.setPropertiesFrom(yar);
    yarCopy.setProperty("timestamp", 3333333);
    assertThat(datastore.prepare(yarQuery).asSingleEntity()).isEqualTo(yarCopy);
    // postLoad callback runs when query results come back
    verify(mock).postLoad(eqContext(new PostLoadContext(txnProvider, yar)));
  }

  @Test
  public void testPostLoad_Query_MultipleResults() throws Exception {
    setCallbacksConfig(
        "yar.PostLoad", HAS_CALLBACKS + ":postLoad",
        "yarchild.PostLoad", HAS_CALLBACKS + ":postLoad");
    DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
    datastore.put(ImmutableList.of(yar, yarChild));
    assertThat(datastore.prepare(kindlessAncestorQuery).asList(withDefaults()))
        .containsExactly(yar, yarChild)
        .inOrder();
    // postLoad callback runs when query results come back, once per result
    verify(mock).postLoad(eqContext(new PostLoadContext(txnProvider, yar)));
    verify(mock).postLoad(eqContext(new PostLoadContext(txnProvider, yarChild)));
  }

  @Test
  public void testPostLoad_Query_FailureInCallback() throws Exception {
    setCallbacksConfig("yar.PostLoad", HAS_CALLBACKS + ":explosivePostLoad");
    DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
    datastore.put(ImmutableList.of(yar, yarChild));
    // postLoad callback runs when query results come back
    doThrow(new RuntimeException("boom"))
        .when(mock)
        .explosivePostLoad(eqContext(new PostLoadContext(txnProvider, ImmutableList.of(yar))));
    // We fail because the callback throws an exception.
    RuntimeException e =
        assertThrows(RuntimeException.class, () -> datastore.prepare(yarQuery).asSingleEntity());
    assertThat(e).hasMessageThat().isEqualTo("boom");
  }

  @Test
  public void testPostOpFuture() throws ExecutionException, InterruptedException {
    final AtomicBoolean ranCallbacks = new AtomicBoolean(false);
    Future<String> delegate = immediateFuture("yar");
    PostOpFuture<String> future =
        new PostOpFuture<String>(delegate, null) {
          @Override
          void executeCallbacks(String ignore) {
            assertThat(ranCallbacks.getAndSet(true)).isFalse();
          }
        };
    assertThat(future.get()).isEqualTo("yar");
    assertThat(ranCallbacks.get()).isTrue();

    // now retrieve the result of the future again to ensure we don't run the
    // callbacks again
    ranCallbacks.set(false);
    assertThat(future.get()).isEqualTo("yar");
    assertThat(ranCallbacks.get()).isFalse();
  }

  private void setCallbacksConfig(String... args) throws IOException {
    String config = newCallbacksConfig(args);
    System.setProperty(DatastoreServiceConfig.CALLBACKS_CONFIG_SYS_PROP, config);
  }

  private String newCallbacksConfig(String... args) throws IOException {
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
    try {
      return baos.toString();
    } finally {
      baos.close();
    }
  }
}
