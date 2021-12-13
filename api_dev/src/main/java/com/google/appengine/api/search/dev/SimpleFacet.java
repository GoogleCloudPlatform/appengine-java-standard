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

package com.google.appengine.api.search.dev;

import com.google.appengine.api.search.proto.SearchServicePb;
import com.google.appengine.api.search.proto.SearchServicePb.FacetRange;
import com.google.appengine.api.search.proto.SearchServicePb.FacetRefinement;
import com.google.appengine.api.search.proto.SearchServicePb.FacetRequest;
import com.google.appengine.api.search.proto.SearchServicePb.FacetResult;
import com.google.appengine.api.search.proto.SearchServicePb.FacetResultValue;
import com.google.appengine.api.search.proto.SearchServicePb.SearchParams;
import com.google.apphosting.api.search.DocumentPb;
import com.google.apphosting.api.search.DocumentPb.FacetValue.ContentType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Fieldable;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.NumericRangeQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;

/**
 * Simple facet information aggregator implementation.
 */
final class SimpleFacet {
  private final Map<String, FacetRequest> manualFacetRequests = new LinkedHashMap<>();
  private final Map<String, FacetNode> manualFacetNodes = new LinkedHashMap<>();
  private final Map<String, FacetNode> discoveredFacetNodes = new LinkedHashMap<>();
  private final SearchParams searchParams;

  private SimpleFacet(SearchParams params) {
    searchParams = params;
  }

  static boolean isFacetingRequested(SearchParams searchParams) {
    return searchParams.getAutoDiscoverFacetCount() > 0 || searchParams.getIncludeFacetCount() > 0;
  }

  static FacetResult[] getFacetResult(SearchParams searchParams, Scorer.Result[] results) {
    return new SimpleFacet(searchParams).getFacetResult(results);
  }

  private FacetResult[] getFacetResult(Scorer.Result[] results) {
    preprocessManualFacets();
    // Aggegate facet information into discovered_facets and manual_facets by iterating through
    // all results up to facet_depth param, and all facets in each of them.
    for (Scorer.Result result : results) {
      Document doc = result.doc;
      for (Object fieldObject : doc.getFields()) {
        Fieldable field = (Fieldable) fieldObject;
        if (LuceneUtils.isFacetField(field)) {
          processSingleFacet(LuceneUtils.convertLuceneFieldToFacet(field));
        }
      }
    }
    // return top auto_discover_facet_count result of discovered facets and all manual facets.
    FacetResult[] ret = new FacetResult[
        Math.min(discoveredFacetNodes.values().size(), searchParams.getAutoDiscoverFacetCount())
        + manualFacetNodes.values().size()];
    int pointer = 0;
    for (FacetNode node : manualFacetNodes.values()) {
      ret[pointer++] = convertFacetNodeToFacetResult(node);
    }
    for (FacetNode node : getTopN(
        discoveredFacetNodes.values(), searchParams.getAutoDiscoverFacetCount())) {
      ret[pointer++] = convertFacetNodeToFacetResult(node);
    }
    return ret;
  }

  private FacetResult convertFacetNodeToFacetResult(FacetNode node) {
    // is this a number facet with min/max values (all number facets should have min and max)?
    if (node.getMin() != null) {
      node.addValue(getFacetLabelForRange(node.getMin(), node.getMax()), node.getMinMaxCount(),
          FacetRange.newBuilder().setStart(Double.toString(node.getMin()))
          .setEnd(Double.toString(node.getMax())).build());
    }
    FacetResult.Builder resultBuilder = FacetResult.newBuilder();
    resultBuilder.setName(node.name);
    for (FacetNode.Value value : getTopN(node.getValues(), node.valueLimit)) {
      FacetResultValue.Builder valueBuilder = resultBuilder.addValueBuilder();
      FacetRefinement.Builder refBuilder = valueBuilder.getRefinementBuilder();
      // if this value needs special threatment for refinement. This is only the case for
      // values resulted from a range.
      if (value.range != null) {
        if (value.range.hasStart()) {
          refBuilder.getRangeBuilder().setStart(
              Double.toString(Double.parseDouble(value.range.getStart())));
        }
        if (value.range.hasEnd()) {
          refBuilder.getRangeBuilder().setEnd(
              Double.toString(Double.parseDouble(value.range.getEnd())));
        }
      } else {
        // Fill refinement in the normal case where value label is the actual value expected.
        refBuilder.setValue(value.label);
      }
      refBuilder.setName(node.name);
      valueBuilder.setName(value.label);
      valueBuilder.setCount(value.getCount());
    }
    return resultBuilder.build();
  }

  /**
   * Returns top N objects from the provided collection of comparables.
   */
  private <T extends Comparable<T>> List<T> getTopN(Collection<T> items, int n) {
    ArrayList<T> ret = new ArrayList<>(items);
    Collections.sort(ret);
    if (ret.size() > n) {
      return ret.subList(0, n);
    } else {
      return ret;
    }
  }

  /**
   * Create a map for manual facets to be accessed faster by the name later.
   */
  private void preprocessManualFacets() {
    for (FacetRequest manualFacet : searchParams.getIncludeFacetList()) {
      manualFacetRequests.put(manualFacet.getName(), manualFacet);
      // Validate request to make sure only range or value_constraint is provided, not both.
      if (!manualFacet.getParams().getRangeList().isEmpty()
          && !manualFacet.getParams().getValueConstraintList().isEmpty()) {
        throw new RuntimeException("Manual facet request should either specify range or "
            + "value constraint, not both");
      }
      FacetNode node = new FacetNode(manualFacet.getName(),
          manualFacet.getParams().hasValueLimit() ? manualFacet.getParams().getValueLimit()
          : searchParams.getFacetAutoDetectParam().getValueLimit());
      manualFacetNodes.put(node.name, node);
      // Initilize facets with zero values for all values to make sure we return them even
      // if the count is zero.
      for (String value : manualFacet.getParams().getValueConstraintList()) {
        node.addValue(value, 0);
      }
      // Initilize ranges with zero values to make sure ranges with zero value will be
      // included in the result.
      for (FacetRange range : manualFacet.getParams().getRangeList()) {
        Double start = range.hasStart() ? Double.parseDouble(range.getStart()) : null;
        Double end = range.hasEnd() ? Double.parseDouble(range.getEnd()) : null;
        node.addValue(getFacetLabelForRange(start, end), 0, range);
      }
    }
  }

  /**
   * Process a single name,value facet and add it to aggregated facet informations
   */
  private void processSingleFacet(DocumentPb.Facet facet) {
    String facetName = facet.getName();
    String facetValue = facet.getValue().getStringValue();
    ContentType facetType = facet.getValue().getType();
    FacetRequest manualFacet = manualFacetRequests.get(facetName);
    if (facetType == ContentType.ATOM) {
      // A manual requested facet?
      if (manualFacet != null) {
        FacetNode manualFacetNode = manualFacetNodes.get(manualFacet.getName());
        // count the value if there is no range (range is not supported for atom facet) and
        // either no value_constraint or the value is in that constraint list.
        if (manualFacet.getParams().getRangeList().isEmpty()
            && (manualFacet.getParams().getValueConstraintList().isEmpty()
                || manualFacet.getParams().getValueConstraintList().contains(facetValue))) {
          manualFacetNode.addValue(facetValue, 1);
        }
      } else if (searchParams.getAutoDiscoverFacetCount() > 0) {
        FacetNode discoveredFacetNode = discoveredFacetNodes.get(facetName);
        if (discoveredFacetNode == null) {
          discoveredFacetNode = new FacetNode(
              facetName, searchParams.getFacetAutoDetectParam().getValueLimit());
          discoveredFacetNodes.put(facetName, discoveredFacetNode);
        }
        discoveredFacetNode.addValue(facetValue, 1, null);
      }
    } else if (facetType == ContentType.NUMBER) {
      double facetValueDouble = Double.parseDouble(facetValue);
      if (manualFacet != null) {
        FacetNode manualFacetNode = manualFacetNodes.get(manualFacet.getName());
        if (!manualFacet.getParams().getRangeList().isEmpty()) {
          for (FacetRange range : manualFacet.getParams().getRangeList()) {
            Double start = range.hasStart() ? Double.parseDouble(range.getStart()) : null;
            Double end = range.hasEnd() ? Double.parseDouble(range.getEnd()) : null;
            if ((start == null || facetValueDouble >= start)
                && (end == null || facetValueDouble < end)) {
              manualFacetNode.addValue(getFacetLabelForRange(start, end), 1, range);
            }
          }
        } else if (!manualFacet.getParams().getValueConstraintList().isEmpty()) {
          for (String constraint : manualFacet.getParams().getValueConstraintList()) {
            if (Double.parseDouble(facetValue) == Double.parseDouble(constraint)) {
              manualFacetNode.addValue(constraint, 1);
            }
          }
        } else {
              manualFacetNode.addNumericValue(Double.parseDouble(facetValue));
        }
      }  else if (searchParams.getAutoDiscoverFacetCount() > 0) {
        FacetNode discoveredFacetNode = discoveredFacetNodes.get(facetName);
        if (discoveredFacetNode == null) {
          discoveredFacetNode = new FacetNode(
              facetName, searchParams.getFacetAutoDetectParam().getValueLimit());
          discoveredFacetNodes.put(facetName, discoveredFacetNode);
        }
        discoveredFacetNode.addNumericValue(Double.parseDouble(facetValue));
      }
    } else {
      throw new RuntimeException("Facet type is not supported : " + facetType);
    }
  }

  private String getFacetLabelForRange(Double start, Double end) {
    return String.format("[%s,%s)", start != null ? Double.toString(start) : "-Infinity",
        end != null ? Double.toString(end) : "Infinity");
  }

  /**
   * Creates and return a lucene query for facet refinement. This query can be combined with
   * user provided query to filter the result based on FacetRefinement requests.
   */
  static Query getRefinementQuery(SearchParams params) {
    List<SearchServicePb.FacetRefinement> refinements = params.getFacetRefinementList();
    BooleanQuery rootQuery = new BooleanQuery();
    for (List<SearchServicePb.FacetRefinement> group : getFacetRefinementsByName(refinements)) {

      List<Query> groupQueries = new ArrayList<>();
      for (SearchServicePb.FacetRefinement refinement : group) {
        if (refinement.hasValue() && refinement.hasRange()) {
          throw new RuntimeException("Refinement request for facet "
              + refinement.getName()
              + " should either specify range or value constraint, not both.");
        } else if (!refinement.hasValue() && !refinement.hasRange()) {
          throw new RuntimeException("Refinement request for facet "
              + refinement.getName() + " should specify range or value constraint.");
        }
        if (refinement.hasValue()) {
          groupQueries.add(new TermQuery(
              new Term(LuceneUtils.makeLuceneFieldName(
                  refinement.getName(), ContentType.ATOM), refinement.getValue())));
          try {
            groupQueries.add(NumericRangeQuery.newDoubleRange(
                LuceneUtils.makeLuceneFieldName(
                    refinement.getName(), ContentType.NUMBER),
                Double.parseDouble(refinement.getValue()),
                Double.parseDouble(refinement.getValue()),
                true, true));
          } catch (NumberFormatException e) {
            // ignore values that are not in numeric format.
          }
        } else {
          Double start = refinement.getRange().hasStart()
              ? Double.parseDouble(refinement.getRange().getStart()) : null;
          Double end = refinement.getRange().hasEnd()
              ? Double.parseDouble(refinement.getRange().getEnd()) : null;
          groupQueries.add(NumericRangeQuery.newDoubleRange(
              LuceneUtils.makeLuceneFieldName(refinement.getName(), ContentType.NUMBER),
              start, end, true, false));
        }
      }
      rootQuery.add(getDisjunction(groupQueries), BooleanClause.Occur.MUST);
    }
    return rootQuery;
  }

  /**
   * Group facets with the same name to process them together.
   */
  private static Collection<List<SearchServicePb.FacetRefinement>> getFacetRefinementsByName(
      List<SearchServicePb.FacetRefinement> refinements) {
    Map<String, List<SearchServicePb.FacetRefinement>> refinementGroups = new TreeMap<>();

    for (SearchServicePb.FacetRefinement refinement : refinements) {
      List<SearchServicePb.FacetRefinement> group = refinementGroups.get(refinement.getName());
      if (group == null) {
        group = new ArrayList<>();
        refinementGroups.put(refinement.getName(), group);
      }
      group.add(refinement);
    }
    return refinementGroups.values();
  }

  private static Query getDisjunction(Iterable<? extends Query> queries) {
    BooleanQuery query = new BooleanQuery();
    for (Query q : queries) {
      query.add(q, BooleanClause.Occur.SHOULD);
    }
    return query;
  }
}

