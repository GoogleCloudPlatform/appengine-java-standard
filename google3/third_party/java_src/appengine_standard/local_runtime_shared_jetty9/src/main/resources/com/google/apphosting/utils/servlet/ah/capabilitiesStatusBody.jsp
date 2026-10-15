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


<div id="capabilitiesstatus">
    <h3>Capabilities Status Configuration</h3>
    With the Capabilities API, your application can detect outages and scheduled downtime
    for specific API capabilities. <br>With this information,
    you can gracefully disable functionality in your application that depends on the
    unavailable capability before it impacts your users.
    <br>
    This page allows you to disable capabilities so that you can test the behaviour
    of your application in a degraded environment.
    <br><br>

    <div id="capabilitiesstatus-feedback"></div>
    <form id="capabilitiesstatusform"
          action="/_ah/admin/capabilitiesstatus" method="post"
            >

        <table id="CapabilitiesTable">
            <c:forEach var="entity" items="${requestScope.capabilities_status}">
                <tr>
                    <td align="right">
                        <label for="<c:out value="${entity.name}"/>"
                               ><c:out value="${entity.name}"/></label>
                    </td>
                    <td>
                        <select name="<c:out value="${entity.name}"/>"
			        id="<c:out value="${entity.name}"/>"
                                onchange="document.forms[0].submit();">
                            <option value="DISABLED"
                                    <c:if test='${entity.status == "DISABLED"}'>
                                        selected="selected"
                                    </c:if>
                                    >
                                The capability is disabled.
                            </option>
                            <option value="ENABLED"
                                    <c:if test='${entity.status == "ENABLED"}'>
                                        selected="selected"
                                    </c:if>
                                    >
                                The capability is available and no maintenance is currently planned.
                            </option>
                            <option value="SCHEDULED_MAINTENANCE"
                                    <c:if test='${entity.status == "SCHEDULED_MAINTENANCE"}'>
                                        selected="selected"
                                    </c:if>
                                    >
                                The capability is available but scheduled for maintenance.
                            </option>
                            <option value="UNKNOWN"
                                    <c:if test='${entity.status == "UNKNOWN"}'>
                                        selected="selected"
                                    </c:if>
                                    >
                                    The status of the capability is unknown..
                            </option>
                        </select>
                    </td>
                </tr>
            </c:forEach>
        </table>

        <!--div id="capabilitiesstatus-submit">
            <input name="change-capabilities" type="submit" value="Change Status">
        </div-->

    </form>
</div>
