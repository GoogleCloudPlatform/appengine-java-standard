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

package com.google.apphosting.runtime;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * {@code SessionData} is a simple data container for the contents of
 * a {@link javax.servlet.http.HttpSession}.  It must be shared
 * between user and runtime code, as it is deserialized in the context
 * of a user class loader.
 *
 */
public class SessionData implements Serializable {
  private static final long serialVersionUID = 4836129086749892349L;

  private Map<String, Object> valueMap;
  private long expirationTime;

  public SessionData() {
    valueMap = new HashMap<String, Object>();
  }

  public long getExpirationTime() {
    return expirationTime;
  }

  public void setExpirationTime(long expirationTime) {
    this.expirationTime = expirationTime;
  }

  public Map<String, Object> getValueMap() {
    return valueMap;
  }

  public void setValueMap(Map<String, Object> valueMap) {
    this.valueMap = valueMap;
  }
}
