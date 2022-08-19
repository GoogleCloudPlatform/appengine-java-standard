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

import com.google.appengine.api.datastore.Link;
import static org.junit.Assert.assertTrue;

import com.google.appengine.apicompat.ApiComparison;
import com.google.appengine.apicompat.ExhaustiveApiUsage;
import com.google.appengine.apicompat.ExhaustiveApiUsageVerifier;
import com.google.appengine.apicompat.Utils;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.common.reflect.ClassPath;
//import com.google.testing.util.TestUtil;
import java.io.File;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.junit.Assert;
import org.junit.Test;

/**
 * Base class for tests that verify the exhaustive usage of a list of api
 * classes.
 */
public abstract class ApiExhaustiveUsageTestCase {

    /**
     * The path to the jar containing all the usage classes needed for the test.
     */
    private static final String USAGE_JAR_PATH
            = "/google3/javatests/com/google/appengine/apicompat/usage/";

    /**
     * The path to the sdk api jar.
     */
    private static final String API_JAR_PATH
            = "/tmp/check_build/appengine-api-1.0-sdk/target/appengine-api-1.0-sdk-2.0.8-SNAPSHOT.jar";

    private boolean isExhaustiveUsageClass(String clsName) {
        return clsName.startsWith("com.google.appengine.apicompat.usage");
    }

    @Test
    public final void testUsage() throws Exception {
        com.google.appengine.apicompat.usage.DatastoreApiUsage.LinkUsage
                aa = new com.google.appengine.apicompat.usage.DatastoreApiUsage.LinkUsage();
        ExhaustiveApiUsageVerifier usageVerifier
                = new ExhaustiveApiUsageVerifier(this::isExhaustiveUsageClass);
        for (Class<? extends ExhaustiveApiUsage<?>> cls : getUsageClasses()) {
            assertSameApi(usageVerifier.verify(cls));
        }
    }

    /**
     * This test works in conjunction with the
     * {@link #getApiJarEntryNameFilter()} template method to verify that every
     * accessible class in the api jar has an associate usage class. If someone
     * adds a new accessible class to the api jar and does not create a usage
     * class, this test will fail until they add the usage class.
     *
     * @throws Exception Exception
     */
    @Test
    public final void testHasUsageClassesForAllAccessibleClasses() throws Exception {
        Set<Class<?>> apiClassesWithUsage = getApiClassesWithUsage();
        Set<Class<?>> apiClassesThatShouldHaveUsage = getApiClasses();

        // Make sure we have a usage class for every api class
        Set<Class<?>> apiClassesWithoutUsage = Sets.newHashSet(apiClassesThatShouldHaveUsage);
        apiClassesWithoutUsage.removeAll(apiClassesWithUsage);
        assertTrue(
                "\nThe following classes are missing usage: \n"
                + Joiner.on("\n").join(apiClassesWithoutUsage),
                apiClassesWithoutUsage.isEmpty());

        // Make sure we have an api class for every usage class
        Set<Class<?>> nonApiClassesWithUsage = Sets.newHashSet(apiClassesWithUsage);
        nonApiClassesWithUsage.removeAll(apiClassesThatShouldHaveUsage);
        assertTrue(
                "\nThe following classes have usage but are not in the api: \n"
                + Joiner.on("\n").join(nonApiClassesWithUsage),
                nonApiClassesWithUsage.isEmpty());
    }

    /**
     * @return The set of api classes that have usage classes defined.
     * @throws Exception Exception.
     */
    private Set<Class<?>> getApiClassesWithUsage() throws Exception {
        Set<Class<?>> apiClassesWithUsage = Sets.newHashSet();
        for (Class<? extends ExhaustiveApiUsage<?>> usageClass : getUsageClasses()) {
            // Need to instantiate an instance of the usage class in order to ask it what api class it
            // uses.
            ExhaustiveApiUsage<?> usage = usageClass.getConstructor().newInstance();
            apiClassesWithUsage.add(usage.getApiClass());
        }
        return apiClassesWithUsage;
    }

    protected static void assertSameApi(ApiComparison result) {
        if (result.hasDifference()) {
            Assert.fail(result.toString());
        }
    }

    /**
     * Assembles the set of usage classes that are relevant to the test.
     *
     * @return The usage classes
     * @throws Exception Exception.
     */
    @SuppressWarnings("unchecked")
    private Set<Class<? extends ExhaustiveApiUsage<?>>> getUsageClasses() throws Exception {
        Set<Class<? extends ExhaustiveApiUsage<?>>> usageClasses = Sets.newHashSet();
        ClassPath cp = ClassPath.from(this.getClass().getClassLoader());
        for (ClassPath.ClassInfo n : cp.getAllClasses()) {
            Class cl = null;
            if (n.getName().contains("module-info")) {

            } else if (n.getName().contains("MockMethodDispatcher")) {

            } else if (n.getName().contains("bytebuddy")) {

            } else if (n.getName().contains("org.")) {

            } else if (n.getName().contains("rpc3.")) {

            } else if (n.getName().contains("com.google.common.")) {

            } else if (n.getName().contains("repackaged.")) {

            } else if (n.getName().contains("apphosting.executor.")) {

            } else if (n.getName().contains("UsageWithLocal")) {
                //    System.out.println("module repaxk.");



            } else {
                try {
                    cl = n.load();
                    if (!Modifier.isAbstract(cl.getModifiers()) && (ExhaustiveApiUsage.class.isAssignableFrom(cl))) {
                        usageClasses.add((Class<? extends ExhaustiveApiUsage<?>>) cl);
                        System.out.println("ADDIN " + cl.getName());
                    }
                } catch (Throwable eee) {
                    System.out.println(" ADDIN FAILED " + n.getName());
                }
            }
        }
        return usageClasses;

    }

    // We rely on the test being run from a jar that includes all the relevant usage classes.
    /* JarFile jarFile = new JarFile(USAGE_JAR_PATH + getClass().getSimpleName() + ".jar");
    try {
      for (Enumeration<JarEntry> entryEnum = jarFile.entries(); entryEnum.hasMoreElements(); ) {
        JarEntry entry = entryEnum.nextElement();
        String name = entry.getName();
        if (name.endsWith(".class")) {
          // load the class and see if it is accessible
          Class<?> usageClass = Class.forName(
              name.substring(0, name.length() - ".class".length()).replace('/', '.'));
          if (!Modifier.isAbstract(usageClass.getModifiers())
              && ExhaustiveApiUsage.class.isAssignableFrom(usageClass)) {
            usageClasses.add((Class<? extends ExhaustiveApiUsage<?>>) usageClass);
          }
        }
      }
      return usageClasses;
    } finally {
      jarFile.close();
    }*/
    /**
     * @return A set of all api classes in the api jar that match the
     * {@link Predicate} returned by {@link #getApiJarEntryNameFilter()}.
     * @throws Exception Exception.
     */
    private Set<Class<?>> getApiClasses() throws Exception {
        File apiJarFile = new File(API_JAR_PATH);
        URL apiJarUrl = apiJarFile.toURI().toURL();
        URLClassLoader apiJarClassLoader = new URLClassLoader(new URL[]{apiJarUrl});
        try ( JarFile jarFile = new JarFile(apiJarFile)) {
            Set<Class<?>> apiClasses = Sets.newHashSet();
            Predicate<String> prefix = getApiJarEntryNameFilter();
            for (JarEntry entry : Collections.list(jarFile.entries())) {
                String name = entry.getName();
                // Ignore package-info.java, it's not a normal class that we can load
                if (!name.endsWith("/package-info.class")
                        && name.endsWith(".class")
                        && prefix.apply(name)) {
                    // load the class and see if it is accessible
                    Class<?> apiClass
                            = Class.forName(
                                    name.substring(0, name.length() - ".class".length()).replace('/', '.'),
                                    /* initialize= */ false,
                                    apiJarClassLoader);
                    if (Utils.isAccessible(apiClass)) {
                        apiClasses.add(apiClass);
                    }
                }
            }
            return apiClasses;
        }
    }

    /**
     * Default implementation, subclasses can override. If you override this
     * method, the implementations of {@link #getPrefixesToInclude()} and
     * {@link #getPrefixesToExclude()} will be ignored.
     *
     * @return A predicate that includes or excludes an entry in the api jar.
     */
    Predicate<String> getApiJarEntryNameFilter() {
        return name -> {
            for (String allowed : getPrefixesToInclude()) {
                if (name.startsWith(allowed)) {
                    for (String notAllowed : getPrefixesToExclude()) {
                        if (name.startsWith(notAllowed)) {
                            return false;
                        }
                    }
                    return true;
                }
            }
            return false;
        };
    }

    /**
     * @return Package prefixes to include. Only used by the default
     * implementation of {@link #getApiJarEntryNameFilter()}.
     */
    List<String> getPrefixesToInclude() {
        return ImmutableList.of();
    }

    /**
     * @return Package prefixes to exclude. This overrides prefixes returned by
     * {@link #getPrefixesToInclude()} and is only used by the default
     * implementation of {@link #getApiJarEntryNameFilter()}.
     */
    List<String> getPrefixesToExclude() {
        return ImmutableList.of();
    }
}
