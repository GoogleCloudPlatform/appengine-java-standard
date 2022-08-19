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

package com.google.appengine.apicompat;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.reflect.ClassPath;
import java.io.File;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests that API classes never store immutable Guava collections in fields of serializable objects.
 * Doing so would risk running afoul of repackaging, since the class of the object is likely to be
 * something like com.google.appengine.repackaged.com.google.common.collect.$ImmutableList. Since we
 * reserve the right to change the repackaging scheme, if we allowed objects to contain these
 * repackaged classes they would not necessarily be serial-compatible with other versions.
 */
@RunWith(JUnit4.class)
public class NoSerializeImmutableTest {
  /**
   * Fields that we have audited to make sure that the API classes never assign Guava collections to
   * them.
   */
  private static final ImmutableSet<String> AUDITED =
      ImmutableSortedSet.of(
          "com.google.appengine.api.datastore.Index.properties",
          "com.google.appengine.api.datastore.LazyList.results",
          "com.google.appengine.api.datastore.Query$CompositeFilter.subFilters",
          "com.google.appengine.api.datastore.Query.filterPredicates",
          "com.google.appengine.api.datastore.Query.sortPredicates",
          "com.google.appengine.api.images.CompositeTransform.transforms",
          "com.google.appengine.api.log.LogQuery.majorVersionIds",
          "com.google.appengine.api.log.LogQuery.requestIds",
          "com.google.appengine.api.log.LogQuery.versions",
          "com.google.appengine.api.log.RequestLogs.appLogLines",
          "com.google.appengine.api.search.DeleteException.results",
          "com.google.appengine.api.search.Document.facets",
          "com.google.appengine.api.search.Document.fields",
          "com.google.appengine.api.search.FacetResult.values",
          "com.google.appengine.api.search.Field.vector",
          "com.google.appengine.api.search.GetResponse.results",
          "com.google.appengine.api.search.PutException.ids",
          "com.google.appengine.api.search.PutException.results",
          "com.google.appengine.api.search.PutResponse.ids",
          "com.google.appengine.api.search.PutResponse.results",
          "com.google.appengine.api.search.Results.facets",
          "com.google.appengine.api.search.Results.results",
          "com.google.appengine.api.search.ScoredDocument.expressions",
          "com.google.appengine.api.search.ScoredDocument.scores",
          "com.google.appengine.api.taskqueue.TaskAlreadyExistsException.tasknames",
          "com.google.appengine.api.taskqueue.TaskOptions.params",
          "com.google.appengine.api.urlfetch.HTTPResponse.headers");

  /**
   * Finds all collection-typed fields in serializable API classes, and checks that there are none
   * beyond the ones listed in {@link #AUDITED}. If this test fails because a new field has been
   * added, make sure that the field indeed cannot be given a value of a Guava collection type
   * before adding it to the list.
   */
  @Test
  public void serializableCollectionFieldsAreNotGuavaImmutable() throws Exception {
    File appengineApiJar = new File("/tmp/check_build/appengine-api-1.0-sdk/target/appengine-api-1.0-sdk-2.0.8-SNAPSHOT.jar");
    assertThat(appengineApiJar.exists()).isTrue();
    ClassLoader apiJarClassLoader = new URLClassLoader(new URL[] {appengineApiJar.toURI().toURL()});
    Class<?> messageLite =
        apiJarClassLoader.loadClass(
            "com.google.appengine.repackaged.com.google.protobuf.MessageLite");
    ClassPath classPath = ClassPath.from(apiJarClassLoader);
    ImmutableSortedSet.Builder<String> serializableCollectionFields =
        ImmutableSortedSet.naturalOrder();
    for (ClassPath.ClassInfo classInfo : classPath.getAllClasses()) {
      String className = classInfo.getName();
      if (className.startsWith("com.google.appengine.api.")) {
        Class<?> c = classInfo.load();
        if (Serializable.class.isAssignableFrom(c) && !messageLite.isAssignableFrom(c)) {
          for (Field f : c.getDeclaredFields()) {
            if ((f.getModifiers() & (Modifier.STATIC | Modifier.TRANSIENT)) == 0
                && Collection.class.isAssignableFrom(f.getType())) {
              serializableCollectionFields.add(f.getDeclaringClass().getName() + "." + f.getName());
            }
          }
        }
      }
    }
    assertWithMessage("Audited field list does not contain all collection-type fields")
        .that(AUDITED)
        .containsExactlyElementsIn(serializableCollectionFields.build());
  }
}
