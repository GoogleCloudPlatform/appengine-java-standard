<!--
 Copyright 2021 Google LLC

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

     https://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
-->

# Google App Engine Task Queues API Documentation

*   [Task Queue Overview](#task-queue-overview)
    *   [What are Task Queues?](#what-are-task-queues)
    *   [Push Queues vs Pull Queues](#push-queues-vs-pull-queues)
        *   [Push Queues](#push-queues)
        *   [Pull Queues](#pull-queues)
    *   [Retry Mechanism](#retry-mechanism)
*   [Push Queues](#push-queues)
    *   [Use Cases](#use-cases)
        *   [Slow Operations](#slow-operations)
        *   [Scheduled Tasks](#scheduled-tasks)
    *   [Task Deadlines](#task-deadlines)
    *   [Retrying Failed Tasks](#retrying-failed-tasks)
    *   [Working with Push Queues](#working-with-push-queues)
        *   [Minimum Requirements](#minimum-requirements)
        *   [Optional Tasks](#optional-tasks)
    *   [Creating Push Queues](#creating-push-queues)
        *   [Default Queue](#default-queue)
        *   [Custom Queues](#custom-queues)
        *   [Defining Push Queue Processing Rate](#defining-push-queue-processing-rate)
        *   [Storage Limits](#storage-limits)
        *   [Configuring Max Concurrent Requests](#configuring-max-concurrent-requests)
    *   [Creating Push Tasks](#creating-push-tasks)
        *   [Basic Task Creation](#basic-task-creation)
        *   [Specifying Worker Service](#specifying-worker-service)
        *   [Passing Data to Handler](#passing-data-to-handler)
        *   [Naming Tasks](#naming-tasks)
        *   [Adding Tasks Asynchronously](#adding-tasks-asynchronously)
        *   [Enqueuing Tasks in Datastore Transactions](#enqueuing-tasks-in-datastore-transactions)
        *   [Using DeferredTask Interface](#using-deferredtask-interface)
    *   [Creating Task Handlers](#creating-task-handlers)
        *   [Handler Requirements](#handler-requirements)
        *   [Request Headers](#request-headers)
        *   [Securing Task Handler URLs](#securing-task-handler-urls)
    *   [Retrying Failed Push Tasks](#retrying-failed-push-tasks)
    *   [Deleting Tasks and Queues](#deleting-tasks-and-queues)
        *   [Delete Individual Task](#delete-individual-task)
        *   [Purge All Tasks from Queue](#purge-all-tasks-from-queue)
        *   [Pause Queue](#pause-queue)
        *   [Delete Queue](#delete-queue)
    *   [Testing Push Queues in Development Server](#testing-push-queues-in-development-server)
        *   [Limitations](#limitations)
        *   [Disable Auto-Execution](#disable-auto-execution)
*   [Pull Queues](#pull-queues)
    *   [Use Cases](#use-cases)
    *   [Overview](#overview)
    *   [Creating Pull Queues](#creating-pull-queues)
    *   [Creating Pull Tasks](#creating-pull-tasks)
    *   [Leasing Pull Tasks](#leasing-pull-tasks)
        *   [Basic Leasing](#basic-leasing)
        *   [Batching with Tags](#batching-with-tags)
        *   [Modifying Lease Duration](#modifying-lease-duration)
        *   [Regulating Polling Rates](#regulating-polling-rates)
        *   [Deleting Tasks](#deleting-tasks)
    *   [Pull Queue Workflow](#pull-queue-workflow)
    *   [Monitoring Pull Queues](#monitoring-pull-queues)
*   [Configuration and Management](#configuration-and-management)
    *   [Queue Configuration Reference](#queue-configuration-reference)
    *   [Monitoring Queues](#monitoring-queues)
    *   [Disabling/Pausing Queues](#disablingpausing-queues)
    *   [Authentication Requirements](#authentication-requirements)
    *   [Multitenancy](#multitenancy)
*   [Best Practices](#best-practices)
    *   [General Guidelines](#general-guidelines)
    *   [Push Queue Best Practices](#push-queue-best-practices)
    *   [Pull Queue Best Practices](#pull-queue-best-practices)
    *   [Performance Optimization](#performance-optimization)
    *   [Common Pitfalls to Avoid](#common-pitfalls-to-avoid)
*   [Additional Resources](#additional-resources)

## Task Queue Overview

### What are Task Queues?

Task queues let applications perform work, called tasks, asynchronously outside
of a user request. If an app needs to execute work in the background, it adds
tasks to task queues. The tasks are executed later, by worker services.

**Important**: The Task Queue service is designed for asynchronous work. It does
not provide strong guarantees around the timing of task delivery and is
therefore unsuitable for interactive applications where a user is waiting for
the result.

### Push Queues vs Pull Queues

#### Push Queues

-   Run tasks by delivering HTTP requests to App Engine worker services
-   Dispatch requests at a reliable, steady rate
-   Guarantee reliable task execution
-   Allow control of the rate at which tasks are sent from the queue
-   Subject to stringent deadlines:
    -   Automatic scaling services: must finish in 10 minutes
    -   Basic and manual scaling services: can run up to 24 hours

#### Pull Queues

-   Do not dispatch tasks automatically
-   Depend on worker services to "lease" tasks from the queue
-   Provide more power and flexibility over when and where tasks are processed
-   Require more process management
-   Workers declare a deadline when leasing tasks
-   Tasks must be completed and deleted before the deadline, or another worker
    can lease them

**Note**: All task queue tasks are performed asynchronously. The application
that creates the task hands it off to the queue and is not notified whether or
not the task completes successfully.

### Retry Mechanism

If a worker fails to process a task, the Task Queue service provides a retry
mechanism, so the task can be retried a finite number of times.

--------------------------------------------------------------------------------

## Push Queues

### Use Cases

#### Slow Operations

Example: A social network messaging system where every message requires updating
the sender's followers. Using a push queue: 1. Application enqueues a task for
each message 2. Task is dispatched to a worker service 3. Worker retrieves the
sender's list of followers and updates the database 4. Each database update can
trigger another push task for efficiency

#### Scheduled Tasks

Example: An ad campaign application that sends emails at a specified future
time. Tasks are added to a push queue with instructions to withhold execution
until a specified time.

### Task Deadlines

When a worker service receives a push task request, it must handle the request
and send an HTTP response before a deadline: - **Automatic scaling services**:
10 minutes - **Manual and basic scaling services**: 24 hours

**Success indication**: HTTP response code between 200–299 **Failure
indication**: All other values cause the task to be retried

### Retrying Failed Tasks

If a push task request handler returns an HTTP status code outside 200–299 or
fails to respond before the deadline: - The queue retries the task until it
succeeds - The system backs off gradually to avoid flooding your application -
Retry attempts recur at a minimum of once per hour

### Working with Push Queues

#### Minimum Requirements

1.  Create tasks programmatically and add them to queues
2.  Write a handler that processes task requests
3.  Assign the handler to an App Engine service

#### Optional Tasks

-   Create and customize multiple queues
-   Monitor and manage queues in the Google Cloud console

### Creating Push Queues

#### Default Queue

App Engine provides a default push queue named `default` with default settings.
You can add all tasks to this queue without creating additional queues.

#### Custom Queues

To add queues or change the default configuration, edit the queue configuration
file (`queue.yaml`):

```yaml
queue:
- name: queue-blue
  target: v2.task-module
  rate: 5/s
- name: queue-red
  rate: 1/s
```

Upload the file: `bash gcloud app deploy queue.yaml`

**Limits**: - You can create up to 100 queues - Queues cannot be created
dynamically - If you delete a queue, wait approximately 7 days before creating a
new queue with the same name

#### Defining Push Queue Processing Rate

Control task processing rate using: - `rate`: Tasks processed per second -
`bucket_size`: Maximum burst size - `max_concurrent_requests`: Maximum
simultaneous tasks

**Token Bucket Algorithm**: - Each queue has a token bucket holding tokens (max:
`bucket_size` or 5) - Each task execution removes one token - App Engine refills
tokens continuously based on the `rate`

Example configuration: `yaml queue: - name: queue-blue rate: 20/s bucket_size:
40 max_concurrent_requests: 10`

#### Storage Limits

Set total storage limit for all queues: ```yaml total_storage_limit: 120M

queue: - name: queue-blue rate: 35/s ```

**Default limits**: - Free apps: 500M - Billed apps: No limit until explicitly
set

**Note**: This protects against fork bomb programming errors where tasks create
multiple other tasks.

#### Configuring Max Concurrent Requests

Example: If a queue has rate 20/s and bucket size 40: - Normal latency (0.3
seconds): ~40 tasks run simultaneously - High latency (5 seconds): >100 tasks
could run simultaneously

Setting `max_concurrent_requests: 10` ensures no more than 10 tasks run
simultaneously, preventing resource exhaustion.

```yaml
queue:
- name: optimize-queue
  rate: 20/s
  bucket_size: 40
  max_concurrent_requests: 10
```

### Creating Push Tasks

#### Basic Task Creation

```java
import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TaskOptions;

Queue queue = QueueFactory.getDefaultQueue();
queue.add(TaskOptions.Builder.withUrl("/worker").param("key", key));
```

#### Specifying Worker Service

**Target**: Specifies the service that will receive the HTTP request - Forms:
`service`, `version.service`, `instance.version.service` - Set via: 1. Task
construction: `taskOptions.header("Host", versionHostname)` 2. Queue definition
in `queue.yaml` 3. Default: version of service that enqueues it

**URL**: Selects handler in the target service - Should match handler URL
patterns in target service - Can include query parameters (GET or PULL methods
only) - Default: `/_ah/queue/[QUEUE_NAME]`

#### Passing Data to Handler

Data can be passed as: - Query parameters in URL (GET or PULL methods) - Payload
in HTTP request body - Parameters (added to URL as query parameters)

**Note**: Do not specify params if using POST with payload or GET with URL query
parameters.

#### Naming Tasks

By default, App Engine assigns unique names. You can assign custom names: -
**Advantage**: Named tasks are de-duplicated (guaranteed to be added only
once) - **De-duplication period**: 9 days after task completion or deletion -
**Performance note**: De-duplication introduces overhead, especially with
sequential names - **Recommendation**: Use well-distributed prefix (e.g., hash
of contents) - **Max length**: 500 characters - **Allowed characters**: Letters,
numbers, underscores, hyphens

#### Adding Tasks Asynchronously

Most add operations are fast (median < 5ms), but some take longer. For
latency-sensitive applications: - Use asynchronous methods from the `Queue`
class - Add tasks to different queues in parallel - Call `get()` on the returned
Future to complete the request - When adding tasks in a transaction, call
`get()` before committing

#### Enqueuing Tasks in Datastore Transactions

Tasks can be enqueued as part of a Datastore transaction: - Task is only
enqueued if transaction commits successfully - **Limit**: Maximum 5
transactional tasks per transaction - **Restriction**: Transactional tasks must
not have user-specified names

```java
DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
Queue queue = QueueFactory.getDefaultQueue();

try {
    Transaction txn = ds.beginTransaction();
    // ...
    queue.add(TaskOptions.Builder.withUrl("/path/to/my/worker"));
    // ...
    txn.commit();
} catch (DatastoreFailureException e) {
    // Handle exception
}
```

#### Using DeferredTask Interface

For many diverse but small tasks, use the `DeferredTask` interface: - Define
task as a single method - Uses Java serialization to package work into Task
Queue - Simple return = success - Thrown exception = failure

```java
public static class ExpensiveOperation implements DeferredTask {
    @Override
    public void run() {
        System.out.println("Doing an expensive operation...");
        // expensive operation goes here
    }
}

// Add the task
Queue queue = QueueFactory.getDefaultQueue();
queue.add(
    TaskOptions.Builder.withPayload(new ExpensiveOperation())
        .etaMillis(System.currentTimeMillis() + DELAY_MS)
);
```

**Warning**: Carefully control serialization compatibility. Unprocessed objects
remain in the queue even after code updates, which can cause deserialization
issues.

### Creating Task Handlers

#### Handler Requirements

Handlers must: 1. Return HTTP status code 200–299 to indicate success 2.
Complete before the deadline (10 min for automatic scaling, 24 hours for
basic/manual) 3. Be idempotent (tasks may execute multiple times)

**Note**: App Engine returns 503 when instances are overloaded, causing Task
Queue to slow delivery.

#### Request Headers

Push task requests include special headers:

**Always Present**:

-   `X-Appengine-QueueName`: Queue name
-   `X-Appengine-TaskName`: Task name or system-generated ID
-   `X-Appengine-TaskRetryCount`: Number of retries (0 for first attempt)
-   `X-Appengine-TaskExecutionCount`: Number of previous failures during
    execution
-   `X-Appengine-TaskETA`: Target execution time (seconds since Jan 1, 1970)

**Sometimes Present**:

-   `X-Appengine-TaskPreviousResponse`: HTTP response code from previous retry
-   `X-Appengine-TaskRetryReason`: Reason for retry
-   `X-Appengine-FailFast`: Indicates task fails immediately if no instance
    available

**Security**: External user requests have these headers stripped and replaced
(except for administrators).

#### Securing Task Handler URLs

Restrict handler URLs to administrators using `web.xml`:

```xml
<security-constraint>
    <web-resource-collection>
        <web-resource-name>tasks</web-resource-name>
        <url-pattern>/tasks/*</url-pattern>
    </web-resource-collection>
    <auth-constraint>
        <role-name>admin</role-name>
    </auth-constraint>
</security-constraint>
```

**Note**: Tasks can access URLs restricted with `admin` role, but not `*` role
(requires user authentication).

### Retrying Failed Push Tasks

#### Default Retry Behavior

If a handler returns status code outside 200–299 or fails to respond before
deadline:

-   System gradually reduces retry rate
-   Maximum retry rate: once per hour
-   Continues until task succeeds

#### Custom Retry Configuration

Configure retry parameters in `queue.yaml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<queue-entries>
    <!-- Retry up to 7 times for up to 2 days -->
    <queue>
        <name>fooqueue</name>
        <rate>1/s</rate>
        <retry-parameters>
            <task-retry-limit>7</task-retry-limit>
            <task-age-limit>2d</task-age-limit>
        </retry-parameters>
    </queue>

    <!-- Linear backoff: 10s, 20s, 30s, ..., 200s, 200s, ... -->
    <queue>
        <name>barqueue</name>
        <rate>1/s</rate>
        <retry-parameters>
            <min-backoff-seconds>10</min-backoff-seconds>
            <max-backoff-seconds>200</max-backoff-seconds>
            <max-doublings>0</max-doublings>
        </retry-parameters>
    </queue>

    <!-- Exponential then linear: 10s, 20s, 40s, 80s, 160s, 240s, 300s, 300s, ... -->
    <queue>
        <name>bazqueue</name>
        <rate>1/s</rate>
        <retry-parameters>
            <min-backoff-seconds>10</min-backoff-seconds>
            <max-backoff-seconds>300</max-backoff-seconds>
            <max-doublings>3</max-doublings>
        </retry-parameters>
    </queue>
</queue-entries>
```

### Deleting Tasks and Queues

#### Delete Individual Task

```java
Queue q = QueueFactory.getQueue("queue1");
q.deleteTask("foo");
```

#### Purge All Tasks from Queue

```java
Queue queue = QueueFactory.getQueue("foo");
queue.purge();
```

**Notes**:

-   Purge operations take up to 1 minute to take effect
-   Can take several hours to reclaim freed quotas
-   **Warning**: Wait at least 1 second after purging before creating new tasks

#### Pause Queue

Two methods: 1. Remove queue definition from `queue.yaml` and upload 2. Set
queue rate to 0

To resume: Upload `queue.yaml` with queue defined and non-zero rate.

Can also pause from Cloud Tasks page in Google Cloud console.

#### Delete Queue

Best practice: 1. Pause the queue first (remove from `queue.yaml`) 2. Upload the
change: `gcloud app deploy queue.yaml` 3. Delete from Google Cloud console

**Important**: Wait 7 days before recreating a queue with the same name.

### Testing Push Queues in Development Server

#### Limitations

-   Development server doesn't respect `rate` and `bucket-size` attributes
-   Tasks execute as close to their ETA as possible
-   Setting rate to 0 doesn't prevent automatic execution
-   Queue state is not preserved across restarts

#### Disable Auto-Execution

```bash
dev_appserver.sh --jvm_flag=-Dtask_queue.disable_auto_task_execution=true
```

--------------------------------------------------------------------------------

## Pull Queues

### Use Cases

Pull queues work well for batching tasks together for efficient execution.

**Example**: Leaderboard application - App maintains leaderboards for multiple
games - Each high score triggers a pull task with score, player, and game ID as
tag - Worker periodically leases tasks with the same game ID - Updates
leaderboard for all tasks in batch

**Advantages**: - Tags can be dynamically generated while app runs - Workers
handle new game IDs with no special effort

### Overview

In pull queues: - Worker service must request tasks from the queue - Queue
responds by allowing unique access (lease) for a specified period - Workers can
group related tasks using tags (batching) - Workers must explicitly delete tasks
after processing

**Important Differences from Push Queues**: 1. **Scaling**: Your code must scale
workers based on processing volume 2. **Deletion**: Your code must explicitly
delete tasks after processing

### Creating Pull Queues

Define pull queues in `queue.yaml` with `mode: pull` directive:

```yaml
queue:
- name: my-queue-name
  mode: pull
```

Upload: `bash gcloud app deploy queue.yaml`

**Important Notes**:

-   Local development server does not support pull queues
-   Test using App Engine staging environment
-   Don't mix `queue.yaml` uploads with Queue Management API methods

### Creating Pull Tasks

Get the queue and add tasks using `TaskOptions.Method.PULL`:

```java
Queue q = QueueFactory.getQueue("pull-queue");
q.add(
    TaskOptions.Builder
        .withMethod(TaskOptions.Method.PULL)
        .payload(content.toString())
);
```

### Leasing Pull Tasks

#### Basic Leasing

Workers lease tasks using `lease_tasks()` method: - Specify number of tasks
(max: 1,000) - Specify lease duration (max: 1 week) - Leased tasks are
unavailable to other workers until lease expires

```java
List<TaskHandle> tasks = q.leaseTasks(3600, TimeUnit.SECONDS, numberOfTasksToLease);
```

**Note**: Short delay may occur before recently added tasks become available for
leasing.

#### Batching with Tags

Tag tasks to enable filtered leasing:

```java
// Add task with tag
q.add(
    TaskOptions.Builder
        .withMethod(TaskOptions.Method.PULL)
        .payload(content.toString())
        .tag("process".getBytes())
);

// Lease only tasks with specific tag
List<TaskHandle> tasks = q.leaseTasksByTag(
    3600,
    TimeUnit.SECONDS,
    numberOfTasksToLease,
    "process"
);
```

#### Modifying Lease Duration

If a worker cannot complete a task before lease expires:

-   Renew the lease using `modifyTaskLease()`
-   Or let it expire (another worker can then lease it)

#### Regulating Polling Rates

Workers should detect if they're attempting to lease tasks faster than the queue
can supply.

**Exceptions to catch**: - `TransientFailureException` -
`ApiDeadlineExceededException`

**Best practices**:

-   Catch exceptions and back off from calling `lease_tasks()`
-   Consider setting a higher RPC deadline
-   Back off when lease request returns empty list

**Rate limit**: Maximum 10 LeaseTasks requests per queue per second (excess
returns OK with zero results)

#### Deleting Tasks

After processing, workers must delete tasks:

```java
q.deleteTask(task);
```

**Important**: Task names are available in the Task object returned by
`lease_tasks()`.

### Pull Queue Workflow

1.  Create pull queue using `queue.xml`
2.  Create tasks and add to queue
3.  Worker leases task using `TaskQueue`
4.  App Engine sends task data in lease response
5.  Worker processes task (can modify lease duration if needed)
6.  Worker deletes task after successful processing

### Monitoring Pull Queues

View pull queues in Google Cloud console:

1.  Open Cloud Tasks page
2.  Look for "Pull" value in Type column
3.  Click queue name to view all tasks

--------------------------------------------------------------------------------

## Configuration and Management

### Queue Configuration Reference

**Key Parameters**:

-   `name`: Queue name
-   `mode`: `push` or `pull`
-   `rate`: Tasks processed per second
-   `bucket_size`: Maximum burst size
-   `max_concurrent_requests`: Maximum simultaneous tasks
-   `target`: Default service for tasks
-   `total_storage_limit`: Storage limit across all queues

### Monitoring Queues

Access Cloud Tasks page in Google Cloud console: 1. Enable Cloud Tasks API 2.
View list of all queues 3. Click queue name for details page showing all tasks

### Disabling/Pausing Queues

Methods: 1. Remove queue definition from configuration file and upload 2. Set
rate to 0 3. Use Google Cloud console

Paused queues:

-   Tasks remain in queue
-   New tasks can be added but won't be processed
-   Continue to count toward quota

### Authentication Requirements

Service accounts need `serviceusage.services.list` permission:

-   Use `serviceusage.serviceUsageViewer` role, or
-   Create custom role with that permission

### Multitenancy

Push queues use the current namespace set in the namespace manager at task
creation time. See Namespaces API documentation for details.

--------------------------------------------------------------------------------

## Best Practices

### General Guidelines

1.  **Idempotency**: Always make handlers idempotent (safe to execute multiple
    times)
2.  **Error Handling**: Return appropriate HTTP status codes (200–299 for
    success)
3.  **Deadlines**: Design handlers to complete within deadline limits
4.  **Task Naming**: Use well-distributed prefixes for custom task names
5.  **Storage Limits**: Set limits corresponding to several days' worth of tasks

### Push Queue Best Practices

1.  **Rate Control**: Use `max_concurrent_requests` to prevent resource
    exhaustion
2.  **Security**: Restrict handler URLs to admin-only access
3.  **Testing**: Test handlers by visiting URLs as administrator
4.  **Retry Configuration**: Customize retry parameters based on task
    requirements
5.  **Async Operations**: Use async methods for multiple queue operations

### Pull Queue Best Practices

1.  **Scaling**: Implement automatic worker scaling based on processing volume
2.  **Lease Duration**: Set lease duration long enough for slowest task
3.  **Polling**: Implement backoff strategy when leasing returns no tasks
4.  **Batching**: Use tags to efficiently process related tasks together
5.  **Cleanup**: Always delete tasks after successful processing

### Performance Optimization

1.  **Batch Operations**: Group similar tasks together
2.  **Concurrent Processing**: Configure appropriate `max_concurrent_requests`
3.  **Resource Management**: Monitor and adjust `bucket_size` and `rate`
4.  **Queue Organization**: Use multiple queues for different task types
5.  **Monitoring**: Regularly review queue metrics in Cloud console

### Common Pitfalls to Avoid

1.  **Fork Bombs**: Set `total_storage_limit` to prevent runaway task creation
2.  **Sequential Task Names**: Avoid timestamp-based names (use hash prefixes)
3.  **Missing Deletion**: Always delete pull queue tasks after processing
4.  **Inadequate Deadlines**: Ensure lease durations accommodate task complexity
5.  **Mixing Management Methods**: Don't mix `queue.yaml` with Queue Management
    API

--------------------------------------------------------------------------------

### Additional Resources

-   [Google Cloud Pub/Sub](https://cloud.google.com/pubsub/overview) -
    Alternative to pull queues
-   [Cloud Tasks API Documentation](https://cloud.google.com/tasks/docs)
-   [Queue Configuration Reference](https://cloud.google.com/appengine/docs/standard/reference/queueref)
