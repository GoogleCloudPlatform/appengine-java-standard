/*
 * Copyright 2022 Google LLC
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
package com.google.appengine.setup.testapps.jetty11.servlets;

import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TaskHandle;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.jcabi.aspects.RetryOnFailure;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;


public class TaskQueueTestServlet extends HttpServlet {
    private static final Random random = new Random(System.currentTimeMillis());

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        String payload = testAddGetRemoveTasksFromPullQueue();
        out.println("Pull Queue operations succeeded with payload - " + payload + "<br/>");
        payload = testAddGetRemoveTasksFromPushQueue();
        out.println("Push Queue operations succeeded with payload - " + payload);
    }

    private String testAddGetRemoveTasksFromPullQueue() {
        Queue queue = QueueFactory.getQueue("pull-queue-1");
        String taskName = "pull_task" + random.nextInt(10000);
        queue.add(TaskOptions.Builder.withMethod(TaskOptions.Method.PULL).payload(taskName)
            .tag(taskName).taskName(taskName));

        TaskHandle task = getTaskFromPullQueue(taskName, queue);
        String payload = new String(task.getPayload(), StandardCharsets.UTF_8);
        queue.deleteTask(task);
        return payload;
    }

    @RetryOnFailure(attempts = 3, delay = 2, unit = TimeUnit.SECONDS)
    private TaskHandle getTaskFromPullQueue(String taskName, Queue queue) {
        List<TaskHandle> tasks = queue.leaseTasksByTag(10, TimeUnit.SECONDS, 1, taskName);
        if (tasks.size() == 0) {
            throw new RuntimeException("Pull Task - " + taskName + " is not yet available. Please retry.");
        }
        return tasks.get(0);
    }

    private String testAddGetRemoveTasksFromPushQueue() {
        Queue queue = QueueFactory.getQueue("push-queue-1");
        String taskName = "push_task" + random.nextInt(10000);
        queue.add(TaskOptions.Builder.withUrl("/system").method(TaskOptions.Method.GET)
            .taskName(taskName));
        return taskName;
    }
}