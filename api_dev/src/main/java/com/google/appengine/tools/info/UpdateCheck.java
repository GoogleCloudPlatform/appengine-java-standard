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

package com.google.appengine.tools.info;

import com.google.apphosting.utils.config.ApplicationXml;
import com.google.apphosting.utils.config.ApplicationXmlReader;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * {@code UpdateCheck} is responsible for gathering version
 * information about the local SDK, uploading this information to
 * Google's servers in exchange for information about the latest
 * version available, and making both sets of information available
 * programmatically via {@link UpdateCheckResults} and for direct user
 * consumption via a nag screen printed to a specified {@link
 * PrintStream}.
 *
 */
public class UpdateCheck {
  private static final Logger logger = Logger.getLogger(UpdateCheck.class.getName());

  /**
   * Nag the user no more frequently than once per week.
   */
  private static final long MAX_NAG_FREQUENCY = 60 * 60 * 24 * 7;

  /**
   * Users can create this file in their home directories to disable
   * the check for updates and nag screens.
   */
  private static final String OPT_OUT_FILE = ".appcfg_no_nag";

  private static final ApplicationXmlReader APPLICATION_XML_READER = new ApplicationXmlReader();

  private final String server;
  private final File appDirectory;
  private final Preferences prefs;
  private final boolean secure;

  /**
   * Create a new {@code UpdateCheck}.
   *
   * @param server The remote server to connect to when retrieving
   * remote version information.
   */
  public UpdateCheck(String server) {
    this(server, null, false);
  }

  /**
   * Create a new {@code UpdateCheck}.
   *
   * @param server The remote server to connect to when retrieving
   * remote version information.
   * @param appDirectory The application directory that you plan to
   * test or publish, or {@code null} if no application directory is
   * available.
   * @param secure if {@code true}, use an https (instead of http)
   * connection to the remote server.
   */
  public UpdateCheck(String server, File appDirectory, boolean secure) {
    this.server = server;
    this.appDirectory = appDirectory;
    this.secure = secure;
    prefs = Preferences.userNodeForPackage(UpdateCheck.class);
  }

  /**
   * Returns true if the user wants to check for updates even when we
   * don't need to.  We assume that users will want this
   * functionality, but they can opt out by creating an .appcfg_no_nag
   * file in their home directory.
   */
  public boolean allowedToCheckForUpdates() {
    // N.B.(schwardo): It would be nice to use Preferences here, but
    // we want to make it very easy for users to opt-out, and
    // requiring users to modify the registry on Windows by hand is
    // not a good idea.  Creating a file should be simple enough.
    File optOutFile = new File(System.getProperty("user.home"), OPT_OUT_FILE);
    return !optOutFile.exists();
  }

  /**
   * Returns an {@link UpdateCheckResults} for checking if a WAR
   * directory or the local installation uses an out of date version
   * of the SDK.
   *
   * <p>Callers that do not already communicate with Google explicitly
   * (e.g. the DevAppServer) should check {@code
   * allowedToCheckForUpdates} before calling this method.
   */
  @Deprecated
  public UpdateCheckResults checkForUpdates() {
    Version localVersion = getWarVersion(appDirectory);
    logger.fine("Local Version: " + localVersion);

    Version remoteVersion = new RemoteVersionFactory(localVersion, server, secure).getVersion();
    logger.fine("Remote Version: " + remoteVersion);

    return new UpdateCheckResults(localVersion, remoteVersion);
  }

  /**
   * Returns a {@link Version} with the same {@link Version#getRelease} and
   * {@link Version#getTimestamp} as the first version in the passed in list
   * and with the union of all the {@link Version#getApiVersions} values
   * from the passed in list.
   * <p>
   * Note that {@link Version#getRelease} and {@link Version#getTimestamp} values
   * are derived from the SDK and match for all the passed in local versions.
   * <p>
   * For details on the construction of the passed in list see
   * {@link #getLocalVersions(File)}.
   *
   * @param localVersionWithPaths The non empty {@Link List} of {link {@link VersionWithWarPath}
   * objects for an application returned by {@link #getLocalVersions(File)}.
   */
  @VisibleForTesting
  static Version makeCombinedLocalVersion(List<VersionWithWarPath> localVersionWithPaths) {
    if (localVersionWithPaths.isEmpty()) {
      throw new IllegalArgumentException("localVersionWithPaths may not be empty.");
    }
    ImmutableSet.Builder<String> setBuilder = ImmutableSet.builder();
    for (VersionWithWarPath localVersionWithPath : localVersionWithPaths) {
      setBuilder.addAll(localVersionWithPath.getVersion().getApiVersions());
    }
    Version localVersion0 = localVersionWithPaths.get(0).getVersion();
    return new Version(localVersion0.getRelease(), localVersion0.getTimestamp(),
        setBuilder.build());
  }

  ApplicationVersionInfo getApplicationVersionInfo() {
    List<VersionWithWarPath> localVersionWithWarPaths = getLocalVersions(appDirectory);
    logger.fine("Local Versions: " + localVersionWithWarPaths);

    Version remoteVersion =
        new RemoteVersionFactory(makeCombinedLocalVersion(localVersionWithWarPaths),
            server, secure).getVersion();
    logger.fine("Remote Version: " + remoteVersion);

    return new ApplicationVersionInfo(localVersionWithWarPaths, remoteVersion);
  }

  @VisibleForTesting
  static class VersionWithWarPath {
    private final File warPath;
    private final Version version;

    VersionWithWarPath(File warPath, Version version) {
      this.warPath = warPath;
      this.version = version;
    }

    File getWarPath() {
      return warPath;
    }

    Version getVersion() {
      return version;
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((version == null) ? 0 : version.hashCode());
      result = prime * result + ((warPath == null) ? 0 : warPath.hashCode());
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null || getClass() != obj.getClass()) {
        return false;
      }
      VersionWithWarPath other = (VersionWithWarPath) obj;
      if (version == null) {
        if (other.version != null) {
          return false;
        }
      } else if (!version.equals(other.version)) {
        return false;
      }
      if (warPath == null) {
        if (other.warPath != null) {
          return false;
        }
      } else if (!warPath.equals(other.warPath)) {
        return false;
      }
      return true;
    }

    @Override
    public String toString() {
      return "VersionWithWarPath: warPath=" + warPath + " version=" + version;
    }
  }

  @VisibleForTesting
  static class ApplicationVersionInfo {
    final List<VersionWithWarPath> localVersions;
    final Version remoteVersion;

    ApplicationVersionInfo(List<VersionWithWarPath> localVersions, Version remoteVersion) {
      this.localVersions = localVersions;
      this.remoteVersion = remoteVersion;
    }

    final List<VersionWithWarPath> getLocalVersions() {
      return localVersions;
    }

    final Version getRemoteVersion() {
      return remoteVersion;
    }

    @Override
    public String toString() {
      return "ApplicationVersionInfo: localVersions=" + localVersions
          + " remoteVersion=" + remoteVersion;
    }
  }

  /**
   * Returns true if the passed in path looks like the path for an
   * exploded WAR directory.
   */
  static boolean isWar(File maybeWarPath) {
    if (maybeWarPath == null) {
      return false;
    }
    File webInf = new File(maybeWarPath, "WEB-INF");
    File appengineWebXml = new File(webInf, "appengine-web.xml");
    File webXml = new File(webInf, "web.xml");
    File libDir = new File(webInf, "lib");

    return appengineWebXml.isFile()
        && webXml.isFile()
        && libDir.isDirectory();
  }

  /**
   * Returns true if the passed in path looks like the path for an
   * exploded EAR directory.
   */
  static boolean isEar(File maybeEarPath) {
    if (maybeEarPath == null) {
      return false;
    }
    File metaInf = new File(maybeEarPath, "META-INF");
    File appengineApplicationXml = new File(metaInf, "appengine-application.xml");
    File applicationXml = new File(metaInf, "application.xml");
    return appengineApplicationXml.isFile()
        && applicationXml.isFile();
  }

  /**
   * Returns the {@link Version} for the passed in war directory.
   * If unable to extract a version from the pased in WAR
   * directory the user jars provided with the SDK will be used.
   */
  static Version getWarVersion(File warPath) {
    if (warPath != null) {
      File libDir = new File(new File(warPath, "WEB-INF"), "lib");
      if (libDir.isDirectory()) {
        File[] libFiles = libDir.listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
              String lowercasePath = file.getPath().toLowerCase();
              return lowercasePath.endsWith(".jar") || lowercasePath.endsWith(".zip");
            }
        });
        if (libFiles != null) {
          return new LocalVersionFactory(Arrays.asList(libFiles)).getVersion();
        }
      }
    }
    return AppengineSdk.getSdk().getLocalVersion();
  }

  static List<VersionWithWarPath> getEarVersions(File earDirectory) {
    File metaInf = new File(earDirectory, "META-INF");
    File applicationXmlFile = new File(metaInf, "application.xml");
    ImmutableList.Builder<VersionWithWarPath> resultBuilder = ImmutableList.builder();
    try {
      ApplicationXml applicationXml =
          APPLICATION_XML_READER.processXml(new FileInputStream(applicationXmlFile));
      for (ApplicationXml.Modules.Web web : applicationXml.getModules().getWeb()) {
        File warDirectory = new File(earDirectory, web.getWebUri());
        if (isWar(warDirectory)) {
          resultBuilder.add(new VersionWithWarPath(warDirectory, getWarVersion(warDirectory)));
        }
      }
    } catch (FileNotFoundException fnfe) {
      throw new IllegalStateException("File should exist - '" + applicationXmlFile + "'");
    }
    return resultBuilder.build();
  }

  /**
   * Returns the {@link List} of {@link VersionWithWarPath} objects for the
   * {@code applicationDirectory}.
   * If {@code applicationDirectory} is an EAR directory containing
   * at least one WAR directory the returned list contains an
   * entry for ever WAR within the EAR. Otherwise if {@code applicationDirectory}
   * is a war directory the returned list contains an entry
   * for the WAR. Otherwise for compatibility purposes the returned
   * list contains an entry with the path equal to the path for
   * {@code applicationDirectory} and a version derived from the
   * SDK (see {@link #getWarVersion(File)}.
   */
  static List<VersionWithWarPath> getLocalVersions(File applicationDirectory) {
    List<VersionWithWarPath> result = null;

    if (isEar(applicationDirectory)) {
      result = getEarVersions(applicationDirectory);
    }

    //
    // We treat an empty EAR as a WAR which has the effect
    // of defaulting to the SDK's api version.
    if (result == null || result.isEmpty()) {
      result = ImmutableList.of(new VersionWithWarPath(applicationDirectory,
          getWarVersion(applicationDirectory)));
    }
    return result;
  }

  /**
   * Check to see if there is a new version of the SDK available and,
   * if sufficient time has passed since the last nag, print a nag
   * screen to {@code out}.  This method always errs on the side of
   * not nagging the user if errors are encountered.
   *
   * <p>Callers that do not already communicate with Google explicitly
   * (e.g. the DevAppServer) should check {@code
   * allowedToCheckForUpdates} before calling this method.
   *
   * @return true if a nag screen was printed, false otherwise
   */
  public boolean maybePrintNagScreen(PrintStream out) {
    ApplicationVersionInfo applicationVersionInfo = getApplicationVersionInfo();
    // We always check for updates if the user has not opted out (to
    // gather more accurate version statistics), but we don't bother
    // nagging the user unless it's time.
    if (!canNagUser()) {
      return false;
    }

    if (doNagScreen(applicationVersionInfo, out)) {
      prefs.putLong("lastNagTime", System.currentTimeMillis());
      // Flush the nag time to disk.
      try {
        prefs.flush();
      } catch (BackingStoreException ex) {
        logger.log(Level.WARNING, "Could not update last nag time.");
      }
      return true;
    } else {
      return false;
    }
  }

  /**
   * Returns true if enough time has elapsed since we last nagged the
   * user that we can nag them again.
   */
  private boolean canNagUser() {
    // Check again in case we've nagged the user in other JVM since
    // our JVM started.
    try {
      prefs.sync();
    } catch (BackingStoreException ex) {
      logger.log(Level.WARNING, "Could not sync last nag time.");
    }

    long lastNagTime = prefs.getLong("lastNagTime", 0);
    return (System.currentTimeMillis() - lastNagTime) > MAX_NAG_FREQUENCY;
  }

  static boolean doNagScreen(ApplicationVersionInfo applicationVersionInfo, PrintStream out) {
    for (VersionWithWarPath versionWithWarPath : applicationVersionInfo.getLocalVersions()) {
      UpdateCheckResults checkResults =
          new UpdateCheckResults(versionWithWarPath.getVersion(),
              applicationVersionInfo.getRemoteVersion());
      if (checkResults.isLocalApiVersionNoLongerSupported()) {
        String apiVersionComparisonMessage;
        if (checkResults.isNewerReleaseAvailable()) {
          apiVersionComparisonMessage = "no longer";
        } else {
          apiVersionComparisonMessage = "not yet";
        }
        printNagMessage("The API version in this SDK is " + apiVersionComparisonMessage
            + " supported on the server!",
            out, checkResults);
        logger.fine("Unsupported API version detected for " + versionWithWarPath);
        return true;
      }
    }

    for (VersionWithWarPath versionWithWarPath : applicationVersionInfo.getLocalVersions()) {
      UpdateCheckResults checkResults =
          new UpdateCheckResults(versionWithWarPath.getVersion(),
              applicationVersionInfo.getRemoteVersion());
      if (checkResults.isNewerReleaseAvailable()) {
        // There's no point in telling them there is a new API version
        // available unless they can upgrade to a new SDK.
        if (checkResults.isNewerApiVersionAvailable()) {
          printNagMessage("You are using a deprecated API version.  Please upgrade.",
                          out, checkResults);
          logger.fine("Deprecated API version detected for " + versionWithWarPath);
          return true;
        }

        // No new API version to be aware of, just a SDK update.
        printNagMessage("There is a new version of the SDK available.",
                        out, checkResults);
        logger.fine("Deprecated SDK version detected for " + versionWithWarPath);
        return true;
      }
    }
    return false;
  }

  private static void printNagMessage(String message, PrintStream out, UpdateCheckResults results) {
    out.println("********************************************************");
    out.println(message);
    out.println("-----------");
    out.println("Latest SDK:");
    out.println(results.getRemoteVersion());
    out.println("-----------");
    out.println("Your SDK:");
    out.println(results.getLocalVersion());
    out.println("-----------");
    out.println("Please visit https://cloud.google.com/sdk/gcloud/reference/components/update ");
    out.println("to see how to get the latest version.");
    out.println("********************************************************");
  }

  static boolean validateVersion(String version, PrintStream out) {
    if (version != null) {
      String[] versions = version.split("\\.");
      if (versions.length >= 2) {
        if (versions[0].equals("1")) {
          try {
            int minor = Integer.parseInt(versions[1]);
            if (minor < 6) {
              out.println("********************************************************");
              out.println("Warning: Future versions of the Dev App Server will require "
                  + "Java 1.6 or later. Please upgrade your JRE.");
              out.println("********************************************************");
              return true;
            }
          } catch (NumberFormatException e) {
            //  ignore
          }
        }
      }
    }
    return false;
  }

  public boolean checkJavaVersion(PrintStream out) {
    return validateVersion(System.getProperty("java.specification.version"), out);
  }

}
