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
package org.rvaughn.scm.cvs {

  class Branch(n: String) {
    val name = n
    var time = 0l
    var root: Commit = null
    var parent: Branch = null

    var roots = Set[Revision]()
    var adds = Set[Revision]()

    def setRootCommit(commit: Commit) {
      if (root != null) {
        root.rootOfBranches -= name
      }
      root = commit
      if (commit != null) {
        commit.rootOfBranches += name
      }
    }

    def setParent(commit: Commit, branches: Map[String, Branch]) {
      setRootCommit(commit)
      if (root != null) {
        parent = branches(root.branch)
      } else {
        parent = null
      }
    }

    override def toString: String = {
      val b = new StringBuilder
      b.append("branch:  ").append(name).append("\n")
      if (parent != null) {
        b.append("parent:  ").append(parent.name).append("\n")
      }
      if (root != null) {
        b.append("origin:  ").append(root.id).append("\n")
      }
      b.append("roots:").append("\n")
      roots.toList.sortBy(_.path).map(r => b.append("  ").append(r.path).append(": ").append(r.number).append(if (r.isDead) " (dead)" else "").append("\n"))
      b.append("adds:").append("\n")
      adds.toList.sortBy(_.path).map(r => b.append("  ").append(r.path).append(": ").append(r.number).append(if (r.isDead) " (dead)" else "").append("\n"))
      b.toString      
    }
  }

}
