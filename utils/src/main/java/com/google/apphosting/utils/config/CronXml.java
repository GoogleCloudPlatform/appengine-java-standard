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

import com.google.borg.borgcron.GrocTimeSpecification;
import java.util.ArrayList;
import java.util.List;

/**
 * Parsed cron.xml file.
 *
 * Any additions to this class should also be made to the YAML
 * version in CronYamlReader.java.
 *
 */
public class CronXml {

  /**
   * Describes a single cron entry.
   *
   */
  public static class Entry {

    private static final String TZ_GMT = "UTC";

    private String url;
    private String desc;
    private String tz;
    private String schedule;
    private String target;
    private RetryParametersXml retryParameters;
    private String attemptDeadline;

    /** Create an empty cron entry. */
    public Entry() {
      desc = "";
      tz = TZ_GMT;
      url = null;
      schedule = null;
      target = null;
      retryParameters = null;
      attemptDeadline = null;
    }

    /** Records the human-readable description of this cron entry. */
    public void setDescription(String description) {
      this.desc = description.replace('\n', ' ');
    }

    /** Records the URL of this cron entry */
    public void setUrl(String url) {
      this.url = url.replace('\n', ' ');
    }

    /**
     * Records the schedule of this cron entry.  May throw
     * {@link AppEngineConfigException} if the schedule does not parse
     * correctly.
     *
     * @param schedule the schedule to save
     */
    public void setSchedule(String schedule) {
      schedule = schedule.replace('\n', ' ');
      this.schedule = schedule;
    }

    /**
     * Sets the timezone for this cron entry's schedule.  Defaults to "GMT"
     * @param timezone timezone for the cron entry's {@code schedule}.
     */
    public void setTimezone(String timezone) {
      this.tz = timezone.replace('\n', ' ');
    }

    public void setTarget(String target) {
      this.target = target;
    }

    public void setRetryParameters(RetryParametersXml retryParameters) {
      this.retryParameters = retryParameters;
    }

    public void setAttemptDeadline(String attemptDeadline) {
      this.attemptDeadline = attemptDeadline;
    }

    public String getUrl() {
      return url;
    }

    public String getDescription() {
      return desc;
    }

    public String getSchedule() {
      return schedule;
    }

    public String getTimezone() {
      return tz;
    }

    public String getTarget() {
      return target;
    }

    public RetryParametersXml getRetryParameters() {
      return retryParameters;
    }

    public String getAttemptDeadline() {
      return attemptDeadline;
    }
  }

  private List<Entry> entries;

  /** Create an empty configuration object. */
  public CronXml() {
    entries = new ArrayList<Entry>();
  }

  /**
   * Puts a new entry into the list defined by the config file.
   *
   * @throws AppEngineConfigException if the previously-last entry is still
   *    incomplete.
   * @return the new entry
   */
  public Entry addNewEntry() {
    validateLastEntry();
    Entry entry = new Entry();
    entries.add(entry);
    return entry;
  }

  /**
   * Puts an entry into the list defined by the config file.
   *
   * @throws AppEngineConfigException if the entry is still incomplete.
   */
  public void addEntry(Entry entry) {
    validateLastEntry();
    entries.add(entry);
    validateLastEntry();
  }

  /**
   * Get the entries. Used for testing.
   */
  public List<Entry> getEntries() {
    return entries;
  }

  /**
   * Check that the last entry defined is complete.
   * @throws AppEngineConfigException if it is not.
   */
  public void validateLastEntry() {
    if (entries.size() == 0) {
      return;
    }
    Entry last = entries.get(entries.size() - 1);
    if (last.getUrl() == null) {
      throw new AppEngineConfigException("no URL for cronentry");
    }
    if (last.getSchedule() == null) {
      throw new AppEngineConfigException("no schedule for cronentry " + last.getUrl());
    }
    try {
      GrocTimeSpecification parsedSchedule =
          GrocTimeSpecification.create(last.schedule);
    } catch (IllegalArgumentException iae) {
      throw new AppEngineConfigException("schedule " + last.schedule + " failed to parse",
                                         iae.getCause());
    }
  }

  /**
   * Get the YAML equivalent of this cron.xml file.
   *
   * @return contents of an equivalent {@code cron.yaml} file.
   */
  public String toYaml() {
    validateLastEntry();
    StringBuilder builder = new StringBuilder("cron:\n");
    for (Entry ent : entries) {
      // description may contain YAML special characters.
      builder.append("- description: '" + ent.getDescription().replace("'", "''") + "'\n");
      builder.append("  url: " + ent.getUrl() + "\n");
      builder.append("  schedule: " + ent.getSchedule() + "\n");
      builder.append("  timezone: " + ent.getTimezone() + "\n");
      String target = ent.getTarget();
      if (target != null) {
        builder.append("  target: " + target + "\n");
      }
      RetryParametersXml retryParameters = ent.getRetryParameters();
      if (retryParameters != null) {
        builder.append(retryParameters.toYaml("job"));
      }
      if (ent.getAttemptDeadline() != null) {
        builder.append("  attempt_deadline: '").append(ent.getAttemptDeadline()).append("'\n");
      }
    }
    return builder.toString();
  }
}
