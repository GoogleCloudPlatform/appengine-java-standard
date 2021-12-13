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

package com.google.appengine.api.taskqueue.dev;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.apphosting.utils.config.ConfigurationException;
import com.google.apphosting.utils.config.QueueXml;
import com.google.common.io.Resources;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class LocalTaskQueueConfigTest {
  @ClassRule public static TemporaryFolder temporaryFolder = new TemporaryFolder();

  /** Sets up some fake app dirs with queue configuration files. */
  @BeforeClass
  public static void setUpAppDirs() throws IOException {
    byte[] queueXmlBytes =
        Resources.toByteArray(
            LocalTaskQueueConfigTest.class.getResource("config_test_data/queue.xml"));
    byte[] queueYamlBytes =
        Resources.toByteArray(
            LocalTaskQueueConfigTest.class.getResource("config_test_data/queue.yaml"));

    File xmlOnlyWebInf = temporaryFolder.newFolder("xml-only", "WEB-INF");
    Files.write(xmlOnlyWebInf.toPath().resolve("queue.xml"), queueXmlBytes);

    File yamlOnlyWebInf = temporaryFolder.newFolder("yaml-only", "WEB-INF");
    Files.write(yamlOnlyWebInf.toPath().resolve("queue.yaml"), queueYamlBytes);

    File xmlAndYamlWebInf = temporaryFolder.newFolder("xml-and-yaml", "WEB-INF");
    Files.write(xmlAndYamlWebInf.toPath().resolve("queue.xml"), queueXmlBytes);
    Files.write(xmlAndYamlWebInf.toPath().resolve("queue.yaml"), queueYamlBytes);
  }

  private final LocalTaskQueue localQueue = new LocalTaskQueue();

  private QueueXml getQueueConfig(String appDir) {
    String fullAppDir = temporaryFolder.getRoot().toPath().resolve(appDir).toString();
    return localQueue.parseQueueConfiguration(fullAppDir, getXmlFilename(fullAppDir), null);
  }

  private QueueXml getQueueConfigFromYaml(String appDir) {
    String fullAppDir = temporaryFolder.getRoot().toPath().resolve(appDir).toString();
    return localQueue.parseQueueConfiguration(fullAppDir, null, getYamlFilename(fullAppDir));
  }

  private QueueXml getQueueConfigFromXmlAndYaml(String appDir) {
    String fullAppDir = temporaryFolder.getRoot().toPath().resolve(appDir).toString();
    return localQueue.parseQueueConfiguration(
        fullAppDir, getXmlFilename(fullAppDir), getYamlFilename(fullAppDir));
  }

  private QueueXml getQueueConfigFromImplicitXmlAndYaml() {
    String fullAppDir = temporaryFolder.getRoot().toPath().resolve("xml-and-yaml").toString();
    return localQueue.parseQueueConfiguration(fullAppDir, null, null);
  }

  private static String getXmlFilename(String fullAppDir) {
    return Paths.get(fullAppDir, "WEB-INF", "queue.xml").toString();
  }

  private static String getYamlFilename(String fullAppDir) {
    return Paths.get(fullAppDir, "WEB-INF", "queue.yaml").toString();
  }

  private static void verifyIsXmlContent(QueueXml queueConfig) {
    // The queue.xml in test data has 4 entities.
    assertThat(queueConfig.getEntries()).hasSize(4);
  }

  private static void verifyIsYamlContent(QueueXml queueConfig) {
    // The queue.yaml in test data has 3 entities.
    assertThat(queueConfig.getEntries()).hasSize(3);
  }

  @Test
  public void testOnlyXml_PromotingYaml() {
    System.setProperty("appengine.promoteYaml", "true");
    QueueXml queueConfig = getQueueConfig("xml-only");
    verifyIsXmlContent(queueConfig);
  }

  @Test
  public void testOnlyXml_NotPromotingYaml() {
    System.setProperty("appengine.promoteYaml", "false");
    QueueXml queueConfig = getQueueConfig("xml-only");
    verifyIsXmlContent(queueConfig);
  }

  @Test
  public void testOnlyYaml_PromotingYaml() {
    System.setProperty("appengine.promoteYaml", "true");
    QueueXml queueConfig = getQueueConfigFromYaml("yaml-only");
    verifyIsYamlContent(queueConfig);
  }

  @Test
  public void testOnlyYaml_NotPromotingYaml() {
    System.setProperty("appengine.promoteYaml", "false");
    QueueXml queueConfig = getQueueConfigFromYaml("yaml-only");
    verifyIsYamlContent(queueConfig);
  }

  @Test
  public void testErrorForXmlAndYamlPath() {
    ConfigurationException expected =
        assertThrows(
            ConfigurationException.class, () -> getQueueConfigFromXmlAndYaml("xml-and-yaml"));
    assertThat(expected).hasMessageThat().contains("Found both queue.xml and queue.yaml");
  }

  @Test
  public void testXmlWithYaml_PromotingYaml() {
    System.setProperty("appengine.promoteYaml", "true");
    QueueXml queueConfig = getQueueConfigFromImplicitXmlAndYaml();
    verifyIsYamlContent(queueConfig);
  }

  @Test
  public void testXmlWithYaml_NotPromotingYaml() {
    System.setProperty("appengine.promoteYaml", "false");
    QueueXml queueConfig = getQueueConfigFromImplicitXmlAndYaml();
    verifyIsXmlContent(queueConfig);
  }
}
