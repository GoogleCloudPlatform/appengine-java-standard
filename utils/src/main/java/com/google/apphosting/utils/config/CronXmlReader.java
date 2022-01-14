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

import java.io.InputStream;
import java.time.Duration;
import java.util.regex.Pattern;
import org.w3c.dom.Element;

/**
 * Creates an {@link CronXml} instance from <appdir>WEB-INF/cron.xml. If you want to read the
 * configuration from a different file, subclass and override {@link #getFilename()}. If you want to
 * read the configuration from something that isn't a file, subclass and override {@link
 * #getInputStream()}.
 *
 */
public class CronXmlReader extends AbstractConfigXmlReader<CronXml> {

  // N.B.: this class is not currently used in, and
  // therefore has not been tested in, the runtime.  Before adding a
  // dependency on this code from the runtime please ensure that there
  // is no possibility for external entity references or other
  // dependencies that may cause it to fail when running under the
  // restricted environment

  // Relative location of the config file
  private static final String FILENAME = "WEB-INF/cron.xml";

  // XML Constants
  private static final String CRONENTRIES_TAG = "cronentries";
  private static final String CRON_TAG = "cron";
  private static final String DESCRIPTION_TAG = "description";
  private static final String SCHEDULE_TAG = "schedule";
  private static final String TARGET_TAG = "target";
  private static final String TIMEZONE_TAG = "timezone";
  private static final String URL_TAG = "url";
  private static final String RETRY_PARAMETERS_TAG = "retry-parameters";
  private static final String JOB_RETRY_LIMIT_TAG = "job-retry-limit";
  private static final String JOB_AGE_LIMIT_TAG = "job-age-limit";
  private static final String MIN_BACKOFF_SECONDS_TAG = "min-backoff-seconds";
  private static final String MAX_BACKOFF_SECONDS_TAG = "max-backoff-seconds";
  private static final String MAX_DOUBLINGS_TAG = "max-doublings";
  private static final String ATTEMPT_DEADLINE_TAG = "attempt-deadline";

  private static final Duration MAX_ATTEMPT_DEADLINE = Duration.ofHours(24);
  private static final Duration MIN_ATTEMPT_DEADLINE = Duration.ofSeconds(15);

  /**
   * Constructs the reader for {@code cron.xml} in a given application directory.
   *
   * @param appDir the application directory
   */
  public CronXmlReader(String appDir) {
    super(appDir, false);
  }

  /**
   * Parses the config file.
   *
   * @return A {@link CronXml} object representing the parsed configuration.
   */
  public CronXml readCronXml() {
    return readConfigXml();
  }

  @Override
  protected CronXml processXml(InputStream is) {
    CronXml cronXml = new CronXml();
    Element root = XmlUtils.parseXml(is).getDocumentElement();
    if (!root.getTagName().equals(CRONENTRIES_TAG)) {
      throw new AppEngineConfigException(getFilename() + " does not contain <"
          + CRONENTRIES_TAG + ">");
    }
    for (Element child : XmlUtils.getChildren(root)) {
      if (child.getTagName().equals(CRON_TAG)) {
        parseCron(child, cronXml.addNewEntry());
      } else {
        throw new AppEngineConfigException(getFilename() + " contains <"
            + child.getTagName() + "> instead of <" + CRON_TAG + "/>");
      }
    }
    cronXml.validateLastEntry();
    return cronXml;
  }

  private void parseCron(Element cronElement, CronXml.Entry entry) {
    for (Element child : XmlUtils.getChildren(cronElement)) {
      switch (child.getTagName()) {
        case DESCRIPTION_TAG:
          entry.setDescription(stringContents(child));
          break;
        case URL_TAG:
          entry.setUrl(stringContents(child));
          break;
        case SCHEDULE_TAG:
          entry.setSchedule(stringContents(child));
          break;
        case TIMEZONE_TAG:
          entry.setTimezone(stringContents(child));
          break;
        case TARGET_TAG:
          entry.setTarget(stringContents(child));
          break;
        case RETRY_PARAMETERS_TAG:
          entry.setRetryParameters(parseRetryParameters(child));
          break;
        case ATTEMPT_DEADLINE_TAG:
          String contents = stringContents(child);
          validateAttemptDeadline(contents);
          entry.setAttemptDeadline(contents);
          break;
        default:
          throw new AppEngineConfigException(
              getFilename()
                  + " contains unknown <"
                  + child.getTagName()
                  + "> inside <"
                  + CRON_TAG
                  + "/>");
      }
    }
  }

  private void validateAttemptDeadline(String attemptDeadline) {
    // For compatibility with Cloud Scheduler and Cloud Tasks APIs and SDKs, the attempt deadline
    // is expected to follow the JSON mapping format for google.protobuf.Duration, which is a
    // floating point number followed by 's' describing the duration in seconds.
    if (!Pattern.matches("\\d*[.]?\\d+s", attemptDeadline)) {
      throw new AppEngineConfigException(
          "Deadline has invalid format. Expected a string formatted as number ending with"
              + " 's'.");
    }
    String deadlineStr = attemptDeadline.substring(0, attemptDeadline.length() - 1);
    Duration deadline = Duration.ofMillis(Math.round(Double.parseDouble(deadlineStr) * 1000));

    if (deadline.compareTo(MAX_ATTEMPT_DEADLINE) > 0) {
      throw new AppEngineConfigException(
          String.format("Deadline %s is larger than %s.", attemptDeadline, MAX_ATTEMPT_DEADLINE));
    }
    if (deadline.compareTo(MIN_ATTEMPT_DEADLINE) < 0) {
      throw new AppEngineConfigException(
          String.format("Deadline %s is smaller than %s.", attemptDeadline, MIN_ATTEMPT_DEADLINE));
    }
  }

  private RetryParametersXml parseRetryParameters(Element retryElement) {
    RetryParametersXml retryParameters = new RetryParametersXml();
    for (Element child : XmlUtils.getChildren(retryElement)) {
      switch (child.getTagName()) {
        case JOB_RETRY_LIMIT_TAG:
          retryParameters.setRetryLimit(stringContents(child));
          break;
        case JOB_AGE_LIMIT_TAG:
          retryParameters.setAgeLimitSec(stringContents(child));
          break;
        case MIN_BACKOFF_SECONDS_TAG:
          retryParameters.setMinBackoffSec(stringContents(child));
          break;
        case MAX_BACKOFF_SECONDS_TAG:
          retryParameters.setMaxBackoffSec(stringContents(child));
          break;
        case MAX_DOUBLINGS_TAG:
          retryParameters.setMaxDoublings(stringContents(child));
          break;
        default:
          throw new AppEngineConfigException(
              getFilename() + " contains unknown <" + child.getTagName() + "> inside <"
                  + RETRY_PARAMETERS_TAG + "/>");
      }
    }
    return retryParameters;
  }

  @Override
  protected String getRelativeFilename() {
    return FILENAME;
  }
}
