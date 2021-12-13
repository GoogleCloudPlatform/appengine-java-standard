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

package com.google.appengine.tools.development.testing;

import com.google.appengine.api.mail.dev.LocalMailService;
import com.google.appengine.tools.development.ApiProxyLocal;
import java.util.logging.Level;

/**
 * Config for accessing the local mail service in tests.
 * {@link #tearDown()} wipes out sent messages so that there is no state passed
 * between tests.
 *
 */
public class LocalMailServiceTestConfig implements LocalServiceTestConfig {

  private Boolean logMailBody;
  private Level logMailLevel;

  @Override
  public void setUp() {
    ApiProxyLocal proxy = LocalServiceTestHelper.getApiProxyLocal();
    if (logMailBody != null) {
      proxy.setProperty(LocalMailService.LOG_MAIL_BODY_PROPERTY, logMailBody.toString());
    }
    if (logMailLevel != null) {
      proxy.setProperty(LocalMailService.LOG_MAIL_LEVEL_PROPERTY, logMailLevel.getName());
    }
  }

  @Override
  public void tearDown() {
    LocalMailService mailService = getLocalMailService();
    mailService.clearSentMessages();
  }

  public Boolean getLogMailBody() {
    return logMailBody;
  }

  /**
   * Controls whether or not the message body is logged.
   * @param logMailBody
   * @return {@code this} (for chaining)
   */
  public LocalMailServiceTestConfig setLogMailBody(boolean logMailBody) {
    this.logMailBody = logMailBody;
    return this;
  }

  public Level getLogMailLevel() {
    return logMailLevel;
  }

  /**
   * Controls the level at which each message is logged.
   * @param logMailLevel
   * @return {@code this} (for chaining)
   */
  public LocalMailServiceTestConfig setLogMailLevel(Level logMailLevel) {
    this.logMailLevel = logMailLevel;
    return this;
  }

  public static LocalMailService getLocalMailService() {
    return (LocalMailService) LocalServiceTestHelper.getLocalService(
        LocalMailService.PACKAGE);
  }
}
