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

import static java.lang.Math.ceil;
import static java.lang.Math.floor;
import static java.lang.Math.max;
import static java.lang.Math.min;

import com.google.appengine.api.NamespaceManager;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.Index;
import com.google.appengine.api.datastore.Index.IndexState;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.SortDirection;
import com.google.appengine.api.datastore.dev.LocalDatastoreService;
import com.google.appengine.api.memcache.MemcacheService;
import com.google.appengine.api.memcache.MemcacheServiceFactory;
import com.google.appengine.tools.development.ApiProxyLocal;
import com.google.apphosting.api.ApiProxy;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Handler for the datastore viewer:
 * Pagination, entity creation, entity updates, entity deletes.
 *
 */
@SuppressWarnings("serial")
public class DatastoreViewerServlet extends HttpServlet {

  private static final String APPLICATION_NAME = "applicationName";

  private static final String NAMESPACE = "namespace";

  private static final String KIND = "kind";

  private static final String SELECTED_KIND_PROPS = "props";

  private static final String ALL_KINDS = "kinds";

  private static final String START = "start";

  private static final String NUM_PER_PAGE = "numPerPage";

  private static final String ENTITIES = "entities";

  private static final String NUM_ENTITIES = "numEntities";

  private static final String START_BASE_URL = "startBaseURL";

  private static final String ORDER_BASE_URL = "orderBaseURL";

  private static final String ORDER = "order";

  private static final String DELETE_ACTION = "Delete";

  private static final String CLEAR_DATASTORE_ACTION = "Clear Datastore";

  private static final String ACTION = "action";

  private static final String NUM_KEYS = "numkeys";

  private static final String KEY = "key";

  private static final String PAGES = "pages";

  private static final String CURRENT_PAGE = "currentPage";

  private static final String PREV_START = "prevStart";

  private static final String NEXT_START = "nextStart";

  private static final String PROPERTY_OVERFLOW = "propertyOverflow";

  private static final String ERROR_MESSAGE = "errorMessage";

  private static final String INDEXES = "indexes";

  private static final int MAX_PAGER_LINKS = 8;

  private static final int DEFAULT_MAX_DATASTORE_VIEWER_COLUMNS = 100;

  private LocalDatastoreService localDatastoreService;

  /**
   * Requests to this servlet contain an optional subsection parameter
   * that we use to determine which view we need to gather data for.
   * The datastore viewer is the default subsection, so requests
   * without a subsection parameter are treated as datastore viewer
   * requests.
   */
  private enum Subsection {
    datastoreViewer,
    entityDetails,
    indexDetails,
  }

  @Override
  public void init() throws ServletException {
    super.init();
    ApiProxyLocal apiProxyLocal = (ApiProxyLocal) getServletContext().getAttribute(
        "com.google.appengine.devappserver.ApiProxyLocal");
    localDatastoreService =
        (LocalDatastoreService) apiProxyLocal.getService(LocalDatastoreService.PACKAGE);
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
      // throws NFE if null, which is what we want
      return Integer.parseInt(val);
    } catch (NumberFormatException nfe) {
      return defaultVal;
    }
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


  /** Return all kinds in the current namespace. */
  List<String> getKinds() {
    List<String> kinds = new ArrayList<String>();
    DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
    Query q = new Query(Query.KIND_METADATA_KIND)
        .addSort(Entity.KEY_RESERVED_PROPERTY, SortDirection.ASCENDING);
    for (Entity e : ds.prepare(q).asIterable()) {
      kinds.add(e.getKey().getName());
    }
    return kinds;
  }

  /** Return all (indexed) properties of kind in the current namespace. */
  List<String> getIndexedProperties(String kind) throws UnsupportedEncodingException {
    List<String> properties = new ArrayList<String>();
    DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
    Key kindKey = KeyFactory.createKey(Query.KIND_METADATA_KIND, kind);
    Query q = new Query(Query.PROPERTY_METADATA_KIND).setKeysOnly().setAncestor(kindKey)
        .addSort(Entity.KEY_RESERVED_PROPERTY, SortDirection.ASCENDING);
    for (Entity e : ds.prepare(q).asIterable()) {
      properties.add(urlencode(e.getKey().getName()));
    }
    return properties;
  }

  /**
   * Retrieve all EntityViews of the given kind for display, sorted by the
   * (possibly null) given order.
   */
  List<EntityView> getEntityViews(String kind, String order, int start, int numPerPage) {
    List<EntityView> entityViews = new ArrayList<EntityView>();
    Query q = new Query(kind);
    SortDirection dir = SortDirection.ASCENDING;
    if (order != null) {
      // If the order string begins with a dash, sort in descending order.
      if (order.charAt(0) == '-') {
        dir = SortDirection.DESCENDING;
        order = order.substring(1);
      }
      q.addSort(order, dir);
    }
    DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
    FetchOptions opts = FetchOptions.Builder.withOffset(start).limit(numPerPage);
    for (Entity e : ds.prepare(q).asIterable(opts)) {
      entityViews.add(new EntityView(e));
    }
    return entityViews;
  }

  /**
   * Retrieve the number of entities of the given kind in the datastore.
   */
  private int countForKind(String kind) {
    DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
    return ds.prepare(new Query(kind)).countEntities(FetchOptions.Builder.withDefaults());
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    String subsectionStr = req.getParameter("subsection");
    // datastore viewer is the default subsection
    Subsection subsection = Subsection.datastoreViewer;
    if (subsectionStr != null) {
      subsection = Subsection.valueOf(subsectionStr);
    }
    switch (subsection) {
      case datastoreViewer:
        doGetDatastoreViewer(req, resp);
        break;
      case entityDetails:
        doGetEntityDetails(req, resp);
        break;
      case indexDetails:
        doGetIndexes(req, resp);
        break;
      default:
        resp.sendError(404);
    }
  }

  private void doGetIndexes(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    // Empty namespace parameter equals to no namespace specified
    String requestedNamespace = req.getParameter(NAMESPACE);
    String namespace = requestedNamespace != null ? requestedNamespace : "";
    String savedNamespace = NamespaceManager.get();
    try {
      NamespaceManager.set(namespace);
      DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
      Map<Index, IndexState> indexes = ds.getIndexes();
      req.setAttribute(INDEXES, indexes);
      req.setAttribute(APPLICATION_NAME, ApiProxy.getCurrentEnvironment().getAppId());
      try {
        getServletContext().getRequestDispatcher(
            "/_ah/adminConsole?subsection=" + Subsection.indexDetails.name()).forward(req, resp);
      } catch (ServletException e) {
        throw new RuntimeException("Could not forward request", e);
      }
    } finally {
      NamespaceManager.set(savedNamespace);
    }
  }

  // TODO: Implement pagination
  private void doGetDatastoreViewer(HttpServletRequest req, HttpServletResponse resp)
      throws IOException {
    int start = getIntParam(req, START, 0);
    int numPerPage = getIntParam(req, NUM_PER_PAGE, 10);
    String requestedNamespace = req.getParameter(NAMESPACE);
    String selectedKind = req.getParameter(KIND);

    // Empty namespace parameter equals to no namespace specified
    String namespace = requestedNamespace != null ? requestedNamespace : "";

    String savedNamespace = NamespaceManager.get();
    List<EntityView> entities = new ArrayList<EntityView>();
    List<String> kinds = new ArrayList<String>();
    Set<String> props = new HashSet<String>();
    int countForKind = 0;

    // All code in the following try block will use specified namespace
    // if it is valid. Starting from the metadata queries and ending with
    // fetching entries from the datastore.
    try {
      NamespaceManager.set(namespace);
      kinds = getKinds();
      if (kinds.contains(selectedKind)) {
        props.addAll(getIndexedProperties(selectedKind));
        entities = getEntityViews(selectedKind, req.getParameter(ORDER), start, numPerPage);
        countForKind = countForKind(selectedKind);
      }
    } catch (IllegalArgumentException e) {
      req.setAttribute(ERROR_MESSAGE, "Error: " + e.getMessage());
      selectedKind = null;
    } finally {
      NamespaceManager.set(savedNamespace);
    }
    // Add all properties including unindexed.
    for (EntityView e : entities) {
      props.addAll(e.getProperties().keySet());
    }

    List<String> sortedProps = new ArrayList<String>(props);
    Collections.sort(sortedProps);
    // Limit the number of columns that we display.
    boolean propertyOverflow =
        sortedProps.size() > DEFAULT_MAX_DATASTORE_VIEWER_COLUMNS;
    if (propertyOverflow) {
      sortedProps = sortedProps.subList(
          0, DEFAULT_MAX_DATASTORE_VIEWER_COLUMNS);
    }
    req.setAttribute(PROPERTY_OVERFLOW, propertyOverflow);

    Collections.sort(kinds);
    int currentPage = start / numPerPage;
    int numPages = (int) ceil(countForKind * (1.0 / numPerPage));
    int pageStart = (int) max(floor(currentPage - (MAX_PAGER_LINKS / 2)), 0);
    int pageEnd = min(pageStart + MAX_PAGER_LINKS, numPages);
    List<Page> pages = new ArrayList<Page>();
    for (int i = pageStart + 1; i < pageEnd + 1; i++) {
      pages.add(new Page(i, (i - 1) * numPerPage));
    }

    setDatastoreViewerAttributes(
        req, kinds, sortedProps, entities, countForKind, pages, currentPage + 1, numPerPage,
        numPages, requestedNamespace);
    try {
      getServletContext().getRequestDispatcher(
          "/_ah/adminConsole?subsection=" + Subsection.datastoreViewer.name()).forward(req, resp);
    } catch (ServletException e) {
      throw new RuntimeException("Could not forward request", e);
    }
  }

  private void doGetEntityDetails(HttpServletRequest req, HttpServletResponse resp)
      throws IOException {
    String key = req.getParameter(KEY);
    String keyName = null;
    Long keyId = null;
    String kind = null;
    String parentKey = null;
    String parentKind = null;
    if (key != null) {
      Key k = KeyFactory.stringToKey(key);
      if (k.getName() != null) {
        keyName = k.getName();
      } else {
        keyId = k.getId();
      }
      kind = k.getKind();
      if (k.getParent() != null) {
        parentKey = KeyFactory.keyToString(k.getParent());
        parentKind = k.getParent().getKind();
      }
      DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
      Entity e;
      try {
        e = ds.get(KeyFactory.stringToKey(key));
      } catch (EntityNotFoundException e1) {
        throw new RuntimeException("Could not locate entity " + key);
      }
      req.setAttribute("entity", new EntityDetailsView(e));
    } else {
      // TODO Handle creation case
    }
    String url =
        String.format(
            "/_ah/adminConsole?subsection=entityDetails&" +
                "key=%s&keyName=%s&keyId=%d&kind=%s&parentKey=%s&parentKind=%s",
            key, keyName, keyId, kind, parentKey, parentKind);
    try {
      getServletContext().getRequestDispatcher(url).forward(req, resp);
    } catch (ServletException e) {
      throw new RuntimeException("Could not forward request", e);
    }
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp)
      throws IOException {
    if (req.getParameter("flush") != null) {
      flushMemcache(req, resp);
    } else if (CLEAR_DATASTORE_ACTION.equals(req.getParameter(ACTION))) {
      // not currently hooked up to the UI so we're not redirecting anywhere
      localDatastoreService.clearProfiles();
    } else if (DELETE_ACTION.equals(req.getParameter(ACTION))) {
      deleteEntities(req, resp);
    } else {
      resp.sendError(404);
    }
  }

  private void flushMemcache(HttpServletRequest req, HttpServletResponse resp)
      throws IOException {
    MemcacheService ms = MemcacheServiceFactory.getMemcacheService();
    ms.clearAll();
    String message = "Cache flushed, all keys dropped.";
    resp.sendRedirect(String.format("%s&msg=%s", req.getParameter("next"), urlencode(message)));
  }

  private void deleteEntities(HttpServletRequest req, HttpServletResponse resp)
      throws IOException {
    DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
    int numDeleted = 0;
    int numKeys = Integer.parseInt(req.getParameter(NUM_KEYS));
    for (int i = 1; i <= numKeys; i++) {
      String key = req.getParameter(KEY + i);
      if (key != null) {
        ds.delete(KeyFactory.stringToKey(key));
        numDeleted++;
      }
    }
    String message = String
        .format("%d entit%s deleted. If your app uses memcache to cache entities " +
                "(e.g. uses Objectify), you may see stale results unless you flush memcache.",
                numDeleted, numDeleted == 1 ? "y" : "ies");
    resp.sendRedirect(String.format("%s&msg=%s", req.getParameter("next"), urlencode(message)));
  }

  private void setDatastoreViewerAttributes(HttpServletRequest req, List<String> kinds,
      List<String> props, List<EntityView> entities, int countForKind,
      List<Page> pages, int nextPage, int num, int numPages, String namespace)
      throws UnsupportedEncodingException {
    req.setAttribute(ALL_KINDS, kinds);
    req.setAttribute(SELECTED_KIND_PROPS, props);
    req.setAttribute(ENTITIES, entities);
    req.setAttribute(NUM_ENTITIES, countForKind);
    req.setAttribute(START_BASE_URL, filterURL(req, NAMESPACE, KIND, ORDER, NUM_ENTITIES));
    req.setAttribute(ORDER_BASE_URL, filterURL(req, NAMESPACE, KIND, NUM_ENTITIES));
    req.setAttribute(NAMESPACE, namespace);
    req.setAttribute(APPLICATION_NAME, ApiProxy.getCurrentEnvironment().getAppId());
    req.setAttribute(PAGES, pages);
    req.setAttribute(CURRENT_PAGE, nextPage);
    req.setAttribute(NUM_PER_PAGE, num);
    req.setAttribute(PREV_START, nextPage > 1 ? (nextPage - 2) * num : -1);
    req.setAttribute(NEXT_START, nextPage < numPages ? nextPage * num : -1);
  }

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
   * View of an {@link Entity} that lets us access the key and the individual
   * properties using jstl.
   */
  public static class EntityView {

    private final String key;

    private final String idOrName;

    private final String editURI;

    private final Map<String, Object> properties;

    // This is a Map rather than just a Set of indexed properties so that we can
    // access it more easily from the JSTL expression language.
    private final Map<String, Boolean> propertyIndexedness = new HashMap<String, Boolean>();

    EntityView(Entity e) {
      this.key = KeyFactory.keyToString(e.getKey());
      if (e.getKey().getName() == null) {
        this.idOrName = Long.toString(e.getKey().getId());
      } else {
        this.idOrName = e.getKey().getName();
      }
      this.properties = e.getProperties();
      this.editURI =
          "/_ah/admin/datastore?subsection=" + Subsection.entityDetails.name() + "&key=" + key;
      for (String p : properties.keySet()) {
        propertyIndexedness.put(p, !e.isUnindexedProperty(p));
      }
    }

    public String getKey() {
      return key;
    }

    public String getIdOrName() {
      return idOrName;
    }

    public Map<String, Object> getProperties() {
      return properties;
    }

    public Map<String, Boolean> getPropertyIndexedness() {
      return propertyIndexedness;
    }

    public String getEditURI() {
      return editURI;
    }
  }

  /**
   * Extension to {@code EntityView} that provides additional info required
   * by the entity details page.
   */
  public static class EntityDetailsView extends EntityView {

    /**
     * Maps property names to property types.
     */
    private final Map<String, String> propertyTypes;

    private final List<String> sortedPropertyNames;

    EntityDetailsView(Entity e) {
      super(e);
      this.propertyTypes = buildPropertyTypesMap(e);
      this.sortedPropertyNames = buildSortedPropertyNameList(e);
    }

    public Map<String, String> getPropertyTypes() {
      return propertyTypes;
    }

    public List<String> getSortedPropertyNames() {
      return sortedPropertyNames;
    }

    private static List<String> buildSortedPropertyNameList(Entity e) {
      List<String> result = new ArrayList<String>(e.getProperties().keySet());
      Collections.sort(result);
      return result;
    }

    private static Map<String, String> buildPropertyTypesMap(Entity e) {
      Map<String, String> result = new HashMap<String, String>();
      for (String prop : e.getProperties().keySet()) {
        // TODO: implement this
        result.put(prop, "TODO");
      }
      return result;
    }
  }
}
