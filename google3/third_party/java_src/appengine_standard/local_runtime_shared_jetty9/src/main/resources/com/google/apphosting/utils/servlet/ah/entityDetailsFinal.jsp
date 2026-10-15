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

<script type="text/javascript">
  //<![CDATA[

  // Sets the focus on the field with the given name in the given array (if any)
  function setFocus(fields, fieldName) {
    for (var i = 0; i < fields.length; i++) {
      var field = fields[i];
      var name = field.name;
      if (field.focus && name.length > focus.length &&
          name.substring(name.length - focus.length - 1) == '|' + focus) {
        field.focus();
        break;
      }
    }
  }

  // Focus on the appropriate field in the form based on the "focus" argument
  // in the URL
  var focus = "<c:out value="${focus}"/>";
  if (focus) {
    setFocus(document.getElementsByTagName("input"), focus);
    setFocus(document.getElementsByTagName("textarea"), focus);
  }

  //]]>
</script>
