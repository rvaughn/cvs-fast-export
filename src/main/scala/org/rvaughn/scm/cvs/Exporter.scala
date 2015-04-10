/*
 * Copyright (c) 2015 Roger Vaughn
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
import annotation.tailrec
import collection.JavaConversions._
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.PrintStream
import java.util.zip.GZIPOutputStream

package org.rvaughn.scm.cvs {

  class Exporter(log: PrintStream, con: PrintStream) extends Object with ExportWriter with Transformations {

    var revNum = 0
    var revCount = 0

    def export(model: Model) {
      if (Config.exportToRepo) {
        exportToGit(model)
      } else {
        exportToFile(model)
      }
      if (Config.logs) {
        exportAuthors
        exportMarks
      }
    }

    def exportToFile(model: Model) {
      val out = createExportFile
      try {
        exportToStream(model, out)
      } catch {
        case ex: IOException => log.println("export has failed")
      } finally {
        out.close
      }
    }

    def createExportFile: PrintStream = {
      if (Config.exportToFile && Config.gzip) {
        new UnixPrintStream(new GZIPOutputStream(new FileOutputStream(Config.filename)))
      } else if (Config.exportToFile) {
        new UnixPrintStream(new FileOutputStream(Config.filename))
      } else if (Config.gzip) {
        new UnixPrintStream(new GZIPOutputStream(System.out))
      } else {
        System.out
      }
    }

    def exportToGit(model: Model) {
      class Monitor(input: InputStream) extends Thread {
        val in = new BufferedReader(new InputStreamReader(input))
        override def run {
          try {
            var line = in.readLine
            while (line != null) {
              log.println(line)
              line = in.readLine
            }
          } catch {
            case ex: IOException => // do nothing
          }
        }
      }

      val init = if (Config.bare) {
        new ProcessBuilder(List("git", "init", "--bare", Config.exportToDir)).start
      } else {
        new ProcessBuilder(List("git", "init", Config.exportToDir)).start
      }
      init.waitFor
      
      val fastImportCmd = List("git", "fast-import") ++ (
          if (Config.importMarks) {
            List("--import-marks=" + Config.marksImportFile)
          } else {
            List()
          }
        ) ++ (
          if (Config.exportMarks) {
            List("--export-marks=" + Config.marksExportFile)
          } else {
            List()
          }
        ) ++ (
          if (Config.force) {
            List("--force")
          } else {
            List()
          }
        )
      val builder = new ProcessBuilder(fastImportCmd)
      builder.directory(new File(Config.exportToDir))
      val process = builder.start

      val monitor = new Monitor(process.getInputStream).start
      val errMonitor = new Monitor(process.getErrorStream).start
      val out = new UnixPrintStream(process.getOutputStream)
      try {
        exportToStream(model, out)
      } catch {
        case ex: IOException => log.println("export has failed")
      } finally {
        out.close
      }
      process.waitFor

      val f = if (Config.bare) {
        new File(Config.exportToDir, "TAG_FIXUP")
      } else {
        new File(Config.exportToDir, ".git/TAG_FIXUP")
      }
      if (f.exists) {
        f.delete
      }
    }

    def optimize {
      class Monitor(input: InputStream) extends Thread {
        val in = new BufferedReader(new InputStreamReader(input))
        override def run {
          try {
            var line = in.readLine
            while (line != null) {
              log.println(line)
              line = in.readLine
            }
          } catch {
            case ex: IOException => // do nothing
          }
        }
      }

      val builder = new ProcessBuilder(List("git", "repack", "-adf"))
      builder.directory(new File(Config.exportToDir))
      val process = builder.start

      val monitor = new Monitor(process.getInputStream).start
      val errMonitor = new Monitor(process.getErrorStream).start
      process.waitFor
    }

    def exportToStream(model: Model, out: PrintStream) {
      // make sure our marks are stable
      primeBlobMarks(model)
      primeCommitMarks(model)

      if (Config.blobs) {
        exportBlobs(model, out)
      }
      if (Config.commits) {
        exportCommits(model, out)
      }
      if (Config.tags) {
        exportTags(model, out)
      }
    }

    def exportBlobs(model: Model, out: PrintStream) {
      val fileCount = model.files.size
      var fileNum = 0
      for (file <- model.files.values.toList.sortBy(_.path)) {
        if (out.checkError) {
          throw new IOException
        }
        fileNum += 1
        con.print("\rexporting file " + fileNum + " of " + fileCount)
        con.print("\u001b[0K")
        // con.print(" (" + file.name + ")")
        con.print("\u001b7")
        revNum = 0
        revCount = file.revisions.size
        val p = new FileParser(new File(file.path), log)
        exportBranch(file.head, file, p, null, out)
        p.close
      }
      con.println
    }

    def exportCommits(model: Model, out: PrintStream) {
      val commitCount = model.commits.size
      var commitNum = 0
      for (commit <- Commit.timeAndTopoSort(model.commits.values.toList)) {
        if (out.checkError) {
          throw new IOException
        }
        commitNum += 1
        con.print("\rexporting commit " + commitNum + " of " + commitCount)
        if (!commit.isBranchAdd) {
          export(commit, out)
        } else {
          setDummyMark(commit)
        }
      }
      con.println
    }

    def exportTags(model: Model, out: PrintStream) {
      val tagCount = model.tags.size
      var tagNum = 0
      for (tag <- model.tags.values.toList.sortBy(_.name)) {
        tagNum += 1
        con.print("\rexporting tag " + tagNum + " of " + tagCount)
        export(tag, out)
      }
      con.println
    }

    // export one branch of the file, following next pointers
    @tailrec private
    def exportBranch(r: String, f: CvsFile, p: FileParser, seed: List[Array[Byte]], out: PrintStream) {
      if (r != "") {
        revNum += 1
        if (f.rcsFileSize > 1000000l) {
          con.print("\u001b8")
          con.print("\u001b7")
          con.print(" (rev " + revNum + "/" + revCount + ")")
        }
        val rev = f.revisions(r)
        val delta = p.getText(rev.deltaOffset)
        val data =
          if (seed == null) {
            delta
          } else {
            FileParser.applyDelta(seed, delta)
          }
        if (rev.isLive) {
          val text = expandEncodings(rev, data)
          exportBlob(rev, text, out)
          if (out.checkError) {
            log.println("error in blob output stream")
          }
        }
        // we can't do this here because it breaks the tail recursion. no, really.
        // rev.branches.foreach(b => exportBranch(b, f, p, data, out))
        exportChildBranches(rev, f, p, data, out)
        exportBranch(rev.next, f, p, data, out)
      }
    }

    def exportChildBranches(rev: Revision, f: CvsFile, p: FileParser, data: List[Array[Byte]], out: PrintStream) {
      rev.branches.foreach(b => exportBranch(b, f, p, data, out))
    }

    def primeBlobMarks(model: Model) {
      for (file <- model.files.values.toList.sortBy(_.path)) {
        for (rev <- file.revisions.values.toList.sortBy(_.time)) {
          markForRevision(rev)
        }
      }
    }

    def primeCommitMarks(model: Model) {
      for (commit <- model.commits.values.toList.sortBy(_.id)) {
        markForCommit(commit)
      }
    }
  }

}
