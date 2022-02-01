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

import com.google.appengine.api.datastore.CallbackContext;
import com.google.appengine.api.datastore.DeleteContext;
import com.google.appengine.api.datastore.PostDelete;
import com.google.appengine.api.datastore.PostLoad;
import com.google.appengine.api.datastore.PostLoadContext;
import com.google.appengine.api.datastore.PostPut;
import com.google.appengine.api.datastore.PreDelete;
import com.google.appengine.api.datastore.PreGet;
import com.google.appengine.api.datastore.PreGetContext;
import com.google.appengine.api.datastore.PrePut;
import com.google.appengine.api.datastore.PreQuery;
import com.google.appengine.api.datastore.PreQueryContext;
import com.google.appengine.api.datastore.PutContext;
import com.google.auto.common.MoreElements;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic.Kind;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

/**
 * Processes datastore callback annotations (
 * {@link PrePut}, {@link PostPut}, {@link PreDelete}, {@link PostDelete},
 * {@link PreGet}, {@link com.google.appengine.api.datastore.PostLoad}, {@link PreQuery}) and
 * generates a config file that the Datastore API can load at runtime.  Each
 * line of the config file is of the format: <br>
 * kind.callback_type=comma-separated list of methods<br>
 * where 'kind' is the kind of the entity to which the config on that line
 * applies, 'callback_type' is one of PrePut, PostPut, PreDelete, PostDelete,
 * and each entry in the comma-separated list of methods is a colon-delimited
 * fully-qualified classname:method name tuple. So for example, if the dev
 * wants a method named 'prePutCallback1' belonging to class
 * 'com.example.MyCallbacks and a method named 'prePutCallback2'
 * belonging to the same class to be invoked before any entity of kind 'yar' is
 * put, the config file will look like this:
 * <blockquote>
 * <pre>
 * yar.PrePut=com.example.MyCallbacks:prePutCallback1,com.example.MyCallbacks:prePutCallback2
 * </pre>
 * </blockquote>
 *
 * Note that it is possible to have a line which refers to all kinds by
 * omitting the kind name:
 * <blockquote>
 * <pre>
 * .PreDelete=com.example.MyCallbacks:preDeleteCallback1
 * </pre>
 * </blockquote>
 *
 * <p>
 * Each type of callback has its own signature requirements for the methods it
 * annotates. If any of these signature requirements are violated the processor
 * will produce an error. See the javadoc for the annotations for more
 * information about the callback-specific signature requirements.
 *
 * <p>
 * Processor Options:<ul>
 *   <li>debug - turns on debug statements</li>
 * </ul>
 *
 */
@SupportedAnnotationTypes({
    "com.google.appengine.api.datastore.PrePut",
    "com.google.appengine.api.datastore.PostPut",
    "com.google.appengine.api.datastore.PreDelete",
    "com.google.appengine.api.datastore.PostDelete",
    "com.google.appengine.api.datastore.PreGet",
    "com.google.appengine.api.datastore.PostLoad",
    "com.google.appengine.api.datastore.PreQuery"})
@SupportedOptions({"debug"})
public class DatastoreCallbacksProcessor extends AbstractProcessor {

  private static final String CALLBACKS_CONFIG_FILE = "META-INF/datastorecallbacks.xml";

  // Typically we'd have our test subclass to provide a test-specific output
  // stream, but the compiler doesn't walk the class hierarchy looking for the
  // annotations that are required on an annotation processor, so a subclass
  // isn't a good option.  Instead we just provide a constructor overload that
  // takes the custom output stream.
  private final OutputStream configOutputStream;

  public DatastoreCallbacksProcessor() {
    this(null);
  }

  @VisibleForTesting
  DatastoreCallbacksProcessor(OutputStream configOutputStream) {
    this.configOutputStream = configOutputStream;
  }

  /**
   * Internal interface describing an object that knows how to perform
   * callback-specific verifications.
   */
  interface CallbackVerifier {
    void verify(ExecutableElement annotatedMethod);
  }

  /**
   * Abstract {@link CallbackVerifier} implementation containing state and
   * functionality that is common to all types of callbacks.
   */
  abstract class BaseCallbackVerifier implements CallbackVerifier {
    final Class<? extends Annotation> callbackType;

    BaseCallbackVerifier(Class<? extends Annotation> callbackType) {
      this.callbackType = callbackType;
    }

    void verifySingleParamIsOfProperType(ExecutableElement annotatedMethod,
        Class<? extends CallbackContext<?>> expectedType) {
      // The context classes we expose are final so an equality check is
      // sufficient.
      if (annotatedMethod.getParameters().size() != 1
          || !processingEnv
              .getTypeUtils()
              .isSameType(
                  annotatedMethod.getParameters().get(0).asType(), getTypeMirror(expectedType))) {
        error(String.format("%s method must have a single argument of type '%s'.",
            callbackType.getSimpleName(), expectedType.getName()), annotatedMethod);
      }
    }
  }

  /**
   * Verifier for callbacks that take a single argument that extends
   * {@link CallbackContext}.
   */
  class SingleParamCallbackVerifier extends BaseCallbackVerifier {
    private final Class<? extends CallbackContext<?>> callbackContextClass;

    SingleParamCallbackVerifier(Class<? extends Annotation> callbackType,
        Class<? extends CallbackContext<?>> callbackContextClass) {
      super(callbackType);
      this.callbackContextClass = callbackContextClass;
    }

    @Override
    public void verify(ExecutableElement annotatedMethod) {
      verifySingleParamIsOfProperType(annotatedMethod, callbackContextClass);
    }
  }

  /**
   * Keeps track of the callbacks we encounter and writes them out in the
   * appropriate format once processing is complete.
   */
  private DatastoreCallbacksConfigWriter callbacksConfigWriter;

  /**
   * Used to avoid performing class-level validation more than once.
   */
  private final Set<Element> verifiedClasses = Sets.newHashSet();

  /* @VisibleForTesting */
  final Map<Class<? extends Annotation>, CallbackVerifier> callbackVerifiers =
      new ImmutableMap.Builder<Class<? extends Annotation>, CallbackVerifier>()
          .put(PrePut.class, new SingleParamCallbackVerifier(PrePut.class, PutContext.class))
          .put(PostPut.class, new SingleParamCallbackVerifier(PostPut.class, PutContext.class))
          .put(
              PreDelete.class,
              new SingleParamCallbackVerifier(PreDelete.class, DeleteContext.class))
          .put(
              PostDelete.class,
              new SingleParamCallbackVerifier(PostDelete.class, DeleteContext.class))
          .put(PreGet.class, new SingleParamCallbackVerifier(PreGet.class, PreGetContext.class))
          .put(
              PostLoad.class,
              new SingleParamCallbackVerifier(PostLoad.class, PostLoadContext.class))
          .put(
              PreQuery.class,
              new SingleParamCallbackVerifier(PreQuery.class, PreQueryContext.class))
          .buildOrThrow();

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    try {
      return processImpl(annotations, roundEnv);
    } catch (RuntimeException e) {
      // We don't allow exceptions of any kind to propagate to the compiler
      error(Throwables.getStackTraceAsString(e), null);
      return false;
    }
  }

  private boolean processImpl(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    if (callbacksConfigWriter == null) {
      loadCallbacksConfigWriter();
    }
    if (roundEnv.processingOver()) {
      if (roundEnv.errorRaised()) {
        log("Not writing config file due to errors.");
      } else {
        generateConfigFiles();
      }
    } else {
      processAnnotations(annotations, roundEnv);
    }
    return true;
  }

  private void loadCallbacksConfigWriter() {
    try {
      // There's no guarantee the compiler is going to process every file, so
      // we need to first read in an existing file (if one exists) and then apply
      // our changes on top of it.  We are assuming (safely I think) that when
      // people rebuild their applications for deployment they will do a clean
      // build which wipes out any existing config file.  If they don't they may
      // end up with a config file that references classes and/or methods that no
      // longer exist.  I don't think there's anything we can reasonably do to
      // defend against that other than to resolve the classes and methods at
      // startup time.
      FileObject existingFile = processingEnv.getFiler().getResource(
          StandardLocation.CLASS_OUTPUT, "", CALLBACKS_CONFIG_FILE);
      InputStream inputStream = null;
      if (existingFile != null) {
        try {
          inputStream = existingFile.openInputStream();
        } catch (IOException e) {
          // file does not exist and that's ok
        }
      }
      callbacksConfigWriter = new DatastoreCallbacksConfigWriter(inputStream);
      if (inputStream != null) {
        inputStream.close();
      }
    } catch (IOException e) {
      throw new RuntimeException(String.format("Unable to read %s", CALLBACKS_CONFIG_FILE), e);
    }
  }

  OutputStream getConfigOutputStream() throws IOException {
    if (configOutputStream != null) {
      return configOutputStream;
    }
    Filer filer = processingEnv.getFiler();
    FileObject fileObject =
        filer.createResource(StandardLocation.CLASS_OUTPUT, "", CALLBACKS_CONFIG_FILE);
    return fileObject.openOutputStream();
  }

  private void generateConfigFiles() {
    try (OutputStream outputStream = getConfigOutputStream()) {
      callbacksConfigWriter.store(outputStream);
      log("Wrote config: " + callbacksConfigWriter);
    } catch (IOException e) {
      throw new RuntimeException(String.format("Unable to create %s", CALLBACKS_CONFIG_FILE), e);
    }
  }

  private void processAnnotations(
      Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    for (TypeElement annotationElement : annotations) {
      Set<? extends Element> annotatedMethods =
          roundEnv.getElementsAnnotatedWith(annotationElement);
      for (Element annotatedMethod : annotatedMethods) {
        String enclosingClass =
            getBinaryName(MoreElements.asType(annotatedMethod.getEnclosingElement()));
        String method = annotatedMethod.getSimpleName().toString();
        Set<String> kinds =
            verifyCallback(
                MoreElements.asExecutable(annotatedMethod),
                annotationElement,
                enclosingClass,
                method);
        if (!roundEnv.errorRaised()) {
          callbacksConfigWriter.addCallback(kinds, annotationElement.getSimpleName().toString(),
              enclosingClass, method);
        }
      }
    }
  }

  /**
   * Extract the value of the kind attribute from the given annotation mirror.
   * Return {@code null} if we encountered errors while validating the kinds.
   * An empty set indicates that the callback applies to entities of all kinds.
   */
  @SuppressWarnings("unchecked")
  private Set<String> extractKindsFromCallbackAnnotation(Element annotatedMethod,
      AnnotationMirror annotationMirror) {
    Map<? extends ExecutableElement, ? extends AnnotationValue> annotationValueMap =
        processingEnv.getElementUtils().getElementValuesWithDefaults(annotationMirror);
    Set<String> kinds = Sets.newLinkedHashSet();
    for (ExecutableElement annotationParamName : annotationValueMap.keySet()) {
      if (annotationParamName.getSimpleName().contentEquals("kinds")) {
        Object value = annotationValueMap.get(annotationParamName).getValue();
        if (value instanceof String) {
          addKind((String) value, annotatedMethod, kinds);
        } else {
          // array args come through as a List<AnnotationValue>, but this
          // doesn't seem to be documented anywhere.
          for (AnnotationValue av : (List<AnnotationValue>) value) {
            addKind(av.getValue().toString(), annotatedMethod, kinds);
          }
        }
        // We found the kinds annotation so no need to keep looking.
        break;
      }
    }
    return kinds;
  }

  /**
   * Perform validation on the provided kind and, if it passes, add it to the
   * given {@link Set}.
   */
  private void addKind(String kind, Element annotatedMethod, Set<String> kinds) {
    kind = kind.trim();
    if (kind.isEmpty()) {
      // Super aggravating bug.  The compiler won't generate
      // appropriate source file or line number references for anything
      // related to annotations, so while I'd really like to log the
      // error against the annotation values, I'm forced to just
      // associate the error with the annotated method instead.
      error("A callback cannot be associated with an empty kind.", annotatedMethod);
//              error("A callback cannot be associated with an empty kind.", annotationElement,
//                  annotationMirror, av);
    } else {
      kinds.add(kind);
    }
  }
  /**
   * Verifies constraints on the callback method and class.
   */
  private Set<String> verifyCallback(ExecutableElement annotatedMethod,
      TypeElement annotationElement, String cls, String method) {
    Element classElement = annotatedMethod.getEnclosingElement();
    if (verifiedClasses.add(classElement)) {
      // The class containing the callback method must have a no-arg constructor
      boolean hasNoArgConstructor = false;
      for (ExecutableElement ctor : ElementFilter.constructorsIn(
          classElement.getEnclosedElements())) {
        if (ctor.getParameters().isEmpty()) {
          hasNoArgConstructor = true;
          break;
        }
      }
      if (!hasNoArgConstructor) {
        error("A class with a callback method must have a no-arg constructor.", classElement);
      }
    }

    if (callbacksConfigWriter.hasCallback(cls, method)) {
      error("Method can only have one callback annotation.", annotatedMethod);
    }
    // Callback methods cannot be static
    if (annotatedMethod.getModifiers().contains(Modifier.STATIC)) {
      error("Callback method must not be static.", annotatedMethod);
    }

    // Callback method return types must be void
    if (!annotatedMethod.getReturnType().getKind().equals(TypeKind.VOID)) {
      error("Return type of callback method must be void.", annotatedMethod);
    }

    // See if any of these exceptions are checked exceptions.
    for (TypeMirror typeMirror : annotatedMethod.getThrownTypes()) {
      if (!isSubTypeOfOneOf(typeMirror, RuntimeException.class, Error.class)) {
        error("Callback methods cannot throw checked exceptions.", annotatedMethod);
      }
    }

    AnnotationMirror annotationMirror = getAnnotationMirror(annotatedMethod, annotationElement);
    Set<String> kinds = extractKindsFromCallbackAnnotation(annotatedMethod, annotationMirror);

    // Verify the signature of the callback method.  This depends on the type of
    // callback.
    CallbackVerifier verifier;
    try {
      verifier = callbackVerifiers.get(
          Class.forName(annotationElement.getQualifiedName().toString()));
    } catch (ClassNotFoundException e) {
      // should not happen
      throw new RuntimeException(e);
    }
    if (verifier == null) {
      // should not happen
      throw new RuntimeException(
          "No verifier registered for " + annotationElement.getQualifiedName());
    }
    verifier.verify(annotatedMethod);
    return kinds;
  }

  private boolean isSubTypeOfOneOf(TypeMirror typeMirror, Class<?>... classes) {
    for (Class<?> cls : classes) {
      if (processingEnv.getTypeUtils().isSubtype(typeMirror, getTypeMirror(cls))) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns the binary name of a reference type. For example,
   * {@code com.google.Foo$Bar}, instead of {@code com.google.Foo.Bar}.
   */
  private String getBinaryName(TypeElement element) {
    return getBinaryNameImpl(element, element.getSimpleName().toString());
  }

  private String getBinaryNameImpl(Element element, String className) {
    Element enclosingElement = element.getEnclosingElement();

    if (enclosingElement instanceof PackageElement) {
      PackageElement pkg = MoreElements.asPackage(enclosingElement);
      if (pkg.isUnnamed()) {
        return className;
      }
      return pkg.getQualifiedName() + "." + className;
    }

    return getBinaryNameImpl(enclosingElement, enclosingElement.getSimpleName() + "$" + className);
  }

  private AnnotationMirror getAnnotationMirror(Element annotatedMethod,
      final TypeElement annotationElement) {
    return Iterables.find(
        annotatedMethod.getAnnotationMirrors(),
        (AnnotationMirror mirror) -> {
          DeclaredType type = mirror.getAnnotationType();
          TypeElement typeElement = MoreElements.asType(type.asElement());
          return typeElement.getQualifiedName().contentEquals(annotationElement.getQualifiedName());
        });
  }

  private void log(String msg) {
    if (processingEnv.getOptions().containsKey("debug")) {
      processingEnv.getMessager().printMessage(Kind.NOTE, "Datastore Callbacks: " + msg);
    }
  }

  private void error(String msg, Element element) {
    processingEnv.getMessager().printMessage(
        Kind.ERROR, "Datastore Callbacks: " + msg, element);
  }

  private TypeMirror getTypeMirror(Class<?> cls) {
    return processingEnv.getElementUtils().getTypeElement(cls.getName()).asType();
  }
}
