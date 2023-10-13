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

import java.lang.reflect.Method;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Try to setup a Jetty QueuedThreadPool to use JDK21 virtual threads via
 * introspection. No op if this cannot be done (i.e running with old JDKs.
 */
public class VirtualThreadSetup {

    private static final Logger logger = Logger.getLogger(VirtualThreadSetup.class.getName());

    /*
     * Try to setup a Jetty QueuedThreadPool to use JDK21 virtual threads via
     * introspection. No op if this cannot be done (i.e running with old JDKs.
    Object should be a Jetty QueuedThreadPool.
     */
    public static Object tryToSetVirtualThread(Object threadPool) {
        try {
            Method newVirtualThreadPerTaskExecutor = Executor.class.getMethod("newVirtualThreadPerTaskExecutor");
            Method setVirtualThreadsExecutor = threadPool.getClass().getMethod("setVirtualThreadsExecutor",
                    Class.forName("org.eclipse.jetty.util.thread.QueuedThreadPool"));
            setVirtualThreadsExecutor.invoke(threadPool, newVirtualThreadPerTaskExecutor.invoke(null));
        } catch (Exception e) {
            logger.log(Level.INFO, "Could not configure JDK21 virtual threads in Jetty runtime.", e);
        }
        return threadPool;
    }

    private VirtualThreadSetup() {

    }
}
