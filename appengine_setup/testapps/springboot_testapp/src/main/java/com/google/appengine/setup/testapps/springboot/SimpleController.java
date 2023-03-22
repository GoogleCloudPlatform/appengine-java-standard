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

package com.google.appengine.setup.testapps.springboot;

import com.google.appengine.setup.ApiProxySetupUtil;
import com.google.appengine.setup.testapps.common.TestAppsCommonServletLogic;
import java.io.IOException;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class SimpleController {
    @Value("${spring.application.name}")
    String appName;

    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String home(Model model) {
        return appName;
    }

    @GetMapping(value = "/status", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String status(Model model) {
        return "{ \"status\": \"ok\"}";
    }

    @GetMapping(value = "/memcache", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String memcache(Model model, @RequestHeader Map<String, String> headers) {
        ApiProxySetupUtil.registerAPIProxy(name -> headers.get(name));
        return TestAppsCommonServletLogic.testMemcacheService();
    }

    @GetMapping(value = "/image", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String image(Model model, @RequestHeader Map<String, String> headers) throws IOException {
        ApiProxySetupUtil.registerAPIProxy(name -> headers.get(name));
        return TestAppsCommonServletLogic.testImageService();
    }
}