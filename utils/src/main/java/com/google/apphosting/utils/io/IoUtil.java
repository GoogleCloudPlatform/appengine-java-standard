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

package com.google.apphosting.utils.io;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * IO utilities.
 *
 */
public class IoUtil {
  private static final Logger logger = Logger.getLogger(IoUtil.class.getName());

  private IoUtil() {
  }

  /**
   * Returns all of the files from a root including directories.
   * Also includes the root. 
   *
   * @param root a non-null root file or directory
   * @return a non-null, non-empty List of files 
   */
  public static List<File> getFilesAndDirectories(File root) {
    List<File> files = new ArrayList<File>();
    getFiles(files, root);
    return files;
  }

  private static void getFiles(List<File> files, File file) {
    if (file.isDirectory()) {
      File[] listedFiles = file.listFiles();
      if (listedFiles == null) {
        logger.log(Level.WARNING, "Could not read directory {0}", file);
        return;
      }
      for (File f : listedFiles) {
        getFiles(files, f);
      }
    }
    files.add(file);
  }
}
