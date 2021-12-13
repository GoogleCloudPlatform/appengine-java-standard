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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents the retry parameters from a parsed .xml file that contains
 * them (e.g. cron.xml or queue.xml).  While there is nothing XML-related
 * in this file, it is intended to only be used with the QueueXml and
 * CronXml classes.
 */
public class RetryParametersXml {

  private static final String AGE_LIMIT_REGEX =
      "([0-9]+(?:\\.?[0-9]*(?:[eE][\\-+]?[0-9]+)?)?)([smhd])";
  private static final Pattern AGE_LIMIT_PATTERN = Pattern.compile(AGE_LIMIT_REGEX);

  private Integer retryLimit;
  private Integer ageLimitSec;
  private Double minBackoffSec;
  private Double maxBackoffSec;
  private Integer maxDoublings;

  /**
   * Enumerates the allowed units for a retry parameter age limit.
   */
  public enum AgeLimitUnit {
    SECOND('s', 1),
    MINUTE('m', SECOND.getSeconds() * 60),
    HOUR('h', MINUTE.getSeconds() * 60),
    DAY('d', HOUR.getSeconds() * 24);

    final char ident;
    final int seconds;

    AgeLimitUnit(char ident, int seconds) {
      this.ident = ident;
      this.seconds = seconds;
    }

    static AgeLimitUnit valueOf(char unit) {
      switch (unit) {
        case 's' : return SECOND;
        case 'm' : return MINUTE;
        case 'h' : return HOUR;
        case 'd' : return DAY;
      }
      throw new AppEngineConfigException(
          "Invalid age limit '" + unit + "' was specified.");
    }

    public char getIdent() {
      return ident;
    }

    public int getSeconds() {
      return seconds;
    }
  }

  public RetryParametersXml() {
    retryLimit = null;
    ageLimitSec = null;
    minBackoffSec = null;
    maxBackoffSec = null;
    maxDoublings = null;
  }

  public Integer getRetryLimit() {
    return retryLimit;
  }

  public void setRetryLimit(int retryLimit) {
    this.retryLimit = retryLimit;
  }

  public void setRetryLimit(String retryLimit) {
    try {
      this.retryLimit = Integer.valueOf(retryLimit);
    } catch (NumberFormatException exc) {
      throw new AppEngineConfigException(
          "Illegal retry limit '" + retryLimit + "'.");
    }
  }

  public Integer getAgeLimitSec() {
    return ageLimitSec;
  }

  public void setAgeLimitSec(String ageLimitString) {
    Matcher matcher = AGE_LIMIT_PATTERN.matcher(ageLimitString);
    if (!matcher.matches() || matcher.groupCount() != 2) {
      throw new AppEngineConfigException(
          "Invalid age limit ('" + ageLimitString + "') was specified.");
    }

    try {
      double rateUnitSec =
        AgeLimitUnit.valueOf(matcher.group(2).charAt(0)).getSeconds();
      Double ageLimit = Double.parseDouble(matcher.group(1)) * rateUnitSec;
      this.ageLimitSec = ageLimit.intValue();
    } catch (NumberFormatException exc) {
      throw new AppEngineConfigException(
          "Invalid age limit ('" + ageLimitString + "') was specified.");
    }
  }

  public Double getMinBackoffSec() {
    return minBackoffSec;
  }

  public void setMinBackoffSec(double minBackoffSec) {
    this.minBackoffSec = minBackoffSec;
  }

  public void setMinBackoffSec(String minBackoffSec) {
    try {
      this.minBackoffSec = Double.valueOf(minBackoffSec);
    } catch (NumberFormatException exc) {
      throw new AppEngineConfigException(
          "Illegal min backoff '" + minBackoffSec + "'.");
    }
  }

  public Double getMaxBackoffSec() {
    return maxBackoffSec;
  }

  public void setMaxBackoffSec(double maxBackoffSec) {
    this.maxBackoffSec = maxBackoffSec;
  }

  public void setMaxBackoffSec(String maxBackoffSec) {
    try {
      this.maxBackoffSec = Double.valueOf(maxBackoffSec);
    } catch (NumberFormatException exc) {
      throw new AppEngineConfigException(
          "Illegal max backoff '" + maxBackoffSec + "'.");
    }
  }

  public Integer getMaxDoublings() {
    return maxDoublings;
  }

  public void setMaxDoublings(int maxDoublings) {
    this.maxDoublings = maxDoublings;
  }

  public void setMaxDoublings(String maxDoublings) {
    try {
      this.maxDoublings = Integer.valueOf(maxDoublings);
    } catch (NumberFormatException exc) {
      throw new AppEngineConfigException(
          "Illegal max doublings value '" + maxDoublings + "'.");
    }
  }

  /**
   * Get the YAML equivalent of this retry parameter.
   *
   * @return contents of an equivalent {@code retry parameter} section of a
   * Yaml file.  The retry_limit and age_limit are pre-pended with label + '_'.
   */
  public String toYaml(String label) {
    StringBuilder builder = new StringBuilder();
    builder.append("  retry_parameters:\n");
    if (getRetryLimit() != null) {
      builder.append(
          "    " + label + "_retry_limit: " + getRetryLimit() + "\n");
    }
    if (getAgeLimitSec() != null) {
      builder.append(
          "    " + label + "_age_limit: " + getAgeLimitSec() + "s\n");
    }
    if (getMinBackoffSec() != null) {
      builder.append("    min_backoff_seconds: " + getMinBackoffSec() + "\n");
    }
    if (getMaxBackoffSec() != null) {
      builder.append("    max_backoff_seconds: " + getMaxBackoffSec() + "\n");
    }
    if (getMaxDoublings() != null) {
      builder.append("    max_doublings: " + getMaxDoublings() + "\n");
    }

    return builder.toString();
  }

  // Generated by Eclipse
  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((ageLimitSec == null) ? 0 : ageLimitSec.hashCode());
    result = prime * result + ((maxBackoffSec == null) ? 0 : maxBackoffSec.hashCode());
    result = prime * result + ((maxDoublings == null) ? 0 : maxDoublings.hashCode());
    result = prime * result + ((minBackoffSec == null) ? 0 : minBackoffSec.hashCode());
    result = prime * result + ((retryLimit == null) ? 0 : retryLimit.hashCode());
    return result;
  }

  // Generated by Eclipse
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    RetryParametersXml other = (RetryParametersXml) obj;
    if (ageLimitSec == null) {
      if (other.ageLimitSec != null) {
        return false;
      }
    } else if (!ageLimitSec.equals(other.ageLimitSec)) {
      return false;
    }
    if (maxBackoffSec == null) {
      if (other.maxBackoffSec != null) {
        return false;
      }
    } else if (!maxBackoffSec.equals(other.maxBackoffSec)) {
      return false;
    }
    if (maxDoublings == null) {
      if (other.maxDoublings != null) {
        return false;
      }
    } else if (!maxDoublings.equals(other.maxDoublings)) {
      return false;
    }
    if (minBackoffSec == null) {
      if (other.minBackoffSec != null) {
        return false;
      }
    } else if (!minBackoffSec.equals(other.minBackoffSec)) {
      return false;
    }
    if (retryLimit == null) {
      if (other.retryLimit != null) {
        return false;
      }
    } else if (!retryLimit.equals(other.retryLimit)) {
      return false;
    }
    return true;
  }
}
