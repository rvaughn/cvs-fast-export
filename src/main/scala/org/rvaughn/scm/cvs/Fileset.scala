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

  class Fileset {
    var fileset = Map[CvsFile, Revision]()

    def files = fileset.keys
    def revisions = fileset.values
    def apply(f: CvsFile) = fileset(f)

    def +=(rev: Revision) = fileset += (rev.file -> rev)

    def ++(that: Traversable[Revision]): Fileset = {
      val result = new Fileset
      result.fileset = fileset
      that.foreach(result += _)
      result
    }

    def contains(file: CvsFile): Boolean = fileset.contains(file)

    override def toString: String = {
      val b = new StringBuilder
      fileset.values.toList.sortBy(_.path).foreach(r => 
        b.append("  ").append(r.path).append(": ").append(r.number).append(if (r.isDead) " (dead)" else "").append("\n"))
      b.toString
    }
  }

}
