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
<title><c:out value="${requestScope.applicationName}"
  /> Development Console - Task Queue Viewer
  <c:if test="${!empty requestScope.listQueueName}">
    <c:out value="- ${requestScope.listQueueName}"/>
  </c:if>
</title>
<style type="text/css">
  <%@ include file="css/taskqueue.css" %>
</style>
<style type="text/css">
  <%@ include file="css/pager.css" %>
</style>
<script type="text/javascript" language="javascript" src="/_ah/resources?resource=webhook"></script>
<script type="text/javascript">
  //<![CDATA[
    // this function ripped from /apphosting/ext/admin/templates/tasks.html
    var handleTaskResult = function(hook, req, error) {
      if (error != null) {
        return;
      };
      if (req == null) {
        return;
      };
      if (req.status != 200) {
        return;
      };
      var parts = hook.formId.split('.');// + [''];
      var deleteForm = document.getElementById('deleteform.' + parts[1]);
      if (deleteForm != null) {
        deleteForm.submit();
      };
    };

    function load() {
    }
  //]]>
</script>

