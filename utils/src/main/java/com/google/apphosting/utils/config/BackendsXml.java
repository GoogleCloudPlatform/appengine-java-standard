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

import com.google.common.base.Joiner;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Parsed backends.xml file.
 *
 */
public class BackendsXml {

  public static final Set<Option> EMPTY_OPTIONS = Collections.<Option>emptySet();

  private final List<Entry> backends = new ArrayList<Entry>();

  public List<Entry> getBackends() {
    return Collections.unmodifiableList(backends);
  }

  public void addBackend(Entry entry) {
    backends.add(entry);
  }

  @Override
  public String toString() {
    return backends.toString();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof BackendsXml) {
      return backends.equals(((BackendsXml) obj).backends);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return backends.hashCode();
  }

  /**
   * Get the YAML equivalent of this backends.xml file.
   *
   * @return contents of an equivalent {@code backends.yaml} file.
   */
  public String toYaml() {
    StringBuilder builder = new StringBuilder();
    List<BackendsXml.Entry> backends = getBackends();
    if (!backends.isEmpty()) {
      builder.append("backends:\n");
      for (BackendsXml.Entry entry : backends) {
        builder.append("- name: ").append(entry.getName()).append("\n");
        if (entry.getInstances() != null) {
          builder.append("  instances: ").append(entry.getInstances()).append("\n");
        }
        if (entry.getInstanceClass() != null) {
          builder.append("  class: ").append(entry.getInstanceClass()).append("\n");
        }
        if (entry.getMaxConcurrentRequests() != null) {
          builder.append("  max_concurrent_requests: ").append(entry.getMaxConcurrentRequests())
              .append("\n");
        }
        List<String> options = new ArrayList<String>();
        for (BackendsXml.Option option : entry.getOptions()) {
          options.add(option.getYamlValue());
        }
        if (!options.isEmpty()) {
          builder.append("  options: ").append(Joiner.on(", ").useForNull("null").join(options))
              .append("\n");
        }
      }
    }
    return builder.toString();
  }

  /**
   * A {@code Entry} encapsulates the definition of a
   * long-running, addressable server.
   */
  public static class Entry {
    private final String name;
    private final Integer instances;
    private final String instanceClass;
    private final Integer maxConcurrentRequests;
    private final Set<Option> options;
    private final State state;

    public Entry(String name, Integer instances, String instanceClass,
                 Integer maxConcurrentRequests,
                 Set<Option> options, State state) {
      this.name = name;
      this.instances = instances;
      this.instanceClass = instanceClass;
      this.maxConcurrentRequests = maxConcurrentRequests;
      if (options == null) {
        throw new NullPointerException("options must be specified");
      }
      this.options = options;
      this.state = state;
    }

    public String getName() {
      return name;
    }

    /**
     * The number of instances of this server to run.  Each will be
     * individually addressable.  If {@code null}, the default number
     * of instances will be used.
     */
    public Integer getInstances() {
      return instances;
    }

    /**
     * The instance class of the instances.  If {@code null}, the
     * default instance class will be used.
     */
    public String getInstanceClass() {
      return instanceClass;
    }

    /**
     * The maximum number of threads used to serve concurrent
     * requests.  If {@code null} and the application is marked
     * thread-safe in {@code appengine-web.xml}, the default number of
     * concurrent requests will be used.  If it is not marked
     * threadsafe, this value is assumed to be 1.
     */
    public Integer getMaxConcurrentRequests() {
      return maxConcurrentRequests;
    }

    /**
     * The set of boolean options enabled for this server.
     */
    public Set<Option> getOptions() {
      return options;
    }

    /**
     * If true, indicates that requests to this backend will fail if
     * they cannot be handled immediately.  If false, requests to this
     * backend will wait nia pending queue for up to 10 seconds before
     * being aborted.
     */
    public boolean isFailFast() {
      return options.contains(Option.FAIL_FAST);
    }

    /**
     * If true, indicates that instances of this backend will only be
     * started when user requests arrive, and are turned down when
     * idle.  If false, this backend is started automatically and is
     * resident in memory until manually stopped.
     */
    public boolean isDynamic() {
      return options.contains(Option.DYNAMIC);
    }

    /**
     * If true, indicates that this backend is publicly accessible and
     * can serve HTTP on the web.  If false, this backend is private
     * and can only be accessed by internal requests, such as
     * URLFetch, Task Queue, and Cron requests.
     */
    public boolean isPublic() {
      return options.contains(Option.PUBLIC);
    }

    /**
     * The current state of the server, or {@code null} if the state is unknown.
     */
    public State getState() {
      return state;
    }


    @Override
    public int hashCode() {
      int result = 1;
      result = 31 * result + name.hashCode();
      result = 31 * result + ((instances == null) ? 0 : instances.hashCode());
      result = 31 * result + ((instanceClass == null) ? 0 : instanceClass.hashCode());
      result = 31 * result + ((maxConcurrentRequests == null) ? 0 :
                              maxConcurrentRequests.hashCode());
      result = 31 * result + ((options == null) ? 0 : options.hashCode());
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof Entry)) {
        return false;
      }

      Entry entry = (Entry) obj;
      if (!name.equals(entry.name)) {
        return false;
      }
      if ((instances == null) ? entry.instances != null : !instances.equals(entry.instances)) {
        return false;
      }
      if ((instanceClass == null) ? entry.instanceClass != null :
                                    !instanceClass.equals(entry.instanceClass)) {
        return false;
      }
      if ((maxConcurrentRequests == null) ? entry.maxConcurrentRequests != null :
          !maxConcurrentRequests.equals(entry.maxConcurrentRequests)) {
        return false;
      }
      if ((options == null) ? entry.options != null : !options.equals(entry.options)) {
        return false;
      }
      return true;
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder();
      builder.append("Backend: ");
      builder.append(name);
      if (instances != null) {
        builder.append(", instances = ").append(instances);
      }
      if (instanceClass != null) {
        builder.append(", instanceClass = ").append(instanceClass);
      }
      if (maxConcurrentRequests != null) {
        builder.append(", maxConcurrentRequests = ").append(maxConcurrentRequests);
      }
      if (options != null) {
        builder.append(", options = ").append(options);
      }
      if (state != null) {
        builder.append(", state = ").append(state);
      }
      return builder.toString();
    }
  }

  public enum Option {
    DYNAMIC("dynamic"),
    FAIL_FAST("failfast"),
    PUBLIC("public");

    private final String yamlValue;

    Option(String yamlValue) {
      this.yamlValue = yamlValue;
    }

    public String getYamlValue() {
      return yamlValue;
    }

    public static Option fromYamlValue(String yamlValue) {
      for (Option option : values()) {
        if (option.getYamlValue().equals(yamlValue)) {
          return option;
        }
      }
      throw new IllegalArgumentException("Unknown value: " + yamlValue);
    }
  }

  /**
   * Defines the current or desired state of a particular server.
   */
  public enum State {
    START("START"),
    STOP("STOP");

    private final String yamlValue;

    State(String yamlValue) {
      this.yamlValue = yamlValue;
    }

    public String getYamlValue() {
      return yamlValue;
    }

    public static State fromYamlValue(String yamlValue) {
      for (State state : values()) {
        if (state.getYamlValue().equals(yamlValue)) {
          return state;
        }
      }
      throw new IllegalArgumentException("Unknown value: " + yamlValue);
    }
  }
}
