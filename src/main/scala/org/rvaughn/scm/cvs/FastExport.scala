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
import java.io.PrintStream
import java.io.FileOutputStream

package org.rvaughn.scm.cvs {

  object FastExport {
    var log: PrintStream = System.err;
    var con: PrintStream = System.err;
    var t = new Array[Long](7);

    def formatTime(t: Long): String = {
      def hours(t: Long): String = t.toString
        
      def minutes(t: Long): String = {
        if (t >= 60) {
          hours(t / 60) + ":" + ("00" + (t % 60)).takeRight(2)
        } else {
          t.toString
        }
      }
        
      def seconds(t: Long): String = {
        if (t >= 60) {
          minutes(t / 60) + ":" + ("00" + (t % 60)).takeRight(2)
        } else {
          t.toString
        }
      }
        
      if (t >= 1000) {
        seconds(t / 1000) + "." + ("000" + (t % 1000)).takeRight(3)
      } else {
        t.toString
      }
    }

    def getRepoPath(args: Array[String]): String = {
      val rest = Config.parse(args)
      
      if (rest.length != 1) {
        System.err.println(Config.help)
        sys.exit(1)
      }

      rest(0)
    }
      
    def getLogStream = new PrintStream(
      if (Config.quiet && Config.logOutput) {
        new FileOutputStream(Config.logFile)
      } else if (Config.logOutput) {
        new TeeOutputStream(System.err, new FileOutputStream(Config.logFile))
      } else {
        System.err
      }
    )

    def getConsoleStream = {
      if (Config.quiet || !Config.progress) {
        new PrintStream(new NullOutputStream)
      } else {
        System.err
      }
    }

    def buildModel(repo: String): Model = {
      val b = new ModelBuilder(log, con)
      b.parse(new RepoParser(repo, log))
      t(1) = System.currentTimeMillis
      val m = b.build
      t(2) = System.currentTimeMillis
      if (Config.logs) {
        log.println("writing log files...")
        m.report
      }
      if (Config.logFilesets) {
        b.reportFilesets
      }
      t(3) = System.currentTimeMillis
      m
    }

    def exportModel(m: Model): Long = {
      log.println("exporting...")
      val e = new Exporter(log, con)
      e.export(m)
      t(4) = System.currentTimeMillis
      if (Config.exportToRepo && Config.optimize) {
        log.println("optimizing...")
        e.optimize
      }
      t(5) = System.currentTimeMillis
      e.bytesExported
    }

    def printSummary(m: Model, b: Long) {
      log.println
      log.println("-" * 69)
      log.println("files           : " + m.fileCount)
      log.println("commits         : " + m.commitCount + " (" + m.syntheticCommitCount + " new)")
      log.println("revisions       : " + m.revisionCount)
      log.println("branches        : " + m.branchCount)
      log.println("tags            : " + m.tagCount)
      log.println("bytes           : " + b)
      log.println("import time     : " + formatTime(t(1) - t(0)))
      log.println("conversion time : " + formatTime(t(2) - t(1)))
      if (Config.logs) {
        log.println("logging time    : " + formatTime(t(3) - t(2)))
      }
      log.println("export time     : " + formatTime(t(4) - t(3)))
      if (Config.exportToRepo && Config.optimize) {
        log.println("optimize time   : " + formatTime(t(5) - t(4)))
      }
      log.println("total time      : " + formatTime(t(5) - t(0)) + " (" + (t(5) - t(0)) + " ms)")
      log.println("-" * 69)
    }

    def main (args: Array[String]) {
      val repo = getRepoPath(args)      
      log = getLogStream
      con = getConsoleStream

      try {
        t(0) = System.currentTimeMillis
        val m = buildModel(repo)
        val b = exportModel(m)
        printSummary(m, b)
      } catch {
        case t: Throwable => 
          t.printStackTrace
      } finally {
        log.close
      }
    }
  }
}
