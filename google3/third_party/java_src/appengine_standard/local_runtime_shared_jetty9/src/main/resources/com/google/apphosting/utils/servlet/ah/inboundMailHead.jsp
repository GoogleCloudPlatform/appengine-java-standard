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
<%@ page isELIgnored="false" %>
<title>
  <c:out value="${requestScope.applicationName}"/>
  Development Console - Inbound Mail
</title>
<style type="text/css">
  <%@ include file="css/inboundmail.css" %>
</style>
<script type="text/javascript" language="javascript" src="/_ah/resources?resource=webhook"></script>
<script type="text/javascript" language="javascript" src="/_ah/resources?resource=multipart_form_data"></script>
<script type="text/javascript" language="javascript" src="/_ah/resources?resource=rfc822_date"></script>
<script type="text/javascript">
  //<![CDATA[
  var inboundmailFeedbackEl;
  var inboundmailFormEl;
  var payloadEl;
  var fromEl;
  var toEl;
  var chatEl;
  var contentLengthEl;
  var contentTypeEl;

  var sendInboundMailWebhook = function() {

    if (!inboundmailFeedbackEl) {
      inboundmailFeedbackEl = document.getElementById('inboundmail-feedback');
      inboundmailFormEl = document.getElementById('inboundmail-form');
      fromEl = document.getElementById('from');
      toEl = document.getElementById('to');
      ccEl = document.getElementById('cc');
      subjectEl = document.getElementById('subject');
      bodyEl = document.getElementById('body');
      payloadEl = document.getElementById('payload');
      contentLengthEl = document.getElementById('content-length');
    }

    var from = fromEl.value;
    var to = toEl.value;
    var cc = ccEl.value;
    var subject = subjectEl.value;
    var body = bodyEl.value;

    if (!to || !from || !body) {
      inboundmailFeedbackEl.className = 'ae-errorbox';
      inboundmailFeedbackEl.innerHTML = 'From, To and Message body are required.';
      return;
    }

    inboundmailFeedbackEl.className = 'ae-message';
    inboundmailFeedbackEl.innerHTML = 'Sending mail message...';

    var mpfd = new MultipartFormData();
    mpfd.addHeader('MIME-Version', '1.0');
    mpfd.addHeader('Date', RFC822Date.format(new Date()));
    mpfd.addHeader('From', from);
    mpfd.addHeader('To', to);
    if (cc) {
      mpfd.addHeader('Cc', cc);
    }
    mpfd.addHeader('Subject', subject);
    mpfd.addHeader('Content-Type', 'multipart/alternative; ' +
        'boundary=' + mpfd.boundary);
    mpfd.addPart(null, body, 'text/plain; charset=UTF-8');
    mpfd.addPart(null, body, 'text/html; charset=UTF-8');

    payloadEl.value = mpfd.toString();

    contentLengthEl = payloadEl.value.length;

    inboundmailFormEl.action = '/_ah/mail/' + escape(to);

    (new Webhook('inboundmail-form')).run(handleInboundMailResult);

    // Prevents actual form posts.
    return false;
  };

  var handleInboundMailResult = function(hook, req, error) {
    if (error != null || req == null || req.status != 200) {
      inboundmailFeedbackEl.className = 'ae-errorbox';
      inboundmailFeedbackEl.innerHTML = 'Message send failure<br>' +
          req.responseText;
    } else {
      var timestamp;
      var dateString = new Date().toString();
      var match = dateString.match(/(\d\d:\d\d:\d\d).+\((.+)\)/);
      if (!match || !match[0] || !match[2]) {
        timestamp = dateString;
      } else {
        timestamp = match[1] + ' ' + match[2];
      }

      inboundmailFeedbackEl.className = 'ae-message';
      inboundmailFeedbackEl.innerHTML = 'Message has been sent at ' + timestamp;
    }
  };

  //]]>
</script>

