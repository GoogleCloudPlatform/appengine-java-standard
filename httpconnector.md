
# **Google App Engine Java new performant HTTP connector**

Webtide/Jetty has implemented a new App Engine Java Runtime mode to use an HTTP-only path to the instance, avoiding the need to proxy to/from RPC UP request/response instances. This document reports on the benchmarks carried out to compare the old and new modes of operation.

The new HTTP-only path gives memory savings in all areas compared to the currently used RPC path. The savings are quite significant for larger requests and responses.

These benchmarks were carried out by deploying to App Engine on an F2 instance. We compared two deployments using the same runtime-deployment jars and the same Web Application. One app enabled the HttpMode via the system property in appengine-web.xml the other did not.

Requests were sent one at a time so that memory usage for a single request could be measured. We measured the memory usage and garbage generated for each request. We measured this using java.lang.management.MemoryMXBean from inside the HttpServlet.service() method.

# **Code History**

Original Gen1 App Engine runtimes (Java7, Java8) have been using a proprietary RPC path to communicate back and forth with the AppServer. So the customers HTTP requests are given to a Gen1 clone as a protocol buffer containing all the request information and via complex Jetty customization, is processing this request and returns another protocol buffer containing the HTTP response.

Gen2 runtimes removed this internal GRPC communication and switched to standard HTTP protocol to receive and process HTTP requests via a standard HTTP port (8080).

In order to accommodate both the widely used Gen1 Java8 runtime and the new Gen2 runtimes, we originally decided to reuse as much of the gen1 code for the gen2 runtimes, and introduced on the gen2 Java code a layer that transforms the new HTTP path to the old gen1 RPC path so that the exact same gen1 code could be used as is with gen2 runtimes. This helped us launch Java 11, 17, and now 21, but introduced memory and CPU overhead.

Java 8 gen1 runtime is now EOL and it is about time to remove this extra layer in our code that is both complex and introduce memory duplication when copying HTTP memory buffers to GRPC internal protocol buffers. Jetty can understand natively HTTP requests, so the code simplification and optimization is the right path now we can stop supporting Java8 runtimes. 

This code optimization is now available optionally via a customer flag in appengine-web.xml:

```
   <system-properties>
        <property name="appengine.use.httpconnector" value="true"/>
    </system-properties>
```

and this document presents some initial benchmark numbers, comparing the new HTTP mode versus the original RPC mode. As you can see, both memory and CPU usage show significant improvement. You can test on your own AppEngine application by redeploying with the new system property.


# **Heap Memory Uage Improvement**

Heap memory usage was measured inside the `HttpServlet.service()` method for varying sizes of request/responses. A `System.gc()` was performed first so that garbage is not included. 


<img width="800" alt="image1" src="https://github.com/GoogleCloudPlatform/appengine-java-standard/blob/doc/image1.png">


<table>
  <tr>
   <td><strong>Heap Memory (After GC</strong>)
   </td>
   <td><strong>32KB Req/Resp</strong>
   </td>
   <td><strong>1MB Req/Resp</strong>
   </td>
   <td><strong>30MB Req/Resp</strong>
   </td>
  </tr>
  <tr>
   <td><strong>HTTP Mode</strong>
   </td>
   <td>14.6MB
   </td>
   <td>14.6MB
   </td>
   <td>15.2MB
   </td>
  </tr>
  <tr>
   <td><strong>RPC Mode</strong>
   </td>
   <td>14.8MB
   </td>
   <td>16.9MB
   </td>
   <td>78.8MB
   </td>
  </tr>
</table>


We can see that even for these requests the new HttpMode uses less memory per request, significantly less for larger requests.


# **Garbage Generation Improvement**

By examining the memory usage before and after the `System.gc()` call, we can measure how much garbage was created per request. 


<img width="800" alt="image2" src="https://github.com/GoogleCloudPlatform/appengine-java-standard/blob/doc/image2.png">


<table>
  <tr>
   <td>
<strong>Garbage Generated</strong>
   </td>
   <td>
<strong>32KB Req/Resp</strong>
   </td>
   <td>
<strong>1MB Req/Resp</strong>
   </td>
   <td>
<strong>30MB Req/Resp</strong>
   </td>
  </tr>
  <tr>
   <td>
<strong>HTTP Mode</strong>
   </td>
   <td>
27.5KB
   </td>
   <td>
0.1MB
   </td>
   <td>
2.9MB
   </td>
  </tr>
  <tr>
   <td>
<strong>RPC Mode</strong>
   </td>
   <td>
1.1MB
   </td>
   <td>
2.3MB
   </td>
   <td>
31.2MB
   </td>
  </tr>
</table>


We can see from these results that the new HttpMode produces 90%+ less garbage at all request sizes.


# **Native Memory Usage Improvement**

The native memory usage was measured inside the `HttpServlet.service()` method for varying sizes of request/responses.


<img width="800" alt="image3" src="https://github.com/GoogleCloudPlatform/appengine-java-standard/blob/doc/image3.png">


<table>
  <tr>
   <td>
<strong>Native Memory</strong>
   </td>
   <td>
<strong>32KB Req/Resp</strong>
   </td>
   <td>
<strong>1MB Req/Resp</strong>
   </td>
   <td>
<strong>30MB Req/Resp</strong>
   </td>
  </tr>
  <tr>
   <td>
<strong>HTTP Mode</strong>
   </td>
   <td>
28.2MB
   </td>
   <td>
28.2MB
   </td>
   <td>
31.2MB
   </td>
  </tr>
  <tr>
   <td>
<strong>RPC Mode</strong>
   </td>
   <td>
28.6MB
   </td>
   <td>
28.7MB
   </td>
   <td>
31.9MB
   </td>
  </tr>
</table>


Native memory was pretty similar for all request sizes, with the HttpMode using slightly less across all three sizes measured.


# **CPU Usage Improvement**

In our CPU benchmark, we subjected both deployments on GAE to a steady load of 100 requests per second for a duration of one hour. Each request was 1KB in size, and the corresponding response was 32KB. By reading the **/proc/[pid]/stat** file, we were able to gather detailed information about the CPU usage of the process.


<img width="800" alt="image4" src="https://github.com/GoogleCloudPlatform/appengine-java-standard/blob/doc/image4.png">


<table>
  <tr>
   <td>
<strong>CPU Metric</strong>
   </td>
   <td>
<strong>HTTP Mode</strong>
   </td>
   <td>
<strong>RPC Mode</strong>
   </td>
  </tr>
  <tr>
   <td>
<strong>Total CPU Time</strong>
   </td>
   <td>
67218
   </td>
   <td>
79105
   </td>
  </tr>
  <tr>
   <td>
<strong>Average CPU Utilization</strong>
   </td>
   <td>
14.11%
   </td>
   <td>
16.98%
   </td>
  </tr>
</table>


From these results we can see that the HTTP mode used approximately 15% less CPU time than the RPC mode, this reduced the average CPU utilization over the duration of the benchmark.


# **Benchmarks Under High Load**

We also benchmarked AppEngine on the Webtide Load testing machines. This tested a Web Application sending back 1MB responses by sending 3k requests/second for 2 minutes. By running the code on the Webtide machines we are able to see how the runtime behaves under higher loads than possible in the production environment, as we have more available memory and allow more concurrent requests.

<img width="800" alt="image5" src="https://github.com/GoogleCloudPlatform/appengine-java-standard/blob/doc/image5.png">


We can see that the new HttpMode uses far less memory, and also has much lower latency times across the board, and performs much better at the 99th percentile. This is likely due to the amount of garbage produced by the RPC mode as seen previously.

Source code for this benchmark can be found here [lachlan-roberts/appengine-performance-testing](https://github.com/lachlan-roberts/appengine-performance-testing).


### **Memory**


* Reduction in total committed memory 682MB to 202MB (70% reduction).
* Reduction is RSS 688MB to 253MB (63% reduction).
* Reduction in Java Heap committed size 422MB to 70MB (83% reduction).


### **Latency**


* Reduction in median latency 222µs to 153µs (31% reduction).
* Reduction of mean latency 14111µs to 208µs (98.5% reduction).
* Reduction in min latency 153µs to 127µs (16% reduction).
* Reduction of latency in 99th percentile 254541µs to 752µs (99.7% reduction).
