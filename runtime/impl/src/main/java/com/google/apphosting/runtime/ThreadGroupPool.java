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

import com.google.auto.value.AutoBuilder;
import com.google.common.flogger.GoogleLogger;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@code ThreadGroupPool} is a very simple thread pool where each
 * pooled thread is in its own {@link ThreadGroup}.  Unfortunately
 * threads cannot be moved around between thread groups, so we just
 * pool (ThreadGroup, Thread) pairs.  If additional threads are
 * started in a thread group, they are expected to have exited before
 * the runnable provided to {@link #start} completes.  If this is not
 * the case, the thread will be dropped from the thread pool and
 * detailed diagnostics will be written to the log.
 *
 * <p>Unlike thread names, thread group names are immutable so thread
 * groups will be named with a specified prefix with a counter
 * appended.  The name of the main thread for each thread pool is
 * determined when {@link #start} is called.
 *
 */
public class ThreadGroupPool {
  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();

  public static Builder builder() {
    return new AutoBuilder_ThreadGroupPool_Builder();
  }

  /** Builder for ThreadGroupPool. */
  @AutoBuilder
  public abstract static class Builder {
    public abstract Builder setParentThreadGroup(ThreadGroup threadGroup);

    public abstract Builder setThreadGroupNamePrefix(String threadGroupNamePrefix);

    public abstract Builder setUncaughtExceptionHandler(
        UncaughtExceptionHandler uncaughtExceptionHandler);

    public abstract Builder setIgnoreDaemonThreads(boolean ignoreDaemonThreads);

    public abstract ThreadGroupPool build();
  }

  private final ThreadGroup parentThreadGroup;
  private final String threadGroupNamePrefix;
  private final AtomicInteger threadGroupCounter;
  private final Queue<PoolEntry> waitingThreads;
  private final UncaughtExceptionHandler uncaughtExceptionHandler;
  private final boolean ignoreDaemonThreads;

  public ThreadGroupPool(
      ThreadGroup parentThreadGroup,
      String threadGroupNamePrefix,
      UncaughtExceptionHandler uncaughtExceptionHandler,
      boolean ignoreDaemonThreads) {
    this.parentThreadGroup = parentThreadGroup;
    this.threadGroupNamePrefix = threadGroupNamePrefix;
    this.threadGroupCounter = new AtomicInteger(0);
    this.waitingThreads = new ConcurrentLinkedQueue<>();
    this.uncaughtExceptionHandler = uncaughtExceptionHandler;
    this.ignoreDaemonThreads = ignoreDaemonThreads;
  }

  /**
   * Execute {@code runnable} in a thread named {@code threadName}.
   * This may be a newly created thread or it may be a thread that was
   * was already used to run one or more previous invocations.
   *
   * <p>{@code runnable} can spawn other threads in the pooled
   * {@link ThreadGroup}, but they must all exit before the runnable
   * completes.  Failure of the extra threads to complete will result
   * in a severe log message and the dropping of this thread from the
   * pool.
   *
   * <p>This method will block until the thread begins executing
   * {@code runnable}.  If executing {@link Runnable#run} on
   * {@code runnable} throws an exception, the thread will not be
   * returned to the thread pool.
   */
  public void start(String threadName, Runnable runnable) throws InterruptedException {
    PoolEntry entry = waitingThreads.poll();
    if (entry == null) {
      entry = buildPoolEntry();
    }
    initThread(entry.getMainThread(), threadName);
    entry.runInMainThread(runnable);
  }

  private void removeThread(PoolEntry entry) {
    waitingThreads.remove(entry);
  }

  private void returnThread(PoolEntry entry) {
    initThread(entry.getMainThread(), "Idle");
    waitingThreads.add(entry);
  }

  public int waitingThreadCount() {
    return waitingThreads.size();
  }

  private void initThread(Thread thread, String threadName) {
    thread.setName(threadName);
    thread.setUncaughtExceptionHandler(null);
  }

  private PoolEntry buildPoolEntry() {
    String name = threadGroupNamePrefix + threadGroupCounter.getAndIncrement();
    ThreadGroup threadGroup =
        new ThreadGroup(parentThreadGroup, name) {
          @Override
          public void uncaughtException(Thread th, Throwable ex) {
            uncaughtExceptionHandler.uncaughtException(th, ex);
          }
        };
    PoolEntry entry = new PoolEntry(threadGroup);
    entry.startMainThread();
    return entry;
  }

  /**
   * If the current thread is main thread started in response to a
   * call to {@link #start}, this method will arrange for it to expect
   * to be "restarted."  See {@link RestartableThread} for more
   * information.
   *
   * @throws IllegalStateException If the current thread is not a main
   * thread.
   */
  public static CountDownLatch resetCurrentThread() throws InterruptedException {
    Thread thread = Thread.currentThread();
    if (thread instanceof RestartableThread) {
      return ((RestartableThread) thread).reset();
    } else {
      throw new IllegalStateException("Current thread is not a main request thread.");
    }
  }

  /**
   * {@code RestartableThread} is a thread that can be put to sleep
   * until {@link Thread#start} is called again.  This is required for
   * background threads, which will be spawned normally and passed to
   * user code, but then need to block until user code invokes the
   * start method before proceeding.  To facilitate this, calling code
   * needs to invoke {@link #reset} before returning the
   * thread to user code, and it can block on the returned latch to be
   * awoke when user code calls start.  Note that subsequent start
   * calls will behave normally, including throwing an
   * {@link IllegalStateException} when appropriate.
   */
  private static final class RestartableThread extends Thread {
    private final Object lock = new Object();
    private CountDownLatch latch;

    RestartableThread(ThreadGroup threadGroup, Runnable runnable) {
      super(threadGroup, runnable);
    }

    public CountDownLatch reset() {
      synchronized (lock) {
        latch = new CountDownLatch(1);
        return latch;
      }
    }

    @SuppressWarnings("UnsynchronizedOverridesSynchronized") // synchronized on lock, then on super
    @Override
    public void start() {
      synchronized (lock) {
        if (latch != null) {
          latch.countDown();
          latch = null;
          return;
        }
      }
      // No reset was pending, do the normal thing.
      super.start();
    }

    @Override
    public Thread.State getState() {
      synchronized (lock) {
        if (latch != null) {
          // Thread has been reset, pretend it is not yet started.
          return Thread.State.NEW;
        }
      }
      return super.getState();
    }
  }

  /**
   * {@code PoolEntry} is one entry in a {@link ThreadGroupPool} that
   * consists of a {@link ThreadGroup}, a single {@link Thread} within
   * that group, and a {@link SynchronousQueue} that is used to pass a
   * {@link Runnable} into the thread for execution.  The entry itself
   * serves as a {@link Runnable} that forwards control the
   * {@link Runnable} received via the {@link Executor}, and then
   * verifies that no other threads remain in the {@link ThreadGroup}
   * before returning it to the pool.
   */
  private class PoolEntry implements Runnable {
    final ThreadGroup threadGroup;

    /**
     * This SynchronousQueue is passed Runnables from the thread calling (via
     * {@link #start}) to one of the pooled threads waiting to execute
     * the Runnable.
     */
    private final SynchronousQueue<Runnable> runnableQueue;

    private final RestartableThread mainThread;

    PoolEntry(ThreadGroup threadGroup) {
      this.threadGroup = threadGroup;
      this.runnableQueue = new SynchronousQueue<>();

      mainThread = new RestartableThread(threadGroup, this);
      mainThread.setDaemon(false);
    }

    void startMainThread() {
      mainThread.start();
    }

    Thread getMainThread() {
      return mainThread;
    }

    void runInMainThread(Runnable runnable) throws InterruptedException {
      if (!mainThread.isAlive()) {
        throw new IllegalStateException("Main thread is not running.");
      }
      runnableQueue.put(runnable);
    }

    @Override
    public void run() {
      try {
        while (true) {
          Runnable runnable;
          try {
            runnable = runnableQueue.take();
          } catch (InterruptedException ex) {
            logger.atInfo().withCause(ex).log("Interrupted while waiting for next Runnable");
            removeThread(this);
            return;
          }
          runnable.run();
          if (otherThreadsLeftInThreadGroup()) {
            return;
          }
          if (Thread.interrupted()) {
            logger.atInfo().log("Not reusing %s, interrupt bit was set.", this);
            return;
          }
          returnThread(this);
        }
      } catch (Throwable th) {
        JavaRuntime.killCloneIfSeriousException(th);
        throw th;
      }
    }

    /**
     * Verifies that no other active threads are present in
     * {@code threadGroup}.  If any threads are still running, log
     * their stack trace and return {@code true}.
     */
    private boolean otherThreadsLeftInThreadGroup() {
      List<Thread> threads = threadsInThreadGroup();
      boolean otherThreads = false;
      if (threads.size() > 1) {
        for (Thread thread : threads) {
          if (thread != Thread.currentThread()
              && !(ignoreDaemonThreads && thread.isDaemon())) {
            Throwable th = new Throwable();
            th.setStackTrace(thread.getStackTrace());
            logger.atSevere().withCause(th).log("Extra thread left running: %s", thread);
            otherThreads = true;
          }
        }
      }
      return otherThreads;
    }

    private List<Thread> threadsInThreadGroup() {
      Thread[] threads = new Thread[50];
      int threadCount;
      while ((threadCount = threadGroup.enumerate(threads, true)) == threads.length) {
        threads = new Thread[threads.length * 2];
      }
      return Arrays.asList(threads).subList(0, threadCount);
    }
  }
}
