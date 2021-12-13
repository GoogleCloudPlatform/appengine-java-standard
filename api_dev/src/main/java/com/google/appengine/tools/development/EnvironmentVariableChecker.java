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

package com.google.appengine.tools.development;

import com.google.apphosting.utils.config.AppEngineConfigException;
import com.google.apphosting.utils.config.AppEngineWebXml;
import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * Checker for reporting differences between environment variables specified
 * in an application's appengine-web.xml files and values from
 * {@link System#getenv()}.
 * <p>
 * Users can specify values for environment variables in an application's
 * WEB-INF/appengine-web.xml file. It is not possible to set environment
 * variables after JVM startup so we report mismatches based on the current
 * reporting policy.
 *
 */
public class EnvironmentVariableChecker {
  @VisibleForTesting
  static final Logger LOGGER = Logger.getLogger(EnvironmentVariableChecker.class.getName());

  /**
   * Enum for reporting policy.
   */
  public enum MismatchReportingPolicy {
    /** Report mismatches by logging. */
    LOG,
    /** Report mismatches by throwing an {@link AppEngineConfigException} */
    EXCEPTION,
    /** Don't report mismatches. */
    NONE
  }

  private final MismatchReportingPolicy mismatchReportingPolicy;
  private final ImmutableList.Builder<Mismatch> mismatchListBuilder = ImmutableList.builder();


  EnvironmentVariableChecker(MismatchReportingPolicy mismatchReportingPolicy) {
    this.mismatchReportingPolicy = mismatchReportingPolicy;
  }

  void add(AppEngineWebXml appEngineWebXml, File appEngineWebXmlFile) {
    // Environment variables are read-only so we can't actually set the
    // values that are provided.  We can, however, verify that the values
    // provided are actually set in the environment.
    for (Map.Entry<String, String> entry : appEngineWebXml.getEnvironmentVariables().entrySet()) {
      if (!entry.getValue().equals(System.getenv(entry.getKey()))) {
        mismatchListBuilder.add(Mismatch.of(entry.getKey(), System.getenv(entry.getKey()),
            entry.getValue(), appEngineWebXmlFile));
      }
    }
  }

  void check() throws AppEngineConfigException {
    if (Boolean.getBoolean("appengine.disableEnvironmentCheck")) {
      return;
    }
    List<Mismatch> mismatches = mismatchListBuilder.build();
    if (!mismatches.isEmpty()) {
      String msg =
          "One or more environment variables have been configured in appengine-web.xml that have "
          + "missing or different values in your local environment. We recommend you use system "
          + "properties instead, but if you are interacting with legacy code that requires "
          + "specific environment variables to have specific values, please set these environment "
          + "variables in your environment before running.\n"
          + mismatches;

      if (mismatchReportingPolicy == MismatchReportingPolicy.LOG) {
        LOGGER.warning(msg);
      } else if (mismatchReportingPolicy == MismatchReportingPolicy.EXCEPTION) {
        throw new IncorrectEnvironmentVariableException(msg, mismatches);
      }
    }

  }

  @VisibleForTesting
  @AutoValue
  abstract static class Mismatch {
    abstract String getEnvironmentVariableName();

    @Nullable
    abstract String getEnvironmentVariableValue();

    abstract String getAppEngineWebXmlValue();

    abstract File getAppEngineWebXmlFile();

    static Mismatch of(
        String name, String value, String appEngineWebXmlValue, File appEngineWebXmlFile) {
      return new AutoValue_EnvironmentVariableChecker_Mismatch(
          /* environmentVariableName= */ name,
          /* environmentVariableValue= */ value,
          /* appEngineWebXmlValue= */ appEngineWebXmlValue,
          /* appEngineWebXmlFile= */ appEngineWebXmlFile);
    }
  }

  @VisibleForTesting
  public static class IncorrectEnvironmentVariableException extends
      AppEngineConfigException {

    private final List<Mismatch> mismatches;
    private IncorrectEnvironmentVariableException(String msg, List<Mismatch> mismatches) {
      super(msg);
      this.mismatches = mismatches;
    }

    public List<Mismatch> getMismatches() {
      return mismatches;
    }
  }
}
