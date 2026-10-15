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
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<h3><a id="prev" href="<c:out value="${ requestScope.prev }"/>">Text Search</a> &gt; <c:out value="${ requestScope.indexName }"/>
<c:if test="${!empty requestScope.namespace }">
  (Namespace: <c:out value="${ requestScope.namespace }"/>)
</c:if>
</h3>

<c:if test="${!empty param.msg }">
  <div id="message"><c:out value="${ param.msg }"/></div>
</c:if>

<c:choose>
  <c:when test="${!empty requestScope.documents }">
    <div id="pagetotal">Results <span class="count"><c:out
        value="${ param.start + 1 }"/></span> - <span class="count"><c:out
        value="${ requestScope.end }"/></span> of <span class="count"><c:out
        value="${ requestScope.matchedCount }"/></span></div>
    <br/>
    <form action="/_ah/admin/search" method="post">
      <input type="hidden" name="namespace" value="<c:out value="${ requestScope.namespace }"/>"/>
      <input type="hidden" name="indexName" value="<c:out value="${ requestScope.indexName }"/>"/>
      <input type="hidden" name="numdocs" value="${ fn:length(requestScope.documents) }"/>
      <input type="hidden" name="next" value="<c:out value="${ requestScope.startBaseURL }"/>"/>
      <input type="hidden" name="action" value="Delete"/>
      <table class="entities" id="entities_table">
        <thead>
          <tr>
            <th><input id="alldocs" type="checkbox" onclick="checkAllDocuments();"/></th>
            <th>DocId</th>
            <th>OrderId</th>
            <c:forEach var="fieldName" items="${ requestScope.fieldNames }">
            <th><c:out value="${ fieldName }"/></th>
            </c:forEach>
          </tr>
        </thead>
        <c:set var="counter" value="0"/>
        <c:forEach var="document" items="${ requestScope.documents }">
          <c:choose>
            <c:when test="${ counter mod 2 == 0 }">
              <c:set var="trclass" value="even"/>
            </c:when>
            <c:otherwise>
              <c:set var="trclass" value="odd"/>
            </c:otherwise>
          </c:choose>
          <tr class="<c:out value="${ trclass }"/>">
            <td><input id="doc<c:out value="${ counter + 1 }"/>"
              type="checkbox" name="doc<c:out value="${ counter + 1 }"/>"
              value="<c:out value="${ document.id }"/>"
              onclick="updateDeleteButtonAndCheckbox();"/>
            </td>
            <td><a id="doc${counter
                }" href="/_ah/admin/search?subsection=searchDocument&namespace=${requestScope.namespace
                }&indexName=${requestScope.indexName
                }&docid=${document.id
                }&prev=${requestScope.current
                }"><c:out value="${ document.id }"/></a></td>
            <td><c:out value="${ document.orderId }"/></td>
            <c:forEach var="fieldView" items="${ document.fieldViews }">
              <td><c:out value="${ fieldView.truncatedValue }"/></td>
            </c:forEach>
          </tr>
          <c:set var="counter" value="${ counter + 1 }"/>
        </c:forEach>
      </table>
      <div class="entities g-section g-tpl-50-50">
        <div class="g-unit g-first">
          <div id="entities-control"><input id="delete_button"
            type="submit" value="Delete"
            onclick="return confirm('Are you sure you wish to delete these documents?')"/>
          </div>
        </div>
        <div class="g-unit">
          <div id="entities-pager">
            <c:if test="${!empty requestScope.pages }">
              <%@ include file="pager.jsp" %>
            </c:if>
          </div>
        </div>
      </div>
    </form>
  </c:when>
  <c:when test="${!empty requestScope.errorMessage }">
    <div id="error_message"><b><c:out value="${ requestScope.errorMessage }"/></b></div>
  </c:when>
  <c:otherwise>
    <div id="datastore_empty">
      <c:choose>
        <c:when test="${! empty requestScope.query }">
          No documents match the specified query.
        </c:when>
        <c:when test="${ params.start > 0}">
          No documents found at the specified offset.
        </c:when>
        <c:when test="${ empty requestScope.namespace }">
          There are no documents in the index in the default namespace.
        </c:when>
        <c:otherwise>
          There are no documents in the index in the namespace
          "<c:out value="${ requestScope.namespace }"/>".
        </c:otherwise>
      </c:choose>
    </div>
  </c:otherwise>
</c:choose>
