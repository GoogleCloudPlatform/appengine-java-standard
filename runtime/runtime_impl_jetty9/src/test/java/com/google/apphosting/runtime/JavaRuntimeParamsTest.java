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

package com.google.apphosting.runtime;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static org.junit.Assert.fail;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.google.common.base.CaseFormat;
import com.google.common.base.Converter;
import com.google.common.collect.ImmutableList;
import com.google.common.reflect.ImmutableTypeToInstanceMap;
import com.google.common.reflect.TypeToInstanceMap;
import com.google.common.reflect.TypeToken;
import com.google.common.truth.Expect;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class JavaRuntimeParamsTest {
  @Rule public Expect expect = Expect.create();



  @Test
  public void testBooleanDefaultTrue() {
    JavaRuntimeParams params = JavaRuntimeParams.parseArgs();
    assertThat(params.getLogJettyExceptionsToAppLogs()).isTrue();
  }

  @Test
  public void testBooleanDefaultTrueSpecifiedFalse() {
    String[] args = {"--log_jetty_exceptions_to_app_logs=false"};
    JavaRuntimeParams params = JavaRuntimeParams.parseArgs(args);
    assertThat(params.getLogJettyExceptionsToAppLogs()).isFalse();
  }

  @Test
  public void testBooleanTrueCaseInsensitive() {
    String[] args = {"--log_jetty_exceptions_to_app_logs=tRuE"};
    JavaRuntimeParams params = JavaRuntimeParams.parseArgs(args);
    assertThat(params.getLogJettyExceptionsToAppLogs()).isTrue();
  }

  @Test
  public void testBooleanFalseCaseInsensitive() {
    String[] args = {"--thread_stop_terminates_clone=fAlSe"};
    JavaRuntimeParams params = JavaRuntimeParams.parseArgs(args);
    assertThat(params.getThreadStopTerminatesClone()).isFalse();
  }

  @Test
  public void testBooleanDefaultTrueSpecifiedOne() {
    String[] args = {"--log_jetty_exceptions_to_app_logs=1"};
    JavaRuntimeParams params = JavaRuntimeParams.parseArgs(args);
    assertThat(params.getLogJettyExceptionsToAppLogs()).isTrue();
  }

  @Test
  public void testBooleanDefaultTrueSpecifiedZero() {
    String[] args = {"--log_jetty_exceptions_to_app_logs=0"};
    JavaRuntimeParams params = JavaRuntimeParams.parseArgs(args);
    assertThat(params.getLogJettyExceptionsToAppLogs()).isFalse();
  }

  @Test
  public void testBooleanDefaultTrueSpecifiedYes() {
    String[] args = {"--log_jetty_exceptions_to_app_logs=yes"};
    JavaRuntimeParams params = JavaRuntimeParams.parseArgs(args);
    assertThat(params.getLogJettyExceptionsToAppLogs()).isTrue();
  }

  @Test
  public void testBooleanDefaultTrueSpecifiedNo() {
    String[] args = {"--log_jetty_exceptions_to_app_logs=no"};
    JavaRuntimeParams params = JavaRuntimeParams.parseArgs(args);
    assertThat(params.getLogJettyExceptionsToAppLogs()).isFalse();
  }

  @Test
  public void testBooleanDefaultTrueSpecifiedInvalid() {
    String[] args = {"--log_jetty_exceptions_to_app_logs=invalid"};
    try {
      JavaRuntimeParams.parseArgs(args);
      fail("Invalid boolean parameters should be rejected");
    } catch (ParameterException expected) {
    }
  }

  @Test
  public void testBooleanNoParameter() {
    String[] args = {"--nolog_jetty_exceptions_to_app_logs"};
    JavaRuntimeParams params = JavaRuntimeParams.parseArgs(args);
    assertThat(params.getLogJettyExceptionsToAppLogs()).isFalse();

    String[] args1 = {"--log_jetty_exceptions_to_app_logs"};
    params = JavaRuntimeParams.parseArgs(args1);
    assertThat(params.getLogJettyExceptionsToAppLogs()).isTrue();
  }

  @Test
  public void testNonexistentBooleanParamIgnored() {
    String[] args = {"--enable_xyz", "--noenable_abc"};
    JavaRuntimeParams params = JavaRuntimeParams.parseArgs(args);
    assertThat(params.getUnknownParams())
        .containsExactly("--enable_xyz", "--noenable_abc")
        .inOrder();
  }

  @Test
  public void testBooleanParamsArityIsOne() {
    for (Field field : JavaRuntimeParams.class.getDeclaredFields()) {
      Parameter param = field.getAnnotation(Parameter.class);
      if (param != null) {
        Class<?> fieldType = field.getType();
        if (fieldType == boolean.class || fieldType == Boolean.class) {
          assertWithMessage("Boolean param %s must have arity=1", param.names()[0])
              .that(param.arity())
              .isEqualTo(1);
        }
      }
    }
  }

  @Test
  public void testBooleanParamsDoNotStartWithNo() {
    for (Field field : JavaRuntimeParams.class.getDeclaredFields()) {
      Parameter param = field.getAnnotation(Parameter.class);
      if (param != null) {
        Class<?> fieldType = field.getType();
        if (fieldType == boolean.class || fieldType == Boolean.class) {
          for (String name : param.names()) {
            assertWithMessage("Boolean param %s cannot start with '--no'", name)
                .that(name)
                .doesNotMatch("--no.*");
          }
        }
      }
    }
  }

  @Test
  public void testBooleanParamNamesShortForm() {
    List<String> input = new ArrayList<>();
    List<String> expectedOutput = new ArrayList<>();
    for (Field field : JavaRuntimeParams.class.getDeclaredFields()) {
      Parameter param = field.getAnnotation(Parameter.class);
      if (param != null && field.getType() == boolean.class) {
        for (String name : param.names()) {
          assertThat(name).startsWith("--");
          name = name.substring(2);
          input.add("--" + name);
          input.add("--no" + name);
          expectedOutput.add("--" + name + "=true");
          expectedOutput.add("--" + name + "=false");
        }
      }
    }
    assertThat(input).isNotEmpty();
    List<String> expanded = ParameterFactory.expandBooleanParams(input, JavaRuntimeParams.class);
    assertThat(expanded).isEqualTo(expectedOutput);
  }

  @Test
  public void testExpandBooleanParamsTrue() {
    List<String> args = ImmutableList.of(
        "--flag1=a", "--log_jetty_exceptions_to_app_logs", "--flag3=b");
    List<String> params = ParameterFactory.expandBooleanParams(args, JavaRuntimeParams.class);
    assertThat(params)
        .containsExactly("--flag1=a", "--log_jetty_exceptions_to_app_logs=true", "--flag3=b")
        .inOrder();
  }

  @Test
  public void testExpandBooleanParamsFalse() {
    List<String> args = ImmutableList.of(
        "--flag1=a", "--nolog_jetty_exceptions_to_app_logs", "--flag3=b");
    List<String> params = ParameterFactory.expandBooleanParams(args, JavaRuntimeParams.class);
    assertThat(params)
        .containsExactly("--flag1=a", "--log_jetty_exceptions_to_app_logs=false", "--flag3=b")
        .inOrder();
  }

  @Test
  public void testExpandBooleanParamsUnknownParams() {
    List<String> args = ImmutableList.of("--unknown1", "--nounknown2");
    List<String> params = ParameterFactory.expandBooleanParams(args, JavaRuntimeParams.class);
    assertThat(params).containsExactly("--unknown1", "--nounknown2").inOrder();
  }



  @Test
  public void testUnknownArgumentsAllowed() {
    String[] args = {"--xyz=abc"};
    var unused = JavaRuntimeParams.parseArgs(args);
  }
  @Test
  public void testDefaults() {
    JavaRuntimeParams params = JavaRuntimeParams.parseArgs();
    assertThat(params.getTrustedHost()).isEmpty();
    // Skipped entropyString.
    assertThat(params.getLogJettyExceptionsToAppLogs()).isTrue();
    assertThat(params.getCloneMaxOutstandingApiRpcs()).isEqualTo(100);
    assertThat(params.getThreadStopTerminatesClone()).isTrue();
    // Skipped deprecated params.
    assertThat(params.getByteCountBeforeFlushing()).isEqualTo(100 * 1024L);
    assertThat(params.getMaxLogLineSize()).isEqualTo(16 * 1024);
    assertThat(params.getMaxLogFlushSeconds()).isEqualTo(60);
    assertThat(params.getMaxRuntimeLogPerRequest()).isEqualTo(3000L * 1024L);
    // Skipped deprecated params.
    assertThat(params.getEnableHotspotPerformanceMetrics()).isFalse();
    assertThat(params.getEnableCloudCpuProfiler()).isFalse();
    assertThat(params.getEnableCloudHeapProfiler()).isFalse();
    assertThat(params.getUrlfetchDeriveResponseMessage()).isTrue();
    assertThat(params.getPollForNetwork()).isFalse();
    assertThat(params.getForceUrlfetchUrlStreamHandler()).isFalse();
    assertThat(params.getFixedApplicationPath()).isNull();
    assertThat(params.getDisableApiCallLogging()).isFalse();
  }

  @Test
  public void testGetUnknownParams() {
    String[] args = {"--unknown1=xyz", "--trusted_host=abc", "--unknown2=xyz"};
    JavaRuntimeParams params = JavaRuntimeParams.parseArgs(args);
    assertThat(params.getUnknownParams())
        .containsExactly("--unknown1=xyz", "--unknown2=xyz")
        .inOrder();
  }

  private static final Converter<String, String> LOWER_UNDERSCORE_TO_LOWER_CAMEL =
      CaseFormat.LOWER_UNDERSCORE.converterTo(CaseFormat.LOWER_CAMEL);
  private static final Converter<String, String> LOWER_UNDERSCORE_TO_UPPER_CAMEL =
      CaseFormat.LOWER_UNDERSCORE.converterTo(CaseFormat.UPPER_CAMEL);

  public interface FakeServletEngineAdapter extends ServletEngineAdapter {}

  private static final TypeToInstanceMap<Object> ARBITRARY_VALUES =
      ImmutableTypeToInstanceMap.builder()
          .put(new TypeToken<Class<? extends ServletEngineAdapter>>() {},
              FakeServletEngineAdapter.class)
          .put(int.class, 23)
          .put(long.class, 5L)
          .put(double.class, 17.5)
          .put(String.class, "flibbertigibbet")
      .build();

  @Test
  public void testExhaustive() {
    for (Field field : JavaRuntimeParams.class.getDeclaredFields()) {
      Parameter parameter = field.getAnnotation(Parameter.class);
      if (parameter != null && !parameter.description().startsWith("Deprecated")) {
        String optionName = parameter.names()[0];
        assertThat(optionName).startsWith("--");
        String fieldName = LOWER_UNDERSCORE_TO_LOWER_CAMEL.convert(optionName.substring(2));
        expect.that(field.getName()).isEqualTo(fieldName);
        String methodName =
            "get" + LOWER_UNDERSCORE_TO_UPPER_CAMEL.convert(optionName.substring(2));
        Method getter;
        try {
          getter = JavaRuntimeParams.class.getDeclaredMethod(methodName);
          checkOption(optionName, getter);
        } catch (ReflectiveOperationException e) {
          expect.withMessage("For option %s: %s", optionName, e).fail();
        }
      }
    }
  }

  private void checkOption(String optionName, Method getter)
      throws IllegalAccessException, InvocationTargetException {
    Type optionType = getter.getGenericReturnType();
    if (optionType.equals(boolean.class)) {
      checkBooleanOption(optionName, getter);
    } else {
      Object arbitraryValue = ARBITRARY_VALUES.get(TypeToken.of(optionType));
      if (arbitraryValue == null) {
        expect
            .withMessage("No default value for option %s of type %s", optionName, optionType)
            .fail();
      } else {
        String[] args = {optionName + "=" + string(arbitraryValue)};
        JavaRuntimeParams params = JavaRuntimeParams.parseArgs(args);
        Object actualValue = getter.invoke(params);
        expect.withMessage(args[0]).that(actualValue).isEqualTo(arbitraryValue);
      }
    }
  }

  private void checkBooleanOption(String optionName, Method getter)
      throws IllegalAccessException, InvocationTargetException {
    for (boolean value : new boolean[] {false, true}) {
      String[] args = {optionName + "=" + value};
      JavaRuntimeParams params = JavaRuntimeParams.parseArgs(args);
      Object actualValue = getter.invoke(params);
      expect.withMessage(args[0]).that(actualValue).isEqualTo(value);
    }
  }

  private String string(Object x) {
    if (x instanceof Class<?>) {
      return ((Class<?>) x).getName();
    } else {
      return String.valueOf(x);
    }
  }
}
