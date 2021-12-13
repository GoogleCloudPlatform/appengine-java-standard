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
<div id="ae-content"> 
<p>
Below are indexes for the application.
Indexes are managed in WEB-INF/datastore-indexes.xml
and WEB-INF/appengine-generated/datastore-indexes-auto.xml.<br/>
Learn more about
<a href="http://code.google.com/appengine/kb/general.html#indexes" target="_blank">indexes</a>
</p>
<br/>
<table id="ah-indexes" class="ae-table ae-table-striped">
  <colgroup> 
  <col> 
    <col id="ae-datastore-index-status-col"> 
  </colgroup> 
  <thead>
    <tr>
      <th>Entity and Indexes</th>
      <th>Status</th>
    </tr>
  </thead>
  <tbody>
  <c:set var="counter" value="0"/>
  <c:forEach var="entity" items="${requestScope.indexes}">
    <c:set var="index" scope="page" value="${entity.key}"/>
    <c:set var="indexStatus" scope="page" value="${entity.value}"/>
    <c:choose>
      <c:when test="${counter mod 2 == 0}">
        <c:set var="trclass" value="ae-even"/>
      </c:when>
      <c:otherwise>
        <c:set var="trclass" value="ae-odd"/>
      </c:otherwise>
    </c:choose>    
    <tr class="<c:out value="${trclass}"/>">
    <td colspan="2" class="ae-datastore-index-name"><c:out value="${index.kind}"/></td>
    </tr>
    <tr class="<c:out value="ae-datastore-index-defs-row  ${trclass}"/>">
      <td class="ae-datastore-index-defs" valign="top">
        <c:forEach var="property" items="${index.properties}">
          <c:out value="${property.name}"/>
          <c:choose>
                <c:when test="${property.direction eq 'ASCENDING'}">
                  <span class="ae-unimportant" title="Ascending">&#x25b2;</span> 
                </c:when>
                <c:otherwise>
                  <span class="ae-unimportant" title="Descending">&#x25bc;</span>
                </c:otherwise>
          </c:choose>&nbsp;
        </c:forEach>
        <c:if test="${index.ancestor}">
          <div class="ae-unimportant">Includes ancestors</div>
        </c:if>
      </td>
      <td>
        <div class="ae-datastore-index-status">
          <strong><c:out value="${indexStatus}"/></strong>
          <div class="ae-nowrap ae-field-hint"></div> 
        </div>
      </td>
    </tr>
  </c:forEach>
  </tbody>
</table>
</div>
