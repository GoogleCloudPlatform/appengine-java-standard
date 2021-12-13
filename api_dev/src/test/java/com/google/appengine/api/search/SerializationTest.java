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

package com.google.appengine.api.search;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.appengine.api.testing.SerializationTestBase;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SerializationTest extends SerializationTestBase {
  @Override
  protected Iterable<Serializable> getCanonicalObjects() {
    Date date = new Date();
    date.setTime(0);
    Document document = Document.newBuilder().setId("docId")
        .addField(Field.newBuilder().setName("textName").setText("text1"))
        .addField(Field.newBuilder().setName("numberName").setNumber(0))
        .addField(Field.newBuilder().setName("dateName").setDate(date))
        .addField(Field.newBuilder().setName("uprefixName").setUntokenizedPrefix("uprefix_value"))
        .addField(Field.newBuilder().setName("tprefixName").setTokenizedPrefix("tprefix_value"))
        .addField(Field.newBuilder().setName("vector").setVector(Arrays.asList(1.0, 2.0, 3.0)))
        .build();
    OperationResult result = new OperationResult(StatusCode.OK, "all good");
    List<ScoredDocument> scoredDocs = new ArrayList<>();
    List<String> docIds = new ArrayList<>();
    List<Document> docs = new ArrayList<>();
    List<OperationResult> opResults = new ArrayList<>();
    Cursor cursor = Cursor.newBuilder().build();
    ScoredDocument.Builder scoredDocumentBuilder = ScoredDocument.newBuilder();
    scoredDocumentBuilder.setId("docId");
    ScoredDocument scoredDocument = scoredDocumentBuilder.build();
    List<FacetResult> facetResults = new ArrayList<>();
    Results<ScoredDocument> docResults =
        new Results<>(result, scoredDocs, 0L, 0, cursor, facetResults);
    ScoredDocument.Builder scoredDocBuilder = ScoredDocument.newBuilder();
    scoredDocBuilder.setId("docId");
    FacetResultValue facetResultValue =
        FacetResultValue.create("label", 1, "test_token_not_valid");
    FacetResult facetResult = FacetResult.newBuilder().setName("facet").addValue(facetResultValue)
        .build();
    return ImmutableList.of(
        new DeleteException(result),
        document,
        scoredDocument,
        scoredDocBuilder.build(),
        Field.FieldType.TEXT,
        Field.newBuilder().setName("name").build(),
        new PutException(result),
        new PutResponse(opResults, docIds),
        new GetException(result),
        new GetResponse<Document>(docs),
        result,
        SortExpression.SortDirection.DESCENDING,
        new SearchException("problem"),
        new SearchBaseException(result),
        new SearchQueryException("bad query"),
        new SearchServiceException("problem"),
        cursor,
        docResults,
        StatusCode.OK,
        Facet.withAtom("name", "atom"),
        facetResult,
        facetResultValue);
  }

  private Method getPrivateMethodOrNull(
      Class<?> cls, String methodName, Class<?>... parameterTypes) {
    try {
      Method m = cls.getDeclaredMethod(methodName, parameterTypes);
      m.setAccessible(true);
      return m;
    } catch (NoSuchMethodException e) {
      return null;
    }
  }

  @Override
  @Test
  public void testSerializationBackwardsCompatibility() throws Exception {
    if (System.getenv("JAVA_COVERAGE_FILE") != null) {
      // Coverage instrumentation interferes with this test.
      return;
    }
    for (Serializable ser : getCanonicalObjects()) {
      try (InputStream is = getGoldenFileForClass(ser.getClass());
          ObjectInputStream ois = new ObjectInputStream(is)) {
        Object o = ois.readObject();
        // Only check equality if we've defined an equals method.
        Method equalsMethod = o.getClass().getMethod("equals", Object.class);
        if (equalsMethod.getDeclaringClass() != Object.class) {
          assertWithMessage(ser.getClass().getName()).that(o).isEqualTo(ser);
        }
        if (ser instanceof Document) {
          Iterable<Field> serFields = ((Document) ser).getFields();
          Iterable<Field> oFields = ((Document) o).getFields();
          assertThat(serFields).containsExactlyElementsIn(oFields).inOrder();
        }
      } catch (Exception e) {
        throw new Exception(
            "Non-backwards compatible changes to the serialized form of " + ser.getClass().getName()
                + " have been made.  If you need to update the golden files please follow the"
                + " instructions in the BUILD file in the same directory as the test that failed.",
            e);
      }
    }
  }

  /**
   * Deserialize golden files and call checkValid on the resulted object, if the method exists, to
   * make sure serialized form of new objects using old/golden files are still valid.
   */
  @Test
  public void testSerializationBackwardsValidity() throws Exception {
    if (System.getenv("JAVA_COVERAGE_FILE") != null) {
      // Coverage instrumentation interferes with this test.
      return;
    }
    for (Serializable ser : getCanonicalObjects()) {
      try (InputStream is = getGoldenFileForClass(ser.getClass());
          ObjectInputStream ois = new ObjectInputStream(is)) {
        Object o = ois.readObject();
        // only check validity if we've defined a checkValid method
        Method checkValidMethod = getPrivateMethodOrNull(o.getClass(), "checkValid");
        if (checkValidMethod != null) {
          checkValidMethod.invoke(o);
        }
      } catch (Exception e) {
        throw new Exception(
            "checkValid method failed on serialized form of " + ser.getClass().getName()
            + " If you need to update the golden files please follow the"
            + " instructions in the BUILD file in the same directory as the test that failed.",
            e);
      }
    }
  }

  @Override
  protected Class<?> getClassInApiJar() {
    return Index.class;
  }

  public static void main(String[] args) throws IOException {
    new SerializationTest().writeCanonicalObjects();
  }
}
