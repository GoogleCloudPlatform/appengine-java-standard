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

<c:if test="${requestScope.prevStart != -1}">
  <a id='prev_link' href='<c:out value="${requestScope.startBaseURL}"/>&amp;start=<c:out value="${requestScope.prevStart}"/>'>&lsaquo; Previous</a>
</c:if>
&nbsp;
<c:forEach var="page" items="${requestScope.pages}">
  <a id='page_<c:out value="${page.number}"/>' <c:if test="${page.number == requestScope.currentPage}"> class='ae-page-selected ae-page-number'</c:if>href='<c:out value="${requestScope.startBaseURL}"/>&amp;start=<c:out value="${page.start}"/>'><c:out value="${page.number}"/></a>
</c:forEach>
&nbsp;
<c:if test="${requestScope.nextStart != -1}">
  <a id='next_link' href='<c:out value="${requestScope.startBaseURL}"/>&amp;start=<c:out value="${requestScope.nextStart}"/>'>Next &rsaquo;</a>
</c:if>
