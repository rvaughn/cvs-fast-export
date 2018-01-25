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
import annotation.tailrec
import java.util.Date
import scala.collection.mutable.HashSet
import scala.collection.mutable.LinkedList

package org.rvaughn.scm.cvs {

  class Commit(n: String) {
    val id = n
    var author = ""
    var comment = List[String]()
    var time = 0l
    var branch = "TRUNK"
    var isBranchStart = false
    var rootOfBranches = Set[String]()
    var revisions = Set[Revision]()
    var parent: Commit = null
    var merges = Set[Commit]()
    var isTagFixup = false
    var synthetic = false

    def add(rev: Revision) {
      revisions += rev
    }

    def +=(rev: Revision) {
      add(rev)
    }

    def isBranchRoot: Boolean = {
      rootOfBranches.size > 0
    }

    def isChildOf(other: Commit): Boolean = parent == other

    def isAncestorOf(other: Commit): Boolean = {
      this != other && other.isSimpleDescendantOf(this)
    }

    def isDescendantOf(other: Commit): Boolean = {
      this != other && isSimpleDescendantOf(other)
    }

    @tailrec private
    def isSimpleDescendantOf(other: Commit): Boolean = {
      parent match {
        case null => false
        case _ => isChildOf(other) || parent.isSimpleDescendantOf(other)
      }
    }

    // returns true only if this commit contains only branch add revisions
    def isBranchAdd: Boolean = {
      revisions.forall(_.isBranchAddRoot)
    }

    def hasLiveRevisions: Boolean = {
      return revisions.exists(r => r.isLive)
    }

    def hasParent: Boolean = {
      parent != null
    }

    def contains(rev: Revision): Boolean = {
      revisions.contains(rev)
    }

    def containsDescendant(rev: Revision): Boolean = {
      revisions.exists(r => r.isDescendantOf(rev))
    }

    override def toString = {
      val b = new StringBuilder
      b.append("commit:  ").append(id).append("\n")
      if (parent != null) {
        b.append("parent:  ").append(parent.id).append("\n")
      }
      if (merges.size > 0) {
        b.append("merges:  ")
        merges.map(c => c.id).addString(b, "\n         ")
        b.append("\n")
      }
      b.append("author:  ").append(author).append("\n")
      if (branch != null) {
        b.append("branch:  ").append(branch).append("\n")
      }
      if (isBranchStart) {
        b.append("start:   true").append("\n")
      }
      if (isBranchRoot) {
        b.append("root of: ")
        rootOfBranches.addString(b, " ")
        b.append("\n")
      }
      b.append("time:    ").append(new Date(time)).append("\n")
      b.append("comment:").append("\n")
      comment.addString(b, "  ", "\n  ", "\n")
      b.append("revisions:").append("\n")
      revisions.toList.sortBy(_.path).map(r => b.append("  ").append(r.path).append(": ").append(r.number).append(if (r.isDead) " (dead)" else "").append("\n"))
      b.toString
    }
  }

  object Commit {
    class Data(start: List[Commit]) {
      var unsorted = Set[Commit]()
      var sorted = List[Commit]()

      unsorted ++= start
    }

    def timeSort(commits: List[Commit]): List[Commit] = {
      commits.sortBy(_.time)
    }

    def topoSort(commits: List[Commit]): List[Commit] = {
      val result = commits.foldLeft(new Data(commits))((d,c) => popNextNode(c, d))
      result.sorted.reverse
    }

    def popNextNode(commit: Commit, data: Data): Data = {
      if (commit != null && data.unsorted.contains(commit)) {
        data.unsorted -= commit
        popNextNode(commit.parent, data)
        commit.merges.foreach(c => popNextNode(c, data))
        data.sorted ::= commit
        data
      } else {
        data
      }
    }

    def timeAndTopoSort(commits: List[Commit]): List[Commit] = {
      topoSort(timeSort(commits))
    }
  }

}
