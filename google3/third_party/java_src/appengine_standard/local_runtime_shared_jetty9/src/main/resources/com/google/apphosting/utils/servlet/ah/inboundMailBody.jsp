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

<div id="inboundmail">
  <h3>Email</h3>
  <div id="inboundmail-feedback"></div>
  <form id="inboundmail-form"
    action="/_ah/mail/" method="post"
    onsubmit="sendInboundMailWebhook(); return false">

    <input type="hidden" name="payload" id="payload">
    <input type="hidden" id="content-type" name="header:Content-Type" value="message/rfc822">
    <input type="hidden" id="content-length" name="header:Content-Length">

    <div class="fieldset">
      <label for="from">From:</label>
      <input type="text" id="from" name="from" size="40">
    </div>

    <div class="fieldset">
      <label for="to">To:</label>
      <input type="text" id="to" name="to" size="40">
    </div>

    <div class="fieldset">
      <label for="cc">Cc:</label>
      <input type="text" id="cc" name="cc" size="40">
    </div>

    <div class="fieldset">
      <label for="subject">Subject:</label>
      <input type="text" id="subject" name="subject" size="40">
    </div>

    <div id="body-c" class="fieldset">
      <label for="body">Message body (plain text):</label>
      <textarea id="body" name="body" rows="10" cols="50"></textarea>
    </div>

    <div id="inboundmail-submit">
      <input name="send-mail" type="submit" value="Send Email">
    </div>

  </form>
</div>
