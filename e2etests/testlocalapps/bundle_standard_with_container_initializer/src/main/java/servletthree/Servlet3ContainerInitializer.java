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
package servletthree;

import com.google.auto.service.AutoService;
import java.util.Set;
import javax.servlet.ServletContainerInitializer;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;

/** Simple ServletContainerInitializer */
@AutoService(ServletContainerInitializer.class)
public class Servlet3ContainerInitializer implements ServletContainerInitializer {

  @Override
  public void onStartup(Set<Class<?>> set, ServletContext servletContext) throws ServletException {
    System.out.println("test");
  }
}
