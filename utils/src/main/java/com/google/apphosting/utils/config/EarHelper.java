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

package com.google.apphosting.utils.config;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * Utility for server discovery within an EAR directory.
 *
 */
public class EarHelper {
  private static final Logger LOGGER = Logger.getLogger(EarHelper.class.getName());
  private static final AppEngineApplicationXmlReader APP_ENGINE_APPLICATION_XML_READER =
      new AppEngineApplicationXmlReader();
  private static final ApplicationXmlReader APPLICATION_XML_READER = new ApplicationXmlReader();

  private static final String META_INF = "META-INF";
  private static final String APPENGINE_APPLICATION_XML_NAME = "appengine-application.xml";
  private static final String APPLICATION_XML_NAME = "application.xml";

  /**
   * Returns true if passed a path for an EAR directory.
   * <p>
   * This is a convenience fuction that simply calls
   * {@link #isEar(String, boolean)} for {@code directory} with {@code logWhyNot}
   * set to true.
   */
  public static boolean isEar(String dir) {
    return isEar(dir, true);
  }

  /**
   * Returns true if {@code directory} contains a {@link #META_INF} sub-directory
   * with the required {@link #APPENGINE_APPLICATION_XML_NAME} and
   * {@link #APPLICATION_XML_NAME} files.
   * <p>
   * Note this test is a spot check to distinguish an EAR directory as
   * opposed to a WAR directory. Passing this test does not guarantee that a
   * directory is a fully valid EAR directory.

   * @param directory The path for the directory to check or null
   * @param logWhyNot true means to log the reason this returns false if
   *     it does so.
   */
  public static boolean isEar(String directory, boolean logWhyNot) {
    if (directory == null) {
      if (logWhyNot) {
        LOGGER.fine("Directory 'null' is not an EAR directory. ");
      }
      return false;
    }

    File metaInf = new File(directory, META_INF);
    if (!hasFile(metaInf, APPENGINE_APPLICATION_XML_NAME)) {
      logNotAnEar(directory.toString(), APPENGINE_APPLICATION_XML_NAME, logWhyNot);
      return false;
    }

    if (!hasFile(metaInf, APPLICATION_XML_NAME)) {
      logNotAnEar(directory.toString(), APPLICATION_XML_NAME, logWhyNot);
      return false;
    }

    return true;
  }

  private static void logNotAnEar(String dir, String missingFile,
      boolean withLogging) {
    if (withLogging) {
      LOGGER.fine("Directory '" + dir + "' is not an EAR directory. File "
          + new File(dir, missingFile) + " not detected.");
    }
  }

  private static void reportConfigException(String message) {
    LOGGER.info(message);
    throw new AppEngineConfigException(message);
  }

  /**
   * Reads an {@link EarInfo from} the provided EAR directory.
   * Validation is done via the xsd schemaFile.
   * @throws AppEngineConfigException if the provided EAR directory is invalid.
   */
  public static EarInfo readEarInfo(String earDirectoryPath, File schemaFile)
      throws AppEngineConfigException {
    if (!isEar(earDirectoryPath)) {
      throw new IllegalArgumentException("earDir '" + earDirectoryPath
          + "' is not a valid EAR directory.");
    }
    File earDirectory = new File(earDirectoryPath).getAbsoluteFile();
    File metaInf = new File(earDirectory, META_INF);
    XmlUtils.validateXml(
        new File(metaInf, APPENGINE_APPLICATION_XML_NAME).getAbsolutePath(), schemaFile);
    AppEngineApplicationXml appEngineApplicationXml =
        APP_ENGINE_APPLICATION_XML_READER.processXml(
            getInputStream(metaInf, APPENGINE_APPLICATION_XML_NAME));
    ApplicationXml applicationXml =
        APPLICATION_XML_READER.processXml(getInputStream(metaInf, APPLICATION_XML_NAME));
    String applicationId = appEngineApplicationXml.getApplicationId();
    ImmutableList.Builder<WebModule> moduleListBuilder = ImmutableList.builder();
    for (ApplicationXml.Modules.Web web : applicationXml.getModules().getWeb()) {
      File serviceDirectory = getServiceDirectory(earDirectory, web.getWebUri());
      WebModule webModule = readWebModule(web.getContextRoot(), serviceDirectory, null, null, "");
      if (!applicationId.equals(webModule.getAppEngineWebXml().getAppId())) {
        LOGGER.info(
            "Application id '"
                + appEngineApplicationXml.getApplicationId()
                + "' from '"
                + new File(metaInf, APPENGINE_APPLICATION_XML_NAME)
                + "' is overriding "
                + " application id '"
                + webModule.getAppEngineWebXml().getAppId()
                + "' from '"
                + new File(serviceDirectory, AppEngineWebXmlReader.DEFAULT_RELATIVE_FILENAME)
                + "'");
        webModule.getAppEngineWebXml().setAppId(applicationId);
        // TODO: Add support for an application id override.
        // TODO: consider inserting an app id prefix here if relevant
      }
      moduleListBuilder.add(webModule);
    }
    ImmutableList<WebModule> webModules = moduleListBuilder.build();
    if (webModules.size() == 0) {
      reportConfigException("At least one web module is required in '"
          + new File(metaInf, APPLICATION_XML_NAME) + "'");
    }
    return new EarInfo(earDirectory, appEngineApplicationXml, applicationXml, webModules);
  }

  /**
   * Reads a {@link WebModule} from the provided application directory.
   * <p>
   * If the application directory contains a WEB-INF/app.yaml file this will call
   * {@link AppYamlProcessor#convert} to generate WEB-INF/appengine-web.xml and
   * WEB-INF/web.xml files.
   *
   * @param contextRoot if this web module is part of an EAR supply the
   * context-root element from the web module's specification in
   * ear/META-INF/application.xml otherwise supply null to indicate the web
   * module has no context root.
   * @param applicationDirectory the application directory.
   * @param appengineWebXmlFile the appengine-web.xml to read or null to use the default.
   * @param webXmlFile the web.xml to read or null to use the default.
   * @param appIdPrefix a string to prepend to the app id read from appengine-web.xml. May be
   *     empty but not null.
   * @throws AppEngineConfigException if applicationDirectory is not correct.
   */
  public static WebModule readWebModule(
      @Nullable String contextRoot, File applicationDirectory, @Nullable File appengineWebXmlFile,
      @Nullable File webXmlFile, String appIdPrefix) throws AppEngineConfigException {
    Preconditions.checkNotNull(appIdPrefix);
    AppEngineWebXmlReader appEngineWebXmlReader =
        newAppEngineWebXmlReader(applicationDirectory, appengineWebXmlFile);

    String webXmlFileLocation =
        (webXmlFile == null)
            ? new File(applicationDirectory, "WEB-INF/web.xml").getAbsolutePath()
            : webXmlFile.getAbsolutePath();

    // Maybe convert existing app.yaml to both web and appengine xml files:
    AppYamlProcessor.convert(
        new File(applicationDirectory, "WEB-INF"),
        appEngineWebXmlReader.getFilename(),
        webXmlFileLocation);

    AppEngineWebXml appEngineWebXml;
    try {
      appEngineWebXml = appEngineWebXmlReader.readAppEngineWebXml();
    } catch (AppEngineConfigException aece) {
      throw new AppEngineConfigException(String.format("Invalid appengine-web.xml(%s) - %s",
          appEngineWebXmlReader.getFilename(), aece.getMessage()));
    }
    String appId = appEngineWebXml.getAppId();
    if (appId != null) {
      appEngineWebXml.setAppId(appIdPrefix + appEngineWebXml.getAppId());
    }

    WebXmlReader webXmlReader =
        newWebXmlReader(
            applicationDirectory, webXmlFile, appEngineWebXml.isWebXmlRequired());
    WebXml webXml = webXmlReader.readWebXml();

    WebModule webModule =
        new WebModule(applicationDirectory, appEngineWebXml,
            new File(appEngineWebXmlReader.getFilename()), webXml,
            new File(webXmlReader.getFilename()), contextRoot);
    return webModule;
  }

  @VisibleForTesting
  static File getServiceDirectory(File earDirectory, String contextRoot) {
    // If contextRoot is an absolute URI, we skip the earDirectory path
    String absoluteURI = "file:";
    File serviceDirectory = null;
    if (contextRoot.startsWith(absoluteURI)) {
      try {
        serviceDirectory = new File(new URI(contextRoot));
      } catch (URISyntaxException e) {
        reportConfigException("Service directory '" + contextRoot + "' is not a valid URI.");
      }
    } else {
      serviceDirectory = new File(earDirectory, contextRoot);
    }
    if (!serviceDirectory.exists() || !serviceDirectory.isDirectory()) {
      reportConfigException(
          "Service directory '" + serviceDirectory + "' must exist and be a directory.");
    }
    return serviceDirectory;
  }

  private static AppEngineWebXmlReader newAppEngineWebXmlReader(File applicationDirectory,
      File appEngineWebXmlFile) {
    return appEngineWebXmlFile == null ?
        new AppEngineWebXmlReader(applicationDirectory.getAbsolutePath()) :
          new AppEngineWebXmlReader(appEngineWebXmlFile.getParent(),
              appEngineWebXmlFile.getName());
  }

  private static WebXmlReader newWebXmlReader(
      File applicationDirectory, File webXmlFile, boolean isRequired) {
    return webXmlFile == null
        ? new WebXmlReader(applicationDirectory.getAbsolutePath(), isRequired)
        : new WebXmlReader(webXmlFile.getParent(), webXmlFile.getName(), isRequired);
  }

  /**
   * Returns an input stream for an existing file.
   */
  private static InputStream getInputStream(File parent, String fileName) {
    File file = new File(parent, fileName);
    try {
      return new FileInputStream(file);
    } catch (FileNotFoundException fnfe) {
      throw new IllegalStateException("File should exist - '" + file + "'");
    }
  }

  private static boolean hasFile(File parent, String child) {
      File file = new File(parent, child);
      if (!file.isFile()) {
        return false;
      }

      return true;
  }
}
