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
<h3>Text Search &gt; <a id="prev" href="<c:out value="${ requestScope.prev }"/>"><c:out value="${ param.indexName }"/></a>
<c:if test="${!empty param.namespace }">
  <br>
  Namespace: <c:out value="${ param.namespace }"/>
</c:if>
</h3>

<c:if test="${!empty requestScope.errorMessage }">
  <div id="error_message"><c:out value="${ requestScope.errorMessage }"/></div>
</c:if>

<c:if test="${ !empty requestScope.document }">
  <table class="entities" id="entities_table">
    <thead>
      <tr>
        <td colspan="2">Document Id: <c:out value="${ requestScope.document.id }"/></td>
      </tr>
      <tr>
        <td colspan="2">Rank: <c:out value="${ requestScope.document.rank }"/></td>
      </tr>
      <tr>
        <th>Field Name</th>
        <th>Field Type</th>
        <th>Field Value</th>
      </tr>
    </thead>
    <tbody>
      <c:forEach var="field" items="${ requestScope.fields }">
      <tr class="{% cycle ae-odd,ae-even %}">
        <td valign="top">
          <c:out value="${ field.name }"/>
        </td>
        <td valign="top">
          <c:out value="${ field.type }"/>
        </td>
        <td valign="top">
          <c:out value="${ field.value }"/>
        </td>
      </tr>
      </c:forEach>
    </tbody>
  </table>
</c:if>

