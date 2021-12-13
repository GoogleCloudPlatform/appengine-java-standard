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

package com.google.appengine.api.backends.dev;

import com.google.apphosting.utils.config.BackendsXml;
import com.google.common.base.Joiner;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Interface for controlling and getting information about backends in the
 * dev-appserver.
 *
 *
 */
public interface LocalServerController {

  public static final String BACKEND_CONTROLLER_ATTRIBUTE_KEY =
      "com.google.appengine.dev.backend_controller";

  /**
   * Returns a map containing all information about current backend state.
   *
   * @param requestHostName The hostname of the request, if null is specified
   *        the listen address will be used.
   *
   * @return Backend name -> backend information mapping for all configured
   *         backends.
   */
  public TreeMap<String, BackendStateInfo> getBackendState(String requestHostName);

  /**
   * Stop all instances in RUNNING state of a single backend, reset the internal
   * state of each instance, and set state to STOPPED so that any subsequent
   * requests receive a 500 error code response.
   *
   * @param backend The backend to stop.
   *
   * @throws IllegalStateException if the specified backend does not exist.
   *
   * @throws Exception if an error occurred that prevented the backend from
   *         resetting cleanly.
   *
   */
  public void stopBackend(String backend) throws IllegalStateException, Exception;

  /**
   * Start all instances in STOPPED state of a single backend.
   *
   * @param backend The backend to start.
   *
   * @throws IllegalStateException is the specified backend does not exist.
   */
  public void startBackend(String backend) throws IllegalStateException;

  /**
   * Class holding instance information in a way that is useful for display in
   * the backend viewer jsps.
   */
  public static class InstanceStateInfo implements Comparable<InstanceStateInfo> {
    private final String address;
    private final int instanceNumber;
    private final String state;

    public InstanceStateInfo(int instanceNumber, String address, String state) {
      this.instanceNumber = instanceNumber;
      this.address = address;
      this.state = state;
    }

    public String getAddress() {
      return address;
    }

    public int getInstanceNumber() {
      return instanceNumber;
    }

    public String getState() {
      return state;
    }

    /**
     * InstanceStateInfo objects are sorted in order of increasing instance
     * number.
     */
    @Override
    public int compareTo(InstanceStateInfo that) {
      return Integer.compare(this.instanceNumber, that.instanceNumber);
    }
  }

  /**
   * Class holding information about a single backend and its replicas. Used for
   * displaying backend information in the backend viewer jsps.
   */
  public static class BackendStateInfo {
    private final BackendsXml.Entry entry;

    private String address;
    private String state;
    private TreeSet<InstanceStateInfo> instanceStates;


    public BackendStateInfo(BackendsXml.Entry entry) {
      this.entry = entry;

      this.instanceStates = new TreeSet<InstanceStateInfo>();
    }

    public void add(InstanceStateInfo instanceStateInfo) {
      this.instanceStates.add(instanceStateInfo);
    }

    public String getAddress() {
      return address;
    }

    public Set<InstanceStateInfo> getInstanceStates() {
      return instanceStates;
    }

    public String getInstanceClass() {
      return entry.getInstanceClass();
    }

    public String getName() {
      return entry.getName();
    }

    public int getNumInstances() {
      return entry.getInstances();
    }

    public String getOptionString() {
      List<String> optionNames = new ArrayList<String>();
      for (BackendsXml.Option option : entry.getOptions()) {
        optionNames.add(option.getYamlValue());
      }
      return Joiner.on(", ").useForNull("null").join(optionNames);
    }

    public String getState() {
      return state;
    }

    public void setAddress(String address) {
      this.address = address;
    }

    public void setState(String state) {
      this.state = state;
    }
  }
}
