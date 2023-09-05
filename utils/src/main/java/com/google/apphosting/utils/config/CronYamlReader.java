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

import com.esotericsoftware.yamlbeans.YamlException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.Reader;
import java.io.StringReader;
import java.util.List;

/** Class to parse cron.yaml into a CronYaml object. */
public class CronYamlReader {

  /** JavaBean class that matches the YAML cron file syntax. */
  public static class CronYaml {
    private List<Cron> entries;

    /**
     * Wrapper around RetryParametersXml to match the JavaBean properties to the yaml file syntax.
     */
    public static class RetryParameters {
      private RetryParametersXml retryParameters = new RetryParametersXml();

      public RetryParameters() {}

      public void setJob_retry_limit(int limit) {
        retryParameters.setRetryLimit(limit);
      }

      public int getJob_retry_limit() {
        return retryParameters.getRetryLimit();
      }

      public void setJob_age_limit(String ageLimit) {
        retryParameters.setAgeLimitSec(ageLimit);
      }

      public String getJob_age_limit() {
        return retryParameters.getAgeLimitSec().toString() + "s";
      }

      public long getJob_age_limit_sec() {
        return retryParameters.getAgeLimitSec();
      }

      public void setMin_backoff_seconds(double backoff) {
        retryParameters.setMinBackoffSec(backoff);
      }

      public double getMin_backoff_seconds() {
        return retryParameters.getMinBackoffSec();
      }

      public void setMax_backoff_seconds(double backoff) {
        retryParameters.setMaxBackoffSec(backoff);
      }

      public double getMax_backoff_seconds() {
        return retryParameters.getMaxBackoffSec();
      }

      public void setMax_doublings(int doublings) {
        retryParameters.setMaxDoublings(doublings);
      }

      public int getMax_doublings() {
        return retryParameters.getMaxDoublings();
      }

      public RetryParametersXml toXml() {
        return retryParameters;
      }
    }

    /** Wrapper around CronXml.Entry to match the JavaBean properties to the yaml file syntax. */
    public static class Cron {
      private final CronXml.Entry entry = new CronXml.Entry();
      private RetryParameters retryParameters;

      public void setUrl(String name) {
        entry.setUrl(name);
      }

      public String getUrl() {
        return entry.getUrl();
      }

      public void setTimezone(String name) {
        entry.setTimezone(name);
      }

      public String getTimezone() {
        return entry.getTimezone();
      }

      public void setDescription(String rate) {
        entry.setDescription(rate);
      }

      public String getDescription() {
        return entry.getDescription();
      }

      public void setAttempt_deadline(String attemptDeadline) {
        this.entry.setAttemptDeadline(attemptDeadline);
      }

      public String getAttempt_deadline() {
        return entry.getAttemptDeadline();
      }

      public void setRetry_parameters(RetryParameters retryParameters) {
        this.retryParameters = retryParameters;
        // When no sub-field is configured for retry parameters, the YAML parser
        // sets a null, rather than a default RetryParameters. We need to create
        // one by ourselves.
        if (retryParameters != null) {
          entry.setRetryParameters(retryParameters.toXml());
        } else {
          entry.setRetryParameters(new RetryParametersXml());
        }
      }

      public RetryParameters getRetry_parameters() {
        return retryParameters;
      }

      public void setSchedule(String target) {
        entry.setSchedule(target);
      }

      public String getSchedule() {
        return entry.getSchedule();
      }

      public void setTarget(String mode) {
        entry.setTarget(mode);
      }

      public String getTarget() {
        return entry.getTarget();
      }

      public CronXml.Entry toXml() {
        return entry;
      }
    }

    public List<Cron> getCron() {
      return entries;
    }

    public void setCron(List<Cron> entries) {
      this.entries = entries;
    }

    public CronXml toXml() {
      CronXml xml = new CronXml();
      if (entries != null) {
        for (Cron entry : entries) {
          xml.addEntry(entry.toXml());
        }
      }
      return xml;
    }
  }

  private static final String FILENAME = "cron.yaml";
  private String appDir;

  public CronYamlReader(String appDir) {
    if (appDir.length() > 0 && appDir.charAt(appDir.length() - 1) != File.separatorChar) {
      appDir += File.separatorChar;
    }
    this.appDir = appDir;
  }

  public String getFilename() {
    return appDir + CronYamlReader.FILENAME;
  }

  public CronXml parse() {
    if (new File(getFilename()).exists()) {
      try {
        return parse(new FileReader(getFilename()));
      } catch (FileNotFoundException ex) {
        throw new AppEngineConfigException("Cannot find file " + getFilename(), ex);
      }
    }
    return null;
  }

  private static CronYaml parseYaml(Reader yaml) {
    try {
      CronYaml cronYaml = YamlUtils.parse(yaml, CronYaml.class);
      if (cronYaml == null) {
        throw new AppEngineConfigException("Empty cron configuration.");
      }
      return cronYaml;
    } catch (YamlException ex) {
      throw new AppEngineConfigException(ex.getMessage(), ex);
    }
  }

  public static CronXml parse(Reader yaml) {
    CronYaml cronYaml = parseYaml(yaml);
    return cronYaml.toXml();
  }

  public static CronYaml parseFromYaml(String yaml) {
    return parseYaml(new StringReader(yaml));
  }

  public static CronXml parse(String yaml) {
    return parse(new StringReader(yaml));
  }
}
