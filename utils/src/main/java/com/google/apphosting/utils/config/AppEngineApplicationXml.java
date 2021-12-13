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

/**
 * Holder for appengine-applicaion.xml properties.
 */
public class AppEngineApplicationXml {
  private final String applicationId;

  private AppEngineApplicationXml(String applicationId) {
    this.applicationId = applicationId;
  }

  public String getApplicationId() {
    return applicationId;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    return prime + ((applicationId == null) ? 0 : applicationId.hashCode());
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj){
      return true;
    }
    if (obj == null){
      return false;
    }
    if (getClass() != obj.getClass()){
      return false;
    }
    AppEngineApplicationXml other = (AppEngineApplicationXml) obj;
    if (applicationId == null) {
      if (other.applicationId != null){
        return false;
      }
    } else if (!applicationId.equals(other.applicationId)){
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "AppEngineApplicationXml: application=" + applicationId;
  }

  /**
   * Builder for an {@link AppEngineApplicationXml}
   */
  static class Builder{
    private String applicationId;

    Builder setApplicationId(String applicationId) {
      this.applicationId = applicationId;
      return this;
    }

    AppEngineApplicationXml build() {
      return new AppEngineApplicationXml(applicationId);
    }
  }
}
