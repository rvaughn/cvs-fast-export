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
import java.io.PrintWriter
import java.io.FileOutputStream
import java.io.File

package org.rvaughn.scm.cvs {

  class Model {
    var files: Map[String, CvsFile] = Map()
    var commits: Map[String, Commit] = Map()
    var tags: Map[String, Tag] = Map()
    var branches: Map[String, Branch] = Map()

    def fileCount = files.size
    def commitCount = commits.size
    def syntheticCommitCount = commits.count { (p) => p._2.synthetic }
    def tagCount = tags.size
    def branchCount = branches.size
    def revisionCount = files.foldLeft(0) { (n, p) => n + p._2.revisions.size }

    def report {
      reportCommits
      reportTags
      reportBranches
      // reportAuthors
    }

    def reportCommits {
      val out = new PrintWriter(new FileOutputStream("commits.log"))
      try {
        Commit.timeAndTopoSort(commits.values.toList).reverse.foreach(c => if (!c.isTagFixup) out.println(c))
      } finally {
        out.close
      }
    }

    def reportAuthors {
      val out = new PrintWriter(new FileOutputStream("authors.log"))
      try {
        val authors = commits.values.map(c => c.author).toSet
        authors.foreach(out.println)
      } finally {
        out.close
      }
    }

    def reportTags {
      if (Config.splitTags) {
        val dir = new File("tags");
        dir.mkdir;
        for (tag <- tags.values) {
          val out = new PrintWriter(new FileOutputStream("tags/" + tag.name))
          out.println(tag)
          out.close
        }
      } else {
        val out = new PrintWriter(new FileOutputStream("tags.log"))
        try {
          tags.values.toList.sortBy(_.name).foreach(out.println)
        } finally {
          out.close
        }
      }
    }

    def reportBranches {
      val out = new PrintWriter(new FileOutputStream("branches.log"))
      try {
        branches.values.toList.sortBy(_.name).foreach(out.println)
      } finally {
        out.close
      }
    }
  }

}
