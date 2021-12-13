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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.net.URI;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for the DatastoreCallbacksProcessor.  Rather than going nuts trying to
 * mock out half the {@link javax.model.element} package, we instead use the
 * Java Compiler API to compile real source and then validate our expectations
 * against the errors returned by the compiler.
 *
 */
@RunWith(JUnit4.class)
public class DatastoreCallbacksProcessorTest {

  private static final String ERRORSTEST = "ErrorsTest";

  @Test
  public void testPrePutErrors() throws IOException {
    String classBody =
          "  public ErrorsTest(String yar) {}\n"
        + "  @PrePut(kinds=\"yar\") public void wrongParams() {}\n"
        + "  @PrePut(kinds=\"yar\") public void alsoWrongParams("
              + "PutContext c1, PutContext c2) {}\n"
        + "  @PrePut(kinds=\"yar\") public void wrongGenericType(CallbackContext<String> c1) {}\n"
        + "  @PrePut(kinds=\"yar\") public String wrongReturnType("
              + "PutContext c) {return null;}\n"
        + "  @PrePut(kinds=\"yar\") public static void notInstanceMethod("
              + "PutContext c) {}\n"
        + "  @PrePut(kinds=\"yar\") public void throwsChecked(PutContext c)"
              + "throws Exception {}\n"
        + "  @PrePut(kinds=\" \") public void invalidKind(PutContext c) {}\n"
        + "  @PrePut(kinds={\"yar\", \"\"}) public void invalidKinds("
              + "PutContext c) {}\n"
        + "  @PrePut(kinds=\"yar\") @PostPut(kinds=\"yar\") public void multipleCallbacks("
              + "PutContext c) {}\n";

    runCompilationTest(classBody, errorList(
        "3: Datastore Callbacks: A class with a callback method must have a no-arg constructor.",
        "5: Datastore Callbacks: PrePut method must have a single argument of type "
            + "'com.google.appengine.api.datastore.PutContext'.",
        "6: Datastore Callbacks: PrePut method must have a single argument of type "
            + "'com.google.appengine.api.datastore.PutContext'.",
        "7: Datastore Callbacks: PrePut method must have a single argument of type "
            + "'com.google.appengine.api.datastore.PutContext'.",
        "8: Datastore Callbacks: Return type of callback method must be void.",
        "9: Datastore Callbacks: Callback method must not be static.",
        "10: Datastore Callbacks: Callback methods cannot throw checked exceptions.",
        "11: Datastore Callbacks: A callback cannot be associated with an empty kind.",
        "12: Datastore Callbacks: A callback cannot be associated with an empty kind.",
        "13: Datastore Callbacks: Method can only have one callback annotation."
    ));
  }

  @Test
  public void testPostPutErrors() throws IOException {
    String classBody =
          "  public ErrorsTest(String yar) {}\n"
        + "  @PostPut(kinds=\"yar\") public void wrongParams() {}\n"
        + "  @PostPut(kinds=\"yar\") public void alsoWrongParams("
              + "PutContext c1, PutContext c2) {}\n"
        + "  @PostPut(kinds=\"yar\") public void wrongGenericType("
              + "CallbackContext<String> c1) {}\n"
        + "  @PostPut(kinds=\"yar\") public String wrongReturnType("
              + "PutContext c) {return null;}\n"
        + "  @PostPut(kinds=\"yar\") public static void notInstanceMethod("
              + "PutContext c) {}\n"
        + "  @PostPut(kinds=\"yar\") public void throwsChecked(PutContext c)"
              + "throws Exception {}\n"
        + "  @PostPut(kinds=\" \") public void invalidKind(PutContext c) {}\n"
        + "  @PostPut(kinds={\"yar\", \"\"}) public void invalidKinds(PutContext c) {}\n"
        + "  @PostPut(kinds=\"yar\") @PrePut(kinds=\"yar\") public void multipleCallbacks("
              + "PutContext c) {}\n";

    runCompilationTest(classBody, errorList(
        "3: Datastore Callbacks: A class with a callback method must have a no-arg constructor.",
        "5: Datastore Callbacks: PostPut method must have a single argument of type "
            + "'com.google.appengine.api.datastore.PutContext'.",
        "6: Datastore Callbacks: PostPut method must have a single argument of type "
            + "'com.google.appengine.api.datastore.PutContext'.",
        "7: Datastore Callbacks: PostPut method must have a single argument of type "
            + "'com.google.appengine.api.datastore.PutContext'.",
        "8: Datastore Callbacks: Return type of callback method must be void.",
        "9: Datastore Callbacks: Callback method must not be static.",
        "10: Datastore Callbacks: Callback methods cannot throw checked exceptions.",
        "11: Datastore Callbacks: A callback cannot be associated with an empty kind.",
        "12: Datastore Callbacks: A callback cannot be associated with an empty kind.",
        "13: Datastore Callbacks: Method can only have one callback annotation."
    ));
  }

  @Test
  public void testPreDeleteErrors() throws IOException {
    String classBody =
          "  public ErrorsTest(String yar) {}\n"
        + "  @PreDelete(kinds=\"yar\") public void wrongParams() {}\n"
        + "  @PreDelete(kinds=\"yar\") public void alsoWrongParams("
              + "DeleteContext c1, DeleteContext c2) {}\n"
        + "  @PreDelete(kinds=\"yar\") public void wrongGenericType("
              + "CallbackContext<String> c1) {}\n"
        + "  @PreDelete(kinds=\"yar\") public String wrongReturnType("
              + "DeleteContext c) {return null;}\n"
        + "  @PreDelete(kinds=\"yar\") public static void notInstanceMethod("
              + "DeleteContext c) {}\n"
        + "  @PreDelete(kinds=\"yar\") public void throwsChecked("
              + "DeleteContext c) throws Exception {}\n"
        + "  @PreDelete(kinds=\" \") public void invalidKind(DeleteContext c) {}\n"
        + "  @PreDelete(kinds={\"yar\", \"\"}) public void invalidKinds(DeleteContext c) {}\n"
        + "  @PreDelete(kinds=\"yar\") @PostDelete(kinds=\"yar\") "
              + "public void multipleCallbacks(DeleteContext c) {}\n";

    runCompilationTest(classBody, errorList(
        "3: Datastore Callbacks: A class with a callback method must have a no-arg constructor.",
        "5: Datastore Callbacks: PreDelete method must have a single argument of type "
            + "'com.google.appengine.api.datastore.DeleteContext'.",
        "6: Datastore Callbacks: PreDelete method must have a single argument of type "
            + "'com.google.appengine.api.datastore.DeleteContext'.",
        "7: Datastore Callbacks: PreDelete method must have a single argument of type "
            + "'com.google.appengine.api.datastore.DeleteContext'.",
        "8: Datastore Callbacks: Return type of callback method must be void.",
        "9: Datastore Callbacks: Callback method must not be static.",
        "10: Datastore Callbacks: Callback methods cannot throw checked exceptions.",
        "11: Datastore Callbacks: A callback cannot be associated with an empty kind.",
        "12: Datastore Callbacks: A callback cannot be associated with an empty kind.",
        "13: Datastore Callbacks: Method can only have one callback annotation."
    ));
  }

  @Test
  public void testPostDeleteErrors() throws IOException {
    String classBody =
          "  public ErrorsTest(String yar) {}\n"
        + "  @PostDelete(kinds=\"yar\") public void wrongParams() {}\n"
        + "  @PostDelete(kinds=\"yar\") public void alsoWrongParams("
              + "DeleteContext c1, DeleteContext c2) {}\n"
        + "  @PostDelete(kinds=\"yar\") public void wrongGenericType("
              + "CallbackContext<String> c1) {}\n"
        + "  @PostDelete(kinds=\"yar\") public String wrongReturnType("
              + "DeleteContext c) {return null;}\n"
        + "  @PostDelete(kinds=\"yar\") public static void notInstanceMethod("
              + "DeleteContext c) {}\n"
        + "  @PostDelete(kinds=\"yar\") public void throwsChecked(DeleteContext c)"
              + "throws Exception {}\n"
        + "  @PostDelete(kinds=\" \") public void invalidKind(DeleteContext c) {}\n"
        + "  @PostDelete(kinds={\"yar\", \"\"}) public void invalidKinds(DeleteContext c) {}\n"
        + "  @PostDelete(kinds=\"yar\") @PreDelete(kinds=\"yar\") "
              + "public void multipleCallbacks(DeleteContext c) {}\n";

    runCompilationTest(classBody, errorList(
        "3: Datastore Callbacks: A class with a callback method must have a no-arg constructor.",
        "5: Datastore Callbacks: PostDelete method must have a single argument of type "
            + "'com.google.appengine.api.datastore.DeleteContext'.",
        "6: Datastore Callbacks: PostDelete method must have a single argument of type "
            + "'com.google.appengine.api.datastore.DeleteContext'.",
        "7: Datastore Callbacks: PostDelete method must have a single argument of type "
            + "'com.google.appengine.api.datastore.DeleteContext'.",
        "8: Datastore Callbacks: Return type of callback method must be void.",
        "9: Datastore Callbacks: Callback method must not be static.",
        "10: Datastore Callbacks: Callback methods cannot throw checked exceptions.",
        "11: Datastore Callbacks: A callback cannot be associated with an empty kind.",
        "12: Datastore Callbacks: A callback cannot be associated with an empty kind.",
        "13: Datastore Callbacks: Method can only have one callback annotation."
    ));
  }

  @Test
  public void testPreGetErrors() throws IOException {
    String classBody =
          "  public ErrorsTest(String yar) {}\n"
        + "  @PreGet(kinds=\"yar\") public void wrongParams() {}\n"
        + "  @PreGet(kinds=\"yar\") public void alsoWrongParams("
              + "PreGetContext c1, PreGetContext c2) {}\n"
        + "  @PreGet(kinds=\"yar\") public void wrongGenericType("
              + "CallbackContext<String> c1) {}\n"
        + "  @PreGet(kinds=\"yar\") public String wrongReturnType("
              + "PreGetContext c) {return null;}\n"
        + "  @PreGet(kinds=\"yar\") public static void notInstanceMethod("
              + "PreGetContext c) {}\n"
        + "  @PreGet(kinds=\"yar\") public void throwsChecked("
              + "PreGetContext c) throws Exception {}\n"
        + "  @PreGet(kinds=\" \") public void invalidKind(PreGetContext c) {}\n"
        + "  @PreGet(kinds={\"yar\", \"\"}) public void invalidKinds(PreGetContext c) {}\n";

    runCompilationTest(classBody, errorList(
        "3: Datastore Callbacks: A class with a callback method must have a no-arg constructor.",
        "5: Datastore Callbacks: PreGet method must have a single argument of type "
            + "'com.google.appengine.api.datastore.PreGetContext'.",
        "6: Datastore Callbacks: PreGet method must have a single argument of type "
            + "'com.google.appengine.api.datastore.PreGetContext'.",
        "7: Datastore Callbacks: PreGet method must have a single argument of type "
            + "'com.google.appengine.api.datastore.PreGetContext'.",
        "8: Datastore Callbacks: Return type of callback method must be void.",
        "9: Datastore Callbacks: Callback method must not be static.",
        "10: Datastore Callbacks: Callback methods cannot throw checked exceptions.",
        "11: Datastore Callbacks: A callback cannot be associated with an empty kind.",
        "12: Datastore Callbacks: A callback cannot be associated with an empty kind."
    ));
  }

  @Test
  public void testPostLoadErrors() throws IOException {
    String classBody =
          "  public ErrorsTest(String yar) {}\n"
        + "  @PostLoad(kinds=\"yar\") public void wrongParams() {}\n"
        + "  @PostLoad(kinds=\"yar\") public void alsoWrongParams("
              + "PostLoadContext c1, PostLoadContext c2) {}\n"
        + "  @PostLoad(kinds=\"yar\") public void wrongGenericType("
              + "CallbackContext<String> c1) {}\n"
        + "  @PostLoad(kinds=\"yar\") public String wrongReturnType("
              + "PostLoadContext c) {return null;}\n"
        + "  @PostLoad(kinds=\"yar\") public static void notInstanceMethod("
              + "PostLoadContext c) {}\n"
        + "  @PostLoad(kinds=\"yar\") public void throwsChecked(PostLoadContext c)"
              + "throws Exception {}\n"
        + "  @PostLoad(kinds=\" \") public void invalidKind(PostLoadContext c) {}\n"
        + "  @PostLoad(kinds={\"yar\", \"\"}) public void invalidKinds(PostLoadContext c) {}\n";

    runCompilationTest(classBody, errorList(
        "3: Datastore Callbacks: A class with a callback method must have a no-arg constructor.",
        "5: Datastore Callbacks: PostLoad method must have a single argument of type "
            + "'com.google.appengine.api.datastore.PostLoadContext'.",
        "6: Datastore Callbacks: PostLoad method must have a single argument of type "
            + "'com.google.appengine.api.datastore.PostLoadContext'.",
        "7: Datastore Callbacks: PostLoad method must have a single argument of type "
            + "'com.google.appengine.api.datastore.PostLoadContext'.",
        "8: Datastore Callbacks: Return type of callback method must be void.",
        "9: Datastore Callbacks: Callback method must not be static.",
        "10: Datastore Callbacks: Callback methods cannot throw checked exceptions.",
        "11: Datastore Callbacks: A callback cannot be associated with an empty kind.",
        "12: Datastore Callbacks: A callback cannot be associated with an empty kind."
    ));
  }

  @Test
  public void testPreQueryErrors() throws IOException {
    String classBody =
          "  public ErrorsTest(String yar) {}\n"
        + "  @PreQuery(kinds=\"yar\") public void wrongParams() {}\n"
        + "  @PreQuery(kinds=\"yar\") public void alsoWrongParams("
              + "PreQueryContext c1, PreQueryContext c2) {}\n"
        + "  @PreQuery(kinds=\"yar\") public void wrongGenericType("
              + "CallbackContext<String> c1) {}\n"
        + "  @PreQuery(kinds=\"yar\") public String wrongReturnType("
              + "PreQueryContext c) {return null;}\n"
        + "  @PreQuery(kinds=\"yar\") public static void notInstanceMethod("
              + "PreQueryContext c) {}\n"
        + "  @PreQuery(kinds=\"yar\") public void throwsChecked("
              + "PreQueryContext c) throws Exception {}\n"
        + "  @PreQuery(kinds=\" \") public void invalidKind(PreQueryContext c) {}\n"
        + "  @PreQuery(kinds={\"yar\", \"\"}) public void invalidKinds(PreQueryContext c) {}\n";

    runCompilationTest(classBody, errorList(
        "3: Datastore Callbacks: A class with a callback method must have a no-arg constructor.",
        "5: Datastore Callbacks: PreQuery method must have a single argument of type "
            + "'com.google.appengine.api.datastore.PreQueryContext'.",
        "6: Datastore Callbacks: PreQuery method must have a single argument of type "
            + "'com.google.appengine.api.datastore.PreQueryContext'.",
        "7: Datastore Callbacks: PreQuery method must have a single argument of type "
            + "'com.google.appengine.api.datastore.PreQueryContext'.",
        "8: Datastore Callbacks: Return type of callback method must be void.",
        "9: Datastore Callbacks: Callback method must not be static.",
        "10: Datastore Callbacks: Callback methods cannot throw checked exceptions.",
        "11: Datastore Callbacks: A callback cannot be associated with an empty kind.",
        "12: Datastore Callbacks: A callback cannot be associated with an empty kind."
    ));
  }

  @Test
  public void testOneVerifierPerRegisteredAnnotation() throws ClassNotFoundException {
    // This test will fail if we add support for a new annotation but forget to
    // create and register a callback verifier for it.
    SupportedAnnotationTypes sat =
        DatastoreCallbacksProcessor.class.getAnnotation(SupportedAnnotationTypes.class);
    DatastoreCallbacksProcessor processor = new DatastoreCallbacksProcessor();
    assertEquals(sat.value().length, processor.callbackVerifiers.size());

    for (String annoClassStr : sat.value()) {
      @SuppressWarnings("unchecked")
      Class<? extends Annotation> annoClass =
          (Class<? extends Annotation>) Class.forName(annoClassStr);
      assertTrue(processor.callbackVerifiers.containsKey(annoClass));
    }
  }

  @Test
  public void testConfigOutput() throws IOException {
    // This is our only real end-to-end success test.
    String classBody =
          "  @PrePut(kinds=\"yar\") public void justRight1(PutContext c) {}\n"
        + "  @PrePut(kinds={\"yar\", \"yam\"}) public void justRight2(PutContext c) {}\n"
        + "  @PrePut public void justRight3(PutContext c) {}\n"
        + "  @PrePut(kinds=\"yar\") public void justRight4(PutContext c) "
              + "throws RuntimeException, NullPointerException, OutOfMemoryError {}\n"
        + "  @PostPut(kinds=\"yar\") public void justRight5(PutContext c) {}\n"
        + "  @PostPut(kinds={\"yar\", \"yam\"}) public void justRight6(PutContext c) {}\n"
        + "  @PostPut public void justRight7(PutContext c) {}\n"
        + "  @PostPut(kinds=\"yar\") public void justRight8(PutContext c) "
              + "throws RuntimeException, NullPointerException, OutOfMemoryError {}\n"
        + "  @PreDelete(kinds=\"yar\") public void justRight9(DeleteContext c) {}\n"
        + "  @PreDelete(kinds={\"yar\", \"yam\"}) public void justRight10(DeleteContext c) {}\n"
        + "  @PreDelete public void justRight11(DeleteContext c) {}\n"
        + "  @PreDelete(kinds=\"yar\") public void justRight12(DeleteContext c) "
              + "throws RuntimeException, NullPointerException, OutOfMemoryError {}\n"
        + "  @PostDelete(kinds=\"yar\") public void justRight13(DeleteContext c) {}\n"
        + "  @PostDelete(kinds={\"yar\", \"yam\"}) public void justRight14(DeleteContext c) {}\n"
        + "  @PostDelete public void justRight15(DeleteContext c) {}\n"
        + "  @PostDelete(kinds=\"yar\") public void justRight16(DeleteContext c) "
              + "throws RuntimeException, NullPointerException, OutOfMemoryError {}\n"
        + "  @PreGet(kinds=\"yar\") public void justRight17(PreGetContext c) {}\n"
        + "  @PreGet(kinds={\"yar\", \"yam\"}) public void justRight18(PreGetContext c) {}\n"
        + "  @PreGet public void justRight19(PreGetContext c) {}\n"
        + "  @PreGet(kinds=\"yar\") public void justRight20(PreGetContext c) "
              + "throws RuntimeException, NullPointerException, OutOfMemoryError {}\n"
        + "  @PostLoad(kinds=\"yar\") public void justRight21(PostLoadContext c) {}\n"
        + "  @PostLoad(kinds={\"yar\", \"yam\"}) public void justRight22(PostLoadContext c) {}\n"
        + "  @PostLoad public void justRight23(PostLoadContext c) {}\n"
        + "  @PostLoad(kinds=\"yar\") public void justRight24(PostLoadContext c) "
              + "throws RuntimeException, NullPointerException, OutOfMemoryError {}\n"
        + "  @PreQuery(kinds=\"yar\") public void justRight25(PreQueryContext c) {}\n"
        + "  @PreQuery(kinds={\"yar\", \"yam\"}) public void justRight26(PreQueryContext c) {}\n"
        + "  @PreQuery public void justRight27(PreQueryContext c) {}\n"
        + "  @PreQuery(kinds=\"yar\") public void justRight28(PreQueryContext c) "
             + "throws RuntimeException, NullPointerException, OutOfMemoryError {}\n";

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    runCompilationTest(classBody, Collections.<String>emptyList(), baos);
    Properties props = new Properties();
    InputStream inputStream = new ByteArrayInputStream(baos.toByteArray());
    props.loadFromXML(inputStream);
    Properties expected = new Properties();
    expected.setProperty(DatastoreCallbacksConfigWriter.FORMAT_VERSION_PROPERTY, "1");

    expected.setProperty("yar.PrePut",
        "ErrorsTest:justRight1,ErrorsTest:justRight2,ErrorsTest:justRight4");
    expected.setProperty("yar.PostPut",
        "ErrorsTest:justRight5,ErrorsTest:justRight6,ErrorsTest:justRight8");
    expected.setProperty("yar.PreDelete",
        "ErrorsTest:justRight9,ErrorsTest:justRight10,ErrorsTest:justRight12");
    expected.setProperty("yar.PostDelete",
        "ErrorsTest:justRight13,ErrorsTest:justRight14,ErrorsTest:justRight16");
    expected.setProperty("yar.PreGet",
        "ErrorsTest:justRight17,ErrorsTest:justRight18,ErrorsTest:justRight20");
    expected.setProperty("yar.PostLoad",
        "ErrorsTest:justRight21,ErrorsTest:justRight22,ErrorsTest:justRight24");
    expected.setProperty("yar.PreQuery",
        "ErrorsTest:justRight25,ErrorsTest:justRight26,ErrorsTest:justRight28");

    expected.setProperty("yam.PrePut", "ErrorsTest:justRight2");
    expected.setProperty("yam.PostPut", "ErrorsTest:justRight6");
    expected.setProperty("yam.PreDelete", "ErrorsTest:justRight10");
    expected.setProperty("yam.PostDelete", "ErrorsTest:justRight14");
    expected.setProperty("yam.PreGet", "ErrorsTest:justRight18");
    expected.setProperty("yam.PostLoad", "ErrorsTest:justRight22");
    expected.setProperty("yam.PreQuery", "ErrorsTest:justRight26");

    expected.setProperty(".PrePut", "ErrorsTest:justRight3");
    expected.setProperty(".PostPut", "ErrorsTest:justRight7");
    expected.setProperty(".PreDelete", "ErrorsTest:justRight11");
    expected.setProperty(".PostDelete", "ErrorsTest:justRight15");
    expected.setProperty(".PreGet", "ErrorsTest:justRight19");
    expected.setProperty(".PostLoad", "ErrorsTest:justRight23");
    expected.setProperty(".PreQuery", "ErrorsTest:justRight27");
    assertEquals(expected, props);
  }

  private void runCompilationTest(String classBody, List<String> expectedErrors)
      throws IOException {
    runCompilationTest(classBody, expectedErrors, null);
  }

  private void runCompilationTest(String classBody, List<String> expectedErrors,
      final OutputStream configOutputStream) throws IOException {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    DiagnosticCollector<JavaFileObject> diagnosticCollector =
        new DiagnosticCollector<JavaFileObject>();
    StandardJavaFileManager fileManager = compiler.getStandardFileManager(
        diagnosticCollector, null, null);
    Iterable<? extends JavaFileObject> compilationUnit =
        Arrays.asList(new JavaSourceFromString(classBody));
    fileManager.setLocation(
        StandardLocation.CLASS_OUTPUT,
        Arrays.asList(Files.createTempDirectory("compiler_output").toFile()));
    JavaCompiler.CompilationTask task = compiler.getTask(
        null, fileManager, diagnosticCollector, null, null, compilationUnit);
    task.setProcessors(Arrays.asList(new DatastoreCallbacksProcessor(configOutputStream)));
    // if no expected errors then we expect compilation to succeed
    assertEquals(expectedErrors.isEmpty(), task.call());
    List<Diagnostic<? extends JavaFileObject>> diagnostics = diagnosticCollector.getDiagnostics();
    List<String> diagnosticStrings = new ArrayList<String>();
    Locale locale = Locale.getDefault();
    for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics) {
      StringBuilder diag = new StringBuilder();
      // The source name starts with the '/' prefix.  Strip it off so
      // that the expected strings don't need to deal with them.
      diag.append(diagnostic.getSource().getName().substring(1));
      diag.append(":");
      diag.append(diagnostic.getLineNumber());
      diag.append(": ");
      diag.append(diagnostic.getMessage(locale));
      diag.append("\n");
      diagnosticStrings.add(diag.toString());
    }
    assertEquals(expectedErrors, diagnosticStrings);
    fileManager.close();

  }

  /**
   * Decorate the error messages provided by the test with boilerplate prefix
   * and suffix to keep the test code concise.
   */
  private static List<String> errorList(String... errors) {
    List<String> errorList = new ArrayList<String>(errors.length);
    for (String error : errors) {
      errorList.add(ERRORSTEST + ".java:" + error + "\n");
    }
    return errorList;
  }

  /**
   * A file object used to represent source coming from a string.
   * Based closely on http://download.oracle.com/javase/6/docs/api/javax/tools/JavaCompiler.html
   */
  private static class JavaSourceFromString extends SimpleJavaFileObject {

    private static final String CLASS_TEMPLATE =
        "import com.google.appengine.api.datastore.*;\n"
      + "\n"
      + "public class " + ERRORSTEST + " {\n"
      + "%s\n"
      + "}";

    /**
     * The source code of this "file".
     */
    final String classBody;

    /**
     * Constructs a new JavaSourceFromString.
     *
     * @param classForBody the source code for the body of the class.
     * Everything else (imports, class declaration, open curly, close curly) is
     * provided by this class.
     */
    JavaSourceFromString(String classForBody) {
      super(URI.create("string:///" + ERRORSTEST + Kind.SOURCE.extension), Kind.SOURCE);
      this.classBody = String.format(CLASS_TEMPLATE, classForBody);
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
      return classBody;
    }
  }
}
