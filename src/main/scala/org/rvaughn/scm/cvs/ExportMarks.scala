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
import scala.collection.mutable.HashMap

package org.rvaughn.scm.cvs {

  trait ExportMarks {
    var lastMark = 0
    val commitMarks = new HashMap[Commit, Int]
    val tagMarks = new HashMap[String, Int]
    val revisionMarks = new HashMap[Revision, Int]

    def hasMark(commit: Commit): Boolean = commitMarks.contains(commit)

    def markForCommit(commit: Commit): Int = {
      if (commitMarks.contains(commit)) {
        commitMarks(commit)
      } else {
        lastMark += 1
        commitMarks += (commit -> lastMark)
        lastMark
      }
    }

    def setDummyMark(commit: Commit) {
      // use the mark for the parent of this commit as this commit's mark
      // this lets us "skip" this commit in the output graph
      if (commit.hasParent) {
        commitMarks += (commit -> markForCommit(commit.parent))
      }
    }

    def markForTag(tag: Tag): Int = {
      if (tagMarks.contains(tag.name)) {
        tagMarks(tag.name)
      } else {
        val mark = markForCommit(tag.commit)
        tagMarks += (tag.name -> mark)
        mark
      }
    }

    def markForRevision(revision: Revision): Int = {
      if (revisionMarks.contains(revision)) {
        revisionMarks(revision)
      } else {
        lastMark += 1
        revisionMarks += (revision -> lastMark)
        lastMark
      }
    }

    def exportMarks {
      exportBlobMarks
      exportCommitMarks
      exportTagMarks
    }

    def exportCommitMarks {
      val out = new PrintStream(new FileOutputStream("commit-marks.log"))
      commitMarks.toList.sortBy(_._2).foreach(m => out.println(":" + m._2 + " " + m._1.id))
      out.close
    }

    def exportTagMarks {
      val out = new PrintStream(new FileOutputStream("tag-marks.log"))
      tagMarks.toList.sortBy(_._2).foreach(m => out.println(":" + m._2 + " " + m._1))
      out.close
    }

    def exportBlobMarks {
      val out = new PrintStream(new FileOutputStream("blob-marks.log"))
      revisionMarks.toList.sortBy(_._2).foreach(m => if (m._1.isLive) out.println(":" + m._2 + " " + m._1.revString))
      out.close
    }
  }

}
