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

package com.google.appengine.tools.enhancer;

import java.io.File;
import java.util.StringTokenizer;
import java.util.Vector;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Java;
import org.apache.tools.ant.types.FileSet;

/**
 * An Ant task for ORM enhancement. 
 * <p>
 * In order to use this task, users must install a taskdef in Ant:
 * <pre>
 *   &lt;taskdef name="enhancer" classpathref="appengine-sdk-classpath" 
 *       classname="com.google.appengine.tools.development.enhancer.EnhancerTask"/&gt;
 * </pre>
 * Where appengine-sdk-classpath includes appengine-tools-api.jar.
 * <p>
 * Options for this task are documented on 
 * <a href="http://www.datanucleus.org/products/accessplatform/enhancer.html#ant">
 * DataNucleus' web site</a>.
 *
 */
public class EnhancerTask extends Java {

  // NB This code borrows heavily from DataNucleus' EnhancerTask:
  // <a
  // href="http://datanucleus.svn.sourceforge.net/viewvc/datanucleus/platform/enhancer/trunk/src/java/org/datanucleus/enhancer/tools/EnhancerTask.java?revision=5225">
  // org.datanucleus.enhancer.tools.EnhancerTask, Revision 5225</a>
  //
  // We write our own task instead of DataNucleus' task directly so that
  //   - we can change the behavior as we desire
  //   - we don't depend upon any DataNucleus classes at build time
  //   (they're not even available to us at build time)
  //   - we don't depend upon any DataNucleus classes at run time
  //   (we don't want to introduce extra dependencies upon the taskdef
  //    outside of appengine-tools-api.jar).

  private File dir;

    /** Only runs this task if the property is set. */
    private String ifpropertyset;

    /** The suffixes of the files to use. Defaults to files suffixed "jdo". */
    private String fileSuffixes = "jdo";

    /** Filesets of metadata files or class files to be enhanced. */
    Vector<FileSet> filesets = new Vector<FileSet>();

    /**
     * Default constructor
     */
    public EnhancerTask() {
      setClassname("com.google.appengine.tools.enhancer.Enhance");
      setFork(true); // Default to fork=true
    }

  /**
   * Execution method
   *
   * @throws BuildException Thrown when an error occurs when processing the task
   */
  @Override
  public void execute() throws BuildException {
      if (ifpropertyset != null) {
        if (getProject().getProperty(ifpropertyset) == null){
          log("Property " + ifpropertyset + " is not set. This task will not execute.", 
              Project.MSG_VERBOSE);
          return;
        }
      }

      File[] files = getFiles();
      if (files.length == 0) {
        log("Scanning for files with suffixes: " + fileSuffixes, Project.MSG_VERBOSE);
        StringTokenizer token = new StringTokenizer(fileSuffixes, ",");
        while (token.hasMoreTokens()) {
          DirectoryScanner ds = getDirectoryScanner(getDir());
          ds.setIncludes(new String[]{"**\\*." + token.nextToken()});
          ds.scan();
          for (int i = 0; i < ds.getIncludedFiles().length; i++) {
            createArg().setFile(new File(getDir(), ds.getIncludedFiles()[i]));
          }
        }
      } else {
        log("FileSet has " + files.length
            + " files. Enhancer task will not scan for additional files.", Project.MSG_VERBOSE);
        for (int i = 0; i < files.length; i++) {
          createArg().setFile(files[i]);
        }
      }

      super.execute();
    }

  /**
   * Whether to just check the enhancement state
   *
   * @param checkonly Whether to just check
   */
  public void setCheckonly(boolean checkonly) {
    if (checkonly) {
      createArg().setValue("-checkonly");
      log("Enhancer checkonly: " + checkonly, Project.MSG_VERBOSE);
    }
  }

  private DirectoryScanner getDirectoryScanner(File dir) {
    FileSet fileset = new FileSet();
    fileset.setDir(dir);
    return fileset.getDirectoryScanner(getProject());
  }

  /**
   * set output directory
   *
   * @param destdir output dir
   */
  public void setDestination(File destdir) {
    if (destdir != null && destdir.length() > 0) {
      createArg().setValue("-d");
      createArg().setFile(destdir);
      log("Enhancer destdir: " + destdir, Project.MSG_VERBOSE);
    }
  }

  /**
   * set API Adapter
   *
   * @param api API Adapter
   */
  public void setApi(String api) {
    if (api != null && api.length() > 0) {
      createArg().setValue("-api");
      createArg().setValue(api);
      log("Enhancer api: " + api, Project.MSG_VERBOSE);
    }
  }

  /**
   * Set the symbolic name of the ClassEnhancer to use
   *
   * @param enhancer Class Enhancer to use
   */
  public void setEnhancerName(String enhancer) {
    if (enhancer != null && enhancer.length() > 0) {
      createArg().setValue("-enhancerName");
      createArg().setValue(enhancer);
      log("Enhancer enhancerName: " + enhancer, Project.MSG_VERBOSE);
    }
  }

  /**
   * Set the persistence-unit name to enhance
   *
   * @param unit Name of the persistence-unit to enhance
   */
  public void setPersistenceUnit(String unit) {
    if (unit != null && unit.length() > 0) {
      createArg().setValue("-persistenceUnit");
      createArg().setValue(unit);
      log("Enhancer persistenceUnit: " + unit, Project.MSG_VERBOSE);
    }
  }

  /**
   * Sets the root dir for looking for files
   *
   * @param dir the root dir
   */
  @Override
  public void setDir(File dir) {
    this.dir = dir;
  }

  /**
   * Gets the root dir for looking for files
   *
   * @return the root dir
   */
  public File getDir() {
    return dir == null ? getProject().getBaseDir() : dir;
  }

  /**
   * Set one or more file suffixes for the input files. Suffixes are separated with a comma(,)
   *
   * @param suffixes the suffices
   */
  public void setFileSuffixes(String suffixes) {
    this.fileSuffixes = suffixes;
  }

  /**
   * set verbose
   *
   * @param verbose Whether to give verbose output
   */
  public void setVerbose(boolean verbose) {
    if (verbose) {
      createArg().setValue("-v");
      log("Enhancer verbose: " + verbose, Project.MSG_VERBOSE);
    }
  }

  /**
   * Add a fileset. @see ant manual
   *
   * @param fs the FileSet
   */
  public void addFileSet(FileSet fs) {
    filesets.addElement(fs);
  }

  protected File[] getFiles() {
    Vector<File> v = new Vector<File>();
    final int size = filesets.size();
    for (int i = 0; i < size; i++) {
      FileSet fs = (FileSet) filesets.elementAt(i);
      DirectoryScanner ds = fs.getDirectoryScanner(getProject());
      ds.scan();
      String[] f = ds.getIncludedFiles();
      for (int j = 0; j < f.length; j++) {
        String pathname = f[j];
        File file = new File(ds.getBasedir(), pathname);
        file = getProject().resolveFile(file.getPath());
        v.add(file);
      }
    }
    File[] files = new File[v.size()];
    v.copyInto(files);
    return files;
  }

  /**
   * Executes this task only if the property is set
   */
  public void setIf(String ifpropertyset) {
    this.ifpropertyset = ifpropertyset;
  }
}
