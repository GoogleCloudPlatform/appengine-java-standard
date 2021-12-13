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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.w3c.dom.Element;

/**
 * Creates an {@link QueueXml} instance from
 * <appdir>WEB-INF/queue.xml.  If you want to read the configuration
 * from a different file, subclass and override {@link #getFilename()}.  If you
 * want to read the configuration from something that isn't a file, subclass
 * and override {@link #getInputStream()}.
 *
 */
// Mutation of CronXmlReader.
public class QueueXmlReader extends AbstractConfigXmlReader<QueueXml> {

  // Relative location of the config file
  private static final String FILENAME = "WEB-INF/queue.xml";

  // XML Constants
  private static final String TOTAL_STORAGE_LIMIT_TAG = "total-storage-limit";
  private static final String QUEUEENTRIES_TAG = "queue-entries";
  private static final String QUEUE_TAG = "queue";
  private static final String NAME_TAG = "name";
  private static final String RATE_TAG = "rate";
  private static final String BUCKET_SIZE = "bucket-size";
  private static final String MAX_CONCURRENT_REQUESTS = "max-concurrent-requests";
  private static final String MODE_TAG = "mode";

  private static final String RETRY_PARAMETERS_TAG = "retry-parameters";
  private static final String TASK_RETRY_LIMIT_TAG = "task-retry-limit";
  private static final String TASK_AGE_LIMIT_TAG = "task-age-limit";
  private static final String MIN_BACKOFF_SECONDS_TAG = "min-backoff-seconds";
  private static final String MAX_BACKOFF_SECONDS_TAG = "max-backoff-seconds";
  private static final String MAX_DOUBLINGS_TAG = "max-doublings";
  private static final String TARGET_TAG = "target";

  private static final String ACL_TAG = "acl";
  private static final String USER_EMAIL_TAG = "user-email";
  private static final String WRITER_EMAIL_TAG = "writer-email";

  /**
   * Constructs the reader for {@code queue.xml} in a given application directory.
   * @param appDir the application directory
   */
  public QueueXmlReader(String appDir) {
    super(appDir, false);
  }

  /**
   * Parses the config file.
   * @return A {@link QueueXml} object representing the parsed configuration.
   */
  public QueueXml readQueueXml() {
    return readConfigXml();
  }

  @Override
  protected QueueXml processXml(InputStream is) {
    QueueXml queueXml = new QueueXml();
    Element root = XmlUtils.parseXml(is).getDocumentElement();
    if (!root.getTagName().equals(QUEUEENTRIES_TAG)) {
      throw new AppEngineConfigException(getFilename() + " does not contain <"
          + QUEUEENTRIES_TAG + ">");
    }
    boolean sawTotalStorageLimit = false;
    for (Element child : XmlUtils.getChildren(root)) {
      switch (child.getTagName()) {
        case QUEUE_TAG:
          parseQueue(child, queueXml.addNewEntry());
          break;
        case TOTAL_STORAGE_LIMIT_TAG:
          if (sawTotalStorageLimit) {
            throw new AppEngineConfigException(getFilename() + " contains multiple <"
                + TOTAL_STORAGE_LIMIT_TAG + ">");
          }
          sawTotalStorageLimit = true;
          queueXml.setTotalStorageLimit(stringContents(child));
          break;
        default:
          throw new AppEngineConfigException(getFilename() + " contains <"
              + child.getTagName() + "> instead of <" + QUEUE_TAG + "/> or <"
              + TOTAL_STORAGE_LIMIT_TAG + "/>");
      }
    }
    return queueXml;
  }

  private void parseQueue(Element queueElement, QueueXml.Entry entry) {
    for (Element child : XmlUtils.getChildren(queueElement)) {
      switch (child.getTagName()) {
        case NAME_TAG:
          entry.setName(stringContents(child));
          break;
        case BUCKET_SIZE:
          entry.setBucketSize(stringContents(child));
          break;
        case RATE_TAG:
          entry.setRate(stringContents(child));
          break;
        case MAX_CONCURRENT_REQUESTS:
          entry.setMaxConcurrentRequests(stringContents(child));
          break;
        case MODE_TAG:
          entry.setMode(stringContents(child));
          break;
        case TARGET_TAG:
          entry.setTarget(stringContents(child));
          break;
        case RETRY_PARAMETERS_TAG:
          entry.setRetryParameters(parseRetryParameters(child));
          break;
        case ACL_TAG:
          entry.setAcl(parseAcl(child));
          break;
        default:
          throw new AppEngineConfigException(getFilename() + " contains unknown <"
              + child.getTagName() + "> inside <" + QUEUE_TAG + "/>");
      }
    }
  }

  private QueueXml.RetryParameters parseRetryParameters(Element retryElement) {
    QueueXml.RetryParameters retryParameters = new QueueXml.RetryParameters();
    for (Element child : XmlUtils.getChildren(retryElement)) {
      switch (child.getTagName()) {
        case TASK_RETRY_LIMIT_TAG:
          retryParameters.setRetryLimit(stringContents(child));
          break;
        case TASK_AGE_LIMIT_TAG:
          retryParameters.setAgeLimitSec(stringContents(child));
          break;
        case MIN_BACKOFF_SECONDS_TAG:
          retryParameters.setMinBackoffSec(stringContents(child));
          break;
        case MAX_BACKOFF_SECONDS_TAG:
          retryParameters.setMaxBackoffSec(stringContents(child));
          break;
        case MAX_DOUBLINGS_TAG:
          retryParameters.setMaxDoublings(stringContents(child));
          break;
        default:
          throw new AppEngineConfigException(getFilename() + " contains unknown <"
              + child.getTagName() + "> inside <" + RETRY_PARAMETERS_TAG + "/>");
      }
    }
    return retryParameters;
  }

  private List<QueueXml.AclEntry> parseAcl(Element aclElement) {
    List<QueueXml.AclEntry> acls = new ArrayList<>();
    for (Element child : XmlUtils.getChildren(aclElement)) {
      QueueXml.AclEntry acl = new QueueXml.AclEntry();
      switch (child.getTagName()) {
        case USER_EMAIL_TAG:
          acl.setUserEmail(stringContents(child));
          break;
        case WRITER_EMAIL_TAG:
          acl.setWriterEmail(stringContents(child));
          break;
        default:
          throw new AppEngineConfigException(getFilename() + " contains unknown <"
              + child.getTagName() + "> inside <" + ACL_TAG + "/>");
      }
      acls.add(acl);
    }
    return acls;
  }

  @Override
  protected String getRelativeFilename() {
    return FILENAME;
  }

}
