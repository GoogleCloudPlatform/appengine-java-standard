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

/**
 * Class to parse queue.yaml into a QueueXml object.
 *
 */
@SuppressWarnings("MemberName") // some methods use snake_case for YAML reflection
public class QueueYamlReader {

  /**
   * Wrapper around QueueXml to match JavaBean properties to
   * the yaml file syntax.
   */
  public static class QueueYaml {

    /**
     * Wrapper around QueueXml.RetryParameters to match the JavaBean
     * properties to the yaml file syntax.
     */
    public static class RetryParameters {
      private QueueXml.RetryParameters retryParameters = new QueueXml.RetryParameters();

      public RetryParameters() {}

      public void setTask_retry_limit(int limit) {
        retryParameters.setRetryLimit(limit);
      }
      public int getTask_retry_limit() {
        return retryParameters.getRetryLimit();
      }
      public void setTask_age_limit(String ageLimit) {
        retryParameters.setAgeLimitSec(ageLimit);
      }
      public String getTask_age_limit() {
        return retryParameters.getAgeLimitSec().toString() + "s";
      }

      public long getTask_age_limit_sec() {
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
      public QueueXml.RetryParameters toXml() {
        return retryParameters;
      }
    }

    /** Wrapper around QueueXml.Entry to match the JavaBean properties to the yaml file syntax. */
    public static class Queue {
      private final QueueXml.Entry entry = new QueueXml.Entry();
      private RetryParameters retryParameters;
      public void setName(String name) {
        entry.setName(name);
      }
      public String getName() {
        return entry.getName();
      }
      public void setRate(String rate) {
        entry.setRate(rate);
      }
      public String getRate() {
        return entry.getRate() + "/" + entry.getRateUnit().getIdent();
      }

      public Double getRateDouble() {
        return entry.getRate();
      }

      // This strange name is so that the bean property is bucket_size
      // to match what's used in the YAML file.
      public void setBucket_size(int size) {
        entry.setBucketSize(size);
      }
      public int getBucket_size() {
        return entry.getBucketSize();
      }
      public void setMax_concurrent_requests(int size) {
        entry.setMaxConcurrentRequests(size);
      }
      public int getMax_concurrent_requests() {
        return entry.getMaxConcurrentRequests();
      }
      public void setRetry_parameters(RetryParameters retryParameters) {
        this.retryParameters = retryParameters;
        // When no sub-field is configured for retry parameters, the YAML parser
        // sets a null, rather than a default RetryParameters. We need to create
        // one by ourselves.
        if (retryParameters != null) {
          entry.setRetryParameters(retryParameters.toXml());
        } else {
          entry.setRetryParameters(new QueueXml.RetryParameters());
        }
      }
      public RetryParameters getRetry_parameters() {
        return retryParameters;
      }
      public void setTarget(String target) {
        entry.setTarget(target);
      }
      public String getTarget() {
        return entry.getTarget();
      }
      public void setMode(String mode) {
        entry.setMode(mode);
      }
      public String getMode() {
        return entry.getMode();
      }
      public QueueXml.Entry toXml() {
        return entry;
      }
    }

    private List<Queue> entries;
    private String total_storage_limit;

    public List<Queue> getQueue() {
      return entries;
    }

    public void setQueue(List<Queue> entries) {
      this.entries = entries;
    }

    public String getTotal_storage_limit() {
      return total_storage_limit;
    }

    public void setTotal_storage_limit(String totalStorageLimit) {
      this.total_storage_limit = totalStorageLimit;
    }

    public QueueXml toXml() {
      QueueXml xml = new QueueXml();
      if (total_storage_limit != null) {
        xml.setTotalStorageLimit(total_storage_limit);
      }
      if (entries != null) {
        for (Queue entry : entries) {
          xml.addEntry(entry.toXml());
        }
      }
      return xml;
    }
  }

  private static final String FILENAME = "queue.yaml";
  private final String fileName;

  public String getFilename() {
    return fileName;
  }

  public QueueYamlReader(String yamlDir) {
    fileName = yamlDir + File.separatorChar + QueueYamlReader.FILENAME;
  }

  public QueueXml parse() {
    if (new File(getFilename()).exists()) {
      try {
        return parse(new FileReader(getFilename()));
      } catch (FileNotFoundException ex) {
        throw new AppEngineConfigException("Cannot find file " + getFilename(), ex);
      }
    }
    return null;
  }

  public static QueueYaml parseYaml(Reader yaml) {
    try {
      return YamlUtils.parse(yaml, QueueYaml.class);
    } catch (YamlException ex) {
      throw new AppEngineConfigException(ex.getMessage(), ex);
    }
  }

  public static QueueXml parse(Reader yaml) {
    QueueYaml queueYaml = parseYaml(yaml);
    return queueYaml.toXml();
  }

  public static QueueYaml parseFromYaml(String yaml) {
    return parseYaml(new StringReader(yaml));
  }

  public static QueueXml parse(String yaml) {
    return parse(new StringReader(yaml));
  }
}
