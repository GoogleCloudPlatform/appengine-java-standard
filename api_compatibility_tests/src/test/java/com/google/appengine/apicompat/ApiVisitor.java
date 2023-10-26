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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import java.util.Arrays;
import javax.annotation.Nullable;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

/**
 * A {@link ClassVisitor} that injects calls to methods that track invoked methods and referenced
 * fields.
 *
 */
class ApiVisitor extends ClassVisitor {

  static final Type TRACKER_CLASS =
      Type.getObjectType(UsageTracker.class.getName().replace('.', '/'));

  // String owner, String paramTypes
  private static final Method TRACK_CONSTRUCTOR_INVOCATION_DESCRIPTOR =
      Method.getMethod("void trackConstructorInvocation(String, String)");

  // String owner, String methodName, String paramTypes
  private static final Method TRACK_METHOD_INVOCATION_DESCRIPTOR =
      Method.getMethod("void trackMethodInvocation(String, String, String)");

  // String owner, String fieldName
  private static final Method TRACK_FIELD_REFERENCE_DESCRIPTOR =
      Method.getMethod("void trackFieldReference(String, String)");

  /**
   * Access to member constants gets inlined by the compiler so there is no opportunity for the
   * visitor to receive a callback. Our solution is impose a very specific usage requirement: In
   * order to register usage of a member constant, the usage class must declare a member named
   * {@value #API_CONSTANT_PREFIX}XXXX, where XXXX is the name of the member constant, and then
   * assign the value of the constant to this member. This is not foolproof because the person
   * implementing the usage class could declare this member and assign some other value to it, but
   * that would be an unusual mistake to make and there is little incentive to do it on purpose.
   *
   * This is a hack but the goal is to protect ourselves and it does work. I think we can live with
   * it.
   */
  static final String API_CONSTANT_PREFIX = "___apiConstant_";

  private final Class<?> apiClass;

  /**
   * @param classVisitor The class visitor.
   * @param apiClass The class whose usage we are tracking.
   */
  ApiVisitor(ClassVisitor classVisitor, Class<?> apiClass) {
    super(Opcodes.ASM6, classVisitor);
    this.apiClass = checkNotNull(apiClass, "apiClass cannot be null");
  }

  @Override
  public @Nullable MethodVisitor visitMethod(
      int access, String methodName, String methodDesc, String signature, String[] exceptions) {
    MethodVisitor mv = super.visitMethod(access, methodName, methodDesc, signature, exceptions);
    return mv == null ? null : new ObjectAccessVisitor(mv, access, methodName, methodDesc);
  }

  /**
   * Transforms a method descriptor, e.g (Ljava/lang/String;)V, to a comma-separated list of
   * fully-qualified class names.
   *
   * @param methodDesc The method descriptor.
   * @return A comma-separated list of fully-qualified class names.
   */
  private String methodDescToParamTypesStr(String methodDesc) {
    return Joiner.on(',').join(
        Iterables.transform(Arrays.asList(Type.getArgumentTypes(methodDesc)),
            new Function<Type, String>() {
              @Override
              public String apply(Type type) {
                if (type.getSort() != Type.ARRAY) {
                  return type.getClassName();
                } else {
                  return type.getDescriptor().replace('/', '.');
                }
              }
            }));
  }

  /**
   * A {@link GeneratorAdapter} that gives us callbacks on field references and method invocations.
   */
  private class ObjectAccessVisitor extends GeneratorAdapter {

    private ObjectAccessVisitor(MethodVisitor methodVisitor, int access, String name, String desc) {
      super(Opcodes.ASM6, methodVisitor, access, name, desc);
    }

    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String desc) {
      String owningClass = owner.replace('/', '.');
      if (shouldInstrumentFieldReference(owningClass, name)) {
        // Look for assignments to members whose names match our naming convention for api
        // constants.
        if ((opcode == Opcodes.PUTFIELD || opcode == Opcodes.PUTSTATIC)
            && name.startsWith(API_CONSTANT_PREFIX)) {
          push(apiClass.getName());
          push(name.substring(API_CONSTANT_PREFIX.length()));
          invokeStatic(TRACKER_CLASS, TRACK_FIELD_REFERENCE_DESCRIPTOR);
        } else if (opcode == Opcodes.GETFIELD || opcode == Opcodes.GETSTATIC) {
          // Otherwise we're looking for code where we retrieve the value of a member.
          push(owningClass);
          push(name);
          invokeStatic(TRACKER_CLASS, TRACK_FIELD_REFERENCE_DESCRIPTOR);
        }
      }
      super.visitFieldInsn(opcode, owner, name, desc);
    }

    private boolean shouldInstrumentFieldReference(String fieldOwnerClsName, String fieldName) {
      try {
        Class<?> fieldOwnerCls = Class.forName(fieldOwnerClsName);
        // If the code references a superclass or subclass of the api class, instrument it. Also
        // instrument the access of any api constant field.
        return fieldOwnerCls.isAssignableFrom(apiClass)
            || apiClass.isAssignableFrom(fieldOwnerCls)
            || fieldName.startsWith(ApiVisitor.API_CONSTANT_PREFIX);
      } catch (ClassNotFoundException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
      String owningClass = owner.replace('/', '.');
      String paramTypesStr = methodDescToParamTypesStr(desc);
      if (shouldInstrumentMethodInvocation(owningClass, name)) {
        if (name.equals("<init>")) {
          // invoke the constructor tracking method
          push(owningClass);
          push(paramTypesStr);
          invokeStatic(TRACKER_CLASS, TRACK_CONSTRUCTOR_INVOCATION_DESCRIPTOR);
        } else {
          // invoke the method tracking method
          push(owningClass);
          push(name);
          push(paramTypesStr);
          invokeStatic(TRACKER_CLASS, TRACK_METHOD_INVOCATION_DESCRIPTOR);
        }
      }
      super.visitMethodInsn(opcode, owner, name, desc, itf);
    }

    private boolean shouldInstrumentMethodInvocation(String methodOwnerClsName, String methodName) {
      try {
        Class<?> methodOwnerCls = Class.forName(methodOwnerClsName);
        // If the code references a superclass or subclass of the api class, instrument it, but
        // filter out all static initializer invocations, all invocations of methods on classes
        // that are part of the core jdk.
        return
            (methodOwnerCls.isAssignableFrom(apiClass) || apiClass.isAssignableFrom(methodOwnerCls))
                && !methodName.equals("<clinit>") && !methodOwnerCls.getName().startsWith("java.");
      } catch (ClassNotFoundException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
