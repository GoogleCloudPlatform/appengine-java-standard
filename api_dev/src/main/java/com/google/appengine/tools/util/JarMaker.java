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

package com.google.appengine.tools.util;

import com.google.common.flogger.GoogleLogger;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
 * A utility for building multiple jar files, each of size less than a specified maximum, from a
 * sequence of {@link JarEntry JarEntries} and {@link InputStream InputStreams}.
 * <p>
 * This is an abstract class. Concrete subclasses must implement {@link #getNextJarEntry} in order
 * to specify the sequence of {@link JarEntry JarEntries} and {@link InputStream InputStreams}.
 * <p>
 * Concrete subclasses must also implement {@link #getManifest} to specify a single manifest that
 * should be included in all the jars, or {@code null} if no manifest should be included.
 * <p>
 * Usage: Construct an abstract subclass and then invoke the method {@link #run()}.
 *
 */
abstract  class JarMaker {
  protected static final String EXT = ".jar";

  private static final int READ_BUFFER_SIZE_BYTES = 8 * 1024;
  private static final int FILE_BUFFER_INITIAL_SIZE_BYTES = 512 * 1024;

  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();

  private final String baseName;
  private final File outputDirectory;
  private final int maximumSize;
  private final Set<String> excludes;
  private final boolean closeEachInputStream;

  private int nextFileIndex = 0;
  private long currentSize = 0L;
  private JarOutputStream currentStream;
  private int outputDigits;

  /**
   * Constructor
   *
   * @param baseName The base name of the emitted jar files. The file name will also include a
   *        suffix containing a number of numerical digits equal to {@code outputDigits}
   * @param outputDigits Number of digits to use for the names of the emitted jar files
   * @param outputDirectory The directory into which to emit the jar files. This directory will be
   *        created if it does not exist.
   * @param maximumSize The maximum size of a jar file that should be emitted, in bytes. Note that
   *        the actual size of the jar files may be less than this value due to compression. The
   *        compression ratio is not specified. (In practice it is frequently 50%).
   * @param excludes A set file-name suffixes. If this is not {@code null} then {@link JarEntry
   *        JarEntries} whose path includes one of these suffixes will be excluded from the
   *        generated jar files.
   * @param closeEachInputStream Determines which party owns the closing of the input streams. If
   *        this is {@code true} then this class will do the closing, otherwise the caller will do
   *        it.
   */
  public JarMaker(String baseName,
      int outputDigits,
      File outputDirectory,
      int maximumSize,
      Set<String> excludes,
      boolean closeEachInputStream) {
    this.baseName = baseName;
    this.outputDirectory = outputDirectory;
    this.maximumSize = maximumSize;
    this.outputDigits = outputDigits;
    this.excludes = excludes;
    this.closeEachInputStream = closeEachInputStream;
  }

  /**
   * A pair consisting of a {@link JarEntry} and an {@link InputStream}.
   */
  protected static class JarEntryData {
    public JarEntry jarEntry;
    public InputStream inputStream;

    public JarEntryData(JarEntry jarEntry, InputStream inputStream) {
      this.jarEntry = jarEntry;
      this.inputStream = inputStream;
    }
  }

  /**
   * Returns the next {@link JarEntryData} specifying the next {@link JarEntry}
   * and {@link InputStream} that should be used in constructing the emitted jar files.
   * @return The next {@link JarEntryData} or {@code null} to indicate that there are no more.
   * <p>
   * This method will be called multiple times during the execution of {@link #run()}. A concrete
   * subclass must implement this method.
   * @throws IOException If any problems occur.
   */
  protected abstract JarEntryData getNextJarEntry() throws IOException;

  /**
   * Returns a single {@link Manifest} that will be included in each of the emitted jar files,
   * or {@code null} to indicate that no manifest should be included. This method will be invoked
   * once during the execution of {@link #run}.
   */
  protected abstract Manifest getManifest();

  /**
   * Generates one or more jar files based on the data provided in the constructor, a single call to
   * {@code getManifest()} and multiple calls to {@code getNextJarEntry()}.
   *
   * @throws IOException
   */
  public void run() throws IOException {
    // Create the directory if it doesn't already exist.
    outputDirectory.mkdirs();

    Manifest manifest = getManifest();
    long manifestSize = (manifest != null) ? getManifestSize(manifest) : 0;

    byte[] readBuffer = new byte[READ_BUFFER_SIZE_BYTES];
    ByteArrayOutputStream fileBuffer = new ByteArrayOutputStream(FILE_BUFFER_INITIAL_SIZE_BYTES);
    JarEntryData entryData;

    try {
      beginNewOutputStream(manifest, manifestSize);
      while ((entryData = getNextJarEntry()) != null) {
        JarEntry entry = entryData.jarEntry;
        InputStream inputStream = entryData.inputStream;
        String name = entry.getName();

        if (shouldIncludeFile(name)) {
          fileBuffer.reset();
          readIntoBuffer(inputStream, readBuffer, fileBuffer);
          if (closeEachInputStream) {
            inputStream.close();
          }
          long size = fileBuffer.size();
          if ((currentSize + size) >= maximumSize) {
            beginNewOutputStream(manifest, manifestSize);
          }

          logger.atFine().log("Copying entry: %s (%d bytes)", name, size);
          currentStream.putNextEntry(entry);
          fileBuffer.writeTo(currentStream);
          currentSize += size;
        }
      }
    } finally {
      currentStream.close();
    }
  }

  protected boolean shouldIncludeFile(String fileName) {
    if (excludes == null) {
      return true;
    }
    for (String suffix : excludes) {
      if (fileName.endsWith(suffix)) {
        logger.atFine().log("Skipping file matching excluded suffix '%s': %s", suffix, fileName);
        return false;
      }
    }

    return true;
  }

  private long getManifestSize(Manifest manifest) throws IOException{
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try {
      manifest.write(baos);
      return baos.size();
    } finally {
      baos.close();
    }
  }

  private JarOutputStream newJarOutputStream(Manifest manifest) throws IOException {
    if (manifest == null) {
      return new JarOutputStream(createOutFile(nextFileIndex++));
    }
    return new JarOutputStream(createOutFile(nextFileIndex++), manifest);
  }

  /**
   * Close the current output stream if there is one, and open a new one with the next available
   * index number.
   */
  private void beginNewOutputStream(Manifest manifest, long manifestSize) throws IOException {
    if (currentStream != null) {
      currentStream.close();
    }
    currentStream = newJarOutputStream(manifest);
    // reset the size to the size of the manifest
    currentSize = manifestSize;
  }

  private void readIntoBuffer(InputStream inputStream, byte[] readBuffer, ByteArrayOutputStream out)
      throws IOException {
    int count;
    while ((count = inputStream.read(readBuffer)) != -1) {
      out.write(readBuffer, 0, count);
    }
  }

  private OutputStream createOutFile(int index) throws IOException {
    String formatString = "%s-%0" + outputDigits + "d%s";
    String newName = String.format(formatString, baseName, index, EXT);
    File newFile = new File(outputDirectory, newName);
    logger.atFine().log("Opening new file: %s", newFile);
    return new BufferedOutputStream(new FileOutputStream(newFile));
  }

}
