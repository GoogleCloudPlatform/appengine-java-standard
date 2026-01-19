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

package com.google.appengine.api.mail;

import com.google.appengine.spi.FactoryProvider;
import com.google.appengine.spi.ServiceProvider;
import com.google.auto.service.AutoService;

/**
 * Factory provider for {@link IMailServiceFactory}.
 *
 * <p><b>Note:</b> This class is not intended for end users.
 *
 */
@AutoService(FactoryProvider.class)
@ServiceProvider(precedence = Integer.MIN_VALUE)
@SuppressWarnings({"rawtypes", "UnnecessaryJavacSuppressWarnings"})
public final class IMailServiceFactoryProvider extends FactoryProvider<IMailServiceFactory> {

  private final MailServiceFactoryImpl implementation = new MailServiceFactoryImpl();

  public IMailServiceFactoryProvider() {
    super(IMailServiceFactory.class);
  }

  @Override
  protected IMailServiceFactory getFactoryInstance() {
    return implementation;
  }

}
