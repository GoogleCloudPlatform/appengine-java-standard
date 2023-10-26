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

package com.google.apphosting.utils.servlet.ee10;

import static java.lang.Math.ceil;
import static java.lang.Math.floor;
import static java.lang.Math.max;
import static java.lang.Math.min;

import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueMode.Mode;
import com.google.appengine.api.taskqueue.dev.LocalTaskQueue;
import com.google.appengine.api.taskqueue.dev.QueueStateInfo;
import com.google.appengine.api.taskqueue.dev.QueueStateInfo.TaskStateInfo;
import com.google.appengine.tools.development.ApiProxyLocal;
import com.google.apphosting.api.ApiProxy;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handler for the task queue viewer.
 * <p>
 * Views list of queues and tasks in queues, execute tasks and delete tasks.
 *
 */
@SuppressWarnings("serial")
public class TaskQueueViewerServlet extends HttpServlet {
  private static final Logger logger = Logger.getLogger(TaskQueueViewerServlet.class.getName());

  /**
   * For JSTL rendering of pager links.
   */
  public static final class Page {
    private final int number;
    private final int start;

    private Page(int number, int start) {
      this.number = number;
      this.start = start;
    }

    public int getNumber() {
      return number;
    }

    public int getStart() {
      return start;
    }
  }

  /**
   * Collection of push queues or pull queues
   */
  public static final class QueueBatch
      extends AbstractCollection<QueueStateInfo> {
    private final String title;
    private final boolean runManually;
    private final boolean rateLimited;
    private final Map<String, QueueStateInfo> contents;

    private QueueBatch(String title,
                       boolean runManually,
                       boolean rateLimited) {
      this.title = title;
      this.runManually = runManually;
      this.rateLimited = rateLimited;
      this.contents = new TreeMap<String, QueueStateInfo>();
    }

    private void put(String key, QueueStateInfo value) {
      contents.put(key, value);
    }

    public String getTitle() {
      return title;
    }

    public boolean isRunManually() {
      return runManually;
    }

    public boolean isRateLimited() {
      return rateLimited;
    }

    @Override
    public Iterator<QueueStateInfo> iterator() {
      return contents.values().iterator();
    }

    @Override
    public int size() {
      return contents.size();
    }
  }

  private static final String APPLICATION_NAME = "applicationName";

  private static final String QUEUE_NAME = "queueName";

  private static final String TASK_NAME = "taskName";

  private static final String LIST_QUEUE_NAME = "listQueueName";

  private static final String LIST_QUEUE_INFO = "listQueueInfo";

  private static final String QUEUE_STATE_INFO = "queueStateInfo";

  private static final String START = "start";

  private static final String NUM_PER_PAGE = "numPerPage";

  private static final String PAGES = "pages";

  private static final String CURRENT_PAGE = "currentPage";

  private static final String PREV_START = "prevStart";

  private static final String NEXT_START = "nextStart";

  private static final int MAX_PAGER_LINKS = 15;

  private static final String QUEUE_NAMES_LIST = "queueNames";

  private static final String TASK_INFO_PAGE = "taskInfoPage";

  private static final String TASK_COUNT = "taskCount";

  private static final String START_BASE_URL = "startBaseURL";

  // "ACTIONS" posted to this servlet
  private static final String ACTION_DELETE_TASK = "action:deletetask";

  private static final String ACTION_EXECUTE_TASK = "action:executetask";

  private static final String ACTION_PURGE_QUEUE = "action:purgequeue";

  private LocalTaskQueue localTaskQueue;

  @Override
  public void init() throws ServletException {
    super.init();
    ApiProxyLocal apiProxyLocal = (ApiProxyLocal) getServletContext().getAttribute(
        "com.google.appengine.devappserver.ApiProxyLocal");
    localTaskQueue = (LocalTaskQueue) apiProxyLocal.getService(LocalTaskQueue.PACKAGE);
  }
  // TODO Pull this function into a utils class for use by other servlets.
  /** URL encode the given string in UTF-8. */
  private static String urlencode(String val) throws UnsupportedEncodingException {
    return URLEncoder.encode(val, "UTF-8");
  }

  private Map<String, QueueStateInfo> getQueueInfo() {
    return localTaskQueue.getQueueStateInfo();
  }

  // TODO Pull this function into a utils class for use by other servlets.
  /**
   * Get the int value of the given param from the given request, returning the given default value
   * if the param does not exist or the value of the param cannot be parsed into an int.
   */
  private static int getIntParam(ServletRequest request, String paramName, int defaultVal) {
    String val = request.getParameter(paramName);
    try {
      // throws NFE if null, which is what we want
      return Integer.parseInt(val);
    } catch (NumberFormatException nfe) {
      return defaultVal;
    }
  }

  // TODO Pull this into a common utils class.
  /**
   * Returns the result of {@link HttpServletRequest#getRequestURI()} with the values of all the
   * params in {@code args} appended.
   */
  private static String filterURL(HttpServletRequest req, String... paramsToInclude)
      throws UnsupportedEncodingException {
    StringBuilder sb = new StringBuilder(req.getRequestURI() + "?");
    for (String arg : paramsToInclude) {
      String value = req.getParameter(arg);
      if (value != null) {
        sb.append(String.format("&%s=%s", arg, urlencode(value)));
      }
    }
    return sb.toString();
  }

  // TODO Pull this into a common utils class.
  /** Verifies that the request contains the required parameters. */
  private static boolean checkParams(
      HttpServletRequest req, HttpServletResponse resp, String... paramsRequired)
      throws IOException {
    for (String arg : paramsRequired) {
      String value = req.getParameter(arg);
      if (value == null) {
        logger.log(
            Level.SEVERE,
            "Request does not contain all required parameters :'" + arg + "'.");
        resp.sendError(404);
        return false;
      }
    }
    return true;
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    // If a QUEUE_NAME is supplied, then the tasks for that queue
    // are shown otherwise the list of queues are show.
    String selectedQueueName = req.getParameter(QUEUE_NAME);
    Map<String, QueueStateInfo> queueInfo = getQueueInfo();
    int start = getIntParam(req, START, 0);
    int numPerPage = getIntParam(req, NUM_PER_PAGE, 10);
    List<TaskStateInfo> taskPage = new ArrayList<TaskStateInfo>(numPerPage);

    int currentPage = start / numPerPage;
    int startIndex = currentPage * numPerPage;
    int countForQueue = 0;

    if (selectedQueueName != null) {
      // Generate information for one page of tasks.
      QueueStateInfo queueStateInfo = queueInfo.get(selectedQueueName);
      if (queueStateInfo != null) {
        req.setAttribute(LIST_QUEUE_NAME, selectedQueueName);
        req.setAttribute(LIST_QUEUE_INFO, queueStateInfo);
        List<TaskStateInfo> taskInfo = queueStateInfo.getTaskInfo();
        countForQueue = taskInfo.size();
        // Fix the case that the start index is larger than the count of items.
        if (startIndex >= countForQueue) {
          startIndex = countForQueue - 1;
          currentPage = startIndex / numPerPage;
          startIndex = currentPage * numPerPage;
        }

        // Make a collection of tasks (taskPage) for the current page.
        int lastItemIndex = numPerPage + startIndex;
        // Don't go past the end of the list.
        if (lastItemIndex > countForQueue) {
          lastItemIndex = countForQueue;
        }
        for (int index = startIndex; index < lastItemIndex; ++index) {
          taskPage.add(taskInfo.get(index));
        }
      }
    }

    int nextPage = currentPage + 1;
    int numPages = (int) ceil(countForQueue * (1.0 / numPerPage));

    int pageStart = (int) max(floor(currentPage - (MAX_PAGER_LINKS / 2)), 0);
    int pageEnd = min(pageStart + MAX_PAGER_LINKS, numPages);
    List<Page> pages = new ArrayList<Page>();
    // Page numbers are relative to 0 so we're adding 1 for display.
    for (int i = pageStart + 1; i < pageEnd + 1; ++i) {
      pages.add(new Page(i, (i - 1) * numPerPage));
    }

    Collection<String> queueNames = queueInfo.keySet();

    QueueBatch pushQueueInfo = new QueueBatch("Push Queues", true, true);
    QueueBatch pullQueueInfo = new QueueBatch("Pull Queues", false, false);
    for (Map.Entry<String, QueueStateInfo> entry : queueInfo.entrySet()) {
      if (entry.getValue().getMode() == Mode.PUSH) {
        pushQueueInfo.put(entry.getKey(), entry.getValue());
      } else {
        pullQueueInfo.put(entry.getKey(), entry.getValue());
      }
    }
    List<QueueBatch> queueStateInfo = new ArrayList<QueueBatch>();
    queueStateInfo.add(pushQueueInfo);
    queueStateInfo.add(pullQueueInfo);

    req.setAttribute(QUEUE_STATE_INFO, queueStateInfo);
    req.setAttribute(QUEUE_NAMES_LIST, queueNames);
    req.setAttribute(TASK_INFO_PAGE, taskPage);
    req.setAttribute(TASK_COUNT, countForQueue);
    req.setAttribute(APPLICATION_NAME, ApiProxy.getCurrentEnvironment().getAppId());
    req.setAttribute(PAGES, pages);
    req.setAttribute(CURRENT_PAGE, nextPage);
    req.setAttribute(START, startIndex);
    req.setAttribute(NUM_PER_PAGE, numPerPage);
    req.setAttribute(PREV_START, nextPage > 1 ? (nextPage - 2) * numPerPage : -1);
    req.setAttribute(NEXT_START, nextPage < numPages ? nextPage * numPerPage : -1);
    req.setAttribute(START_BASE_URL, filterURL(req, QUEUE_NAME, NUM_PER_PAGE));

    try {
      getServletContext().getRequestDispatcher(
          "/_ah/adminConsole?subsection=taskqueueViewer").forward(req, resp);
    } catch (ServletException e) {
      throw new RuntimeException("Could not forward request", e);
    }
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp)
      throws IOException {
    if (req.getParameter(ACTION_PURGE_QUEUE) != null) {
      purgeQueue(req, resp);
    } else if (req.getParameter(ACTION_DELETE_TASK) != null) {
      deleteTask(req, resp);
    } else if (req.getParameter(ACTION_EXECUTE_TASK) != null) {
      executeTask(req, resp);
    } else {
      resp.sendError(404);
    }
  }

  private void purgeQueue(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    if (!checkParams(req, resp, QUEUE_NAME)) {
      return;
    }
    String queueName = req.getParameter(QUEUE_NAME);
    localTaskQueue.flushQueue(queueName);
    String message = "Queue '" + queueName + "' has been purged.";
    resp.sendRedirect(String.format("/_ah/admin/taskqueue?msg=%s", urlencode(message)));
  }

  private abstract class TaskOperation {
    public final void execute(HttpServletRequest req, HttpServletResponse resp,
        String successMsg, String errorMsg) throws IOException {
      if (!checkParams(req, resp, QUEUE_NAME, TASK_NAME, START)) {
        return;
      }
      String queueName = req.getParameter(QUEUE_NAME);
      String taskName = req.getParameter(TASK_NAME);
      String start = req.getParameter(START);

      boolean success = doExecuteInternal(queueName, taskName);
      String message;
      if (success) {
        message = String.format(successMsg, queueName, taskName);
      } else {
        message = String.format(errorMsg, queueName, taskName);
      }
      resp.sendRedirect(String.format("/_ah/admin/taskqueue?start=%s&queueName=%s&msg=%s",
                        urlencode(start), urlencode(queueName), urlencode(message)));
    }

    protected abstract boolean doExecuteInternal(String queueName, String taskName);
  }

  private void deleteTask(HttpServletRequest req, HttpServletResponse resp)  throws IOException {
    new TaskOperation() {
      @Override
      protected boolean doExecuteInternal(String queueName, String taskName) {
        return localTaskQueue.deleteTask(queueName, taskName);
      }
    }.execute(req, resp, "Deleted task '%s:%s'.", "Failed to delete task '%s:%s'.");
  }

  private void executeTask(HttpServletRequest req, HttpServletResponse resp)  throws IOException {
    new TaskOperation() {
      @Override
      protected boolean doExecuteInternal(String queueName, String taskName) {
        return localTaskQueue.runTask(queueName, taskName);
      }
    }.execute(req, resp, "Ran task '%s:%s'.", "Failed to run task '%s:%s'.");
  }
}
