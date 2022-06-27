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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.google.appengine.apicompat.testclasses.HasInnerClass;
import com.google.appengine.apicompat.testclasses.InterfaceWithEnum;
import com.google.appengine.apicompat.testclasses.MyAnnotation;
import com.google.appengine.apicompat.testclasses.MyComparator;
import com.google.appengine.apicompat.testclasses.MyEnum;
import com.google.appengine.apicompat.testclasses.PublicAbstractClassExtendingPkgProtectedClass;
import com.google.appengine.apicompat.testclasses.PublicAbstractClassImplementingPkgProtectedInterface;
import com.google.appengine.apicompat.testclasses.PublicBase;
import com.google.appengine.apicompat.testclasses.PublicClassExtendingPkgProtectedClass;
import com.google.appengine.apicompat.testclasses.PublicClassImplementingPkgProtectedInterface;
import com.google.appengine.apicompat.testclasses.PublicFinalSub;
import com.google.appengine.apicompat.testclasses.PublicInterface;
import com.google.appengine.apicompat.testclasses.PublicSub;
import com.google.appengine.apicompat.testclasses.PublicSubWithAppEngineInternal;
import com.google.appengine.apicompat.testclasses.TemplatizedInterface;
import com.google.appengine.apicompat.testclasses.TemplatizedInterfaceImpl;
import com.google.appengine.apicompat.testclasses.p1.PkgWithAppEngineInternal;
import com.google.common.collect.Lists;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@SuppressWarnings("unchecked")
@RunWith(JUnit4.class)
public class AccessibleApiDetectorTest {

  private static final Class<?> PKG_PROTECTED_INTERFACE;
  private static final Class<?> PKG_PROTECTED_CLASS;

  static {
    try {
      PKG_PROTECTED_INTERFACE = Class.forName(
          PublicAbstractClassImplementingPkgProtectedInterface.class.getPackage().getName()
              + ".PkgProtectedInterface");
      PKG_PROTECTED_CLASS = Class.forName(
          PublicAbstractClassImplementingPkgProtectedInterface.class.getPackage().getName()
              + ".PkgProtectedClass");
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  private final AccessibleApiDetector detector = new AccessibleApiDetector();

  @Test
  public void testPublicSub() throws Exception {
    Api detected = detector.detect(PublicSub.class);
    Api expected = new Api(
        PublicSub.class,
        classes(Object.class, PublicBase.class, PublicInterface.class),
        getDeclaredConstructors(PublicSub.class, String.class),
        getDeclaredMethods(PublicSub.class, "foo1", "foo3", "staticFoo3"),
        getDeclaredFields(PublicSub.class, "foo1", "foo3", "staticFoo3"));
    assertSameApi(detected, expected);
  }

  @Test
  public void testPublicSubWithAppEngineInternal() throws Exception {
    Api detected = detector.detect(PublicSubWithAppEngineInternal.class);
    Api expected = new Api(
        PublicSubWithAppEngineInternal.class,
        Collections.<Class<?>>emptySet(),
        Collections.<Constructor<?>>emptySet(),
        Collections.<Method>emptySet(),
        Collections.<Field>emptySet());
    assertSameApi(detected, expected);
  }
  
  @Test
  public void testPublicWithAppEngineInternalPackage() throws Exception {
    Api detected = detector.detect(PkgWithAppEngineInternal.class);
    Api expected = new Api(
        PkgWithAppEngineInternal.class,
        Collections.<Class<?>>emptySet(),
        Collections.<Constructor<?>>emptySet(),
        Collections.<Method>emptySet(),
        Collections.<Field>emptySet());
    assertSameApi(detected, expected);
  }

  @Test
  public void testPublicBase() throws Exception {
    Api detected = detector.detect(PublicBase.class);
    Api expected = new Api(
        PublicBase.class,
        classes(Object.class, PublicInterface.class),
        getDeclaredConstructors(PublicBase.class, String.class),
        getDeclaredMethods(PublicBase.class,
            "foo1", "foo2", "protectedStaticFoo2", "publicStaticFoo2"),
        getDeclaredFields(PublicBase.class, "foo1", "foo2", "protectedStaticFoo2",
            "publicStaticFoo2"));
    assertSameApi(detected, expected);
  }

  @Test
  public void testPublicInterface() throws Exception {
    Api detected = detector.detect(PublicInterface.class);
    Api expected = new Api(
        PublicInterface.class,
        classes(),
        ctors(),
        getDeclaredMethods(PublicInterface.class, "foo1"),
        getDeclaredFields(PublicInterface.class, "FOO1"));
    assertSameApi(detected, expected);
  }

  @Test
  public void testFinalClass() throws Exception {
    Api detected = detector.detect(PublicFinalSub.class);
    Api expected = new Api(
        PublicFinalSub.class,
        classes(Object.class, PublicInterface.class, PublicBase.class),
        getDeclaredConstructors(PublicFinalSub.class, String.class),
        methods(),
        fields());
    assertSameApi(detected, expected);
  }

  @Test
  public void testPublicClassImplementingPkgProtectedInterface() throws Exception {
    Api detected = detector.detect(PublicClassImplementingPkgProtectedInterface.class);
    Api expected = new Api(
        PublicClassImplementingPkgProtectedInterface.class,
        classes(Object.class, PKG_PROTECTED_INTERFACE),
        getDeclaredConstructors(PublicClassImplementingPkgProtectedInterface.class,
            null, String.class),
        getDeclaredMethods(PublicClassImplementingPkgProtectedInterface.class, "foo1", "foo2"),
        getDeclaredFields(PublicClassImplementingPkgProtectedInterface.class, "foo1", "foo2"));
    assertSameApi(detected, expected);
  }

  @Test
  public void testPublicAbstractClassImplementingPkgProtectedInterface() throws Exception {
    Api detected = detector.detect(PublicAbstractClassImplementingPkgProtectedInterface.class);
    Api expected = new Api(
        PublicAbstractClassImplementingPkgProtectedInterface.class,
        classes(Object.class, PKG_PROTECTED_INTERFACE),
        getDeclaredConstructors(PublicAbstractClassImplementingPkgProtectedInterface.class),
        methods(),
        fields());
    assertSameApi(detected, expected);
  }

  @Test
  public void testPublicClassExtendingPkgProtectedClass() throws Exception {
    Api detected = detector.detect(PublicClassExtendingPkgProtectedClass.class);
    Api expected = new Api(
        PublicClassExtendingPkgProtectedClass.class,
        classes(Object.class, PKG_PROTECTED_CLASS),
        getDeclaredConstructors(PublicClassExtendingPkgProtectedClass.class,
            null, String.class),
        getDeclaredMethods(PublicClassExtendingPkgProtectedClass.class, "foo1", "foo2"),
        getDeclaredFields(PublicClassExtendingPkgProtectedClass.class, "foo1", "foo2"));
    assertSameApi(detected, expected);
  }

  @Test
  public void testPublicAbstractClassExtendingPkgProtectedClass() throws Exception {
    Api detected = detector.detect(PublicAbstractClassExtendingPkgProtectedClass.class);
    Api expected = new Api(
        PublicAbstractClassExtendingPkgProtectedClass.class,
        classes(Object.class, PKG_PROTECTED_CLASS),
        getDeclaredConstructors(PublicAbstractClassExtendingPkgProtectedClass.class),
        methods(),
        fields());
    assertSameApi(detected, expected);
  }

  @Test
  public void testTemplatizedInterface() throws Exception {
    Api detected = detector.detect(TemplatizedInterface.class);
    Api expected = new Api(
        TemplatizedInterface.class,
        classes(),
        ctors(),
        getDeclaredMethods(TemplatizedInterface.class, "foo", Object.class),
        fields());
    assertSameApi(detected, expected);
  }

  @Test
  public void testTemplatizedInterfaceImpl() throws Exception {
    Api detected = detector.detect(TemplatizedInterfaceImpl.class);
    Api expected = new Api(
        TemplatizedInterfaceImpl.class, classes(Object.class, TemplatizedInterface.class),
        getDeclaredConstructors(TemplatizedInterfaceImpl.class),
        getDeclaredMethods(TemplatizedInterfaceImpl.class, "foo", String.class),
        fields());
    assertSameApi(detected, expected);
  }

  @Test
  public void testEnum() throws Exception {
    Api detected = detector.detect(MyEnum.class);
    Api expected = new Api(
        MyEnum.class,
        classes(Object.class, Enum.class, Serializable.class, Comparable.class),
        ctors(),
        getDeclaredMethods(MyEnum.class, "foo", "values", "valueOf", String.class),
        getDeclaredFields(MyEnum.class, "ONE", "TWO"));

    assertSameApi(detected, expected);
  }

  @Test
  public void testAnnotation() throws Exception {
    Api detected = detector.detect(MyAnnotation.class);
    Api expected = new Api(
        MyAnnotation.class,
        classes(Annotation.class),
        ctors(),
        getDeclaredMethods(MyAnnotation.class, "foo"),
        fields());

    assertSameApi(detected, expected);
  }

  @Test
  public void testComparator() throws Exception {
    Api detected = detector.detect(MyComparator.class);
    Api expected = new Api(
        MyComparator.class,
        classes(Object.class, Comparator.class),
        getDeclaredConstructors(MyComparator.class),
        getDeclaredMethods(MyComparator.class, "compare",
            Arrays.asList(String.class, String.class)),
        fields());

    assertSameApi(detected, expected);
  }

  @Test
  public void testInterfaceWithEnum() throws Exception {
    Api detected = detector.detect(InterfaceWithEnum.class);
    Api expected = new Api(
        InterfaceWithEnum.class,
        classes(),
        ctors(),
        methods(),
        getDeclaredFields(InterfaceWithEnum.class, "CONSTANT"));

    assertSameApi(detected, expected);
  }

  @Test
  public void testHasInnerClass() throws Exception {
    Api detected = detector.detect(HasInnerClass.class);
    Api expected = new Api(
        HasInnerClass.class,
        classes(Object.class),
        getDeclaredConstructors(HasInnerClass.class),
        methods(),
        fields());

    assertSameApi(detected, expected);

    detected = detector.detect(HasInnerClass.InnerClass.class);
    expected = new Api(
        HasInnerClass.InnerClass.class,
        classes(Object.class),
        getDeclaredConstructors(HasInnerClass.InnerClass.class,
            Lists.newArrayList(HasInnerClass.class, String.class)),
        methods(),
        fields());

    assertSameApi(detected, expected);

    detected = detector.detect(HasInnerClass.InnerClass.InnerInnerClass.class);
    expected = new Api(
        HasInnerClass.InnerClass.InnerInnerClass.class,
        classes(Object.class),
        getDeclaredConstructors(HasInnerClass.InnerClass.InnerInnerClass.class,
            HasInnerClass.InnerClass.class),
        methods(),
        fields());

    assertSameApi(detected, expected);

    detected = detector.detect(HasInnerClass.InnerClassWithAppEngineInternal.class);
    expected = new Api(
        HasInnerClass.InnerClassWithAppEngineInternal.class,
        Collections.<Class<?>>emptySet(),
        Collections.<Constructor<?>>emptySet(),
        Collections.<Method>emptySet(),
        Collections.<Field>emptySet());

    assertSameApi(detected, expected);  
  }

  interface Interface1 {

  }

  interface Interface2 extends Serializable {

  }

  interface Interface3 extends Comparable<Interface3>, Serializable {}

  static class Class1 implements Interface1 {

  }

  static class Class2 extends Class1 implements Interface2 {

  }

  abstract static class Class3 extends Class2 implements Cloneable, Interface3 {}

  @Test
  public void testPopulateInheritanceSet() {
    Set<Class<?>> set = classes();

    AccessibleApiDetector.populateInheritanceSet(Interface1.class, set);
    assertEquals(classes(), set);

    AccessibleApiDetector.populateInheritanceSet(Interface2.class, set);
    assertEquals(classes(Serializable.class), set);
    set.clear();

    AccessibleApiDetector.populateInheritanceSet(Interface3.class, set);
    assertEquals(classes(Serializable.class, Comparable.class), set);
    set.clear();

    AccessibleApiDetector.populateInheritanceSet(Class1.class, set);
    assertEquals(classes(Interface1.class, Object.class), set);
    set.clear();

    AccessibleApiDetector.populateInheritanceSet(Class2.class, set);
    assertEquals(classes(Interface1.class, Interface2.class, Class1.class, Object.class,
        Serializable.class), set);
    set.clear();

    AccessibleApiDetector.populateInheritanceSet(Class3.class, set);
    assertEquals(classes(Interface1.class, Interface2.class, Interface3.class, Class1.class,
        Class2.class, Object.class, Serializable.class, Comparable.class,
        Cloneable.class), set);
    set.clear();
  }

  private void assertSameApi(Api detected, Api expected) {
    ApiComparison result = new ApiComparison(detected, expected);
    if (result.hasDifference()) {
      fail(result.toString());
    }
  }
}
