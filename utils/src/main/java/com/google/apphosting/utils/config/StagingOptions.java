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

package com.google.apphosting.utils.config;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSortedSet;
import java.nio.charset.Charset;
import java.util.Optional;

/** Holder for staging. */
@AutoValue
public abstract class StagingOptions {

  public static final StagingOptions EMPTY = StagingOptions.builder().build();

  public static final StagingOptions ANCIENT_DEFAULTS =
      StagingOptions.builder()
          .setSplitJarFiles(Optional.of(false))
          .setSplitJarFilesExcludes(Optional.of(ImmutableSortedSet.of()))
          .setJarJsps(Optional.of(true))
          .setJarClasses(Optional.of(false))
          .setDeleteJsps(Optional.of(false))
          .setCompileEncoding(Optional.of("UTF-8"))
          .build();

  public static final StagingOptions SANE_DEFAULTS =
      StagingOptions.builder()
          .setSplitJarFiles(Optional.of(true))
          .setSplitJarFilesExcludes(Optional.of(ImmutableSortedSet.of()))
          .setJarJsps(Optional.of(true))
          .setJarClasses(Optional.of(true))
          .setDeleteJsps(Optional.of(true))
          .setCompileEncoding(Optional.of("UTF-8"))
          .build();

  public abstract Optional<Boolean> splitJarFiles();

  public abstract Optional<ImmutableSortedSet<String>> splitJarFilesExcludes();

  public abstract Optional<Boolean> jarJsps();

  public abstract Optional<Boolean> jarClasses();

  public abstract Optional<Boolean> deleteJsps();

  public abstract Optional<String> compileEncoding();

  public abstract Builder toBuilder();

  public static Builder builder() {
    return new AutoValue_StagingOptions.Builder();
  }

  /** AutoValue builder. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setSplitJarFiles(Optional<Boolean> value);

    public abstract Builder setSplitJarFilesExcludes(Optional<ImmutableSortedSet<String>> value);

    public abstract Builder setJarJsps(Optional<Boolean> value);

    public abstract Builder setJarClasses(Optional<Boolean> value);

    public abstract Builder setDeleteJsps(Optional<Boolean> value);

    public abstract Builder setCompileEncoding(Optional<String> value);

    public abstract StagingOptions build();
  }

  /* Checks that all options are instantiated correctly and raises otherwise. */
  public void validate() {
    if (!(splitJarFiles().isPresent()
        && splitJarFilesExcludes().isPresent()
        && jarJsps().isPresent()
        && jarClasses().isPresent()
        && deleteJsps().isPresent()
        && compileEncoding().isPresent())) {
      throw new AppEngineConfigException("Staging options is not complete.");
    }
    // Validate the charset.
    Charset.forName(compileEncoding().get());
  }

  /* Merge several partial staging options together, later args take precedence over former. */
  public static StagingOptions merge(StagingOptions... opts) {
    StagingOptions.Builder merged = StagingOptions.builder();
    for (StagingOptions op : opts) {
      if (op.splitJarFiles().isPresent()) {
        merged.setSplitJarFiles(op.splitJarFiles());
      }
      if (op.splitJarFilesExcludes().isPresent()) {
        merged.setSplitJarFilesExcludes(op.splitJarFilesExcludes());
      }
      if (op.jarJsps().isPresent()) {
        merged.setJarJsps(op.jarJsps());
      }
      if (op.jarClasses().isPresent()) {
        merged.setJarClasses(op.jarClasses());
      }
      if (op.deleteJsps().isPresent()) {
        merged.setDeleteJsps(op.deleteJsps());
      }
      if (op.compileEncoding().isPresent()) {
        merged.setCompileEncoding(op.compileEncoding());
      }
    }
    return merged.build();
  }
}
