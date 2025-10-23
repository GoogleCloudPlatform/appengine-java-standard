<!--
 Copyright 2022 Google LLC

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

# Access legacy bundled services from customer owned Jetty server.

## Before you begin
1. Follow the instructions in [Java 17+ runtime environment](https://cloud.google.com/appengine/docs/standard/java-gen2/runtime#java-17) to build a Java 17/21/25 application.
2. Example Java 11 application built with JettyServer - [github](https://github.com/GoogleCloudPlatform/java-docs-samples/tree/8f899982b9200b44b7e2b69224309637150d9783/appengine-java11/appengine-simple-jetty-main)

## Setting App Engine SDK
### Define SDK Dependency in pom.xml
```xml
    <dependency>
      <groupId>com.google.appengine</groupId>
      <artifactId>appengine-api-1.0-sdk</artifactId>
      <version>3.0.1</version>
    </dependency>
```
### Setup Filter
For Jetty server with version 12, Filter comes from ```jakarta.servlet.Filter```. Whereas for the below Jetty versions, Filter comes from ```javax.servlet.Filter```.

```java
import com.google.appengine.setup.ApiProxySetupUtil;

public class ApiProxyFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        ApiProxySetupUtil.registerAPIProxy(name -> httpRequest.getHeader(name));
        chain.doFilter(httpRequest, response);
    }
}
```
### Attach Filter to Jetty Server
```java
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHandler;

class JettyServerMain {
    public static void main(String[] args) {
        Server server = new Server(8080 /* port */);
        ServletHandler servletHandler = new ServletHandler();
        server.setHandler(servletHandler);

        // ************************************************************
        // *** This is how we can attach a Filter to Jetty Server. ***
        // ************************************************************
        servletHandler.addFilterWithMapping(ApiProxyFilter.class, "/*",
            EnumSet.of(DispatcherType.REQUEST));
      
        server.start();
    }
}
```