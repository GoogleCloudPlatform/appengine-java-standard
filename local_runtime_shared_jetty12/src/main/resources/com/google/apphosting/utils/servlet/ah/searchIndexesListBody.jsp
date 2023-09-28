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
<h3>Text Search Indexes</h3>

<c:if test="${!empty param.msg }">
  <div id="message"><c:out value="${ param.msg }"/></div>
</c:if>

<c:if test="${!empty requestScope.indexes }">
  <div id="pagetotal">Results <span class="count"><c:out
      value="${ param.start + 1 }"/></span> - <span class="count"> <c:out
      value="${ requestScope.end }"/> </span></div>
</c:if>

<form method="get" action="<c:out value="/_ah/admin/search?namespace=${ requestScope.namespace }"/>">
  Namespace: <input type="text" id="namespace" name="namespace"
                    value="<c:out value="${ requestScope.namespace }"/>"/>
  <input id="change_namespace" type="submit" value="View"/>
</form>

<c:choose>
  <c:when test="${!empty requestScope.indexes }">
    <table class="entities" id="entities_table">
      <thead>
        <tr>
          <th>Index Name</th>
        </tr>
      </thead>
      <c:set var="counter" value="0"/>
      <c:forEach var="entity" items="${ requestScope.indexes }">
        <c:choose>
          <c:when test="${ counter mod 2 == 0 }">
            <c:set var="trclass" value="even"/>
          </c:when>
          <c:otherwise>
            <c:set var="trclass" value="odd"/>
          </c:otherwise>
        </c:choose>
        <tr class="<c:out value="${ trclass }"/>">
          <td><a id="index${counter
              }" href="/_ah/admin/search?subsection=searchIndex&indexName=${entity.name
              }&namespace=${requestScope.namespace}&prev=${requestScope.current}"><c:out
              value="${ entity.name }"/></a></td>
        </tr>
        <c:set var="counter" value="${ counter + 1 }"/>
      </c:forEach>
    </table>
    <div class="entities g-section g-tpl-50-50">
      <div class="g-unit">
        <div id="entities-pager">
          <%@ include file="pager.jsp" %>
        </div>
      </div>
    </div>
  </c:when>
  <c:when test="${!empty requestScope.errorMessage }">
    <br/><div id="error_message"><b><c:out value="${ requestScope.errorMessage }"/></b></div>
  </c:when>
  <c:otherwise>
    <div id="datastore_empty">
    <br/>
    <c:choose>
      <c:when test="${ empty requestScope.namespace }">
        There are no Full Text Search indexes in the Empty namespace. You need to
        add data programatically before you can use this tool to view and edit
        it.
      </c:when>
      <c:otherwise>
        There are no Full Text Search indexes in namespace
        "<c:out value="${ requestScope.namespace }"/>".
      </c:otherwise>
    </c:choose>
  </div>
  </c:otherwise>
</c:choose>
