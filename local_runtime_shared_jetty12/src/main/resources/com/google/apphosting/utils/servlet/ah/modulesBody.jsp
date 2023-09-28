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

<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions"%>

<c:choose>
  <c:when test="${!empty requestScope.modulesStateInfo}">
    <h3>Modules</h3>
    <table id="ah-modules" class="ae-table ae-table-striped">
      <thead>
        <tr>
          <th>Name</th>
          <th>State</th>
          <th>Version</th>
          <th>Hostname</th>
          <th>Scaling Type</th>
          <th>Instances</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        <c:set var="odd" scope="page" value="${false}" />
        <c:forEach var="moduleInfo" items="${requestScope.modulesStateInfo}">
          <c:set var="odd" scope="page" value="${!odd}" />
          <c:choose>
            <c:when test="${odd}">
              <tr class="ae-odd">
            </c:when>
            <c:otherwise>
              <tr class="ae-even">
            </c:otherwise>
          </c:choose>
            <td valign="top"><c:out value="${moduleInfo.name}"/></td>
            <td valign="top"><span><c:out value="${moduleInfo.state}"/></span>
              <c:choose>
                <c:when test="${moduleInfo.type != 'AutomaticModule'}">
                  <form id="form_<c:out value="${moduleInfo.name}"/>" action="/_ah/admin/modules" method="post">
                    <input type="hidden" name="moduleName" value="<c:out value="${moduleInfo.name}"/>"/>
                    <input type="hidden" name="moduleVersion" value="<c:out value="${moduleInfo.version}"/>"/>
                    <c:choose>
                      <c:when test="${moduleInfo.state == 'stopped'}">
                        <input id="modulestate_form_submit_<c:out value="${moduleInfo.name}"/>"
                          type="submit" name="action:module" value="Start" />
                      </c:when>
                      <c:when test="${moduleInfo.state == 'running' && moduleInfo.name != requestScope.defaultModuleName}">
                        <input id="modulestate_form_submit_<c:out value="${moduleInfo.name}"/>"
                          type="submit" name="action:module" value="Stop" />
                      </c:when>
                    </c:choose>
                  </form>
                </c:when>
              </c:choose>
            </td>
            <td valign="top"><c:out value="${moduleInfo.version}"/></td>
            <td valign="top"><a href="http://<c:out value="${moduleInfo.hostname}"/>" target="_blank"><c:out value="${moduleInfo.hostname}"/></a></td>
            <td valign="top"><c:out value="${moduleInfo.type}"/></td>
            <c:choose>
              <c:when test="${!empty moduleInfo.instances}">
                <td valign="top">
                  <p><c:out value="${fn:length(moduleInfo.instances)}"/> instances running</p>
                  <div style="padding-left:10px">
                    <c:forEach var="hostname" items="${moduleInfo.instances}">
                      <a href="http://<c:out value="${hostname}"/>" target="_blank"><c:out value="${hostname}"/></a><br>
                    </c:forEach>
                  <div>
                </td>
              </c:when>
            </c:choose>
          </tr>
        </c:forEach>
      </tbody>
    </table>
  </c:when>
  <c:otherwise>
    <div id="modules_empty">This application doesn't define any modules.
    See the documentation for more details.</div>
  </c:otherwise>
</c:choose>
