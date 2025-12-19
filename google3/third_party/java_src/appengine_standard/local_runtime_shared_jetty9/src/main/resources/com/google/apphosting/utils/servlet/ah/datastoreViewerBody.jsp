<!--
 Copyright 2021 Google LLC

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

     https://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
-->

<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<h3>Datastore Viewer</h3>

<c:if test="${!empty param.msg}">
  <div id="message"><c:out value="${param.msg}"/></div>
</c:if>

<c:if test="${!empty requestScope.entities}">
  <div id="pagetotal">Results <span class="count"><c:out
      value="${param.start + 1}"/></span> - <span class="count"> <c:choose>
    <c:when test="${empty param.end}">
      <c:choose>
        <c:when
            test="${requestScope.numEntities > (requestScope.numPerPage + param.start + 1)}">
          <c:out value="${requestScope.numPerPage + param.start}"/>
        </c:when>
        <c:otherwise>
          <c:out value="${requestScope.numEntities}"/>
        </c:otherwise>
      </c:choose>
    </c:when>
    <c:otherwise>
      <c:out value="${requestScope.end}"/>
    </c:otherwise>
  </c:choose> </span> of <span class="count"><c:out
      value="${requestScope.numEntities}"/></span></div>
</c:if>

<form action="/_ah/admin/datastore" method="get">
  <div id="datastore_search">
    <table>
      <tr>
<c:if test="${requestScope.namespace ne null}">
        <td>
          <span class="name">Namespace:</span>
        </td>
</c:if>
        <td>
          <span class="name">Entity Kind:</span>
        </td>
      </tr>
      <tr>
<c:if test="${requestScope.namespace ne null}">
        <td>
          <span class="value">
            <input id="namespace" name="namespace" type="text" size="20"
                   value="<c:out value="${requestScope.namespace}"/>"/>
          </span>
        </td>
</c:if>
        <td align="right">
          <span class="value">
            <select name="kind" id="kind_input">
              <c:forEach var="kind" items="${requestScope.kinds}">
              <option value="<c:out value="${kind}"/>"
              <c:if test="${param.kind == kind}"> selected="selected"</c:if>><c:out
              value="${kind}"/></option>
              </c:forEach>
            </select>
          </span>
        </td>
        <td>
          <span class="buttons">
            <input type="submit" value="List Entities" id="list_button"/>
          </span>
        </td>
      </tr>
<c:if test="${requestScope.namespace eq null}">
      <tr>
        <td colspan="3">
            <a id="select_namespace" href="/_ah/admin/datastore?namespace=&kind=${param.kind}">Select different namespace</a>
        </td>
      </tr>
</c:if>
      <tr>
        <td colspan="3">
          <a id="index_details" href="/_ah/admin/datastore?subsection=indexDetails">Show indexes</a>
        </td>
      </tr>
    </table>
  </div>
</form>
<c:choose>
  <c:when test="${empty requestScope.kinds && empty requestScope.errorMessage}">
    <div id="datastore_empty">
      <c:choose>
        <c:when test="${empty requestScope.namespace}">
          Datastore has no entities in the Empty namespace. You need to
          add data programatically before you can use this tool to view and edit
          it.
        </c:when>
        <c:otherwise>
          Datastore has no entities in namespace
          "<c:out value="${requestScope.namespace}"/>".
        </c:otherwise>
      </c:choose>
    </div>
  </c:when>
</c:choose>
<c:choose>
  <c:when test="${!empty requestScope.entities}">
    <form action="/_ah/admin/datastore" method="post"><input
        type="hidden" name="kind" value="<c:out value="${param.kind}"/>"/> <input
        type="hidden" name="numkeys"
        value="<c:out value="${requestScope.numEntities}"/>"/> <input
        type="hidden" name="next"
        value="<c:out value="${requestScope.startBaseURL}"/>"/> <input
        type="hidden" name="action" value="Delete"/>
      <table class="entities" id="entities_table">
        <thead>
          <tr>
            <th><input id="allkeys" type="checkbox" onclick="checkAllEntities();"/></th>
            <th id="entities_table_header_key" style="cursor: pointer"
                onclick="document.location.href='<c:out value="${ requestScope.orderBaseURL }"/>&order=<c:if test="${param.order == '__key__'}">-</c:if>__key__'">
              <a
                  href="<c:out value="${ requestScope.orderBaseURL }"/>&order=<c:if test="${param.order == '__key__'}">-</c:if>__key__"
                  onclick="return false">Key</a></th>
            <%--No sorting by cost for now--%>
            <th id="entities_table_header_idname" style="cursor: pointer"
                onclick="document.location.href='<c:out value="${ requestScope.orderBaseURL }"/>&order=<c:if test="${param.order == '__key__'}">-</c:if>__key__'">
              <a
                  href="<c:out value="${ requestScope.orderBaseURL }"/>&order=<c:if test="${param.order == '__key__'}">-</c:if>__key__"
                  onclick="return false">ID/Name</a></th>
            <c:forEach var="prop" items="${requestScope.props}">
              <th id="entities_table_header_<c:out value="${prop}"/>" style="cursor: pointer"
                  onclick="document.location.href='<c:out value="${ requestScope.orderBaseURL }"/>&order=<c:if test="${param.order == prop}">-</c:if><c:out value="${prop}"/>'">
                <a
                    href="<c:out value="${ requestScope.orderBaseURL }"/>&order=<c:if test="${param.order == prop}">-</c:if><c:out value="${prop}"/>"
                    onclick="return false"><c:out value="${prop}"/></a></th>
            </c:forEach>
            <c:choose>
              <c:when test="${propertyOverflow}">
                <th>Properties Elided&hellip;</th>
              </c:when>
            </c:choose>
          </tr>
        </thead>
        <tbody>
          <c:set var="counter" value="0"/>
          <c:forEach var="entity" items="${requestScope.entities}">
            <c:choose>
              <c:when test="${counter mod 2 == 0}">
                <c:set var="trclass" value="even"/>
              </c:when>
              <c:otherwise>
                <c:set var="trclass" value="odd"/>
              </c:otherwise>
            </c:choose>
            <tr class="<c:out value="${trclass}"/>">
              <td><input id="key<c:out value="${counter + 1}"/>"
                         type="checkbox" name="key<c:out value="${counter + 1}"/>"
                         value="<c:out value="${entity.key}"/>"
                         onclick="updateDeleteButtonAndCheckbox();"/>
              </td>
              <td><c:out value="${entity.key}"/></td>
              <%--<td onclick="location.href='<c:out value="${entity.editURI}"/>'">--%>
                <%--<a href="<c:out value="${entity.editURI}"/>"--%>
                   <%--title="Edit entity #<c:out value="${entity.key}"/>" onclick="return false">--%>
                  <%--<c:out value="${entity.key}"/>--%>
                <%--</a>--%>
              <%--</td>--%>
              <td><c:out value="${entity.idOrName}"/></td>
              <c:forEach var="prop" items="${requestScope.props}">
                <%--<td onclick="location.href='<c:out value="${entity.editURI}"/>&amp;focus=<c:out value="${prop}"/>'">--%>
                  <%--<c:out value="${entity.properties[prop]}"/>--%>
                <%--</td>--%>
                <td>
                  <c:if test="${entity.properties[prop] != null}">
                    <c:out value="${entity.properties[prop]}"/>
                    <c:if test="${!entity.propertyIndexedness[prop]}">
                      <span class="unindexed-marker"> (unindexed)</span>
                    </c:if>
                  </c:if>
                </td>
              </c:forEach>
              <c:choose>
                <c:when test="${propertyOverflow}">
                  <td></td>
                </c:when>
              </c:choose>
            </tr>
            <c:set var="counter" value="${counter + 1}"/>
          </c:forEach>
        </tbody>
      </table>
      <div class="entities g-section g-tpl-50-50">
        <div class="g-unit g-first">
          <div id="entities-control">
            <input id="delete_button"
                   type="submit" value="Delete"
                   onclick="return confirm('Are you sure you wish to delete these entities?')"/>
            <input id="flush_button"
                   type="submit" value="Flush Memcache" name="flush"
                   onclick="return confirm('Are you sure you wish to flush Memcache?')"/>
          </div>
        </div>
        <div class="g-unit">
          <div id="entities-pager">
            <c:if test="${!empty requestScope.pages}">
              <%@ include file="pager.jsp" %>
            </c:if>
          </div>
        </div>
      </div>
    </form>
  </c:when>
  <c:otherwise>
    <c:choose>
      <c:when test="${!empty requestScope.errorMessage}">
        <div id="error_message"><b><c:out value="${requestScope.errorMessage}"/></b></div>
      </c:when>
      <c:when test="${!empty requestScope.kinds && !empty param.kind}">
        <p id="no_kind" style="font-size: medium">
        Datastore contains no entities of kind &quot;<c:out value="${param.kind}"/>&quot; in
        <c:choose>
          <c:when test="${empty requestScope.namespace}">
            the Empty namespace.
          </c:when>
          <c:otherwise>
            namespace "<c:out value="${requestScope.namespace}"/>".
          </c:otherwise>
        </c:choose>
        </p>
      </c:when>
    </c:choose>
    </div>
  </c:otherwise>
</c:choose>
