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
  <c:when test="${!empty requestScope.listQueueName}">
    Tasks for Queue:
    <span id="queuename"><c:out value="${requestScope.listQueueName}"/></span>
  </c:when>
  <c:otherwise>
    Task Queues
  </c:otherwise>
</c:choose>
</h3>

<c:if test="${!empty param.msg}">
  <div id="message"><c:out value="${param.msg}"/></div>
</c:if>

<c:choose>
  <c:when test="${empty requestScope.listQueueName}">
    <c:forEach var="queueBatch" items="${requestScope.queueStateInfo}">
      <c:choose>
        <c:when test="${!empty queueBatch}">
          <div>
            <c:if test="${queueBatch.runManually}">
              Select a push queue to run tasks manually.
            </c:if>
          </div>
          <br/>
          <table id="ah-queues" class="ae-table ae-table-striped">
            <thead>
              <tr>
                <th>Queue Name</th>
                <c:choose>
                  <c:when test="${queueBatch.rateLimited}">
                    <th>Maximum Rate</th>
                    <th>Bucket Size</th>
                  </c:when>
                </c:choose>
                <th>Oldest Task (UTC)</th>
                <th>Tasks in Queue</th>
                <th></th>
              </tr>
            </thead>
            <caption><strong>${queueBatch.title}</strong></caption>
            <tbody>
              <c:set var="odd" scope="page" value="${false}"/>
              <c:forEach var="queueInfo" items="${queueBatch}">
                <c:set var="odd" scope="page" value="${!odd}"/>
                <c:set var="queueName" scope="page" value="${queueInfo.entry.name}"/>
                <c:choose>
                  <c:when test="${odd}">
                    <tr class="ae-odd">
                  </c:when>
                  <c:otherwise>
                    <tr class="ae-even">
                  </c:otherwise>
                </c:choose>
                  <td valign="top">
                    <a href="/_ah/admin/taskqueue?queueName=<c:out value="${queueName}"/>"
                      id="<c:out value="${queueName}_details_link"/>">
                      <c:out value="${queueName}"/>
                    </a>
                  </td>
                  <c:choose>
                    <c:when test="${queueBatch.rateLimited}">
                      <td valign="top">
                        <c:out value="${queueInfo.entry.rate}"/>/<c:out value="${queueInfo.entry.rateUnit.ident}"/>
                      </td>
                      <td valign="top">
                        <c:out value="${queueInfo.bucketSize}"/>
                      </td>
                    </c:when>
                  </c:choose>
                  <td valign="top">
                    <c:out value="${queueInfo.oldestTaskEta}"/>
                  </td>
                  <td valign="top">
                    <c:out value="${queueInfo.countTasks}"/>
                  </td>
                  <td valign="top">
                    <form id="purgeform_<c:out value="${queueName}"/>" action="/_ah/admin/taskqueue" method="post">
                    <input type="hidden" name="queueName" value="<c:out value="${queueName}"/>"/>
                    <input id="purgeform_submit_<c:out value="${queueName}"/>" type="submit" name="action:purgequeue" value="Purge Queue"
                      onclick="return confirm('Are you sure you want to purge all ' +
                                              'tasks from <c:out value="${queueName}"/>?');"/>
                    </form>

                  </td>
                </tr>
              </c:forEach>
            </tbody>
          </table>
        </c:when>
        <c:otherwise>
          <div>
            This application doesn't define any ${queueBatch.title}. See the documentation for more.
          </div>
        </c:otherwise>
      </c:choose>
      <p />
    </c:forEach>
  </c:when>

  <c:when test="${empty requestScope.taskInfoPage}">
    This queue doesn't contain any tasks.
  </c:when>
  <c:otherwise>
    <c:set var="queueName" scope="page" value="${requestScope.listQueueName}"/>
    <c:set var="queueInfo" scope="page" value="${requestScope.listQueueInfo}"/>
    <c:if test="${queueInfo.entry.mode ne 'pull'}">
      <div>Push the 'Run' button to execute each task.</div>
    </c:if>
    <br/>
    <table id="ah-tasks" class="ae-table ae-table-striped">
      <thead>
        <tr>
          <th>Task Name</th>
          <th>ETA (UTC)</th>
          <th>Method</th>
          <th>URL</th>
          <th></th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        <c:set var="odd" scope="page" value="${false}"/>
        <c:forEach var="task" items="${requestScope.taskInfoPage}">
          <c:set var="odd" scope="page" value="${!odd}"/>
          <c:choose>
            <c:when test="${odd}">
              <tr class="ae-odd">
            </c:when>
            <c:otherwise>
              <tr class="ae-even">
            </c:otherwise>
          </c:choose>
            <td valign="top">
              <c:out value="${task.taskName}"/>
            </td>
            <td valign="top">
              <c:out value="${task.eta}"/> (<c:out value="${task.etaDelta}"/>s)
            </td>
            <td valign="top">
              <c:choose>
                <c:when test="${queueInfo.entry.mode eq 'pull'}">
                  PULL
                </c:when>
                <c:otherwise>
                  <c:out value="${task.method}"/>
                </c:otherwise>
              </c:choose>
            </td>
            <td valign="top">
              <c:out value="${task.url}"/>
            </td>
            <td valign="top">
              <c:if test="${queueInfo.entry.mode ne 'pull'}">
                <form id="executeform.<c:out value="${task.taskName}"/>" action="/_ah/admin/taskqueue" method="post">
                  <input type="hidden" name="queueName" value="<c:out value="${queueName}"/>"/>
                  <input type="hidden" name="taskName" value="<c:out value="${task.taskName}"/>"/>
                  <input type="hidden" name="start" value="<c:out value="${requestScope.start}"/>"/>
                  <input type="hidden" name="action:executetask" value="Execute"/>
                  <input type="submit" value="Run" id="executeform_submit_<c:out value="${task.taskName}"/>"/>
                </form>
              </c:if>
            </td>
            <td valign="top">
              <form id="deleteform.<c:out value="${task.taskName}"/>" action="/_ah/admin/taskqueue" method="post">
                <input type="hidden" name="queueName" value="<c:out value="${queueName}"/>"/>
                <input type="hidden" name="taskName" value="<c:out value="${task.taskName}"/>"/>
                <input type="hidden" name="start" value="<c:out value="${requestScope.start}"/>"/>
                <input type="hidden" name="action:deletetask" value="Delete"/>
                <input type="submit" value="Delete" id="executeform_delete_<c:out value="${task.taskName}"/>"/>
              </form>
            </td>
          </tr>
        </c:forEach>
        <tr>
          <td colspan="6" class="ae-pager" align="right">
            <div class="g-unit">
              <div id="entities-pager">
                <c:if test="${!empty requestScope.pages}">
                  <%@ include file="pager.jsp" %>
                </c:if>
              </div>
            </div>
          </td>
        </tr>
      </tbody>
    </table>

  </c:otherwise>
</c:choose>
