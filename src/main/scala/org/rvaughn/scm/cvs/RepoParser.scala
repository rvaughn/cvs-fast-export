/*
 * Copyright (c) 2011 Roger Vaughn
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
import java.io.File
import java.io.PrintStream
import annotation.tailrec

package org.rvaughn.scm.cvs {

  class RepoParser(dir: String, log: PrintStream) {
    val base = dir
    val paths = scanpaths(new File(base)).sorted
    val repo = StringCache(findRepo(dir))

    // find out where the repository starts (if at all)
    @tailrec private
    def findRepo(path: String): String = {
      if (path == null) {
        null
      } else if (new File(path, "CVSROOT").exists) {
        path
      } else {
        findRepo(new File(path).getParent)
      }
    }

    def scanpaths(dir: File): List[String] = {
      // tried to use a for comprehension, but couldn't get the types to work??
      var paths: List[String] = List()
      // if we're in the Attic, then don't scan subdirs, because CVS doesn't
      val normalDir = dir.getName != "Attic"
      for (path <- dir.listFiles()) {
        if (path.getName().endsWith(",v") && path.getName() != ".directory_history,v") {
          paths ::= path.getPath
        } else if (path.isDirectory() && normalDir) {
          paths :::= scanpaths(path)
        }
      }
      paths
    }

    def parse(path: String, f: (CvsFile) => Unit) {
      val name = path.substring(base.length + 1, path.length - 2)
      f(new FileParser(new File(path), log).getFile(base, name, repo))
    }

    def foreach(f: CvsFile => Unit) {
      paths.foreach(path => parse(path, f))
    }

    def fileCount: Int = {
      paths.size
    }
  }

/*
 * paths are interpretted like so:
 *
 * |------------ base ------------| |-------- output path ---------------|
 * /subdir1/subdir2/subdir3/subdir4/subdir5/subdir6/subdir7/sourcefile.txt,v
 * |---- repo ----| |-- module ---| |-- relative path ----| |-- rcs file --|
 *
 * If the repository cannot be identified, then repo is considered to be
 * equal to base.
 *
 * We'll normally take (base - repo) as the module, and assume
 * everything else is the relative path. If repo cannot be identified,
 * then we assume that module is the last component of base.
 */

}
