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
import scala.collection.mutable.HashMap

package org.rvaughn.scm.cvs {

  class CvsFile(b: String, n: String, r: String) {
    var head = ""
    var description = ""
    var rcsFileSize = new File(path).length
    var revisions = HashMap[String, Revision]()  // rev num to rev
    var branches = HashMap[String, String]()     // branch num to name
    var tags = HashMap[String, String]()         // tag name to rev num

    // given base + relative path
    def base = b
    def name = n
    def dir = StringCache(new File(n).getParent)

    // full path
    def path = new File(b, n + ",v").getPath

    // CVS interpretation
    def repository = if (r != null) r else StringCache(new File(b).getParent)
    def module = if (r != null) StringCache(b.substring(r.length + 1)) else StringCache(new File(b).getName)
    def relativePath = StringCache(new File(n).getParent)
    def rcsFile = new File(n + ",v").getName

    def rcsPath = new File(module, n + ",v").getPath

    // discard objects we don't need long-term
    def cleanup {
      // branches = null
      tags = null
    }

    override def toString = {
      val b = new StringBuilder
      
      b.append("file:          ").append(name).append("\n")
      b.append("  description: ").append(description).append("\n")
      b.append("  head:        ").append(head).append("\n")
      b.append("  branches:\n")
      branches.keys.toList.sorted.foreach(k => b.append("    ").append(k).append(": ").append(branches(k)).append("\n"))
      b.append("  tags:\n")
      tags.keys.toList.sorted.foreach(k => b.append("    ").append(k).append(": ").append(tags(k)).append("\n"))
      revisions.values.toList.sortBy(_.time).reverse.foreach(r => b.append(r))

      b.toString
    }

    def addBranch(name: String, rev: String) {
      branches += (rev -> name)
    }

    def addTag(name: String, rev: String) {
      tags += (name -> rev)
    }

    def addRevision(rev: Revision) {
      revisions += (rev.number -> rev)
    }

    def getRevision(num: String): Revision = {
      revisions(num)
    }

    def branchName(num: String): String = {
      if (branches.contains(num))
        branches(num)
      else if (num == "1")
        "TRUNK"
      else
        throw new IllegalArgumentException("no name found for branch " + num + " in file " + name)
    }
  }

}
