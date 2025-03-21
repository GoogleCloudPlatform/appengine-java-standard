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

package com.google.appengine.api.taskqueue;

import com.google.appengine.api.datastore.Transaction;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;

/**
 * {@link Queue} is used to manage a task queue.
 *
 * <p>Implementations of this interface must be threadsafe.
 *
 * <p>Queues are transactional. If a datastore transaction is in progress when {@link #add()} or
 * {@link #add(TaskOptions)} is invoked, the task will only be added to the queue if the datastore
 * transaction successfully commits. If you want to add a task to a queue and have that operation
 * succeed or fail independently of an existing datastore transaction you can invoke {@link
 * #add(Transaction, TaskOptions)} with a {@code null} transaction argument. Note that while the
 * addition of the task to the queue can participate in an existing transaction, the execution of
 * the task cannot participate in this transaction. In other words, when the transaction commits you
 * are guaranteed that your task will be added and run, not that your task executed successfully.
 *
 * <p>Queues may be configured in either push or pull mode, but they share the same interface.
 * However, only tasks with {@link TaskOptions.Method#PULL} may be added to pull queues. The tasks
 * in push queues must be added with one of the other available methods.
 *
 * <p>Pull mode queues do not automatically deliver tasks to the application. The application is
 * required to call {@link #leaseTasks(long, TimeUnit, long) leaseTasks} to acquire a lease on the
 * task and process them explicitly. Attempting to call {@link #leaseTasks(long, TimeUnit, long)
 * leaseTasks} on a push queue causes a {@link InvalidQueueModeException} to be thrown. When the
 * task processing has finished processing a task that is leased, it should call {@link
 * #deleteTask(String)}. If deleteTask is not called before the lease expires, the task will again
 * be available for lease.
 *
 * <p>Queue mode can be switched between push and pull. When switching from push to pull, tasks will
 * stay in the task queue and are available for lease, but url and headers information will be
 * ignored when returning the tasks. When switching from pull to push, existing tasks will remain in
 * the queue but will fail on auto-execution because they lack a url. If the queue mode is once
 * again changed to pull, these tasks will eventually be available for lease.
 *
 */
public interface Queue {
  /** The default queue name. */
  String DEFAULT_QUEUE = "default";

  /** The default queue path. */
  String DEFAULT_QUEUE_PATH = "/_ah/queue";

  /** Returns the queue name. */
  String getQueueName();

  /**
   * Submits a task to this queue with an auto generated name with default options.
   *
   * <p>This method is similar to calling {@link #add(TaskOptions)} with a {@link TaskOptions}
   * object returned by {@link TaskOptions.Builder#withDefaults()}.
   *
   * @return A {@link TaskHandle}.
   * @throws InternalFailureException
   * @throws IllegalStateException If the queue does not exist. (see queue.xml)
   * @throws TransientFailureException Attempting the request after this exception may succeed.
   * @throws InvalidQueueModeException task method is {@link TaskOptions.Method#PULL} and queue is
   *     push queue or vice versa.
   */
  TaskHandle add();

  /**
   * Submits a task to this queue.
   *
   * @param taskOptions The definition of the task.
   * @return A {@link TaskHandle}.
   * @throws InternalFailureException
   * @throws IllegalStateException If the queue does not exist. (see queue.xml)
   * @throws TaskAlreadyExistsException
   * @throws TransientFailureException Attempting the request after this exception may succeed.
   * @throws UnsupportedTranslationException If chosen character encoding is unsupported.
   * @throws InvalidQueueModeException task method is {@link TaskOptions.Method#PULL} and queue is
   *     push queue or vice versa.
   */
  TaskHandle add(TaskOptions taskOptions);

  /**
   * Submits tasks to this queue.
   *
   * <p>Submission is not atomic i.e. if this method throws then some tasks may have been added to
   * the queue.
   *
   * @param taskOptions An iterable over task definitions.
   * @return A list containing a {@link TaskHandle} for each added task.
   * @throws InternalFailureException
   * @throws IllegalStateException If the queue does not exist. (see queue.xml)
   * @throws TaskAlreadyExistsException If any of the provided {@code TaskOptions} contained a name
   *     of a task that was previously created, and if no other {@code Exception} would be thrown.
   *     Note that if a {@code TaskAlreadyExistsException} is caught, the caller can be guaranteed
   *     that for each one of the provided {@code TaskOptions}, either the corresponding task was
   *     successfully added, or a task with the given name was successfully added in the past.
   * @throws TransientFailureException Attempting the request after this exception may succeed.
   * @throws UnsupportedTranslationException If chosen character encoding is unsupported.
   * @throws InvalidQueueModeException task method is {@link TaskOptions.Method#PULL} and queue is
   *     push queue or vice versa.
   */
  List<TaskHandle> add(Iterable<TaskOptions> taskOptions);

  /**
   * Submits a task to this queue in the provided Transaction.
   *
   * <p>A task is added if and only if the transaction is applied successfully.
   *
   * @param txn an enclosing {@link Transaction} or null, if not null a task cannot be named.
   * @param taskOptions The definition of the task.
   * @return A {@link TaskHandle}.
   * @throws InternalFailureException
   * @throws IllegalStateException If the queue does not exist. (see queue.xml)
   * @throws TaskAlreadyExistsException if a task with the same name was previously created.
   * @throws TransientFailureException Attempting the request after this exception may succeed.
   * @throws UnsupportedTranslationException If chosen character encoding is unsupported.
   * @throws InvalidQueueModeException task method is {@link TaskOptions.Method#PULL} and queue is
   *     push queue or vice versa.
   */
  TaskHandle add(Transaction txn, TaskOptions taskOptions);

  /**
   * Submits tasks to this queue in the provided Transaction.
   *
   * <p>The tasks are added if and only if the transaction is applied successfully.
   *
   * @param txn an enclosing {@link Transaction} or null, if not null a task cannot be named.
   * @param taskOptions An iterable over task definitions.
   * @return A list containing a {@link TaskHandle} for each added task.
   * @throws InternalFailureException
   * @throws IllegalStateException If the queue does not exist. (see queue.xml)
   * @throws TaskAlreadyExistsException if a task with the same name was previously created.
   * @throws TransientFailureException Attempting the request after this exception may succeed.
   * @throws UnsupportedTranslationException If chosen character encoding is unsupported.
   * @throws InvalidQueueModeException task method is {@link TaskOptions.Method#PULL} and queue is
   *     push queue or vice versa.
   */
  List<TaskHandle> add(Transaction txn, Iterable<TaskOptions> taskOptions);

  /**
   * Asynchronously submits a task to this queue with an auto generated name with default options.
   *
   * <p>This method is similar to calling {@link #addAsync(TaskOptions)} with a {@link TaskOptions}
   * object returned by {@link TaskOptions.Builder#withDefaults()}.
   *
   * @return A {@code Future} with a result type of {@link TaskHandle}.
   */
  Future<TaskHandle> addAsync();

  /**
   * Asynchronously submits a task to this queue.
   *
   * @param taskOptions The definition of the task.
   * @return A {@code Future} with a result type of {@link TaskHandle}.
   * @throws UnsupportedTranslationException If chosen character encoding is unsupported.
   */
  Future<TaskHandle> addAsync(TaskOptions taskOptions);

  /**
   * Asynchronously submits tasks to this queue.
   *
   * <p>Submission is not atomic i.e. if this method fails then some tasks may have been added to
   * the queue.
   *
   * @param taskOptions An iterable over task definitions.
   * @return A {@code Future} whose result is a list containing a {@link TaskHandle} for each added
   *     task.
   * @throws UnsupportedTranslationException If chosen character encoding is unsupported.
   */
  Future<List<TaskHandle>> addAsync(Iterable<TaskOptions> taskOptions);

  /**
   * Asynchronously submits a task to this queue in the provided Transaction.
   *
   * <p>A task is added if and only if the transaction is applied successfully.
   *
   * @param txn an enclosing {@link Transaction} or null, if not null a task cannot be named.
   * @param taskOptions The definition of the task.
   * @return A {@code Future} with a result type of {@link TaskHandle}.
   * @throws UnsupportedTranslationException If chosen character encoding is unsupported.
   */
  Future<TaskHandle> addAsync(Transaction txn, TaskOptions taskOptions);

  /**
   * Asynchronously submits tasks to this queue in the provided Transaction.
   *
   * <p>The tasks are added if and only if the transaction is applied successfully.
   *
   * @param txn an enclosing {@link Transaction} or null, if not null a task cannot be named.
   * @param taskOptions An iterable over task definitions.
   * @return A {@code Future} whose result is a list containing a {@link TaskHandle} for each added
   *     task.
   * @throws UnsupportedTranslationException If chosen character encoding is unsupported.
   */
  Future<List<TaskHandle>> addAsync(Transaction txn, Iterable<TaskOptions> taskOptions);

  /**
   * Deletes a task from this {@link Queue}. Task is identified by taskName.
   *
   * @param taskName name of the task to delete.
   * @return True if the task was successfully deleted. False if the task was not found or was
   *     previously deleted.
   * @throws IllegalArgumentException if the provided name is null, empty or doesn't match the
   *     expected pattern.
   * @throws InternalFailureException
   * @throws IllegalStateException If the queue does not exist. (see queue.xml)
   * @throws TransientFailureException Attempting the request after this exception may succeed.
   */
  boolean deleteTask(String taskName);

  /**
   * Deletes a task from this {@link Queue}. Task is identified by a TaskHandle.
   *
   * @param taskHandle handle of the task to delete.
   * @return True if the task was successfully deleted. False if the task was not found or was
   *     previously deleted.
   * @throws IllegalArgumentException if the provided name is null, empty or doesn't match the
   *     expected pattern.
   * @throws InternalFailureException
   * @throws IllegalStateException If the queue does not exist. (see queue.xml)
   * @throws QueueNameMismatchException if the task handle refers to a different named queue.
   * @throws TransientFailureException Attempting the request after this exception may succeed.
   */
  boolean deleteTask(TaskHandle taskHandle);

  /**
   * Deletes a list of tasks from this {@link Queue}. The tasks are identified by a list of
   * TaskHandles. This method supports deleting up to 1000 tasks.
   *
   * @param taskHandles list of handles of tasks to delete.
   * @return {@code List<Boolean>} that represents the result of deleting each task in the same
   *     order as the input handles. True if a task was successfully deleted. False if the task was
   *     not found or was previously deleted.
   * @throws IllegalArgumentException if the provided name is null, empty or doesn't match the
   *     expected pattern, or if the size of taskHandles is too large.
   * @throws InternalFailureException
   * @throws IllegalStateException If the queue does not exist. (see queue.xml)
   * @throws QueueNameMismatchException if the task handle refers to a different named queue.
   * @throws TransientFailureException Attempting the request after this exception may succeed.
   */
  List<Boolean> deleteTask(List<TaskHandle> taskHandles);

  /**
   * Asynchronously deletes a task from this {@link Queue}. Task is identified by taskName.
   *
   * @param taskName name of the task to delete.
   * @return A {@code Future} whose result is True if the task was successfully deleted, False if
   *     the task was not found or was previously deleted.
   * @throws IllegalArgumentException if the provided name is null, empty or doesn't match the
   *     expected pattern.
   */
  Future<Boolean> deleteTaskAsync(String taskName);

  /**
   * Asynchronously deletes a task from this {@link Queue}. Task is identified by a TaskHandle.
   *
   * @param taskHandle handle of the task to delete.
   * @return A {@code Future} whose result is True if the task was successfully deleted, False if
   *     the task was not found or was previously deleted.
   * @throws IllegalArgumentException if the provided name is null, empty or doesn't match the
   *     expected pattern.
   * @throws QueueNameMismatchException if the task handle refers to a different named queue.
   */
  Future<Boolean> deleteTaskAsync(TaskHandle taskHandle);

  /**
   * Asynchronously deletes a list of tasks from this {@link Queue}. The tasks are identified by a
   * list of TaskHandles. This method supports deleting up to 1000 tasks.
   *
   * @param taskHandles list of handles of tasks to delete.
   * @return A {@code Future} whose result is a {@code List<Boolean>} that represents the result of
   *     deleting each task in the same order as the input handles. True if a task was successfully
   *     deleted. False if the task was not found or was previously deleted.
   * @throws IllegalArgumentException if the provided name is null, empty or doesn't match the
   *     expected pattern.
   * @throws QueueNameMismatchException if the task handle refers to a different named queue.
   */
  Future<List<Boolean>> deleteTaskAsync(List<TaskHandle> taskHandles);

  /**
   * Leases up to {@code countLimit} tasks from this queue for a period specified by {@code lease}
   * and {@code unit}. If fewer tasks than {@code countLimit} are available, all available tasks in
   * this {@link Queue} will be returned. The available tasks are those in the queue having the
   * earliest eta such that eta is prior to the time at which the lease is requested. It is
   * guaranteed that the leased tasks will be unavailable for lease to others in the lease period.
   * You must call deleteTask to prevent the task from being leased again after the lease period.
   * This method supports leasing a maximum of 1000 tasks for no more than one week. If you generate
   * more than 10 LeaseTasks requests per second, only the first 10 requests will return results.
   * The others will return no results.
   *
   * @param lease Number of {@code unit}s in the lease period
   * @param unit Time unit of the lease period
   * @param countLimit maximum number of tasks to lease
   * @return A list of {@link TaskHandle} for each leased task.
   * @throws InvalidQueueModeException if the target queue is not in pull mode.
   * @throws IllegalArgumentException if {@literal lease < 0}, {@literal countLimit <= 0}, or either
   *     is too large.
   * @throws InternalFailureException
   * @throws IllegalStateException If the queue does not exist. (see queue.xml)
   * @throws TransientFailureException Attempting the request after this exception may succeed.
   */
  List<TaskHandle> leaseTasks(long lease, TimeUnit unit, long countLimit);

  /**
   * Leases up to {@code countLimit} tasks from this queue for a period specified by {@code lease}
   * and {@code unit}, having tag {@code tag}. If {@code tag} is {@code null}, tasks having the same
   * tag as the task with earliest eta will be returned. If fewer such tasks than {@code countLimit}
   * are available, all available such tasks in this {@link Queue} will be returned. The available
   * tasks are those in the queue having the earliest eta such that eta is prior to the time at
   * which the lease is requested. It is guaranteed that the leased tasks will be unavailable for
   * lease to others in the lease period. You must call deleteTask to prevent the task from being
   * leased again after the lease period. This method supports leasing a maximum of 1000 tasks for
   * no more than one week. If you generate more than 10 LeaseTasks requests per second, only the
   * first 10 requests will return results. The others will return no results.
   *
   * @param lease Number of {@code unit}s in the lease period
   * @param unit Time unit of the lease period
   * @param countLimit maximum number of tasks to lease
   * @param tag User defined tag required for returned tasks. If {@code null}, the tag of the task
   *     with earliest eta will be used.
   * @return A list of {@link TaskHandle} for each leased task.
   * @throws InvalidQueueModeException if the target queue is not in pull mode.
   * @throws IllegalArgumentException if {@literal lease < 0}, {@literal countLimit <= 0}, or either
   *     is too large.
   * @throws InternalFailureException
   * @throws IllegalStateException If the queue does not exist. (see queue.xml)
   * @throws TransientFailureException Attempting the request after this exception may succeed.
   */
  List<TaskHandle> leaseTasksByTagBytes(long lease, TimeUnit unit, long countLimit, byte[] tag);

  /**
   * Leases up to {@code countLimit} tasks from this queue for a period specified by {@code lease}
   * and {@code unit}, having tag {@code tag}. If {@code tag} is {@code null}, tasks having the same
   * tag as the task with earliest eta will be returned. If fewer such tasks than {@code countLimit}
   * are available, all available such tasks in this {@link Queue} will be returned. The available
   * tasks are those in the queue having the earliest eta such that eta is prior to the time at
   * which the lease is requested.
   *
   * <p>It is guaranteed that the leased tasks will be unavailable for lease to others in the lease
   * period. You must call deleteTask to prevent the task from being leased again after the lease
   * period. This method supports leasing a maximum of 1000 tasks for no more than one week. If you
   * generate more than 10 LeaseTasks requests per second, only the first 10 requests will return
   * results. The others will return no results.
   *
   * @param lease Number of {@code unit}s in the lease period
   * @param unit Time unit of the lease period
   * @param countLimit maximum number of tasks to lease
   * @param tag User defined {@code String} tag required for returned tasks. If {@code null}, the
   *     tag of the task with earliest eta will be used.
   * @return A list of {@link TaskHandle} for each leased task.
   * @throws InvalidQueueModeException if the target queue is not in pull mode.
   * @throws IllegalArgumentException if {@literal lease < 0}, {@literal countLimit <= 0}, or either
   *     is too large.
   * @throws InternalFailureException
   * @throws IllegalStateException If the queue does not exist. (see queue.xml)
   * @throws TransientFailureException Attempting the request after this exception may succeed.
   */
  List<TaskHandle> leaseTasksByTag(long lease, TimeUnit unit, long countLimit, String tag);

  /**
   * Leases tasks from this queue, with lease period and other options specified by {@code options}.
   * The available tasks are those in the queue having the earliest eta such that eta is prior to
   * the time at which the lease is requested.
   *
   * <p>If {@code options} specifies a tag, only tasks having that tag will be returned. If {@code
   * options} specifies no tag, but does specify {@code groupByTag}, only tasks having the same tag
   * as the task with earliest eta will be returned.
   *
   * <p>It is guaranteed that the leased tasks will be unavailable for lease to others in the lease
   * period. You must call deleteTask to prevent the task from being leased again after the lease
   * period. This method supports leasing a maximum of 1000 tasks for no more than one week. If you
   * generate more than 10 LeaseTasks requests per second, only the first 10 requests will return
   * results. The others will return no results.
   *
   * @param options Specific options for this lease request
   * @return A list of {@link TaskHandle} for each leased task.
   * @throws InvalidQueueModeException if the target queue is not in pull mode.
   * @throws IllegalArgumentException if lease period or countLimit is null or either is too large.
   * @throws InternalFailureException
   * @throws IllegalStateException If the queue does not exist. (see queue.xml)
   * @throws TransientFailureException Attempting the request after this exception may succeed.
   */
  List<TaskHandle> leaseTasks(LeaseOptions options);

  /**
   * Asynchronously leases up to {@code countLimit} tasks from this queue for a period specified by
   * {@code lease} and {@code unit}. If fewer tasks than {@code countLimit} are available, all
   * available tasks in this {@link Queue} will be returned. The available tasks are those in the
   * queue having the earliest eta such that eta is prior to the time at which the lease is
   * requested. It is guaranteed that the leased tasks will be unavailable for lease to others in
   * the lease period. You must call deleteTask to prevent the task from being leased again after
   * the lease period. This method supports leasing a maximum of 1000 tasks for no more than one
   * week. If you generate more than 10 LeaseTasks requests per second, only the first 10 requests
   * will return results. The others will return no results.
   *
   * @param lease Number of {@code unit}s in the lease period
   * @param unit Time unit of the lease period
   * @param countLimit maximum number of tasks to lease
   * @return A {@code Future} whose result is a list of {@link TaskHandle} for each leased task.
   * @throws IllegalArgumentException if {@literal lease < 0}, {@literal countLimit <= 0}, or either
   *     is too large.
   */
  Future<List<TaskHandle>> leaseTasksAsync(long lease, TimeUnit unit, long countLimit);

  /**
   * Asynchronously leases tasks from this queue, with lease period and other options specified by
   * {@code options}. The available tasks are those in the queue having the earliest eta such that
   * eta is prior to the time at which the lease is requested.
   *
   * <p>If {@code options} specifies a tag, only tasks having that tag will be returned. If {@code
   * options} specifies no tag, but does specify {@code groupByTag}, only tasks having the same tag
   * as the task with earliest eta will be returned.
   *
   * <p>It is guaranteed that the leased tasks will be unavailable for lease to others in the lease
   * period. You must call deleteTask to prevent the task from being leased again after the lease
   * period. This method supports leasing a maximum of 1000 tasks for no more than one week. If you
   * generate more than 10 LeaseTasks requests per second, only the first 10 requests will return
   * results. The others will return no results.
   *
   * @param options Specific options for this lease request
   * @return A {@code Future} whose result is a list of {@link TaskHandle} for each leased task.
   * @throws IllegalArgumentException if lease period or countLimit is null or either is too large.
   */
  Future<List<TaskHandle>> leaseTasksAsync(LeaseOptions options);

  /**
   * Asynchronously leases up to {@code countLimit} tasks from this queue for a period specified by
   * {@code lease} and {@code unit}, having tag {@code tag}. If {@code tag} is {@code null}, tasks
   * having the same tag as the task with earliest eta will be returned. If fewer such tasks than
   * {@code countLimit} are available, all available such tasks in this {@link Queue} will be
   * returned. The available tasks are those in the queue having the earliest eta such that eta is
   * prior to the time at which the lease is requested. It is guaranteed that the leased tasks will
   * be unavailable for lease to others in the lease period. You must call deleteTask to prevent the
   * task from being leased again after the lease period. This method supports leasing a maximum of
   * 1000 tasks for no more than one week. If you generate more than 10 LeaseTasks requests per
   * second, only the first 10 requests will return results. The others will return no results.
   *
   * @param lease Number of {@code unit}s in the lease period
   * @param unit Time unit of the lease period
   * @param countLimit maximum number of tasks to lease
   * @param tag User defined tag required for returned tasks. If {@code null}, the tag of the task
   *     with earliest eta will be used.
   * @return A {@code Future} whose result is a list of {@link TaskHandle} for each leased task.
   * @throws IllegalArgumentException if {@literal lease < 0}, {@literal countLimit <= 0}, or either
   *     is too large.
   */
  Future<List<TaskHandle>> leaseTasksByTagBytesAsync(
      long lease, TimeUnit unit, long countLimit, byte[] tag);

  /**
   * Asynchronously leases up to {@code countLimit} tasks from this queue for a period specified by
   * {@code lease} and {@code unit}, having tag {@code tag}. If {@code tag} is {@code null}, tasks
   * having the same tag as the task with earliest eta will be returned. If fewer such tasks than
   * {@code countLimit} are available, all available such tasks in this {@link Queue} will be
   * returned. The available tasks are those in the queue having the earliest eta such that eta is
   * prior to the time at which the lease is requested.
   *
   * <p>It is guaranteed that the leased tasks will be unavailable for lease to others in the lease
   * period. You must call deleteTask to prevent the task from being leased again after the lease
   * period. This method supports leasing a maximum of 1000 tasks for no more than one week. If you
   * generate more than 10 LeaseTasks requests per second, only the first 10 requests will return
   * results. The others will return no results.
   *
   * @param lease Number of {@code unit}s in the lease period
   * @param unit Time unit of the lease period
   * @param countLimit maximum number of tasks to lease
   * @param tag User defined {@code String} tag required for returned tasks. If {@code null}, the
   *     tag of the task with earliest eta will be used.
   * @return A {@code Future} whose result is a list of {@link TaskHandle} for each leased task.
   * @throws IllegalArgumentException if {@literal lease < 0}, {@literal countLimit <= 0}, or either
   *     is too large.
   */
  Future<List<TaskHandle>> leaseTasksByTagAsync(
      long lease, TimeUnit unit, long countLimit, String tag);

  /**
   * Clears all the tasks in this {@link Queue}. This function returns immediately. Some delay may
   * apply on the server before the Queue is actually purged. Tasks being executed at the time the
   * purge call is made will continue executing, other tasks in this Queue will continue being
   * dispatched and executed before the purge call takes effect.
   *
   * @throws IllegalStateException If the Queue does not exist. (see queue.xml)
   * @throws TransientFailureException Attempting the request after this exception may succeed.
   * @throws InternalFailureException
   */
  void purge();

  /**
   * Modify the lease of the specified task in this {@link Queue} for a period of time specified by
   * {@code lease} and {@code unit}. A lease time of 0 will relinquish the lease on the task and
   * make it available to be leased by calling {@link #leaseTasks(LeaseOptions) leaseTasks}.
   *
   * @param taskHandle handle of the task that is having its lease modified.
   * @param lease Number of {@code unit}s in the lease period.
   * @param unit Time unit of the lease period.
   * @return Updated {@link TaskHandle} with the new lease period.
   * @throws InvalidQueueModeException if the target queue is not in pull mode.
   * @throws IllegalArgumentException if {@literal lease < 0} or too large.
   * @throws InternalFailureException
   * @throws IllegalStateException If the queue does not exist, or the task lease has expired or the
   *     queue has been paused.
   * @throws TransientFailureException Attempting the request after this exception may succeed.
   */
  TaskHandle modifyTaskLease(TaskHandle taskHandle, long lease, TimeUnit unit);

  /**
   * Obtain statistics for this {@link Queue}.
   *
   * @return The current {@link QueueStatistics} for this queue.
   * @throws IllegalStateException If the Queue does not exist. (see queue.xml)
   * @throws TransientFailureException Attempting the request after this exception may succeed.
   * @throws InternalFailureException
   */
  QueueStatistics fetchStatistics();

  /**
   * Asynchronously obtains statistics for this {@link Queue}.
   *
   * @param deadlineInSeconds the maximum duration, in seconds, that the fetch statistics request
   *     can run. A default deadline will be used if {@code null} is supplied.
   * @throws IllegalArgumentException if {@literal deadlineInSeconds <= 0}.
   * @return A {@code Future} with a result type of {@link QueueStatistics}.
   */
  Future<QueueStatistics> fetchStatisticsAsync(@Nullable Double deadlineInSeconds);
}
