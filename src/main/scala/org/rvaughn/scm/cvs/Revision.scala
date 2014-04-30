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
import java.util.Date
import java.io.File
import java.text.SimpleDateFormat
import java.util.TimeZone

package org.rvaughn.scm.cvs {

  class Revision(n: String, f: CvsFile) {
    val file = f
    val number = n
    var filename = ""
    var commitid = ""
    var time = 0l
    var state = ""
    var deltatype = ""
    var kopt = ""
    var next = ""
    // var bugid: String = null
    var permissions = ""
    var deltaOffset = 0l
    var branches = Set[String]()
    var merges = Set[String]()
    var isFixup = false

    // commit stuff
    var author = ""
    var comment = List[String]()

    override def toString = {
      val b = new StringBuilder

      b.append("revision:      ").append(number).append("\n")
      b.append("  date:        ").append(new Date(time)).append("\n")
      b.append("  author:      ").append(author).append("\n")
      b.append("  state:       ").append(state).append("\n")
      b.append("  delta type:  ").append(deltatype).append("\n")
      b.append("  kopt:        ").append(kopt).append("\n")
      b.append("  commit:      ").append(commitid).append("\n")
      b.append("  filename:    ").append(filename).append("\n")
      b.append("  next:        ").append(next).append("\n")
      b.append("  permissions: ").append(permissions).append("\n")
      b.append("  branches:    ").append(branches.mkString(" ")).append("\n")
      b.append("  merges:      ").append(merges.mkString(" ")).append("\n")
      b.append("  comment:\n")
      comment.foreach(line => b.append("    ").append(line).append("\n"))

      b.toString
    }

    def addBranch(rev: String) {
      branches += rev
    }

    def addMergeParent(rev: String) {
      merges += rev
    }

    // this is the *logical* dir, not the physical source
    def dir: String = {
      if (file.dir != null && file.dir.endsWith("Attic")) {
        new File(file.dir).getParent
      } else {
        file.dir
      }
    }

    // this is the logical output path, not the CVS physical source
    def path: String = new File(dir, filename).getPath

    def rcsFileName: String = file.rcsFile

    // includes the repository name, module path, and file name
    def rcsFileFullPath: String = file.path

    // includes the module path and file name, WITHOUT the repo name
    def rcsFileRelativePath: String = file.rcsPath

    def dateTimeString: String = {
      val dateformat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss")
      dateformat.setTimeZone(TimeZone.getTimeZone("UTC"))
      dateformat.format(new Date(time))
    }

    def branch: String = file.branchName(branchNumber)

    def isTrunk: Boolean = branch == "TRUNK"

    def isRoot: Boolean = number == "1.1"

    def isChild: Boolean = number != "1.1"

    def dead: Boolean = state == "dead"

    def isRemoved: Boolean = dead

    def isDead: Boolean = dead

    def isLive: Boolean = !dead

    def isAdded: Boolean = !dead && (increment == "1" || isResurrected || isFixup)

    def isResurrected: Boolean = isChild && parent.isDead

    def isChanged: Boolean = !isRemoved && !isAdded

    def isBranchAddRoot: Boolean = isRoot && dead

    def isFirstBranchChange: Boolean = !isRoot && increment == "1"

    // if we've resurrected with a new name, we consider this an add, not a rename
    // a fixup overrides this to make it an add
    def isRenamed: Boolean = !isFixup && isChild && filename != parent.filename && parent.isLive

    // we ignore branch add roots and deletions on fixup branches
    def isIgnored = isDead && (isRoot || isFixup)

    def digits: List[String] = number.split("[.]").toList

    def branchNumber: String = digits.init.mkString(".")

    def increment: String = digits.last

    def predecessor: String = {
      val d = digits
      if (d.last == "1") {
        d.dropRight(2).mkString(".")
      } else {
        d.init.mkString(".") + "." + (d.last.toInt - 1).toString
      }
    }

    def branchedFrom: String = digits.dropRight(2).mkString(".")

    def parent: Revision = sibling(predecessor)

    def sibling(num: String): Revision = file.revisions(num)

    def isDescendantOf(other: Revision): Boolean = {
      if (other.file != file) {
        false
      } else if (other.number == number) {
        false
      } else {
        // cases:
        // this     other    result  comment
        // 1.5      1.3      true    other is an earlier rev in the same branch
        // 1.4.2.1  1.4.2.4  false   other is a later rev in the same branch
        // 1.4.2.1  1.3      true    other is an earlier rev on a parent branch
        // 1.4.2.1  1.3.6.2  false   other is on a different branch
        // 1.5      1.3.6.2  false   other is on a different branch
        // 1.4.2.1  1.4      true    other is the parent rev of this branch
        // 1.4      1.4      false   other is the same revision
        // 
        val ourDigits = digits
        val otherDigits = other.digits
        if (ourDigits.size < otherDigits.size) {
          // we can't be a descendant if other is more branched than we are
          false
        } else {
          // otherwise, we're a descendant if the initial digits match
          // and the last digit of other is smaller than or equal to our digit at the same position
          ourDigits.take(otherDigits.size - 1) == otherDigits.init &&
          ourDigits(otherDigits.size - 1).toInt >= otherDigits.last.toInt
        }
      }
    }

    def isAncestorOf(other: Revision): Boolean = other.isDescendantOf(this)

    def revString: String = path + " " + number
  }

}
