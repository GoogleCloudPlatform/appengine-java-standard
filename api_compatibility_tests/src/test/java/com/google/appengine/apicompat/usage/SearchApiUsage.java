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

package com.google.appengine.apicompat.usage;

import static com.google.appengine.apicompat.Utils.classes;

import com.google.appengine.api.search.Cursor;
import com.google.appengine.api.search.DeleteException;
import com.google.appengine.api.search.Document;
import com.google.appengine.api.search.Facet;
import com.google.appengine.api.search.FacetOptions;
import com.google.appengine.api.search.FacetRange;
import com.google.appengine.api.search.FacetRefinement;
import com.google.appengine.api.search.FacetRequest;
import com.google.appengine.api.search.FacetResult;
import com.google.appengine.api.search.FacetResultValue;
import com.google.appengine.api.search.Field;
import com.google.appengine.api.search.FieldExpression;
import com.google.appengine.api.search.GeoPoint;
import com.google.appengine.api.search.GetException;
import com.google.appengine.api.search.GetIndexesRequest;
import com.google.appengine.api.search.GetRequest;
import com.google.appengine.api.search.GetResponse;
import com.google.appengine.api.search.ISearchServiceFactory;
import com.google.appengine.api.search.ISearchServiceFactoryProvider;
import com.google.appengine.api.search.Index;
import com.google.appengine.api.search.IndexSpec;
import com.google.appengine.api.search.MatchScorer;
import com.google.appengine.api.search.OperationResult;
import com.google.appengine.api.search.PutException;
import com.google.appengine.api.search.PutResponse;
import com.google.appengine.api.search.Query;
import com.google.appengine.api.search.QueryOptions;
import com.google.appengine.api.search.RescoringMatchScorer;
import com.google.appengine.api.search.Results;
import com.google.appengine.api.search.Schema;
import com.google.appengine.api.search.ScoredDocument;
import com.google.appengine.api.search.SearchBaseException;
import com.google.appengine.api.search.SearchException;
import com.google.appengine.api.search.SearchQueryException;
import com.google.appengine.api.search.SearchService;
import com.google.appengine.api.search.SearchServiceConfig;
import com.google.appengine.api.search.SearchServiceException;
import com.google.appengine.api.search.SearchServiceFactory;
import com.google.appengine.api.search.SortExpression;
import com.google.appengine.api.search.SortOptions;
import com.google.appengine.api.search.StatusCode;
import com.google.appengine.apicompat.ExhaustiveApiInterfaceUsage;
import com.google.appengine.apicompat.ExhaustiveApiUsage;
import com.google.appengine.apicompat.UsageTracker;
import com.google.appengine.spi.FactoryProvider;
import com.google.appengine.tools.development.testing.LocalSearchServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import com.google.common.collect.Lists;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Future;

/** Exhaustive usage of the Search Api. Used for backward compatibility checks. */
@SuppressWarnings("deprecation")
public class SearchApiUsage {

  /**
   * Exhaustive use of {@link SearchServiceFactory}.
   */
  public static class SearchServiceFactoryUsage extends ExhaustiveApiUsage<SearchServiceFactory> {

    @Override
    public Set<Class<?>> useApi() {
      LocalServiceTestHelper helper =
          new LocalServiceTestHelper(new LocalSearchServiceTestConfig());
      helper.setUp();
      try {
        SearchService ss = SearchServiceFactory.getSearchService();
        ss = SearchServiceFactory.getSearchService(SearchServiceConfig.newBuilder().build());
        ss = SearchServiceFactory.getSearchService("yar");
        return classes(Object.class);
      } finally {
        helper.tearDown();
      }
    }
  }

  /**
   * Exhaustive use of {@link SearchService}.
   */
  public static class SearchServiceUsage extends ExhaustiveApiInterfaceUsage<SearchService> {

    @Override
    protected Set<Class<?>> useApi(SearchService ss) {
      IndexSpec.Builder indexSpecBuilder = IndexSpec.newBuilder();
      Index index = ss.getIndex(indexSpecBuilder);
      IndexSpec indexSpec = indexSpecBuilder.build();
      index = ss.getIndex(indexSpecBuilder.build());

      GetIndexesRequest.Builder getIndexesBuilder = GetIndexesRequest.newBuilder();
      GetResponse<Index> indexResponse = ss.getIndexes(getIndexesBuilder);
      GetIndexesRequest getIndexes = getIndexesBuilder.build();
      indexResponse = ss.getIndexes(getIndexes);

      Future<GetResponse<Index>> indexResponseFuture = ss.getIndexesAsync(getIndexesBuilder);
      indexResponseFuture = ss.getIndexesAsync(getIndexes);

      String strVal = ss.getNamespace();
      return classes();
    }
  }

  /**
   * Exhaustive use of {@link DeleteException}.
   */
  public static class DeleteExceptionUsage extends ExhaustiveApiUsage<DeleteException> {

    @Override
    public Set<Class<?>> useApi() {
      DeleteException ex = new DeleteException(new OperationResult(StatusCode.OK, "Yar"));
      List<OperationResult> results = Lists.newArrayList();
      ex = new DeleteException(new OperationResult(StatusCode.OK, "Yar"), results);
      results = ex.getResults();
      return classes(SearchBaseException.class, RuntimeException.class, Exception.class,
          Throwable.class, Serializable.class, Object.class);
    }
  }

  /**
   * Exhaustive use of {@link GetException}.
   */
  public static class GetExceptionUsage extends ExhaustiveApiUsage<GetException> {

    @Override
    public Set<Class<?>> useApi() {
      GetException ex = new GetException(new OperationResult(StatusCode.OK, "yar"));
      ex = new GetException("yar");
      return classes(SearchBaseException.class, RuntimeException.class, Exception.class,
          Throwable.class, Serializable.class, Object.class);
    }
  }

  /**
   * Exhaustive use of {@link PutException}.
   */
  public static class PutExceptionUsage extends ExhaustiveApiUsage<PutException> {

    @Override
    public Set<Class<?>> useApi() {
      PutException ex = new PutException(new OperationResult(StatusCode.OK, "Yar"));
      List<OperationResult> results = Lists.newArrayList();
      List<String> ids = Lists.newArrayList();
      ex = new PutException(new OperationResult(StatusCode.OK, "Yar"), results, ids);
      results = ex.getResults();
      ids = ex.getIds();
      return classes(SearchBaseException.class, RuntimeException.class, Exception.class,
          Throwable.class, Serializable.class, Object.class);
    }
  }

  /**
   * Exhaustive use of {@link SearchBaseException}.
   */
  public static class SearchBaseExceptionUsage extends ExhaustiveApiUsage<SearchBaseException> {

    @Override
    public Set<Class<?>> useApi() {
      SearchBaseException ex = new SearchBaseException(new OperationResult(StatusCode.OK, "Yar"));
      OperationResult result = ex.getOperationResult();
      return classes(RuntimeException.class, Exception.class, Throwable.class, Serializable.class,
          Object.class);
    }
  }

  /**
   * Exhaustive use of {@link SearchException}.
   */
  public static class SearchExceptionUsage extends ExhaustiveApiUsage<SearchException> {

    @Override
    public Set<Class<?>> useApi() {
      SearchException ex = new SearchException(new OperationResult(StatusCode.OK, "Yar"));
      ex = new SearchException("yar");
      return classes(SearchBaseException.class, RuntimeException.class, Exception.class,
          Throwable.class, Serializable.class, Object.class);
    }
  }

  /**
   * Exhaustive use of {@link SearchQueryException}.
   */
  public static class SearchQueryExceptionUsage extends ExhaustiveApiUsage<SearchQueryException> {

    @Override
    public Set<Class<?>> useApi() {
      SearchQueryException ex = new SearchQueryException("msg");
      return classes(SearchBaseException.class, RuntimeException.class, Exception.class,
          Throwable.class, Serializable.class, Object.class);
    }
  }

  /**
   * Exhaustive use of {@link SearchServiceException}.
   */
  public static class SearchServiceExceptionUsage extends
      ExhaustiveApiUsage<SearchServiceException> {

    @Override
    public Set<Class<?>> useApi() {
      SearchServiceException ex = new SearchServiceException("msg");
      ex = new SearchServiceException("msg", new Exception("yar"));
      return classes(RuntimeException.class, Exception.class, Throwable.class, Serializable.class,
          Object.class);
    }
  }

  /**
   * Exhaustive use of {@link Cursor}.
   */
  public static class CursorUsage extends ExhaustiveApiUsage<Cursor> {

    @Override
    public Set<Class<?>> useApi() {
      Cursor cursor = Cursor.newBuilder().build();
      boolean boolVal = cursor.isPerResult();
      String strVal = cursor.toWebSafeString();
      strVal = cursor.toString();
      try {
        Cursor.Builder builder = Cursor.newBuilder(cursor);
      } catch (NullPointerException npe) {
        // fine
      }
      return classes(Serializable.class, Object.class);
    }
  }

  /**
   * Exhaustive use of {@link com.google.appengine.api.search.Cursor.Builder}.
   */
  public static class CursorBuilderUsage extends ExhaustiveApiUsage<Cursor.Builder> {

    @Override
    public Set<Class<?>> useApi() {
      Cursor.Builder builder = Cursor.newBuilder();
      Cursor cursor = builder.build();
      builder = builder.setPerResult(true);
      try {
        builder.build("invalid format");
      } catch (IllegalArgumentException iae) {
        // fine
      }
      return classes(Object.class);
    }
  }

  /**
   * Exhaustive use of {@link Document}.
   */
  public static class DocumentUsage extends ExhaustiveApiUsage<Document> {

    static class MyDocument extends Document {

      @UsageTracker.DoNotTrackConstructorInvocation
      MyDocument(Builder builder) {
        super(builder);
      }
    }

    @Override
    public Set<Class<?>> useApi() {
      Document.Builder builder = Document.newBuilder().addField(Field.newBuilder()
          .setName("yar").setText("text")).setId("docId")
          .addFacet(Facet.withAtom("name", "value"));
      Document doc = builder.build();
      boolean boolVal = doc.equals(doc);
      int intVal = doc.getFieldCount("yar");
      Set<String> setStr = doc.getFieldNames();
      Iterable<Field> fieldIterable = doc.getFields();
      fieldIterable = doc.getFields("yar");
      String strVal = doc.getId();
      Locale locale = doc.getLocale();
      Field fieldVal = doc.getOnlyField("yar");
      intVal = doc.getRank();
      intVal = doc.hashCode();
      strVal = doc.toString();
      doc = new MyDocument(builder);
      intVal = doc.getFacetCount("name");
      setStr = doc.getFacetNames();
      Iterable<Facet> facetIterable = doc.getFacets();
      facetIterable = doc.getFacets("name");
      Facet facetVal = doc.getOnlyFacet("name");
      return classes(Object.class, Serializable.class);
    }
  }

  /**
   * Exhaustive use of {@link com.google.appengine.api.search.Document.Builder}.
   */
  public static class DocumentBuilderUsage extends ExhaustiveApiUsage<Document.Builder> {

    static class MyDocumentBuilder extends Document.Builder {

      @UsageTracker.DoNotTrackConstructorInvocation
      MyDocumentBuilder() {
      }
    }

    @Override
    public Set<Class<?>> useApi() {
      Document.Builder builder = Document.newBuilder();
      builder = builder.addField(Field.newBuilder().setName("yar"));
      builder = builder.addField(Field.newBuilder().setName("yar").build());
      builder = builder.setId("docId");
      builder = builder.setLocale(Locale.CANADA);
      builder = builder.setRank(1);
      Document doc = builder.build();
      builder = new MyDocumentBuilder();
      builder = builder.addFacet(Facet.withAtom("atom", "name"));
      return classes(Object.class);
    }
  }

  /**
   * Exhaustive use of {@link Field}.
   */
  public static class FieldUsage extends ExhaustiveApiUsage<Field> {

    @Override
    public Set<Class<?>> useApi() {
      Field field = Field.newBuilder().setName("yar").setText("text").build();
      boolean boolVal = field.equals(field);
      String strVal = field.getAtom();
      Date dateVal = field.getDate();
      GeoPoint geoPointVal = field.getGeoPoint();
      strVal = field.getHTML();
      Locale locale = field.getLocale();
      strVal = field.getName();
      Double doubleVal = field.getNumber();
      strVal = field.getText();
      strVal = field.getUntokenizedPrefix();
      strVal = field.getTokenizedPrefix();
      List<Double> doubleList = field.getVector();
      Field.FieldType type = field.getType();
      int intVal = field.hashCode();
      strVal = field.toString();
      return classes(Object.class, Serializable.class);
    }
  }

  /**
   * Exhaustive use of {@link com.google.appengine.api.search.Field.Builder}.
   */
  public static class FieldBuilderUsage extends ExhaustiveApiUsage<Field.Builder> {

    @Override
    public Set<Class<?>> useApi() {
      Field.Builder builder = Field.newBuilder();
      builder = builder.setText("text");
      builder = Field.newBuilder();
      builder = builder.setAtom("atom");
      builder = Field.newBuilder();
      builder = builder.setUntokenizedPrefix("uprefix");
      builder = Field.newBuilder();
      builder = builder.setTokenizedPrefix("tprefix");
      builder = Field.newBuilder();
      builder = builder.setVector(Arrays.asList(1.0, 2.0, 3.0));
      builder = Field.newBuilder();
      builder = builder.setDate(new Date());
      builder = Field.newBuilder();
      builder = builder.setGeoPoint(new GeoPoint(23D, 23D));
      builder = Field.newBuilder();
      builder = builder.setHTML("html");
      builder = builder.setLocale(Locale.CANADA);
      builder = Field.newBuilder();
      builder = builder.setNumber(23L);
      builder = builder.setName("name");
      Field field = builder.build();
      return classes(Object.class);
    }
  }

  /**
   * Exhaustive use of {@link Facet}.
   */
  public static class FacetUsage extends ExhaustiveApiUsage<Facet> {

    @Override
    public Set<Class<?>> useApi() {
      Facet facet = Facet.withAtom("yar", "text");
      boolean boolVal = facet.equals(facet);
      String strVal = facet.getAtom();
      strVal = facet.getName();
      Double doubleVal = facet.getNumber();
      int intVal = facet.hashCode();
      strVal = facet.toString();
      facet = Facet.withNumber("name", 10.0);
      return classes(Object.class, Serializable.class);
    }
  }

  /**
   * Exhaustive use of {@link FacetResult}.
   */
  public static class FacetResultUsage extends ExhaustiveApiUsage<FacetResult> {

    @Override
    public Set<Class<?>> useApi() {
      FacetResultValue fresultv =
          FacetResultValue.create("label", 10,
              FacetRefinement.withValue("name", "value").toTokenString());
      FacetResult result = FacetResult.newBuilder()
          .addValue(fresultv)
          .setName("name").build();
      Iterable<FacetResultValue> valueIter = result.getValues();
      String strVal = result.getName();
      strVal = result.toString();
      return classes(Serializable.class, Object.class);
    }
  }

  /**
   * Exhaustive use of {@link com.google.appengine.api.search.FacetResult.Builder}.
   */
  public static class FacetResultBuilderUsage extends ExhaustiveApiUsage<FacetResult.Builder> {

    @Override
    public Set<Class<?>> useApi() {
      FacetResultValue fresultv =
          FacetResultValue.create("label", 10,
              FacetRefinement.withValue("name", "value").toTokenString());
      FacetResult.Builder builder = FacetResult.newBuilder();
      builder = builder.addValue(fresultv);
      builder = builder.setName("name");
      FacetResult result = builder.build();
      return classes(Object.class);
    }
  }

  /**
   * Exhaustive use of {@link FacetResultValue}.
   */
  public static class FacetResultValueUsage extends ExhaustiveApiUsage<FacetResultValue> {

    @Override
    public Set<Class<?>> useApi() {
      FacetResultValue fvalue =
          FacetResultValue.create("label", 10,
              FacetRefinement.withValue("name", "value").toTokenString());
      int intVal = fvalue.getCount();
      String strVal = fvalue.getLabel();
      strVal = fvalue.getRefinementToken();
      strVal = fvalue.toString();
      return classes(Serializable.class, Object.class);
    }
  }

  /**
   * Exhaustive use of {@link FacetRange}.
   */
  public static class FacetRangeUsage extends ExhaustiveApiUsage<FacetRange> {

    @Override
    public Set<Class<?>> useApi() {
      FacetRange range = FacetRange.withStart(1.0);
      range = FacetRange.withStartEnd(1.0, 2.0);
      range = FacetRange.withEnd(2.0);
      String strVal = range.getStart();
      strVal = range.getEnd();
      strVal = range.toString();
      return classes(Object.class);
    }
  }

  /**
   * Exhaustive use of {@link FacetRefinement}.
   */
  public static class FacetRefinementUsage extends ExhaustiveApiUsage<FacetRefinement> {

    @Override
    public Set<Class<?>> useApi() {
      FacetRefinement ref = FacetRefinement.withValue("name", "value");
      String strVal = ref.getName();
      strVal = ref.getValue();
      FacetRange rangeVal = ref.getRange();
      ref = FacetRefinement.withRange("name", FacetRange.withEnd(10.0));
      strVal = ref.toTokenString();
      ref = FacetRefinement.fromTokenString(strVal);
      strVal = ref.toString();
      return classes(Object.class);
    }
  }

  /**
   * Exhaustive use of {@link FacetRequest}.
   */
  public static class FacetRequestUsage extends ExhaustiveApiUsage<FacetRequest> {

    @Override
    public Set<Class<?>> useApi() {
      FacetRequest request = FacetRequest.newBuilder().setName("Name").build();
      String strVal = request.getName();
      Iterable<FacetRange> rangeItr = request.getRanges();
      Iterable<String> strItr = request.getValueConstraints();
      Integer intVal = request.getValueLimit();
      strVal = request.toString();
      FacetRequest.Builder builder = FacetRequest.newBuilder(request);
      return classes(Object.class);
    }
  }

  /**
   * Exhaustive use of {@link com.google.appengine.api.search.FacetRequest.Builder}.
   */
  public static class FacetRequestBuilderUsage extends ExhaustiveApiUsage<FacetRequest.Builder> {

    @Override
    public Set<Class<?>> useApi() {
      FacetRequest.Builder builder = FacetRequest.newBuilder();
      builder = builder.addRange(FacetRange.withStartEnd(10.0, 20.0));
      builder = FacetRequest.newBuilder().addValueConstraint("value");
      builder = builder.setName("name");
      builder = builder.setValueLimit(10);
      FacetRequest request = builder.build();
      return classes(Object.class);
    }
  }

  /**
   * Exhaustive use of {@link FacetOptions}.
   */
  public static class FacetOptionsUsage extends ExhaustiveApiUsage<FacetOptions> {

    @Override
    public Set<Class<?>> useApi() {
      FacetOptions options = FacetOptions.newBuilder().build();
      Integer intVal = options.getDepth();
      intVal = options.getDiscoveryLimit();
      intVal = options.getDiscoveryValueLimit();
      FacetOptions.Builder builder = FacetOptions.newBuilder(options);
      String strVal = options.toString();
      return classes(Object.class);
    }
  }

  /**
   * Exhaustive use of {@link com.google.appengine.api.search.FacetOptions.Builder}.
   */
  public static class FacetOptionsBuilderUsage extends ExhaustiveApiUsage<FacetOptions.Builder> {

    @Override
    public Set<Class<?>> useApi() {
      FacetOptions.Builder builder = FacetOptions.newBuilder();
      builder = builder.setDepth(1000);
      builder = builder.setDiscoveryLimit(10);
      builder = builder.setDiscoveryValueLimit(5);
      FacetOptions facetOptions = builder.build();
      return classes(Object.class);
    }
  }

  /**
   * Exhaustive use of {@link com.google.appengine.api.search.Field.FieldType}.
   */
  public static class FieldTypeUsage extends ExhaustiveApiUsage<Field.FieldType> {

    @Override
    public Set<Class<?>> useApi() {
      Field.FieldType type = Field.FieldType.ATOM;
      type = Field.FieldType.DATE;
      type = Field.FieldType.GEO_POINT;
      type = Field.FieldType.HTML;
      type = Field.FieldType.NUMBER;
      type = Field.FieldType.TEXT;
      type = Field.FieldType.UNTOKENIZED_PREFIX;
      type = Field.FieldType.TOKENIZED_PREFIX;
      type = Field.FieldType.VECTOR;
      type = Field.FieldType.valueOf("TEXT");
      Field.FieldType[] values = Field.FieldType.values();
      return classes(Enum.class, Serializable.class, Object.class, Comparable.class);
    }
  }

  /**
   * Exhaustive use of {@link FieldExpression}.
   */
  public static class FieldExpressionUsage extends ExhaustiveApiUsage<FieldExpression> {

    @Override
    public Set<Class<?>> useApi() {
      FieldExpression exp =
          FieldExpression.newBuilder().setName("name").setExpression("exp").build();
      String strVal = exp.getExpression();
      strVal = exp.getName();
      strVal = exp.toString();
      return classes(Object.class);
    }
  }

  /**
   * Exhaustive use of {@link com.google.appengine.api.search.FieldExpression.Builder}.
   */
  public static class FieldExpressionBuilderUsage extends
      ExhaustiveApiUsage<FieldExpression.Builder> {

    @Override
    public Set<Class<?>> useApi() {
      FieldExpression.Builder builder = new FieldExpression.Builder();
      builder = builder.setExpression("yar");
      builder = builder.setName("name");
      FieldExpression exp = builder.build();
      return classes(Object.class);
    }
  }

  /**
   * Exhaustive use of {@link GeoPoint}.
   */
  public static class GeoPointUsage extends ExhaustiveApiUsage<GeoPoint> {

    @Override
    public Set<Class<?>> useApi() {
      GeoPoint geoPoint = new GeoPoint(23D, 24D);
      double doubleVal = geoPoint.getLatitude();
      doubleVal = geoPoint.getLongitude();
      String strVal = geoPoint.toString();
      return classes(Object.class);
    }
  }

  /**
   * Exhaustive use of {@link GetIndexesRequest}.
   */
  public static class GetIndexesRequestUsage extends ExhaustiveApiUsage<GetIndexesRequest> {

    @Override
    public Set<Class<?>> useApi() {
      GetIndexesRequest.Builder builder = GetIndexesRequest.newBuilder().setIncludeStartIndex(true)
          .setStartIndexName("indexname");
      GetIndexesRequest req = builder.build();
      boolean boolVal = req.equals(req);
      String strVal = req.getIndexNamePrefix();
      strVal = req.getNamespace();
      strVal = req.getStartIndexName();
      Integer integerVal = req.getLimit();
      integerVal = req.getOffset();
      int intVal = req.hashCode();
      boolVal = req.isIncludeStartIndex();
      Boolean booleanVal = req.isSchemaFetched();
      strVal = req.toString();
      builder = GetIndexesRequest.newBuilder(req);
      return classes(Object.class);
    }
  }

  /**
   * Exhaustive use of {@link com.google.appengine.api.search.GetIndexesRequest.Builder}.
   */
  public static class GetIndexesRequestBuilderUsage extends
      ExhaustiveApiUsage<GetIndexesRequest.Builder> {

    @Override
    public Set<Class<?>> useApi() {
      GetIndexesRequest.Builder builder = GetIndexesRequest.newBuilder();
      builder = builder.setIncludeStartIndex(true);
      builder = builder.setStartIndexName("indexname");
      builder = builder.setIndexNamePrefix("prefix");
      Integer integerVal = 2;
      builder = builder.setLimit(integerVal);
      builder = builder.setOffset(integerVal);
      builder = builder.setNamespace("namespace");
      builder = builder.setSchemaFetched(true);
      GetIndexesRequest req = builder.build();
      return classes(Object.class);
    }
  }

  /**
   * Exhaustive use of {@link GetRequest}.
   */
  public static class GetRequestUsage extends ExhaustiveApiUsage<GetRequest> {

    static class MyGetRequest extends GetRequest {

      @UsageTracker.DoNotTrackConstructorInvocation
      MyGetRequest(Builder builder) {
        super(builder);
      }
    }

    @Override
    public Set<Class<?>> useApi() {
      GetRequest.Builder builder = GetRequest.newBuilder();
      GetRequest req = builder.build();
      boolean boolVal = req.equals(req);
      String strVal = req.toString();
      strVal = req.getStartId();
      boolVal = req.isIncludeStart();
      Boolean booleanVal = req.isReturningIdsOnly();
      int intVal = req.hashCode();
      intVal = req.getLimit();
      builder = GetRequest.newBuilder(req);
      req = new MyGetRequest(builder);
      return classes(Object.class);
    }
  }

  /**
   * Exhaustive use of {@link com.google.appengine.api.search.GetRequest.Builder}.
   */
  public static class GetRequestBuilderUsage extends ExhaustiveApiUsage<GetRequest.Builder> {

    static class MyBuilder extends GetRequest.Builder {

      @UsageTracker.DoNotTrackConstructorInvocation
      MyBuilder() {
      }
    }

    @Override
    public Set<Class<?>> useApi() {
      GetRequest.Builder builder = GetRequest.newBuilder();
      builder = new MyBuilder();
      builder = builder.setIncludeStart(true);
      Integer integerVal = 2;
      builder = builder.setLimit(integerVal);
      builder = builder.setReturningIdsOnly(true);
      builder = builder.setStartId("yar");
      GetRequest req = builder.build();
      return classes(Object.class);
    }
  }

  /**
   * Exhaustive use of {@link GetResponse}.
   */
  public static class GetResponseUsage extends ExhaustiveApiUsage<GetResponse<String>> {

    static class MyResponse extends GetResponse<String> {

      @UsageTracker.DoNotTrackConstructorInvocation
      MyResponse(List<String> results) {
        super(results);
      }
    }

    @Override
    public Set<Class<?>> useApi() {
      List<String> strList = Lists.newArrayList();
      GetResponse<String> resp = new MyResponse(strList);
      strList = resp.getResults();
      String strVal = resp.toString();
      Iterator<String> iterator = resp.iterator();
      return classes(Object.class, Iterable.class, Serializable.class);
    }
  }

  /**
   * Exhaustive use of {@link Index}.
   */
  public static class IndexUsage extends ExhaustiveApiInterfaceUsage<Index> {

    @Override
    protected Set<Class<?>> useApi(Index index) {
      Iterable<Document> documentIterable = null;
      Document.Builder docBuilder1 = null;
      Document.Builder docBuilder2 = null;
      PutResponse putResponse = index.put(documentIterable);
      putResponse = index.put(docBuilder1, docBuilder2);
      Document doc1 = null;
      Document doc2 = null;
      putResponse = index.put(doc1, doc2);
      Future<PutResponse> asyncAddResponse = index.putAsync(documentIterable);
      asyncAddResponse = index.putAsync(docBuilder1, docBuilder2);
      asyncAddResponse = index.putAsync(doc1, doc2);
      Iterable<String> docIds = null;
      index.delete("docId1", "docId2");
      index.delete(docIds);
      Future<Void> voidFuture = index.deleteAsync(docIds);
      voidFuture = index.deleteAsync("docId1", "docId2");
      index.deleteSchema();
      voidFuture = index.deleteSchemaAsync();
      doc1 = index.get("docId");
      String strVal = index.getName();
      strVal = index.getNamespace();
      GetRequest getReq = null;
      GetResponse<Document> getResponse = index.getRange(getReq);
      GetRequest.Builder builder = null;
      getResponse = index.getRange(builder);
      Future<GetResponse<Document>> getResponseFuture = index.getRangeAsync(getReq);
      getResponseFuture = index.getRangeAsync(builder);
      Schema schema = index.getSchema();
      try {
        long storageUsage = index.getStorageUsage();
      } catch (UnsupportedOperationException e) {
        // expected result
      }
      try {
        long storageLimit = index.getStorageLimit();
      } catch (UnsupportedOperationException e) {
        // expected result
      }
      Query query = null;
      Results<ScoredDocument> results = index.search(query);
      results = index.search("query");
      Future<Results<ScoredDocument>> resultsFuture = index.searchAsync(query);
      resultsFuture = index.searchAsync("query");
      return classes();
    }
  }

  /**
   * Exhaustive use of {@link IndexSpec}.
   */
  public static class IndexSpecUsage extends ExhaustiveApiUsage<IndexSpec> {

    @Override
    public Set<Class<?>> useApi() {
      IndexSpec.Builder builder = IndexSpec.newBuilder();
      IndexSpec spec = builder.build();
      boolean boolVal = spec.equals(spec);
      String strVal = spec.toString();
      strVal = spec.getName();
      int intVal = spec.hashCode();
      return classes(Object.class);
    }
  }

  /**
   * Exhaustive use of {@link com.google.appengine.api.search.IndexSpec.Builder}.
   */
  public static class IndexSpecBuilderUsage extends ExhaustiveApiUsage<IndexSpec.Builder> {

    @Override
    public Set<Class<?>> useApi() {
      IndexSpec.Builder builder = IndexSpec.newBuilder();
      builder = builder.setName("yar");
      IndexSpec spec = builder.build();
      return classes(Object.class);
    }
  }

  /**
   * Exhaustive use of {@link MatchScorer}.
   */
  public static class MatchScorerUsage extends ExhaustiveApiUsage<MatchScorer> {

    @Override
    public Set<Class<?>> useApi() {
      MatchScorer.Builder builder = MatchScorer.newBuilder();
      MatchScorer scorer = builder.build();
      String strVal = scorer.toString();
      return classes(Object.class);
    }
  }

  /**
   * Exhaustive use of {@link com.google.appengine.api.search.MatchScorer.Builder}.
   */
  public static class MatchScorerBuilderUsage extends ExhaustiveApiUsage<MatchScorer.Builder> {

    @Override
    public Set<Class<?>> useApi() {
      MatchScorer.Builder builder = MatchScorer.newBuilder();
      MatchScorer scorer = builder.build();
      return classes(Object.class);
    }
  }

  /**
   * Exhaustive use of {@link OperationResult}.
   */
  public static class OperationResultUsage extends ExhaustiveApiUsage<OperationResult> {

    @Override
    public Set<Class<?>> useApi() {
      OperationResult result = new OperationResult(StatusCode.OK, "yar");
      int hashCode = result.hashCode();
      boolean boolVal = result.equals(result);
      StatusCode statusCode = result.getCode();
      String strVal = result.getMessage();
      strVal = result.toString();
      return classes(Object.class, Serializable.class);
    }
  }

  /**
   * Exhaustive use of {@link PutResponse}.
   */
  public static class PutResponseUsage extends ExhaustiveApiUsage<PutResponse> {

    static class MyResponse extends PutResponse {

      @UsageTracker.DoNotTrackConstructorInvocation
      MyResponse(List<OperationResult> results, List<String> ids) {
        super(results, ids);
      }
    }

    @Override
    public Set<Class<?>> useApi() {
      List<OperationResult> resultList = Lists.newArrayList();
      List<String> ids = Lists.newArrayList();
      PutResponse resp = new MyResponse(resultList, ids);
      resultList = resp.getResults();
      String strVal = resp.toString();
      Iterator<OperationResult> iterator = resp.iterator();
      ids = resp.getIds();
      return classes(Object.class, Iterable.class, Serializable.class);
    }
  }

  /**
   * Exhaustive use of {@link Query}.
   */
  public static class QueryUsage extends ExhaustiveApiUsage<Query> {

    static class MyQuery extends Query {

      @UsageTracker.DoNotTrackConstructorInvocation
      MyQuery(Builder builder) {
        super(builder);
      }
    }

    @Override
    public Set<Class<?>> useApi() {
      Query.Builder builder = Query.newBuilder().setOptions(QueryOptions.newBuilder().build());
      Query query = builder.build("yar");
      FacetOptions facetOptions = query.getFacetOptions();
      Iterable<FacetRequest> facetRequestItr = query.getReturnFacets();
      Iterable<FacetRefinement> refItr = query.getRefinements();
      boolean boolVal = query.getEnableFacetDiscovery();
      QueryOptions opts = query.getOptions();
      String strVal = query.getQueryString();
      strVal = query.toString();
      query = new MyQuery(builder);
      builder = Query.newBuilder(query);
      return classes(Object.class);
    }
  }

  /**
   * Exhaustive use of {@link com.google.appengine.api.search.Query.Builder}.
   */
  public static class QueryBuilderUsage extends ExhaustiveApiUsage<Query.Builder> {

    static class MyBuilder extends Query.Builder {

      @UsageTracker.DoNotTrackConstructorInvocation
      MyBuilder() {
        setQueryString("yar");
      }
    }

    @Override
    public Set<Class<?>> useApi() {
      Query.Builder queryBuilder = Query.newBuilder();
      QueryOptions.Builder queryOptionsBuilder = QueryOptions.newBuilder();
      QueryOptions opts = queryOptionsBuilder.build();
      queryBuilder = queryBuilder.setOptions(opts);
      queryBuilder = queryBuilder.setOptions(queryOptionsBuilder);
      queryBuilder = queryBuilder.setEnableFacetDiscovery(true);
      queryBuilder = queryBuilder.addFacetRefinementFromToken(
          FacetRefinement.withValue("name", "value").toTokenString());
      queryBuilder = queryBuilder.addFacetRefinement(FacetRefinement.withValue("name", "value"));
      queryBuilder = queryBuilder.setFacetOptions(FacetOptions.newBuilder().build());
      queryBuilder = queryBuilder.setFacetOptions(FacetOptions.newBuilder());
      queryBuilder = queryBuilder.addReturnFacet(FacetRequest.newBuilder().setName("name").build());
      queryBuilder = queryBuilder.addReturnFacet(FacetRequest.newBuilder().setName("name"));
      queryBuilder = queryBuilder.addReturnFacet("name");
      Query query = queryBuilder.build("yar");
      query = queryBuilder.build();
      queryBuilder = new MyBuilder();
      return classes(Object.class);
    }
  }

  /**
   * Exhaustive use of {@link QueryOptions}.
   */
  public static class QueryOptionsUsage extends ExhaustiveApiUsage<QueryOptions> {

    @Override
    public Set<Class<?>> useApi() {
      QueryOptions.Builder optionsBuilder = QueryOptions.newBuilder();
      QueryOptions opts = optionsBuilder.build();
      List<String> strList = opts.getFieldsToReturn();
      Cursor cursor = opts.getCursor();
      List<FieldExpression> fieldExpList = opts.getExpressionsToReturn();
      int intVal = opts.getOffset();
      String strVal = opts.toString();
      strList = opts.getFieldsToSnippet();
      intVal = opts.getNumberFoundAccuracy();
      boolean boolVal = opts.hasNumberFoundAccuracy();
      intVal = opts.getLimit();
      boolVal = opts.isReturningIdsOnly();
      SortOptions sortOpts = opts.getSortOptions();
      QueryOptions.Builder builder = QueryOptions.newBuilder(opts);
      return classes(Object.class);
    }
  }

  /**
   * Exhaustive use of {@link com.google.appengine.api.search.QueryOptions.Builder}.
   */
  public static class QueryOptionsBuilderUsage extends ExhaustiveApiUsage<QueryOptions.Builder> {

    @Override
    public Set<Class<?>> useApi() {
      QueryOptions.Builder builder = QueryOptions.newBuilder();
      SortOptions.Builder sortOptionsBuilder = SortOptions.newBuilder();
      builder = builder.setSortOptions(sortOptionsBuilder);
      SortOptions sortOptions = sortOptionsBuilder.build();
      builder = builder.setSortOptions(sortOptions);
      FieldExpression.Builder fieldExpBuilder = FieldExpression.newBuilder().setName("yar");
      builder = builder.addExpressionToReturn(fieldExpBuilder);
      FieldExpression fieldExpr = fieldExpBuilder.build();
      builder = builder.addExpressionToReturn(fieldExpr);
      Cursor.Builder cursorBuilder = Cursor.newBuilder();
      builder = builder.setCursor(cursorBuilder);
      Cursor cursor = cursorBuilder.build();
      builder = builder.setCursor(cursor);
      builder = builder.setFieldsToReturn("f1", "f2", "f3");
      builder = builder.setFieldsToSnippet("f1", "f2", "f3");
      int intVal = 2;
      builder = builder.setLimit(intVal);
      builder = builder.setNumberFoundAccuracy(intVal);
      builder = QueryOptions.newBuilder();
      builder = builder.setReturningIdsOnly(false);
      builder = builder.clearNumberFoundAccuracy();
      builder = builder.setOffset(2);
      QueryOptions opts = builder.build();
      return classes(Object.class);
    }
  }

  /**
   * Exhaustive use of {@link ISearchServiceFactory}.
   */
  public static class ISearchServiceFactoryUsage extends
      ExhaustiveApiInterfaceUsage<ISearchServiceFactory> {

    @Override
    protected Set<Class<?>> useApi(ISearchServiceFactory theInterface) {
      SearchService ss = theInterface.getSearchService("yar");
      ss = theInterface.getSearchService(SearchServiceConfig.newBuilder().build());
      return classes();
    }
  }

  /**
   * Exhaustive use of {@link SearchServiceConfig}.
   */
  public static class SearchServiceConfigUsage extends
      ExhaustiveApiUsage<SearchServiceConfig> {

    @Override
    public Set<Class<?>> useApi() {
      SearchServiceConfig config = SearchServiceConfig.newBuilder().build();
      Double doubleVal = config.getDeadline();
      String stringVal = config.getNamespace();
      SearchServiceConfig.Builder builder = config.toBuilder();
      return classes(Object.class);
    }
  }

  /**
   * Exhaustive use of {@link SearchServiceConfig.Builder}.
   */
  public static class SearchServiceConfigBuilderUsage extends
      ExhaustiveApiUsage<SearchServiceConfig.Builder> {

    @Override
    public Set<Class<?>> useApi() {
      SearchServiceConfig.Builder builder = SearchServiceConfig.newBuilder();
      builder.setNamespace("yar");
      builder.setDeadline(10.0);
      SearchServiceConfig configVal = builder.build();
      return classes(Object.class);
    }
  }

  /**
   * Exhaustive use of {@link ISearchServiceFactoryProvider}.
   */
  public static class ISearchServiceFactoryProviderUsage extends
      ExhaustiveApiUsage<ISearchServiceFactoryProvider> {

    @Override
    public Set<Class<?>> useApi() {
      ISearchServiceFactoryProvider provider = new ISearchServiceFactoryProvider();
      return classes(Object.class, FactoryProvider.class, Comparable.class);
    }
  }

  /**
   * Exhaustive use of {@link RescoringMatchScorer}.
   */
  public static class RescoringMatchScorerUsage extends ExhaustiveApiUsage<RescoringMatchScorer> {
    @Override
    public Set<Class<?>> useApi() {
      RescoringMatchScorer scorer = RescoringMatchScorer.newBuilder().build();
      String strVal = scorer.toString();
      return classes(Object.class, MatchScorer.class);
    }
  }

  /**
   * Exhaustive use of {@link com.google.appengine.api.search.RescoringMatchScorer.Builder}.
   */
  public static class RescoringMatchScorerBuilderUsage extends
      ExhaustiveApiUsage<RescoringMatchScorer.Builder> {
    @Override
    public Set<Class<?>> useApi() {
      RescoringMatchScorer.Builder builder = RescoringMatchScorer.newBuilder();
      // TODO(b/79994182): see go/objecttostring-lsc
      @SuppressWarnings(
          "ObjectToString") // TODO(b/79994182): Builder does not implement toString() in builder
      String strVal = builder.toString();
      RescoringMatchScorer scorer = builder.build();
      return classes(Object.class, MatchScorer.Builder.class);
    }
  }

  /**
   * Exhaustive use of {@link Results}.
   */
  public static class ResultsUsage extends ExhaustiveApiUsage<Results<String>> {

    public static class MyResults extends Results<String> {

      @UsageTracker.DoNotTrackConstructorInvocation
      public MyResults(OperationResult operationResult, Collection<String> results,
          long numberFound, int numberReturned, Cursor cursor,
          Collection<FacetResult> facetResult) {
        super(operationResult, results, numberFound, numberReturned, cursor, facetResult);
      }

      @UsageTracker.DoNotTrackConstructorInvocation
      public MyResults(OperationResult operationResult, Collection<String> results,
          long numberFound, int numberReturned, Cursor cursor) {
        super(operationResult, results, numberFound, numberReturned, cursor);
      }
    }

    @Override
    public Set<Class<?>> useApi() {
      OperationResult opResult = new OperationResult(StatusCode.OK, "blar");
      Collection<String> resultColl = Lists.newArrayList();
      Collection<FacetResult> facetColl = Lists.newArrayList();
      Cursor cursor = null;
      Results<String> results = new MyResults(opResult, resultColl, 3, 3, cursor, facetColl);
      results = new MyResults(opResult, resultColl, 3, 3, cursor);
      String strVal = results.toString();
      cursor = results.getCursor();
      long longVal = results.getNumberFound();
      int intVal = results.getNumberReturned();
      resultColl = results.getResults();
      Iterator<String> iter = results.iterator();
      opResult = results.getOperationResult();
      Iterable<FacetResult> facetResultItr = results.getFacets();
      return classes(Object.class, Iterable.class, Serializable.class);
    }
  }

  /**
   * Exhaustive use of {@link Schema}.
   */
  public static class SchemaUsage extends ExhaustiveApiUsage<Schema> {
    @Override
    public Set<Class<?>> useApi() {
      Schema schema = Schema.newBuilder().addTypedField("yar", Field.FieldType.TEXT).build();
      String strVal = schema.toString();
      boolean boolVal = schema.equals(schema);
      Set<String> strSet = schema.getFieldNames();
      List<Field.FieldType> fieldTypes = schema.getFieldTypes("yar");
      int intVal = schema.hashCode();
      return classes(Object.class);
    }
  }

  /**
   * Exhaustive use of {@link com.google.appengine.api.search.Schema.Builder}.
   */
  public static class SchemaBuilderUsage extends ExhaustiveApiUsage<Schema.Builder> {
    @Override
    public Set<Class<?>> useApi() {
      Schema.Builder builder = Schema.newBuilder();
      builder = builder.addTypedField("yar", Field.FieldType.TEXT);
      Schema schema = builder.build();
      return classes(Object.class);
    }
  }

  /**
   * Exhaustive use of {@link ScoredDocument}.
   */
  public static class ScoredDocumentUsage extends ExhaustiveApiUsage<ScoredDocument> {
    @Override
    public Set<Class<?>> useApi() {
      ScoredDocument document = ScoredDocument.newBuilder().build();
      Cursor cursor = document.getCursor();
      String strVal = document.toString();
      List<Field> expressions = document.getExpressions();
      List<Double> sortScores = document.getSortScores();
      return classes(Object.class, Document.class, Serializable.class);
    }
  }

  /**
   * Exhaustive use of {@link com.google.appengine.api.search.ScoredDocument.Builder}.
   */
  public static class ScoredDocumentBuilderUsage extends
      ExhaustiveApiUsage<ScoredDocument.Builder> {
    @Override
    public Set<Class<?>> useApi() {
      ScoredDocument.Builder builder = ScoredDocument.newBuilder();
      Field field = Field.newBuilder().setName("yar").build();
      builder = builder.addExpression(field);
      double score = 23.0;
      builder = builder.addScore(score);
      Cursor cursor = null;
      builder = builder.setCursor(cursor);
      ScoredDocument document = builder.build();
      return classes(Object.class, Document.Builder.class);
    }
  }

  /**
   * Exhaustive use of {@link SortExpression}.
   */
  public static class SortExpressionUsage extends ExhaustiveApiUsage<SortExpression> {
    String ___apiConstant_SCORE_FIELD_NAME;
    String ___apiConstant_TIMESTAMP_FIELD_NAME;
    String ___apiConstant_DOCUMENT_ID_FIELD_NAME;
    String ___apiConstant_LANGUAGE_FIELD_NAME;
    String ___apiConstant_RANK_FIELD_NAME;

    @Override
    public Set<Class<?>> useApi() {
      SortExpression expr = SortExpression.newBuilder().setExpression("yar").setDefaultValue("yar")
          .build();
      String strVal = expr.getDefaultValue();
      Date dateVal = expr.getDefaultValueDate();
      Double doubleVal = expr.getDefaultValueNumeric();
      SortExpression.SortDirection dir = expr.getDirection();
      strVal = expr.getExpression();
      strVal = expr.toString();
      ___apiConstant_SCORE_FIELD_NAME = SortExpression.SCORE_FIELD_NAME;
      ___apiConstant_TIMESTAMP_FIELD_NAME = SortExpression.TIMESTAMP_FIELD_NAME;
      ___apiConstant_DOCUMENT_ID_FIELD_NAME = SortExpression.DOCUMENT_ID_FIELD_NAME;
      ___apiConstant_LANGUAGE_FIELD_NAME = SortExpression.LANGUAGE_FIELD_NAME;
      ___apiConstant_RANK_FIELD_NAME = SortExpression.RANK_FIELD_NAME;
      return classes(Object.class);
    }
  }

  /**
   * Exhaustive use of {@link com.google.appengine.api.search.SortExpression.Builder}.
   */
  public static class SortExpressionBuilderUsage extends
      ExhaustiveApiUsage<SortExpression.Builder> {
    @Override
    public Set<Class<?>> useApi() {
      SortExpression.Builder builder = SortExpression.newBuilder();
      builder = builder.setDefaultValue("yar");
      builder = SortExpression.newBuilder();
      builder = builder.setDefaultValueDate(new Date());
      builder = SortExpression.newBuilder();
      builder = builder.setDefaultValueNumeric(23d);
      builder = builder.setDirection(SortExpression.SortDirection.ASCENDING);
      builder = builder.setExpression("yar");
      SortExpression expr = builder.build();
      return classes(Object.class);
    }
  }

  /**
   * Exhaustive use of {@link com.google.appengine.api.search.SortExpression.SortDirection}.
   */
  public static class SortDirectionUsage extends ExhaustiveApiUsage<SortExpression.SortDirection> {
    @Override
    public Set<Class<?>> useApi() {
      SortExpression.SortDirection dir = SortExpression.SortDirection.ASCENDING;
      dir = SortExpression.SortDirection.DESCENDING;
      dir = SortExpression.SortDirection.valueOf("ASCENDING");
      SortExpression.SortDirection[] values = SortExpression.SortDirection.values();
      return classes(Enum.class, Serializable.class, Object.class, Comparable.class);
    }
  }

  /**
   * Exhaustive use of {@link StatusCode}.
   */
  public static class StatusCodeUsage extends ExhaustiveApiUsage<StatusCode> {
    @Override
    public Set<Class<?>> useApi() {
      StatusCode code = StatusCode.OK;
      code = StatusCode.INTERNAL_ERROR;
      code = StatusCode.INVALID_REQUEST;
      code = StatusCode.PERMISSION_DENIED_ERROR;
      code = StatusCode.TRANSIENT_ERROR;
      code = StatusCode.TIMEOUT_ERROR;
      code = StatusCode.CONCURRENT_TRANSACTION_ERROR;
      code = StatusCode.valueOf("OK");
      StatusCode[] values = StatusCode.values();
      return classes(Enum.class, Serializable.class, Object.class, Comparable.class);
    }
  }

  /**
   * Exhaustive use of {@link SortOptions}.
   */
  public static class SortOptionsUsage extends ExhaustiveApiUsage<SortOptions> {

    @Override
    public Set<Class<?>> useApi() {
      SortOptions opts = SortOptions.newBuilder().build();
      String strVal = opts.toString();
      int intVal = opts.getLimit();
      MatchScorer scorer = opts.getMatchScorer();
      List<SortExpression> sortExpressions = opts.getSortExpressions();
      return classes(Object.class);
    }
  }

  /**
   * Exhaustive use of {@link com.google.appengine.api.search.SortOptions.Builder}.
   */
  public static class SortOptionsBuilderUsage extends ExhaustiveApiUsage<SortOptions.Builder> {
    @Override
    public Set<Class<?>> useApi() {
      SortOptions.Builder builder = SortOptions.newBuilder();
      builder = builder.setLimit(23);
      SortExpression.Builder sortExprBuilder = SortExpression.newBuilder();
      sortExprBuilder.setExpression("yar");
      sortExprBuilder.setDefaultValue("yar");
      SortExpression expr = sortExprBuilder.build();
      builder = builder.addSortExpression(sortExprBuilder);
      builder = builder.addSortExpression(expr);
      MatchScorer.Builder matchScorerBuilder = MatchScorer.newBuilder();
      MatchScorer scorer = matchScorerBuilder.build();
      builder = builder.setMatchScorer(matchScorerBuilder);
      builder = builder.setMatchScorer(scorer);
      RescoringMatchScorer.Builder rescoringMatchScorerBuilder = RescoringMatchScorer.newBuilder();
      builder = builder.setMatchScorer(rescoringMatchScorerBuilder);
      SortOptions opts = builder.build();
      return classes(Object.class);
    }
  }
}
