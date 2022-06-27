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

package com.google.appengine.apicompat.usage;

import com.google.appengine.api.blobstore.BlobstoreService;
import com.google.appengine.api.blobstore.BlobstoreServicePb;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class BlobstoreApiExhaustiveUsageTest extends ApiExhaustiveUsageTestCase {

  @Override
  List<String> getPrefixesToInclude() {
    return ImmutableList.of(BlobstoreService.class.getPackage().getName().replace('.', '/'));
  }

  @Override
  List<String> getPrefixesToExclude() {
    return ImmutableList.of(BlobstoreServicePb.class.getName().replace('.', '/'));
  }
}
