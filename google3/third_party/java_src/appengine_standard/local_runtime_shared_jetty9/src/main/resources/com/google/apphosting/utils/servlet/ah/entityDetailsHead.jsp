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
<title>
  <c:out value="${requestScope.applicationName}"/> Development Console - Datastore Viewer -
  <c:choose>
    <c:when test="${empty param.key}">New Entity</c:when>
    <c:otherwise>Edit Entity</c:otherwise>
  </c:choose>
</title>
<style type="text/css">
  <%@ include file="css/form.css" %>
</style>
<style type="text/css">

  .field_type {
    color: gray;
    font-weight: normal;
  }

</style>
<script type="text/javascript">

  function load() {
    var elements = document.getElementsByTagName("input");
    for (var i = 0; i < elements.length; i++) {
      var element = elements[i];
      var hint = null;
      if (element.className == "time") {
        hint = "e.g., 2006-30-05 23:56:04";
      }
      if (hint) registerHint(element, hint);
    }
  }

  function registerHint(element, hint) {
    function showDefault() {
      if (element.value.length == 0 || element.value == hint) {
        element.style.color = "gray";
        element.value = hint;
      }
    }
    function clearDefault() {
      if (element.style.color == "gray" || element.value == hint) {
        element.value = "";
        element.style.color = "black";
      }
    }
    element.onblur = showDefault;
    element.onfocus = clearDefault;
    showDefault();
  }

  function clearHints(form) {
    var elements = form.getElementsByTagName("input");
    for (var i = 0; i < elements.length; i++) {
      var element = elements[i];
      if (element.type == "text" && element.style.color == "gray") {
        element.onblur = null;
        element.onfocus = null;
        element.value = "";
      }
    }
    return true;
  }

</script>
