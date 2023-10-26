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

import static com.google.appengine.apicompat.Utils.classes;
import static com.google.appengine.apicompat.Utils.ctors;
import static com.google.appengine.apicompat.Utils.fields;
import static com.google.appengine.apicompat.Utils.getDeclaredConstructors;
import static com.google.appengine.apicompat.Utils.getDeclaredFields;
import static com.google.appengine.apicompat.Utils.getDeclaredMethods;
import static com.google.appengine.apicompat.Utils.methods;
import static org.junit.Assert.fail;

import com.google.common.base.Predicate;
import java.io.Serializable;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class UsageTrackingClassLoaderTest {

  public static class ApiClass {

    public ApiClass() {
    }

    protected ApiClass(String s) {
    }

    public void publicNoArgsMethod() {
    }

    public void public1ArgMethod(String s) {
    }

    protected void protectedMethod() {
    }

    public static void publicStaticMethod() {
    }

    protected static void protectedStaticMethod() {
    }

    public String publicField;
    protected String protectedField;

    public static String publicStaticField;
    protected static String protectedStaticField;

    public static final String CONSTANT = "foo";
  }

  public static class ApiClassUsage extends ExhaustiveApiUsage<ApiClass> {

    String ___apiConstant_CONSTANT;

    public static class ApiClassChild extends ApiClass {

      public ApiClassChild(String s) {
        super(s);
        protectedStaticMethod();
        protectedMethod();
        String val = ApiClass.protectedStaticField;
        val = protectedField;
      }
    }

    @Override
    public Set<Class<?>> useApi() {
      ApiClass.publicStaticMethod();
      ApiClass ac = new ApiClass();
      ac = new ApiClassChild("yar");
      ac.publicNoArgsMethod();
      ac.public1ArgMethod("yar");

      String val = ApiClass.publicStaticField;
      val = ac.publicField;
      ___apiConstant_CONSTANT = ApiClass.CONSTANT;
      return classes(ApiClass.class);
    }
  }

  @Test
  public void testUsageTracking_ApiClass() throws Exception {
    useApi(ApiClass.class, ApiClassUsage.class);
    Api expected = new Api(
        ApiClass.class,
        classes(),
        getDeclaredConstructors(ApiClass.class, null, String.class),
        getDeclaredMethods(
            ApiClass.class,
            "publicStaticMethod",
            "protectedStaticMethod",
            "publicNoArgsMethod",
            "public1ArgMethod", String.class,
            "protectedMethod"),
        getDeclaredFields(
            ApiClass.class,
            "publicStaticField",
            "protectedStaticField",
            "publicField",
            "protectedField",
            "CONSTANT"));
    expected.getConstructors().addAll(
        getDeclaredConstructors(ApiClassUsage.ApiClassChild.class, String.class));
    assertExpectedApi(expected, UsageTracker.getExercisedApi());
  }

  public interface ApiInterface {

    void interfaceMethod();

    String CONSTANT = "foo";
  }

  public static class ApiInterfaceUsage extends ExhaustiveApiInterfaceUsage<ApiInterface> {

    String ___apiConstant_CONSTANT;

    @Override
    protected Set<Class<?>> useApi(ApiInterface obj) {
      obj.interfaceMethod();
      ___apiConstant_CONSTANT = ApiInterface.CONSTANT;
      return classes();
    }
  }

  @Test
  public void testUsageTracking_ApiInterface() throws Exception {
    useApi(ApiInterface.class, ApiInterfaceUsage.class);
    Api expected = new Api(
        ApiInterface.class,
        classes(),
        ctors(),
        getDeclaredMethods(ApiInterface.class, "interfaceMethod"),
        getDeclaredFields(ApiInterface.class, "CONSTANT"));
    assertExpectedApi(expected, UsageTracker.getExercisedApi());
  }

  public static enum ApiEnum {
    ONE, TWO;

    public void foo() {
    }
  }

  public static class ApiEnumUsage extends ExhaustiveApiUsage<ApiEnum> {

    @Override
    public Set<Class<?>> useApi() {
      ApiEnum obj = ApiEnum.ONE;
      obj = ApiEnum.TWO;
      obj.foo();
      return classes(Enum.class, Comparable.class, Serializable.class);
    }
  }

  @Test
  public void testUsageTracking_ApiEnum() throws Exception {
    useApi(ApiEnum.class, ApiEnumUsage.class);
    Api expected = new Api(
        ApiEnum.class,
        classes(),
        ctors(),
        getDeclaredMethods(ApiEnum.class, "foo"),
        getDeclaredFields(ApiEnum.class, "ONE", "TWO"));
    assertExpectedApi(expected, UsageTracker.getExercisedApi());
  }

  public @interface ApiAnnotation {

    String foo();

    String CONSTANT = "foo";
  }

  public static class ApiAnnotationUsage extends ExhaustiveApiInterfaceUsage<ApiAnnotation> {

    String ___apiConstant_CONSTANT;

    @Override
    protected Set<Class<?>> useApi(ApiAnnotation theInterface) {
      theInterface.foo();
      ___apiConstant_CONSTANT = ApiAnnotation.CONSTANT;
      return classes();
    }
  }

  @Test
  public void testUsageTracking_ApiAnnotation() throws Exception {
    useApi(ApiAnnotation.class, ApiAnnotationUsage.class);
    Api expected = new Api(
        ApiAnnotation.class,
        classes(),
        ctors(),
        getDeclaredMethods(ApiAnnotation.class, "foo"),
        getDeclaredFields(ApiAnnotation.class, "CONSTANT"));
    assertExpectedApi(expected, UsageTracker.getExercisedApi());
  }

  public static class ApiWithInnerClass {

    public class InnerClass {

      @SuppressWarnings("unused")
      public InnerClass(String s) {
      }

      public class InnerInnerClass {

        public InnerInnerClass() {
        }
      }
    }
  }

  public static class InnerClassUsage extends ExhaustiveApiUsage<ApiWithInnerClass.InnerClass> {

    @Override
    public Set<Class<?>> useApi() {
      ApiWithInnerClass.InnerClass.InnerInnerClass unused =
          new ApiWithInnerClass().new InnerClass("yar").new InnerInnerClass();
      return classes(Object.class);
    }
  }

  @Test
  public void testUsageTracking_InnerClass() throws Exception {
    useApi(ApiWithInnerClass.InnerClass.class, InnerClassUsage.class);
    Api expected = new Api(
        ApiWithInnerClass.InnerClass.class,
        classes(),
        ctors(ApiWithInnerClass.InnerClass.class
            .getDeclaredConstructor(ApiWithInnerClass.class, String.class)),
        methods(),
        fields());
    assertExpectedApi(expected, UsageTracker.getExercisedApi());
  }

  private void useApi(Class<?> apiClass, final Class<? extends ExhaustiveApiUsage<?>> usageClass)
      throws Exception {
    Api api = new Api(apiClass, classes(), ctors(), methods(), fields());
    UsageTracker.setExercisedApi(api);
    Predicate<String> isUsageClass = new Predicate<String>() {
      @Override
      public boolean apply(String clsName) {
        // Instrument the usage class or any class enclosed in the usage class.
        try {
          Class<?> cls = Class.forName(clsName);
          return usageClass.equals(cls) || usageClass.equals(cls.getEnclosingClass());
        } catch (ClassNotFoundException e) {
          throw new RuntimeException(e);
        }
      }
    };
    UsageTrackingClassLoader loader = new UsageTrackingClassLoader(apiClass, isUsageClass);
    Object obj =
        Class.forName(usageClass.getName(), true, loader).getDeclaredConstructor().newInstance();
    obj.getClass().getMethod("useApi").invoke(obj);
  }

  private void assertExpectedApi(Api expected, Api actual) {
    ApiComparison result = new ApiComparison(expected, actual);
    if (result.hasDifference()) {
      fail(result.toString());
    }
  }
}
