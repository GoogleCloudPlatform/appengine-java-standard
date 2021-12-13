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

package com.google.appengine.tools.development.resource;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

public final class ResourceExtractor {

  private ResourceExtractor() {}

  /*
   * Copies a given resource to the destination path. This is useful when running tests that
   * have a directory of resources to consume.
   *
   * If the resource is a 'directory' then the destination path becomes the root folder
   * If the resource is a 'file', then the destination path becomes a file
   */
  public static void toFile(String resourcePath, String dstPath) {
    try {
      toFileThrow(resourcePath, dstPath);
    } catch (IOException | URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }

  private static void toFileThrow(String resourcePath, String dstPath)
      throws IOException, URISyntaxException {
    URL resourceUrl = Resources.getResource(resourcePath);
    switch (resourceUrl.getProtocol()) {
      case "jar":
        toFileFromJar(resourceUrl, dstPath);
        break;
      case "file":
        toFileFromFile(resourceUrl, dstPath);
        break;
      default:
        throw new UnsupportedOperationException(
            String.format("Unimplemented URL protocol: %s", resourceUrl.getProtocol()));
    }
  }

  private static void toFileFromFile(URL resourceUrl, String dstPath)
      throws IOException, URISyntaxException {
    File root = new File(resourceUrl.getPath());
    if (root.isFile()) {
      try (InputStream inputStream = resourceUrl.openStream()) {
        Files.copy(inputStream, Paths.get(dstPath), StandardCopyOption.REPLACE_EXISTING);
        return;
      }
    }
    Path rootPath = Paths.get(resourceUrl.toURI());
    try (Stream<Path> stream = Files.walk(rootPath)) {
      stream.forEach(
          p -> {
            String suffix = p.toString().substring(root.toString().length());
            Path dst = Paths.get(dstPath, suffix);
            if (p.toFile().isDirectory()) {
              dst.toFile().mkdirs();
            } else {
              copy(p, dst);
            }
          });
    }
  }

  private static void toFileFromJar(URL resourceUrl, String dstPath)
      throws IOException, URISyntaxException {
    JarURLConnection jarConnection = (JarURLConnection) resourceUrl.openConnection();
    jarConnection.connect();
    String resourcePath = jarConnection.getEntryName();
    try (FileSystem fileSystem = FileSystems.newFileSystem(resourceUrl.toURI(), ImmutableMap.of());
        JarFile jarFile = jarConnection.getJarFile()) {
      Path rootPath = fileSystem.getPath(resourcePath);
      try (Stream<Path> stream = Files.walk(rootPath)) {
        stream.forEach(
            p -> {
              String pathString = p.toString();
              // on some systems the jarfile paths will begin with a slash, however,
              // JarFile.getJarEntry will not find the entry if it begins with a slash
              if (pathString.startsWith("/")) {
                pathString = pathString.substring(1);
              }
              String suffix = pathString.substring(resourcePath.length());
              Path dst = Paths.get(dstPath, suffix);
              JarEntry jarEntry = jarFile.getJarEntry(pathString);
              if (jarEntry.isDirectory()) {
                dst.toFile().mkdirs();
              } else {
                copy(p, dst);
              }
            });
      }
    }
  }

  private static void copy(Path p, Path dst) {
    try (InputStream inputStream = Files.newInputStream(p)) {
      Files.copy(inputStream, dst, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
