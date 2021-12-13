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

import static com.google.common.base.Preconditions.checkNotNull;

import com.esotericsoftware.yamlbeans.YamlConfig;
import com.esotericsoftware.yamlbeans.YamlException;
import com.esotericsoftware.yamlbeans.YamlWriter;
import java.io.StringWriter;
import java.util.ArrayList;

/**
 * Utility for making client deploy yaml.
 *
 * <p>Keep in sync with apphosting/api/client_deployinfo.py.
 *
 * <p>Some fields and methods in this class are accessed via reflection by yamlbeans. That's why
 * they contain underscore characters (_).
 */
public class ClientDeployYamlMaker {
  public static final String UNKNOWN_RUNTIME = "unknown";

  private String runtime = UNKNOWN_RUNTIME;
  private final long startTimeUsec;
  private final ArrayList<Request> requests = new ArrayList<Request>();
  private final String sdkVersion;

  /**
   * Constructs a {@link ClientDeployYamlMaker} for logging an attempted deployment of
   * a module version to appengine.
   *
   * @param startTimeUsec The start time of the deployment in micro seconds.
   * @param sdkVersion The version of the client SDK.
   */
  public ClientDeployYamlMaker(long startTimeUsec, String sdkVersion) {
    this.startTimeUsec = startTimeUsec;
    this.sdkVersion = checkNotNull(sdkVersion, "sdkVersion may not be null");
  }

  /**
   * Sets the runtime.
   */
  public void setRuntime(String runtime) {
    this.runtime = checkNotNull(runtime, "runtime may not be null");
  }

  /**
   * Adds a record of an HTTP request to include in the yaml returned by
   * {@link #make(long, boolean)}
   *
   * @param path The path for the request.
   * @param responseCode The HTTP response code for the request.
   * @param startTimeUsec The start time for the request in micro seconds.
   * @param endTimeUsec The end time for the request in micro seconds.
   * @param requestSizeBytes The size of the payload for the request.
   */
  public void addRequest(String path, int responseCode, long startTimeUsec, long endTimeUsec,
      long requestSizeBytes) {
    requests.add(new Request(checkNotNull(path, "path may not be null"), responseCode,
        startTimeUsec, endTimeUsec, requestSizeBytes));
  }

  /**
   * Returns the runtime.
   */
  public String getRuntime() {
    return runtime;
  }


  /**
   * Returns the sdk version.
   */
  public String getSdkVersion() {
    return sdkVersion;
  }

  /**
   * Returns client deploy yaml suitable for logging a deployment attempt for
   * a single module version.
   *
   * @param endTimeUsec The time in micro seconds when the deployment completed.
   * @param success True iff the deployment succeeds
   * @return A {@link String} with the requested yaml.
   * @throws YamlException If yaml formatting fails.
   */
  public String make(long endTimeUsec, boolean success) throws YamlException {
    StringWriter stringWriter = new StringWriter();
    YamlConfig yamlConfig = new YamlConfig();
    yamlConfig.writeConfig.setIndentSize(2);
    yamlConfig.writeConfig.setWriteRootTags(false);
    yamlConfig.writeConfig.setWriteRootElementTags(false);
    yamlConfig.writeConfig.setWriteDefaultValues(true);
    yamlConfig.setPropertyElementType(ClientDeploy.class, "requests", Request.class);
    YamlWriter yamlWriter = new YamlWriter(stringWriter, yamlConfig);
    ClientDeploy clientDeploy = new ClientDeploy(runtime, startTimeUsec, endTimeUsec, requests,
        success, sdkVersion);

    yamlWriter.write(clientDeploy);
    yamlWriter.close();
    return stringWriter.toString();
  }

  /**
   * A holder class for representing a http request within a client deployment for use with
   * {@link YamlWriter}.
   * <p>
   * Please do not reference {@link ClientDeploy} objects directly. This class is public
   * to meet the needs of {@link YamlWriter} but should not be used outside its containing class.
   */
  public static class Request {
    private String path;
    private int response_code;
    private long start_time_usec;
    private long end_time_usec;
    private long request_size_bytes;

    public Request() {
    }

    public Request(String path, int response_code, long start_time_usec,
        long end_time_usec, long request_size_bytes) {
      this.path = path;
      this.response_code = response_code;
      this.start_time_usec = start_time_usec;
      this.end_time_usec = end_time_usec;
      this.request_size_bytes = request_size_bytes;
    }

    public String getPath() {
      return path;
    }

    public void setPath(@SuppressWarnings("unused") String path) {
      throw new UnsupportedOperationException();
    }

    public int getResponse_code() {
      return response_code;
    }

    public void setResponse_code(@SuppressWarnings("unused") int response_code) {
      throw new UnsupportedOperationException();
    }

    public long getStart_time_usec() {
      return start_time_usec;
    }

    public void setStart_time_usec(@SuppressWarnings("unused") long start_time_usec) {
      throw new UnsupportedOperationException();
    }

    public long getEnd_time_usec() {
      return end_time_usec;
    }

    public void setEnd_time_usec(@SuppressWarnings("unused") long end_time_usec) {
      throw new UnsupportedOperationException();
    }

    public long getRequest_size_bytes() {
      return request_size_bytes;
    }

    public void setRequest_size_bytes(@SuppressWarnings("unused") long request_size_bytes) {
      throw new UnsupportedOperationException();
    }
  }

  /**
   * A holder class for representing a client deployment for use with {@link YamlWriter}.
   * <p>
   * Please do not reference {@link ClientDeploy} objects directly. This class is public
   * to meet the needs of {@link YamlWriter} but should not be used its containing class.
   */
  public static class ClientDeploy {

    private String runtime;
    private long start_time_usec;
    private long end_time_usec;
    // We use an ArrayList here which is a default collection type for YamlWriter
    // so no type tag will be included in the generated yaml.
    private ArrayList<Request> requests;
    private boolean success;
    private String sdk_version;

    public ClientDeploy(String runtime, long start_time_usec, long end_time_usec,
        ArrayList<Request> requests, boolean success, String sdk_version) {
      this.runtime = runtime;
      this.start_time_usec = start_time_usec;
      this.end_time_usec = end_time_usec;
      this.requests = requests;
      this.success = success;
      this.sdk_version = sdk_version;
    }

    public ClientDeploy() {
    }

    public String getRuntime() {
      return runtime;
    }

    public void setRuntime(@SuppressWarnings("unused") String runtime) {
      throw new UnsupportedOperationException();
    }

    public long getStart_time_usec() {
      return start_time_usec;
    }

    public void setStart_time_usec(@SuppressWarnings("unused") long start_time_usec) {
      throw new UnsupportedOperationException();
    }

    public long getEnd_time_usec() {
      return end_time_usec;
    }

    public void setEnd_time_usec(@SuppressWarnings("unused") long end_time_usec) {
      throw new UnsupportedOperationException();
    }

    public ArrayList<Request> getRequests() {
      return requests;
    }

    public void setRequests(@SuppressWarnings("unused") ArrayList<Request> requests) {
      throw new UnsupportedOperationException();
    }

    public boolean isSuccess() {
      return success;
    }

    public void setSuccess(@SuppressWarnings("unused") boolean success) {
      throw new UnsupportedOperationException();
    }

    public String getSdk_version() {
      return sdk_version;
    }

    public void setSdk_version(@SuppressWarnings("unused") String sdk_version) {
      throw new UnsupportedOperationException();
    }
  }
}
