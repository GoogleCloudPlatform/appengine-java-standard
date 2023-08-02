/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.appengine.tools.development;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;
import java.io.InputStream;
import java.net.URL;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class IsolatedAppClassLoaderTest {
  private static final String WEB_DEFAULT_LOCATION_DEVAPPSERVER1_PATH =
      "com/google/appengine/tools/development/jetty/webdefault.xml";

  @Test
  @org.junit.Ignore
  public void calculateCorrectContentForServletsFiltersDevappServer1() throws Exception {
    Set<String> classes = getClassesInAppDefinition(WEB_DEFAULT_LOCATION_DEVAPPSERVER1_PATH);
    assertThat(classes).hasSize(56);
    Set<String> classesFromWebDefault1 =
        ImmutableSet.of(
            "com.google.appengine.tools.development.DevAppServerRequestLogFilter",
            "com.google.appengine.tools.development.DevAppServerModulesFilter",
            "com.google.appengine.tools.development.jetty.StaticFileFilter",
            "com.google.apphosting.utils.servlet.TransactionCleanupFilter",
            "com.google.appengine.api.blobstore.dev.ServeBlobFilter",
            "com.google.appengine.tools.development.HeaderVerificationFilter",
            "com.google.appengine.tools.development.jetty.JettyResponseRewriterFilter",
            "com.google.appengine.tools.development.jetty.LocalResourceFileServlet",
            "com.google.appengine.api.blobstore.dev.UploadBlobServlet",
            "com.google.appengine.api.images.dev.LocalBlobImageServlet",
            "com.google.appengine.tools.development.jetty.FixupJspServlet",
            "com.google.appengine.api.users.dev.LocalLoginServlet",
            "com.google.appengine.api.users.dev.LocalLogoutServlet",
            "com.google.appengine.api.users.dev.LocalOAuthRequestTokenServlet",
            "com.google.appengine.api.users.dev.LocalOAuthAuthorizeTokenServlet",
            "com.google.appengine.api.users.dev.LocalOAuthAccessTokenServlet",
            "com.google.apphosting.utils.servlet.DeferredTaskServlet",
            "com.google.apphosting.utils.servlet.SessionCleanupServlet",
            "com.google.apphosting.utils.servlet.CapabilitiesStatusServlet",
            "com.google.apphosting.utils.servlet.DatastoreViewerServlet",
            "com.google.apphosting.utils.servlet.ModulesServlet",
            "com.google.apphosting.utils.servlet.TaskQueueViewerServlet",
            "com.google.apphosting.utils.servlet.InboundMailServlet",
            "com.google.apphosting.utils.servlet.SearchServlet",
            "com.google.apphosting.utils.servlet.AdminConsoleResourceServlet",
            "org.apache.jsp.ah.jetty.adminConsole_jsp",
            "org.apache.jsp.ah.jetty.datastoreViewerHead_jsp",
            "org.apache.jsp.ah.jetty.datastoreViewerBody_jsp",
            "org.apache.jsp.ah.jetty.datastoreViewerFinal_jsp",
            "org.apache.jsp.ah.jetty.searchIndexesListHead_jsp",
            "org.apache.jsp.ah.jetty.searchIndexesListBody_jsp",
            "org.apache.jsp.ah.jetty.searchIndexesListFinal_jsp",
            "org.apache.jsp.ah.jetty.searchIndexHead_jsp",
            "org.apache.jsp.ah.jetty.searchIndexBody_jsp",
            "org.apache.jsp.ah.jetty.searchIndexFinal_jsp",
            "org.apache.jsp.ah.jetty.searchDocumentHead_jsp",
            "org.apache.jsp.ah.jetty.searchDocumentBody_jsp",
            "org.apache.jsp.ah.jetty.searchDocumentFinal_jsp",
            "org.apache.jsp.ah.jetty.capabilitiesStatusHead_jsp",
            "org.apache.jsp.ah.jetty.capabilitiesStatusBody_jsp",
            "org.apache.jsp.ah.jetty.capabilitiesStatusFinal_jsp",
            "org.apache.jsp.ah.jetty.entityDetailsHead_jsp",
            "org.apache.jsp.ah.jetty.entityDetailsBody_jsp",
            "org.apache.jsp.ah.jetty.entityDetailsFinal_jsp",
            "org.apache.jsp.ah.jetty.indexDetailsHead_jsp",
            "org.apache.jsp.ah.jetty.indexDetailsBody_jsp",
            "org.apache.jsp.ah.jetty.indexDetailsFinal_jsp",
            "org.apache.jsp.ah.jetty.modulesHead_jsp",
            "org.apache.jsp.ah.jetty.modulesBody_jsp",
            "org.apache.jsp.ah.jetty.modulesFinal_jsp",
            "org.apache.jsp.ah.jetty.taskqueueViewerHead_jsp",
            "org.apache.jsp.ah.jetty.taskqueueViewerBody_jsp",
            "org.apache.jsp.ah.jetty.taskqueueViewerFinal_jsp",
            "org.apache.jsp.ah.jetty.inboundMailHead_jsp",
            "org.apache.jsp.ah.jetty.inboundMailBody_jsp",
            "org.apache.jsp.ah.jetty.inboundMailFinal_jsp");
    assertThat(classes).containsExactlyElementsIn(classesFromWebDefault1);
  }

  private static Set<String> getClassesInAppDefinition(String appDefPath) throws Exception {
    URL resourceURL = Resources.getResource(appDefPath);
    try (InputStream stream = resourceURL.openStream()) {
      return IsolatedAppClassLoader.getServletAndFilterClasses(stream);
    }
  }
}
