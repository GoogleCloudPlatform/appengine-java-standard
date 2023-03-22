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

package com.google.appengine.setup.testapps.common;

import com.google.appengine.api.images.Image;
import com.google.appengine.api.memcache.MemcacheService;
import com.google.appengine.api.memcache.MemcacheServiceFactory;
import com.google.appengine.api.memcache.Stats;
import java.io.IOException;
import java.util.Random;

public class TestAppsCommonServletLogic {

    private static Random random = new Random();

    /**
     * @return HTML output for servlet testing for Memcache
     */
    public static String testMemcacheService() {
        String result = "";
        MemcacheService memcacheService = MemcacheServiceFactory.getMemcacheService();
        String key = "test_key:" + random.nextInt(10000);
        String value = "value" + random.nextInt();
        memcacheService.put(key, value);
        result += "Setting Memcache Service for key - " + key + ", with value - " + value + "<br/>";
        result += "Got Memcache Service for key - " + key + ", with value - " + memcacheService.get(key) + "<br/>";

        Stats stats = memcacheService.getStatistics();
        result += "Memcache Service Stats -  <br/>" + stats.toString();
        return result;
    }

    /**
     * @return HTML output for servlet testing for Image Service
     */
    public static String testImageService() throws IOException {
        String result = "";
        result += "<h1 style='text-align:center;'> Image Processing </h1>";
        result += "<h2 style='text-align:center;'> Original Image </h2>";
        Image originalImage = ImageServiceUtil.readImage();
        result += ImageServiceUtil.getBase64HtmlImage(originalImage);
        result += "<h2 style='text-align:center;'> Transformed Image </h2>";
        result += ImageServiceUtil.getBase64HtmlImage(ImageServiceUtil.transformImage(originalImage));
        return result;
    }
}
