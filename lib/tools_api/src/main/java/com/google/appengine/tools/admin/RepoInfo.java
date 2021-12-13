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

package com.google.appengine.tools.admin;

import com.google.auto.value.AutoValue;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * Auto-detects source context that was used to build and deploy an application by scanning
 * its git directory.
 */
final class RepoInfo {
  /**
   * SourceContext is a reference to a persistent snapshot of the source tree stored in a
   * version control repository.
   */
  @AutoValue
  abstract static class SourceContext {
    /** A URL string identifying the repository. */
    @Nullable
    abstract String getRepositoryUrl();

    /** The canonical, unique, and persistent identifier of the deployed revision. */
    abstract String getRevisionId();

    /** The source context message in JSON format.*/
    @Nullable
    abstract String getJson();

    /** The cloud repo project id, if available. */
    @Nullable
    abstract String getProjectId();

    /** The cloud repo id, if available. */
    @Nullable
    abstract String getRepoId();

    /** The cloud repo name, if available. */
    @Nullable
    abstract String getRepoName();

    /** The type of remote repo this context represents. */
    abstract RemoteType getRemoteType();

    /** The type of context information, in ascending order of preference. */
    enum RemoteType {
      /** No details are known about the context. */
      OTHER,

      /** A git repository stored on an unfamiliar host. */
      GIT_UNKNOWN,

      /** An ssh link to a git repository on a known host (Github or BitBucket) */
      GIT_KNOWN_HOST_SSH,

      /** An http link to a git repository on a known host (Github or BitBucket) */
      GIT_KNOWN_HOST,

      /** A google cloud repo. */
      CLOUD_REPO
    }

    boolean isCloudRepo() {
      return getRepoId() != null || getProjectId() != null;
    }

    static SourceContext createLocal(String revisionId) {
      return new AutoValue_RepoInfo_SourceContext(null, revisionId, null, null, null, null,
          RemoteType.OTHER);
    }

    // Regex for parsing repo URLs.
    //
    // For cloud repos, the URL can take any of three forms:
    // 1: https://<hostname>/id/<repo_id>
    // 2: https://<hostname>/p/<project_id>
    // 3: https://<hostname>/p/<project_id>/r/<repo_name>
    //
    // There are two repo ID types. The first type is the direct repo ID,
    // <repo_id>, which uniquely identifies a repository. The second is the pair
    // (<project_id>, <repo_name>) which also uniquely identifies a repository.
    //
    // Case 2 is equivalent to case 3 with <repo_name> defaulting to "default".
    private static final Pattern CLOUD_REPO_RE = Pattern.compile(
          "^https://"
          + "(?<hostname>[^/]*)/"
          + "(?<idtype>p|id)/"
          + "(?<projectOrRepoId>[^/?#]+)"
          + "(/r/(?<repoName>[^/?#]+))?"
          + "([/#?].*)?");

   /**
     * Builds the source context from a URL and revision ID.
     *
     * <p>If {@code repoUrl} conforms to the predefined format of Google repo URLs, it parses out
     * the components of a Source API CloudRepoSourceContext. If {@code repoUrl} is not a valid
     * Google repo URL, it is treated as a generic GitSourceContext URL. The function assembles
     * everything into a JSON string. JSON values are escaped.
     *
     * <p>It would be better to use some JSON library to build JSON string (like gson). We craft
     * the JSON string manually to avoid new dependencies for the SDK.
     *
     * @param repoUrl remote git URL found in the local .git/config file
     * @param revisionId the HEAD revision of the current branch
     * @return source context BLOB serialized as JSON string, or null if we fail to
     *         parse {@code repoUrl}
     */
    static SourceContext createFromUrl(@Nullable String repoUrl, String revisionId) {
      if (repoUrl == null) {
        return createLocal(revisionId);
      }

      // Parse the URL to determine the other fields.
      Matcher match = CLOUD_REPO_RE.matcher(repoUrl);
      if (match.matches()) {
        // It looks like a GCP repo URL, extract the repo ID blob from it.
        String idType = match.group("idtype");
        if ("id".equals(idType)) {
          String rawRepoId = match.group("projectOrRepoId");
          if (!Strings.isNullOrEmpty(rawRepoId)
              && Strings.isNullOrEmpty(match.group("repoName"))) {
            return SourceContext.createFromRepoId(repoUrl, revisionId, rawRepoId);
          }
        } else if ("p".equals(idType)) {
          String projectId = match.group("projectOrRepoId");
          if (!Strings.isNullOrEmpty(projectId)) {
            String repoName = match.group("repoName");
            if (Strings.isNullOrEmpty(repoName)) {
              repoName = "default";
            }
            return SourceContext.createFromRepoName(repoUrl, revisionId, projectId, repoName);
          }
        }
      }
      return SourceContext.createGit(repoUrl, revisionId);
    }

    static SourceContext createFromRepoId(String repoUrl, String revisionId, String repoId) {
      String json = String.format(
          "{\"cloudRepo\": {\"repoId\": {\"uid\": \"%s\"}, \"revisionId\": \"%s\"}}",
          Utility.jsonEscape(repoId), Utility.jsonEscape(revisionId));
      return new AutoValue_RepoInfo_SourceContext(repoUrl, revisionId, json, null, repoId, null,
          RemoteType.CLOUD_REPO);
    }

    static SourceContext createFromRepoName(
        String repoUrl, String revisionId, String projectId, String repoName) {
      String jsonRepoId = String.format(
          "{\"projectRepoId\": {\"projectId\": \"%s\", \"repoName\": \"%s\"}}",
          Utility.jsonEscape(projectId), Utility.jsonEscape(repoName));
      String json = String.format("{\"cloudRepo\": {\"repoId\": %s, \"revisionId\": \"%s\"}}",
          jsonRepoId, Utility.jsonEscape(revisionId));
      return new AutoValue_RepoInfo_SourceContext(
          repoUrl, revisionId, json, projectId, null, repoName, RemoteType.CLOUD_REPO);
    }

    /** Regex for detecting short forms of SSH protocol URLs. */
    private static final Pattern SSH_PROTOCOL_SHORT_FORM_RE = Pattern.compile("^\\w+@");

    /** Regex for detecting SSH protocol URLs. */
    private static final Pattern SSH_PROTOCOL_RE = Pattern.compile("^ssh://");

    /** Regex for detecting Github domain URLs. */
    private static final Pattern GITHUB_RE = Pattern.compile("\\w:[^/]*github\\.com[/:]");

    /** Regex for detecting BitBucket domain URLs. */
    private static final Pattern BITBUCKET_RE = Pattern.compile("\\w:[^/]*bitbucket\\.org[/:]");

    static SourceContext createGit(String repoUrl, String revisionId) {
      boolean isSsh = false;
      if (SSH_PROTOCOL_SHORT_FORM_RE.matcher(repoUrl).find()
          || SSH_PROTOCOL_RE.matcher(repoUrl).find()) {
        isSsh = true;
      }
      RemoteType remoteType;
      if (GITHUB_RE.matcher(repoUrl).find() || BITBUCKET_RE.matcher(repoUrl).find()) {
        if (isSsh) {
          remoteType = RemoteType.GIT_KNOWN_HOST_SSH;
        } else {
          remoteType = RemoteType.GIT_KNOWN_HOST;
        }
      } else {
        remoteType = RemoteType.GIT_UNKNOWN;
      }
      String json = String.format("{\"git\": {\"url\": \"%s\", \"revisionId\": \"%s\"}}",
          Utility.jsonEscape(repoUrl), Utility.jsonEscape(revisionId));
      return new AutoValue_RepoInfo_SourceContext(repoUrl, revisionId, json, null, null, null,
          remoteType);
    }
  }

  /**
   * Exception for all problems calling git or parsing its output.
   */
  static final class GitException extends Exception {
    GitException(String message) {
      super(message);
    }

    GitException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  /**
   * Abstraction over calling git for unit tests.
   */
  interface GitClient {
    /**
     * Calls git with the given args.
     *
     * <p>The working directory is set to the deployed target directory. This is the potential
     * git repository directory. The current working directory (i.e. directory from which
     * appcfg is called) is irrelevant.
     *
     * <p>Git might not be used by the developer. In this case {@code baseDir} is not a git
     * repository or git might not be even installed on the system. In these cases this
     * function will throw {@link GitException}.
     *
     * @param args arguments for the git command
     * @return raw output of the git command (stdout, not stderr)
     * @throws GitException if not a git repository or problem calling git
     */
    String callGit(String... args) throws GitException;
  }

  /**
   * Implements {@link GitClient} interface by invoking git command as a separate process.
   */
  private static final class GitCommandClient implements GitClient {
    /**
     * Potential git repo directory (doesn't have to be root repo directory).
     */
    private final File baseDir;

    /**
     * Class constructor.
     *
     * @param baseDir potential git repo directory (doesn't have to be root repo directory)
     */
    GitCommandClient(File baseDir) {
      this.baseDir = baseDir;
    }

    @Override
    public String callGit(String... args) throws GitException {
      ImmutableList<String> command = ImmutableList.<String>builder()
          .add(Utility.isOsWindows() ? "git.exe" : "git")
          .add(args)
          .build();

      try {
        Process process = new ProcessBuilder(command)
            .directory(baseDir)
            .start();

        StringWriter stdOutWriter = new StringWriter();
        Thread stdOutPumpThread =
            new Thread(new OutputPump(process.getInputStream(), new PrintWriter(stdOutWriter)));
        stdOutPumpThread.start();

        StringWriter stdErrWriter = new StringWriter();
        Thread stdErrPumpThread =
            new Thread(new OutputPump(process.getErrorStream(), new PrintWriter(stdErrWriter)));
        stdErrPumpThread.start();

        int rc = process.waitFor();
        stdOutPumpThread.join();
        stdErrPumpThread.join();

        String stdout = stdOutWriter.toString();
        String stderr = stdErrWriter.toString();

        logger.fine(String.format("%s completed with code %d\n%s%s",
            command, rc, stdout, stderr));

        if (rc != 0) {
          throw new GitException(String.format(
              "git command failed (exit code = %d), command: %s\n%s%s",
              rc, command, stdout, stderr));
        }

        return stdout;
      } catch (InterruptedException ex) {
        throw new GitException(String.format(
            "InterruptedException caught while executing git command: %s", command), ex);
      } catch (IOException ex) {
        throw new GitException(String.format("Failed to invoke git: %s", command), ex);
      }
    }
  }

  private static final Logger logger = Logger.getLogger(RepoInfo.class.getName());

  /**
   * Regular expression pattern to capture list of origins for the local repo.
   */
  private static final String REMOTE_URL_PATTERN = "remote\\.(.*)\\.url";

  /**
   * Calls git to obtain information about the repository.
   */
  private final GitClient git;

  /**
   * Class constructor.
   *
   * @param baseDir potential git repo directory (doesn't have to be root repo directory)
   */
  RepoInfo(File baseDir) {
    this(new GitCommandClient(baseDir));
  }

  /**
   * Class constructor.
   *
   * @param git git client interface
   */
  RepoInfo(GitClient git) {
    this.git = git;
  }

  /**
   * Constructs a SourceContext for the HEAD revision.
   *
   * @return Returns null if there is no local revision ID.<p><ul>
   *     <li>If there is exactly one remote repo associated with the local repo, its context will be
   *     returned.
   *     <li>If there is exactly one Google-hosted remote repo associated with the local repo, its
   *     {@code SourceContext} will be returned, even if there other non-Google remote repos
   *     associated with the local repo.
   *     </ul><p>In all other cases, the return value will contain only the local head revision ID.
   */
  @Nullable
  SourceContext getSourceContext() {
    Multimap<String, String> remoteUrls;
    String revision = null;

    try {
      // First get the current revision.
      revision = getGitHeadRevision();

      // Then get all of the remote URLs from the source directory.
      remoteUrls = getGitRemoteUrls();
      if (remoteUrls.isEmpty()) {
        logger.fine("Local git repo has no remote URLs");
        return SourceContext.createLocal(revision);
      }

    } catch (GitException e) {
      logger.fine("not a git repository or problem calling git");
      return revision == null ? null : SourceContext.createLocal(revision);
    }

    SourceContext bestReturn = null;
    SourceContext.RemoteType bestRemote = null;
    for (Map.Entry<String, String> remoteUrl : remoteUrls.entries()) {
      SourceContext candidate = SourceContext.createFromUrl(remoteUrl.getValue(), revision);
      if (bestRemote != null) {
        int compareResult = candidate.getRemoteType().compareTo(bestRemote);
        if (compareResult < 0
            || (compareResult == 0 && !remoteUrl.getKey().equals("origin"))) {
          // This remote is no better than the existing one.
          continue;
        }
      }
      bestRemote = candidate.getRemoteType();
      bestReturn = candidate;
    }

    return bestReturn;
  }

  /**
   * Calls git to print every configured remote URL.
   *
   * @return raw output of the command
   * @throws GitException if not a git repository or problem calling git
   */
  private String getGitRemoteUrlConfigs() throws GitException {
    return git.callGit("config", "--get-regexp", REMOTE_URL_PATTERN);
  }

  /**
   * Finds the list of git remotes for the given source directory.
   *
   * @return A list of remote name to remote URL mappings, empty if no remotes are found
   * @throws GitException if not a git repository or problem calling git
   */
  private ImmutableMultimap<String, String> getGitRemoteUrls() throws GitException {
    String remoteUrlConfigOutput = getGitRemoteUrlConfigs();
    if (remoteUrlConfigOutput.isEmpty()) {
      return ImmutableMultimap.of();
    }

    ImmutableMultimap.Builder<String, String> result = ImmutableMultimap.builder();

    String[] configLines = remoteUrlConfigOutput.split("\\r?\\n");
    for (String configLine : configLines) {
      if (configLine.isEmpty()) {
        continue;  // Skip blank lines.
      }

      // Each line looks like "remote.<name>.url <url>.
      String[] parts = configLine.split(" +");
      if (parts.length != 2) {
        logger.fine(String.format("Skipping unexpected git config line, incorrect segments: %s",
            configLine));
        continue;
      }

      // Extract the two parts, then find the name of the remote.
      String remoteUrlConfigName = parts[0];
      String remoteUrl = parts[1];

      Matcher matcher = REMOTE_URL_RE.matcher(remoteUrlConfigName);
      if (!matcher.matches()) {
        logger.fine(String.format("Skipping unexpected git config line, could not match remote: %s",
            configLine));
        continue;
      }

      String remoteUrlName = matcher.group(1);

      result.put(remoteUrlName, remoteUrl);
    }

    logger.fine(String.format("Remote git URLs: %s", result.toString()));

    return result.build();
  }

  private static final Pattern REMOTE_URL_RE = Pattern.compile(REMOTE_URL_PATTERN);

  /**
   * Finds the current HEAD revision for the given source directory
   *
   * @return the HEAD revision of the current branch
   * @throws GitException if not a git repository or problem calling git
   */
  private String getGitHeadRevision() throws GitException {
    String head = git.callGit("rev-parse", "HEAD").trim();
    if (head.isEmpty()) {
      throw new GitException("Empty head revision returned by git");
    }

    return head;
  }
}
