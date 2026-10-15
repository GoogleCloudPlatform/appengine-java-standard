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

<h3>
  <c:choose>
    <c:when test="${empty param.key}">New Entity</c:when>
    <c:otherwise>Edit Entity</c:otherwise>
  </c:choose>
</h3>

<form action="{{ request.path }}" method="post" onsubmit="return clearHints(this)">
  <div><input type="hidden" name="next" value="<c:out value="${param.next}"/>"/></div>
  <table class="form">
    <tr>
      <td class="name">Entity Kind</td>
      <td class="value text">
        <c:out value="${param.kind}"/>
        <input type="hidden" name="kind" value="<c:out value="${param.kind}"/>"/>
      </td>
    </tr>
    <c:if test="${!empty param.key}">
      <tr>
        <td class="name">Entity Key</td>
        <td class="value text">
          <c:out value="${param.key}"/>
          <input type="hidden" name="key" value="<c:out value="${param.key}"/>"/>
        </td>
      </tr>
    </c:if>

    <c:if test="!empty keyName">
      <tr>
        <td class="name">Key Name</td>
        <td class="value text">
          <c:out value="${param.keyName}"/>
        </td>
      </tr>
    </c:if>

    <c:if test="!empty keyId">
      <tr>
        <td class="name">ID</td>
        <td class="value text">
          <c:out value="${param.keyId}"/>
        </td>
      </tr>
    </c:if>

    <c:if test="!empty parentKey">
      <tr>
        <td class="name">Parent</td>
        <td class="value text">
          <a href="?key=<c:out value="${param.parentKey}"/>&kind=<c:out value="${param.parentKind}"/>"><c:out
              value="${param.parentKey}"/></a>
        </td>
      </tr>
    </c:if>
    <c:forEach var="field" items="${requestScope.entity.sortedPropertyNames}">
      <tr>
        <td class="name">
          <span class="field_name"><c:out value="${field}"/></span>
          <span class="field_type">(<c:out
              value="${requestScope.entity.propertyTypes[field]}"/>)</span>
        </td>
        <td class="value">
          <div style="position: relative"><c:out
              value="${requestScope.entity.properties[field]}"/></div>
        </td>
      </tr>
    </c:forEach>
    <tr>
      <td></td>
      <td class="buttons">
        <input type="submit" value="Save Changes"/>
        <c:if test="${!empty param.key}">
          <input type="submit" name="action" value="Delete"
                 onclick="return confirm('Are you sure you want to update this entity?');"/>
        </c:if>
      </td>
    </tr>
  </table>
</form>

<div id="datepicker"></div>
