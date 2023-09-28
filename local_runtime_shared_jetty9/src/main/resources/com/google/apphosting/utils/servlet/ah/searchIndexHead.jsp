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
<title><c:out value="${ requestScope.applicationName }"/> Development Console - Full Text Search</title>
<style type="text/css">
  <%@ include file="css/datastore.css" %>
</style>
<style type="text/css">
  <%@ include file="css/pager.css" %>
</style>
<script type="text/javascript">
  //<![CDATA[

  function load() {
  }
  <c:if test="${ !empty requestScope.documents }">
  function checkAllDocuments() {
    var allCheckBox = document.getElementById("alldocs");
    var check = allCheckBox.checked;
    for (var i = 1; i <= ${fn:length(requestScope.documents)}; i++) {
      var box = document.getElementById("doc" + i);
      if (box)
        box.checked = check;
    }
    updateDeleteButtonAndCheckbox();
  }

  function updateDeleteButtonAndCheckbox() {
    var button = document.getElementById("delete_button");
    var uncheck = false;
    var disable = true;
    for (var i = 1; i <= ${fn:length(requestScope.documents)}; i++) {
      var box = document.getElementById("doc" + i);
      if (box) {
        if (box.checked) {
          disable = false;
        } else {
          uncheck = true;
        }
      }
    }
    button.disabled = disable;
    if (uncheck)
      document.getElementById("alldocs").checked = false;
  }
  </c:if>
  //]]>
</script>
