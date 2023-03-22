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

package com.google.appengine.setup.test.util;

import com.google.appengine.setup.test.TestAppBase;
import com.google.common.base.StandardSystemProperty;
import com.google.common.collect.ImmutableList;
import com.jcabi.aspects.RetryOnFailure;
import java.io.File;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

public class TestUtil {
    @SneakyThrows
    public static Process initializeApp(String userAppRelativeJarPath) {
        File currentDirectory = new File("").getAbsoluteFile();
        File jetty11TestAppJar = new File(currentDirectory, userAppRelativeJarPath);
        ImmutableList<String> processArgs = ImmutableList.<String>builder()
            .add(StandardSystemProperty.JAVA_HOME.value() + "/bin/java")
            .add("-jar")
            .add(jetty11TestAppJar.getAbsolutePath())
            .build();
        ProcessBuilder pb = new ProcessBuilder(processArgs);
        Process userApp = pb.start();
        OutputPump outPump = new OutputPump(userApp.getInputStream(), "[stdout] ");
        OutputPump errPump = new OutputPump(userApp.getErrorStream(), "[stderr] ");
        new Thread(outPump).start();
        new Thread(errPump).start();
        return userApp;
    }

    public static HttpClient initializeHttpClient(int timeoutMillis) {
        return HttpClientBuilder.create()
            .setDefaultRequestConfig(RequestConfig.custom()
                .setConnectTimeout(timeoutMillis)
                .setConnectionRequestTimeout(timeoutMillis)
                .setSocketTimeout(timeoutMillis)
                .build())
            .build();
    }

    @SneakyThrows
    public static Process initializeHttpApiServer() {
        String apiServerRelativeJarPath = "../apiserver_local/target/"
            + "apiserver_local-1.0-SNAPSHOT-jar-with-dependencies.jar";
        File currentDirectory = new File("").getAbsoluteFile();
        File apiServerJar = new File(currentDirectory, apiServerRelativeJarPath);
        ImmutableList<String> processArgs = ImmutableList.<String>builder()
            .add(StandardSystemProperty.JAVA_HOME.value() + "/bin/java")
            .add("-jar")
            .add(apiServerJar.getAbsolutePath())
            .build();
        ProcessBuilder pb = new ProcessBuilder(processArgs);
        Process userApp = pb.start();
        OutputPump outPump = new OutputPump(userApp.getInputStream(), "[stdout] ");
        OutputPump errPump = new OutputPump(userApp.getErrorStream(), "[stderr] ");
        new Thread(outPump).start();
        new Thread(errPump).start();
        return userApp;
    }

    @SneakyThrows
    @RetryOnFailure(attempts = 3, delay = 2, unit = TimeUnit.SECONDS)
    public static void waitUntilUserAppIsInitialized(HttpClient httpClient, String appName) {
        System.out.println(("Waiting for User Application to start - " + appName));
        HttpResponse response = httpClient.execute(new HttpGet(TestAppBase.USER_APPLICATION_URL_PREFIX));
        String content = EntityUtils.toString(response.getEntity());
        if (!content.equals(appName)) {
            throw new RuntimeException("User App is not yet initialized - " + appName);
        }
    }

    @SneakyThrows
    @RetryOnFailure(attempts = 3, delay = 2, unit = TimeUnit.SECONDS)
    public static void waitUntilApiServerIsInitialized(HttpClient httpClient) {
        System.out.println(("Waiting for APIServer to start"));
        HttpResponse response = httpClient.execute(new HttpGet(TestAppBase.API_SERVER_URL_PREFIX));
    }
}
