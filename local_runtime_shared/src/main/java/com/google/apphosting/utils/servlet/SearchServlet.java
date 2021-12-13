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

package com.google.apphosting.utils.servlet;

import com.google.appengine.api.search.Document;
import com.google.appengine.api.search.Field;
import com.google.appengine.api.search.GetIndexesRequest;
import com.google.appengine.api.search.GetRequest;
import com.google.appengine.api.search.GetResponse;
import com.google.appengine.api.search.Index;
import com.google.appengine.api.search.IndexSpec;
import com.google.appengine.api.search.Query;
import com.google.appengine.api.search.QueryOptions;
import com.google.appengine.api.search.Results;
import com.google.appengine.api.search.ScoredDocument;
import com.google.appengine.api.search.SearchService;
import com.google.appengine.api.search.SearchServiceFactory;
import com.google.appengine.api.search.StatusCode;
import com.google.apphosting.api.ApiProxy;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Handler for the Full Text Search viewer:
 * List indexes, list documents, view document, delete document.
 *
 */
@SuppressWarnings("serial")
public class SearchServlet extends HttpServlet {
  private static final String APPLICATION_NAME = "applicationName";

  private static final String SUBSECTION = "subsection";

  private static final String NAMESPACE = "namespace";

  private static final String INDEX_NAME = "indexName";

  private static final String QUERY = "query";

  private static final String DOC_ID = "docid";

  private static final String MATCHED_COUNT = "matchedCount";

  private static final String PREV_LINK = "prev";

  private static final String DOCUMENT = "document";

  private static final String FIELDS = "fields";

  private static final String FIELD_NAMES = "fieldNames";

  private static final String CURRENT_LINK = "current";

  private static final String START = "start";

  private static final String END = "end";

  private static final String DOC = "doc";

  private static final String NUM_PER_PAGE = "numPerPage";

  private static final String DOCUMENTS = "documents";

  private static final String INDEXES = "indexes";

  private static final String START_BASE_URL = "startBaseURL";

  private static final String DELETE_ACTION = "Delete";

  private static final String ACTION = "action";

  private static final String NUM_DOCS = "numdocs";

  private static final String PAGES = "pages";

  private static final String CURRENT_PAGE = "currentPage";

  private static final String PREV_START = "prevStart";

  private static final String NEXT_START = "nextStart";

  private static final String ERROR_MESSAGE = "errorMessage";

  private static final int MAX_PAGER_LINKS = 8;

  private static final Logger logger = Logger.getLogger(DatastoreViewerServlet.class.getName());

  /**
   * Requests to this servlet contain an optional subsection parameter
   * that we use to determine which view we need to gather data for.
   * The indexes list is the default subsection, so requests
   * without a subsection parameter are treated as indexes list
   * requests.
   */
  private enum Subsection {
    searchIndexesList,
    searchIndex,
    searchDocument
  }

  /**
   * URL encode the given string in UTF-8.
   */
  private static String urlencode(String val) throws UnsupportedEncodingException {
    return URLEncoder.encode(val, "UTF-8");
  }

  /**
   * Get the int value of the given param from the given request, returning the
   * given default value if the param does not exist or the value of the param
   * cannot be parsed into an int.
   */
  private static int getIntParam(ServletRequest request, String paramName, int defaultVal) {
    String val = request.getParameter(paramName);
    try {
      // Throws NFE if null, which is what we want.
      return Integer.parseInt(val);
    } catch (NumberFormatException nfe) {
      return defaultVal;
    }
  }

  /**
   * Get string value from parameter. Default value will be return if no such parameter found.
   */
  private static String getStringParam(
      ServletRequest request, String paramName, String defaultVal) {
    String val = request.getParameter(paramName);
    if (val == null) {
      val = defaultVal;
    }
    return val;
  }

  private static String getPrevLink(ServletRequest request, String defaultVal) {
    String val = request.getParameter(PREV_LINK);
    if (val == null || !val.startsWith("/")) {
      val = defaultVal;
    }
    return val;
  }

  /**
   * Returns the result of {@link HttpServletRequest#getRequestURI()} with the
   * values of all the params in {@code args} appended.
   */
  private static String filterURL(HttpServletRequest req, String... paramsToInclude)
      throws UnsupportedEncodingException {
    StringBuilder sb = new StringBuilder(req.getRequestURI() + "?");
    for (String arg : paramsToInclude) {
      String value = req.getParameter(arg);
      if (value != null) {
        sb.append(String.format("&%s=%s", arg, urlencode(value)));
      }
    }
    return sb.toString();
  }

  private String makeErrorMessage(Object msg) {
    if (msg == null) {
      return "Error: unknown error occurred";
    }
    return "Error: " + msg;
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    String subsectionStr = req.getParameter(SUBSECTION);
    // Indexes list is the default subsection
    Subsection subsection = Subsection.searchIndexesList;
    if (subsectionStr != null) {
      subsection = Subsection.valueOf(subsectionStr);
    }
    switch (subsection) {
      case searchIndexesList:
        doGetIndexesList(req, resp);
        break;
      case searchIndex:
        doGetIndex(req, resp);
        break;
      case searchDocument:
        doGetDocument(req, resp);
        break;
      default:
        resp.sendError(404);
    }
  }

  private void doGetIndexesList(HttpServletRequest req, HttpServletResponse resp)
      throws IOException {
    int start = getIntParam(req, START, 0);
    int numPerPage = getIntParam(req, NUM_PER_PAGE, 10);
    String requestedNamespace = req.getParameter(NAMESPACE);
    List<Index> indexes = null;

    // Empty namespace parameter equals to no namespace specified.
    String namespace = requestedNamespace != null ? requestedNamespace : "";
    boolean hasMore = false;

    try {
      SearchService search = SearchServiceFactory.getSearchService(namespace);
      GetIndexesRequest.Builder searchRequest = GetIndexesRequest.newBuilder();
      searchRequest.setIncludeStartIndex(true);
      searchRequest.setLimit(numPerPage + 1);
      searchRequest.setOffset(start);
      GetResponse<Index> searchResponse = search.getIndexes(searchRequest.build());
      indexes = searchResponse.getResults();

      // Check if we have more indexes and remove extra one if needed.
      if (indexes.size() > numPerPage) {
        hasMore = true;
        indexes = new ArrayList<Index>(indexes);
        indexes.remove(numPerPage - 1);
      }

      // Fill in paging parameters.
      int currentPage = start / numPerPage;
      req.setAttribute(END, start + indexes.size());
      req.setAttribute(PREV_START, currentPage > 0 ? (currentPage - 1) * numPerPage : -1);
      req.setAttribute(NEXT_START, hasMore ? (currentPage + 1) * numPerPage : -1);
      req.setAttribute(INDEXES, indexes);
    } catch (RuntimeException e) {
      logger.log(Level.SEVERE, "failed to retrieve indexes list", e);
      req.setAttribute(ERROR_MESSAGE, makeErrorMessage(e.getMessage()));
    }

    // Set common parameters.
    setCommonAttributes(req, namespace);
    req.setAttribute(CURRENT_LINK, urlencode(
        filterURL(req, NAMESPACE, START, NUM_PER_PAGE)));
    req.setAttribute(START_BASE_URL, filterURL(req, NAMESPACE, NUM_PER_PAGE));

    try {
      getServletContext().getRequestDispatcher(
          "/_ah/adminConsole?subsection=" + Subsection.searchIndexesList.name()).forward(req, resp);
    } catch (ServletException e) {
      throw new RuntimeException("Could not forward request", e);
    }
  }

  private void fillInSearchResults(
      HttpServletRequest req, Results<ScoredDocument> searchResponse, int start, int numPerPage) {
    Collection<ScoredDocument> searchResults = searchResponse.getResults();
    long matchedCount = searchResponse.getNumberFound();

    // Collect field names
    Set<String> fieldNames = new HashSet<String>();
    for (ScoredDocument result : searchResults) {
      for (Field field : result.getFields()) {
        fieldNames.add(field.getName());
      }
    }
    List<String> sortedFieldNames = new ArrayList<String>(fieldNames);
    Collections.sort(sortedFieldNames);
    req.setAttribute(FIELD_NAMES, sortedFieldNames);

    List<DocumentView> docViews = new ArrayList<DocumentView>();

    for (ScoredDocument result : searchResults) {
      docViews.add(new DocumentView(result, sortedFieldNames));
    }
    req.setAttribute(DOCUMENTS, docViews);

    // Set paging attributes.
    int currentPage = start / numPerPage;
    int numPages = (int) Math.ceil(matchedCount * (1.0 / numPerPage));
    int pageStart = (int) Math.max(Math.floor(currentPage - (MAX_PAGER_LINKS / 2)), 0);
    int pageEnd = Math.min(pageStart + MAX_PAGER_LINKS, numPages);
    List<Page> pages = new ArrayList<Page>();
    for (int i = pageStart + 1; i < pageEnd + 1; i++) {
      pages.add(new Page(i, (i - 1) * numPerPage));
    }
    req.setAttribute(END, start + searchResults.size());
    req.setAttribute(MATCHED_COUNT, matchedCount);
    req.setAttribute(PAGES, pages);
    req.setAttribute(CURRENT_PAGE, currentPage + 1);
    req.setAttribute(PREV_START, currentPage > 0 ? (currentPage - 1) * numPerPage : -1);
    req.setAttribute(NEXT_START, currentPage < numPages - 1 ? (currentPage + 1) * numPerPage : -1);
  }

  private void doGetIndex(HttpServletRequest req, HttpServletResponse resp)
      throws IOException {
    int start = getIntParam(req, START, 0);
    int numPerPage = getIntParam(req, NUM_PER_PAGE, 10);
    String indexName = req.getParameter(INDEX_NAME);
    String requestedNamespace = req.getParameter(NAMESPACE);
    String query = getStringParam(req, QUERY, "");

    // Empty namespace parameter equals to no namespace specified.
    String namespace = requestedNamespace != null ? requestedNamespace : "";

    try {
      SearchService search = SearchServiceFactory.getSearchService(namespace);
      Index index = search.getIndex(IndexSpec.newBuilder().setName(indexName));
      Query searchRequest = Query.newBuilder()
          .setOptions(QueryOptions.newBuilder()
              .setLimit(numPerPage)
              .setOffset(start))
          .build(query);
      Results<ScoredDocument> searchResponse = index.search(searchRequest);
      if (searchResponse.getOperationResult().getCode() == StatusCode.OK) {
        fillInSearchResults(req, searchResponse, start, numPerPage);
      } else {
        req.setAttribute(
            ERROR_MESSAGE, makeErrorMessage(searchResponse.getOperationResult().getMessage()));
      }
    } catch (RuntimeException e) {
      logger.log(Level.SEVERE, "failed to retrieve documents list", e);
      req.setAttribute(ERROR_MESSAGE, makeErrorMessage(e.getMessage()));
    }

    // Set common attributes.
    setCommonAttributes(req, requestedNamespace);
    req.setAttribute(INDEX_NAME, indexName);
    req.setAttribute(QUERY, query);
    req.setAttribute(
        START_BASE_URL,
        filterURL(req, SUBSECTION, NAMESPACE, INDEX_NAME, QUERY, NUM_PER_PAGE, PREV_LINK));
    req.setAttribute(PREV_LINK, getPrevLink(req, String.format(
        "/_ah/admin/search?namespace=%s", requestedNamespace)));
    req.setAttribute(CURRENT_LINK, urlencode(
        filterURL(req, SUBSECTION, NAMESPACE, INDEX_NAME, QUERY, START, NUM_PER_PAGE, PREV_LINK)));
    resp.setContentType("text/html; charset=UTF-8");

    String url = String.format(
        "/_ah/adminConsole?subsection=%s&indexName=%s&namespace=%s",
        Subsection.searchIndex.name(), indexName, namespace);
    try {
      getServletContext().getRequestDispatcher(url).forward(req, resp);
    } catch (ServletException e) {
      throw new RuntimeException("Could not forward request", e);
    }
  }

  private void doGetDocument(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    // Empty namespace parameter equals to no namespace specified
    String requestedNamespace = req.getParameter(NAMESPACE);
    String namespace = requestedNamespace != null ? requestedNamespace : "";
    String indexName = req.getParameter(INDEX_NAME);
    String docId = req.getParameter(DOC_ID);

    Document doc = null;
    List<FieldView> fields = new ArrayList<FieldView>();

    try {
      SearchService search = SearchServiceFactory.getSearchService(namespace);
      Index index = search.getIndex(IndexSpec.newBuilder().setName(indexName));
      GetRequest getRequest = GetRequest.newBuilder()
          .setLimit(1)
          .setStartId(docId)
          .setIncludeStart(true)
          .build();
      GetResponse<Document> getResponse = index.getRange(getRequest);
      Iterator<Document> it = getResponse.iterator();

      if (it.hasNext()) {
        doc = it.next();
      }
      if (doc != null && docId.equals(doc.getId())) {
        for (Field field : doc.getFields()) {
          fields.add(new FieldView(field));
        }
      } else {
        doc = null;
        req.setAttribute(ERROR_MESSAGE, "Document is not found");
      }
    } catch (RuntimeException e) {
      logger.log(Level.SEVERE, "failed to retrieve document", e);
      req.setAttribute(ERROR_MESSAGE, makeErrorMessage(e.getMessage()));
    }

    setCommonAttributes(req, requestedNamespace);
    req.setAttribute(PREV_LINK, getPrevLink(req, String.format(
        "/_ah/admin/search?subsection=%s&namespace=%s&indexName=%s",
        Subsection.searchIndex.name(), requestedNamespace, indexName)));
    req.setAttribute(DOCUMENT, doc);
    req.setAttribute(FIELDS, fields);
    resp.setContentType("text/html; charset=UTF-8");

    try {
      getServletContext().getRequestDispatcher(
          "/_ah/adminConsole?subsection=" + Subsection.searchDocument.name()).forward(req, resp);
    } catch (ServletException e) {
      throw new RuntimeException("Could not forward request", e);
    }
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp)
      throws IOException {
    if (DELETE_ACTION.equals(req.getParameter(ACTION))) {
      deleteDocuments(req, resp);
    } else {
      resp.sendError(404);
    }
  }

  private void deleteDocuments(HttpServletRequest req, HttpServletResponse resp)
      throws IOException {

    String indexName = req.getParameter(INDEX_NAME);
    String namespace = req.getParameter(NAMESPACE);
    String message;

    List<String> docIds = new ArrayList<String>();
    int numDocs = Integer.parseInt(req.getParameter(NUM_DOCS));
    for (int i = 1; i <= numDocs; i++) {
      String docId = req.getParameter(DOC + i);
      if (docId != null) {
        docIds.add(docId);
      }
    }

    try {
      SearchService search = SearchServiceFactory.getSearchService(namespace);
      Index index = search.getIndex(IndexSpec.newBuilder().setName(indexName));
      index.delete(docIds);
      message = String.format(
          "%d document%s deleted.", docIds.size(), docIds.size() == 1 ? "" : "s");
    } catch (RuntimeException e) {
      logger.log(Level.SEVERE, "failed to retrieve documents list", e);
      message = makeErrorMessage(e.getMessage());
    }

    resp.sendRedirect(String.format("%s&msg=%s", req.getParameter("next"), urlencode(message)));
  }

  private void setCommonAttributes(HttpServletRequest req, String namespace) {
    req.setAttribute(NAMESPACE, namespace);
    req.setAttribute(APPLICATION_NAME, ApiProxy.getCurrentEnvironment().getAppId());
  }

  /**
   * Represents a page in the search results pager.
   */
  public static final class Page {
    private final int number;
    private final int start;

    private Page(int number, int start) {
      this.number = number;
      this.start = start;
    }

    public int getNumber() {
      return number;
    }

    public int getStart() {
      return start;
    }
  }

  /**
   * Document representation suitable for the templating system in use (JSTL).
   */
  public static class DocumentView {
    String id;
    int orderId;
    List<FieldView> fieldViews;

    public DocumentView(Document doc, List<String> fieldNames) {
      id = doc.getId();
      orderId = doc.getRank();
      fieldViews = new ArrayList<FieldView>();

      Map<String, Field> fieldMap = new HashMap<String, Field>();
      for (Field field : doc.getFields()) {
        fieldMap.put(field.getName(), field);
      }

      for (String fieldName : fieldNames) {
        fieldViews.add(new FieldView(fieldMap.get(fieldName)));
      }
    }

    public String getId() {
      return id;
    }

    public int getOrderId() {
      return orderId;
    }

    public List<FieldView> getFieldViews() {
      return fieldViews;
    }
  }

  /**
   * Document field represetation suitable for the templating system in
   * use (JSTL).
   */
  public static class FieldView {
    private String name;
    private String type;
    private String value;

    public FieldView(Field field) {
      if (field == null) {
        name = "";
        type = "";
        value = "";
        return;
      }
      name = field.getName();
      type = field.getType().toString();
      switch (field.getType()) {
        case TEXT: value = field.getText(); break;
        case HTML: value = field.getHTML(); break;
        case ATOM: value = field.getAtom(); break;
        case NUMBER: value = Double.toString(field.getNumber()); break;
        case DATE: value = new SimpleDateFormat("yyyy-MM-dd").format(field.getDate()); break;
        case GEO_POINT: value = field.getGeoPoint().toString(); break;
        default:
          // TODO: go/enum-switch-lsc
      }
    }

    public String getName() {
      return name;
    }

    public String getType() {
      return type;
    }

    public String getValue() {
      return value;
    }

    public String getTruncatedValue() {
      if (value.length() < 32) {
        return value;
      }
      return value.substring(0, 32) + "...";
    }
  }
}
