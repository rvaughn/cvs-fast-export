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
import java.io.OutputStream
import java.io.PrintStream

package org.rvaughn.scm.cvs {

  trait ExportWriter extends Object with ExportMarks with DataFormatting with AuthorMapping with BranchMapping {

    var bytesExported = 0l

    def export(tag: Tag, out: PrintStream) {
      out.println("reset refs/tags/" + tag.name)
      out.println("from :" + markForTag(tag))
      out.println
    }

    def export(commit: Commit, out: PrintStream) {
      val (author, email) = mapAuthor(commit.author)
      val branch = mapBranch(commit.branch)
      val comment = commentBlock(commit)
      out.println("# cvs commit " + commit.id)
      if (commit.isTagFixup) {
        out.println("commit TAG_FIXUP")
      } else {
        out.println("commit refs/heads/" + branch)
      }
      out.println("mark :" + markForCommit(commit))
      out.println("committer " + author + " <" + email + "> " + formatTime(commit.time))
      out.println("data " + comment.length)
      if (comment.length > 0) {
        out.write(comment)
        out.println
      }
      if (commit.hasParent && hasMark(commit.parent)) {
        out.println("from :" + markForCommit(commit.parent))
      }
      for (merge <- commit.merges) {
        out.println("merge :" + markForCommit(merge))
      }
      if (commit.isTagFixup) {
        // for tag fixups, we start from an unknown fileset
        out.println("deleteall")
        commit.revisions.toList.sortBy(_.path).foreach(r => exportFixup(r, out))
      } else {
        commit.revisions.toList.sortBy(_.path).foreach(r => export(r, out))
      }
      out.println
    }

    def commentBlock(commit: Commit): Array[Byte] = {
      commit.comment.mkString("\n").getBytes("utf-8")
    }

    // export a changed revision
    def export(rev: Revision, out: PrintStream) {
      // TODO: write paths in UTF-8
      if (rev.isIgnored) {
        // ignore these
      } else if (rev.isDead) {
        if (rev.isRenamed) {
          // this is a really special fuck-up - a file was renamed and deleted at the same time
          // yes, CVS(NT) really does this.
          out.println("D " + quotedPath(rev.parent.path))
        } else {
          out.println("D " + quotedPath(rev.path))
        }
      } else {
        if (rev.isRenamed) {
          // double-quote these for safety. this should handle the case
          // where there are spaces in the filename.
          out.println("R \"" + quotedPath(rev.parent.path) + "\" \"" + quotedPath(rev.path) + "\"")
        }
        out.println("M " + fileModeForRev(rev) + " :" + markForRevision(rev) + " " + quotedPath(rev.path))
      } 
    }

    // export a revision for a fixup branch
    // we ignore deletes and renames for these, since we start from an empty file set
    def exportFixup(rev: Revision, out: PrintStream) {
      // TODO: write paths in UTF-8
      if (rev.isIgnored && rev.isDead) {
        // ignore these
      } else {
        out.println("M " + fileModeForRev(rev) + " :" + markForRevision(rev) + " " + quotedPath(rev.path))
      } 
    }

    def fileModeForRev(rev: Revision): String = {
      "100644"
    }

    def exportBlob(rev: Revision, data: List[Array[Byte]], out: PrintStream) {
      val bytes = blobLength(data)
      bytesExported += bytes
      out.println("blob")
      out.println("mark :" + markForRevision(rev))
      out.println("data " + bytes)
      exportRevision(data, out)
      out.println
    }

    def blobLength(data: List[Array[Byte]]): Int = {
      data.foldLeft(0)(_ + _.length)
    }

    def exportRevision(data: List[Array[Byte]], out: OutputStream) {
      data.foreach(l => out.write(l))
    }
  }

}
