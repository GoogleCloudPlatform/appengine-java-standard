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

package com.google.appengine.apicompat.testclasses;

@SuppressWarnings("unused")
public class PublicClassExtendingPkgProtectedClass extends PkgProtectedClass {

  public PublicClassExtendingPkgProtectedClass(String yam) {
  }

  protected PublicClassExtendingPkgProtectedClass() {
  }

  @Override
  public void foo1() {
  }

  public String foo1;

  public void foo2() {
  }

  public String foo2;
}
