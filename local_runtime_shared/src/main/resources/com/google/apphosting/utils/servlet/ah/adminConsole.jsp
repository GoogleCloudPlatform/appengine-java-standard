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

<%!
  private static final String DATASTORE_SUBSECTION = "datastoreViewer";

  private static String getSubsectionPage(HttpServletRequest request, String suffix) {
    String sub = request.getParameter("subsection");
    if (sub == null) {
      // Datastore is the default section
      sub = DATASTORE_SUBSECTION;
    }
    // Note: Use this line to point to JSPs in your application's
    // "war/ah/" directory to speed up development of the local admin page:
    //     return "/ah/" + sub + suffix + ".jsp";
    return "/_ah/" + sub + suffix;
  }
%>

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
  <meta http-equiv="content-type" content="text/html; charset=utf-8"/>
  <style type="text/css">
  <%@ include file="css/base.css" %>
  </style>
  <style type="text/css">
    <%@ include file="css/ae.css" %>
  </style>
  <style type="text/css">
    <%@ include file="css/nav.css" %>
  </style>
  <jsp:include page="<%= getSubsectionPage(request, \"Head\") %>"/>
</head>
<body onload="load()">
<div class="g-doc">

  <div id="hd" class="g-section">

    <div class="g-section g-tpl-50-50 g-split">
      <div class="g-unit g-first">
        <img id="ae-logo" src="/_ah/resources?resource=google" width="153" height="47"
             alt="Google App Engine"/>
      </div>
      <div class="g-unit">
        SDK v<%= System.getProperty("com.google.appengine.runtime.version") %>
      </div>
    </div>

    <div id="ae-appbar-lrg" class="g-section">
      <h1><c:out value="${requestScope.applicationName}"/> Development Console</h1>
    </div>

  </div>


  <div id="bd" class="g-section">

    <div class="g-section g-tpl-160">

      <div id="ae-lhs-nav" class="g-unit g-first">

        <div id="ae-nav" class="g-c">

          <ul id="menu">
            <li><a href="/_ah/admin/datastore" id="datastore_viewer_link">Datastore Viewer</a></li>
            <li><a href="/_ah/admin/taskqueue" id="task_queue_viewer_link">Task Queues</a></li>
            <li><a href="/_ah/admin/inboundmail" id="mail_viewer_link">Inbound Mail</a></li>
            <li><a href="/_ah/admin/modules" id="modules_link">Modules</a></li>
            <li><a href="/_ah/admin/capabilitiesstatus" id="capabilities_link">Capabilities Status</a></li>
            <li><a href="/_ah/admin/search" id="search_link">Full Text Search</a></li>
          </ul>

        </div>

      </div>
      <div id="ae-content" class="g-unit">
        <jsp:include page="<%= getSubsectionPage(request, \"Body\") %>"/>
      </div>

    </div>

    <div id="ft">
      <p>
        &copy;2008-2021 Google
      </p>
    </div>
    <jsp:include page="<%= getSubsectionPage(request, \"Final\") %>"/>
  </div>
  <script type="text/javascript">
    //<![CDATA[

    function walk(element, condition, operation) {
      if (!element) return;
      if (condition(element)) {
        operation(element);
        return;
      }
      for (var e = element.firstChild; e != null; e = e.nextSibling) {
        walk(e, condition, operation);
      }
    }

    function isCurrentLink(e) {
      if (e.tagName != "A") return false;
      re = new RegExp("^" + e.href + ".*(\\?.*)?$");
      return re.test(window.location.href);
    }

    function makeSelected(e) {
      e.className = "ae-nav-selected";
    }

    walk(document.getElementById("menu"), isCurrentLink, makeSelected);

    //]]>
  </script>
  </div>
</body>
</html>
