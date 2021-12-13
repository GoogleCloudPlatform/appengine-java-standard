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

package com.google.appengine.tools.compilation;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Helper that keeps track of the callbacks we encounter and writes them out in
 * the appropriate format.
 *
 * @see DatastoreCallbacksProcessor
 *
 */
class DatastoreCallbacksConfigWriter {

  static final String INCORRECT_FORMAT_MESSAGE = "Existing config file has incorrect "
      + "format version. Please do a clean rebuild of your application.";

  /**
   * Property that stores the format version of the individual entries.  This
   * property is not a valid callback key, so there is no risk of a collision with
   * real data.
   */
  static final String FORMAT_VERSION_PROPERTY = "DatastoreCallbacksFormatVersion";

  /**
   * Right now we only have one format version, so we can hardcode this.
   */
  private static final String FORMAT_VERSION = Integer.toString(1);

  /**
   * Key is the kind (possibly the empty string), value is a {@link Map} where
   * the key is the callback type and the value is a {@link List} of
   * {@link String Strings}, each of which uniquely identifies a method that
   * implements the callback for the kind and callback type.  Each of these
   * Strings is of the form fqn:method_name.  We're not using a LinkedHashMap
   * to make the iteration order deterministic because we're using
   * {@link Properties} to write this data to a file, and Properties, unlike
   * LinkedHashMap, does not have a deterministic ordering.
   */
  /* @VisibleForTesting */
  final Map<String, Multimap<String, String>> callbacks = Maps.newHashMap();

  /**
   * Maps the fully-qualified classnames of all classes with callbacks to the methods on those
   * classes that are callbacks.
   */
  /* @VisibleForTesting */
  final SetMultimap<String, String> methodsWithCallbacks = LinkedHashMultimap.create();

  // We'll just use Properties to write the data.  There's no way to ensure
  // that the data gets written out in the same order every time, which makes
  // testing a little bit harder, but it's also nice to let this class handle
  // reading and writing the file.
  final Properties props = new Properties();

  /**
   * Used to avoid logging about the same pruned class more than once.  This
   * member should not be read until after {@link #store(java.io.OutputStream)}
   * has been called.
   */
  final Set<String> prunedClasses = Sets.newHashSet();

  /**
   * @param inputStream a (possibly {@code null} stream from which we can read an existing config
   */
  DatastoreCallbacksConfigWriter(@Nullable InputStream inputStream) throws IOException {
    if (inputStream != null) {
      props.loadFromXML(inputStream);
      // We'll add the version back when we store the config
      Preconditions.checkState(
          FORMAT_VERSION.equals(props.remove(FORMAT_VERSION_PROPERTY)), INCORRECT_FORMAT_MESSAGE);
    }
  }

  /**
   * Overwrites the contents of the provided {@link InputStream} (if provided)
   * with the callback config and then stores it to disk.
   */
  public void store(OutputStream outputStream) throws IOException {
    pruneExistingConfig();

    // Add a version marker so that it's easy to change the format in the future.
    props.setProperty(FORMAT_VERSION_PROPERTY, FORMAT_VERSION);

    for (String kind : callbacks.keySet()) {
      Multimap<String, String> kindMap = callbacks.get(kind);
      for (String callbackType : kindMap.keySet()) {
        String propKey = String.format("%s.%s", kind, callbackType);
        // We've already purged everything that should no longer exist, so now
        // merge old and new methods for this key.
        Collection<String> newMethods = kindMap.get(callbackType);
        StringBuilder combinedMethods = new StringBuilder();
        String oldMethods = props.getProperty(propKey);
        if (oldMethods != null) {
          combinedMethods.append(oldMethods).append(",");
        }
        props.setProperty(propKey, Joiner.on(",").appendTo(combinedMethods, newMethods).toString());
      }
    }
    // Write the data as XML.
    props.storeToXML(outputStream, "Datastore Callbacks. DO NOT EDIT BY HAND!");
  }

  private void pruneExistingConfig() {
    // props may contain existing data.  Since the annotation processor doesn't
    // get invoked when a class that used to have annotations is deleted, or
    // when a method that used to have annotations is compiled, we need to
    // remove references to these classes and methods ourselves.  We'll
    // remove all references to classes that have been run through the
    // annotation processor.  The most up-to-date config for these classes will be
    // contained in callbacks so we're guaranteed to end up with the correct
    // config.  We'll also remove all references to classes that cannot be
    // loaded because if the class is not on the classpath it either no longer
    // exists (in which case we want to remove the references) or it is going
    // to get compiled (in which case the correct references will get
    // regenerated anyway).
    for (String propName : props.stringPropertyNames()) {
      String propVal = props.getProperty(propName);
      List<String> methodsToPreserve = Lists.newArrayList();
      for (String method : Splitter.on(',').split(propVal)) {
        String classStr = Iterables.get(Splitter.on(':').split(method), 0);
        if (!methodsWithCallbacks.containsKey(classStr) && classExists(classStr)) {
          // the class still exists and is not one that we've processed so
          // leave the reference to the method
          methodsToPreserve.add(method);
        }
      }
      if (methodsToPreserve.isEmpty()) {
        props.remove(propName);
      } else {
        props.setProperty(propName, Joiner.on(",").join(methodsToPreserve));
      }
    }
  }

  private boolean classExists(String classStr) {
    try {
      Class.forName(classStr);
      return true;
    } catch (ClassNotFoundException e) {
      prunedClasses.add(classStr);
      return false;
    }
  }

  public void addCallback(Set<String> kinds, String callbackType, String cls, String method) {
    String clsMethod = String.format("%s:%s", cls, method);
    if (kinds.isEmpty()) {
      // wildcard callback that matches all kinds.
      kinds = ImmutableSet.of("");
    }
    for (String kind : kinds) {
      Multimap<String, String> kindMap =
          callbacks.computeIfAbsent(kind, (String k) -> LinkedHashMultimap.create());
      kindMap.put(callbackType, clsMethod);
    }
    methodsWithCallbacks.put(cls, method);
  }

  @Override
  public String toString() {
    return String.format("Datastore Callbacks: %s\nPruned Classes: %s", callbacks, prunedClasses);
  }

  public boolean hasCallback(String cls, String method) {
    return methodsWithCallbacks.containsEntry(cls, method);
  }
}
