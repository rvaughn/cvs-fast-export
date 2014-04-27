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
import scala.collection.mutable.HashMap
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.WrappedArray

package org.rvaughn.scm.cvs {

  object LineMode extends Enumeration {
    type LineMode = Value
    val Unix = Value("unix")
    val Mac = Value("mac")
    val DOS = Value("dos")
    val Binary = Value("binary") // binary - no translation
  }
  import LineMode._

  trait Transformations {
    // modes: 
    //  k  - preserve keywords but don't expand values: "$Revision$"
    //  v  - expand keywords to values only: "$Revision: 1.1 $" becomes "1.1"
    //  kv - normal keyword expansion
    //  L  - Unix (Linux) line endings
    //  D  - Windows (DOS) line endings
    //  M  - Mac line endings
    //  o  - keep the "old" keyword string: IOW don't touch existing keywords
    // encodings:
    //  b  - binary - no keywords or line endings
    //  B  - binary - no keywords or line endings
    //  u  - Unicode. stored internally as UTF-8, but checked out as UTF-16 (UCS-2).
    //  t  - text file. default if no other encoding

    // TODO: handle keywords and encodings separately.
    //       handle line endings.
    //       throw exception if we encounter any flags we don't handle:
    //       o, k, v, L, D, M

    val defaultLineMode = LineMode.withName(Config.lineStyle)
    val keywords = HashMap[String, (Revision)=>String]()
    keywords += ("Id"        -> (r => r.rcsFileName + " " + r.number + " " + r.dateTimeString + " " + r.author + " " + r.state))
    keywords += ("Header"    -> (r => r.rcsFileFullPath + " " + r.number + " " + r.dateTimeString + " " + r.author + " " + r.state))
    keywords += ("CVSHeader" -> (r => r.rcsFileRelativePath + " " + r.number + " " + r.dateTimeString + " " + r.author + " " + r.state))
    keywords += ("Revision"  -> (r => r.number))
    keywords += ("Source"    -> (r => r.rcsFileFullPath))
    keywords += ("Author"    -> (r => r.author))
    keywords += ("Date"      -> (r => r.dateTimeString))
    keywords += ("RCSfile"   -> (r => r.rcsFileName))
    keywords += ("Branch"    -> (r => if (r.isTrunk) "" else r.branch))
    keywords += ("CommitId"  -> (r => r.commitid))
    keywords += ("State"     -> (r => r.state))
    // also Name, Locker

    val logKeyword = HashMap[String, (Revision)=>String]()
    logKeyword += ("Log" -> (_ => ""))

    class Region(s: Int, e: Int, k: String) {
      val start = s;
      val end = e;
      val keyword = k;
    }

    def expandEncodings(rev: Revision, data: List[Array[Byte]]): List[Array[Byte]] = {
      optionallyExpandUnicode(
        rev, 
        translateLineEnds(
          rev, optionallyExpandKeywords(rev, data)))
    }

    def optionallyExpandUnicode(rev: Revision, data: List[Array[Byte]]): List[Array[Byte]] = {
      if (rev.kopt.contains('u')) {
        expandUnicode(data)
      } else {
        data
      }
    }

    def expandUnicode(data: List[Array[Byte]]): List[Array[Byte]] = {
      data.map(l => new String(l, "utf-8").getBytes("utf-16le"))
    }

    def translateLineEnds(rev: Revision, data: List[Array[Byte]]): List[Array[Byte]] = {
      val lineMode = lineModeFor(rev)
      // only DOS and Mac need translation - Unix and Binary are unchanged
      if (lineMode == DOS) {
        data.map(l => replaceNewline(l, List[Byte]('\r', '\n')))
      } else if (lineMode == Mac) {
        data.map(l => replaceNewline(l, List[Byte]('\r')))
      } else {
        data
      }
    }

    def replaceNewline(line: Array[Byte], end: List[Byte]): Array[Byte] = {
      if (line.length > 0 && line(line.length - 1) == '\n') {
        replaceNewline(wrapByteArray(line), end).toArray
      } else {
        line
      }
    }

    def replaceNewline(line: WrappedArray[Byte], end: List[Byte]): WrappedArray[Byte] = {
      line.init ++ end
    }

    //  L  - Unix (Linux) line endings
    //  D  - Windows (DOS) line endings
    //  M  - Mac line endings
    def lineModeFor(rev: Revision): LineMode = {
      if (rev.kopt.contains('b') || rev.kopt.contains('B')) {
        Binary
      } else if (rev.kopt.contains('L')) {
        Unix
      } else if (rev.kopt.contains('D')) {
        DOS
      } else if (rev.kopt.contains('M')) {
        Mac
      } else {
        defaultLineMode
      }
    }

    def optionallyExpandKeywords(rev: Revision, data: List[Array[Byte]]): List[Array[Byte]] = {
      if (rev.kopt.contains('v') && rev.kopt.contains('k')) {
        expandKeywords(rev, data)
      } else {
        data
      }
    }

    def expandKeywords(rev: Revision, data: List[Array[Byte]]): List[Array[Byte]] = {
      if (Config.keywords) {
        if (Config.logKeywords) {
          data.flatMap(line => replaceLogKeywords(line, rev))
        } else {
          data.map(line => replaceKeywords(line, rev))
        }
      } else {
        data
      }
    }

    def replaceKeywords(line: Array[Byte], rev: Revision): Array[Byte] = {
      replaceKeywords(wrapByteArray(line), rev).toArray
    }

    def replaceKeywords(line: WrappedArray[Byte], rev: Revision): WrappedArray[Byte] = {
      val region = findKeyword(line, keywords)
      if (region == None) {
        line
      } else {
        val start = region.get.start
        val end = region.get.end
        val keyword = region.get.keyword
        val s1 = line.take(start)
        val s2 = keywordArray(keyword, keywords(keyword)(rev))
        val s3 = replaceKeywords(line.drop(end + 1), rev)
        s1 ++ s2 ++ s3
      }
    }

    def replaceLogKeywords(line: Array[Byte], rev: Revision): List[Array[Byte]] = {
      replaceLogKeywords(wrapByteArray(line), rev).map(_.toArray)
    }

    def replaceLogKeywords(line: WrappedArray[Byte], rev: Revision): List[WrappedArray[Byte]] = {
      def trimRight(s: WrappedArray[Byte]): WrappedArray[Byte] = {
        s.reverse.dropWhile(b => b == ' ' || b == '\t').reverse
      }
      
      def logLine1: WrappedArray[Byte] = {
        arrayFromString("$Log: " + rev.rcsFileName + " $\n")
      }

      def logLine2: WrappedArray[Byte] = {
        arrayFromString("Revision " + rev.number + "  " + rev.dateTimeString + "  " + rev.author + "\n")
      }

      val lines = ListBuffer[WrappedArray[Byte]]()
      val region = findKeyword(line, logKeyword)
      if (region == None) {
        val s = replaceKeywords(line, rev)
        lines += s
      } else {
        // Expansion of extra keywords on a line with a $Log$ keyword is
        // royally fucked. It is extremely difficult to deduce what the hell
        // CVS is doing in this case. It seems to always fully expand the
        // $Log$ line itself, but prefixed keywords sometimes get expanded
        // on the following lines, sometimes not. I've got no hope of
        // matching it perfectly without examining the CVS code, and that's
        // more effort than I'm willing to invest. This is uncommon enough
        // a case that I feel safe punting on it.
        //
        // Instead, we always fully expand any keywords encountered.
        val start = region.get.start
        val end = region.get.end
        val keyword = region.get.keyword
        val s1 = replaceKeywords(line.take(start), rev)
        val s3 = replaceKeywords(line.drop(end + 1), rev)
        lines += s1 ++ logLine1
        lines += s1 ++ logLine2
        rev.comment.foreach(s => lines += s1 ++ arrayFromString(s + "\n"))
        lines += trimRight(s1) ++ s3
      }
      lines.toList
    }

    // can we speed this up at all? perhaps by looking for the second $ immediately?
    def findKeyword(line: WrappedArray[Byte], keywords: HashMap[String, (Revision)=>String]): Option[Region] = {
      var pos = line.indexOf('$')
      while (pos >= 0) {
        val start = pos
        pos += 1
        // this is basically a takeWhile
        while (pos < line.length && Character.isLetter(line(pos))) {
          pos += 1
        }
        if (pos < line.length) {
          val keyword = new String(line.slice(start + 1, pos).toArray, "utf-8")
          if (keywords.contains(keyword)) {
            if (line(pos) == '$') {
              return Some(new Region(start, pos, keyword))
            } else if (line(pos) == ':') {
              pos = line.indexOf('$', pos + 1)
              if (pos == -1) {
                // if there's no other $, then there's no keyword here, exit now
                return None
              } else {
                return Some(new Region(start, pos, keyword))
              }
            }
            // else it's not a real keyword string - try again
          }
        }
        pos = line.indexOf('$', pos)
      }
      None
    }

    def keywordArray(keyword: String, value: String): WrappedArray[Byte] = arrayFromString(keywordString(keyword, value))

    def arrayFromString(str: String): WrappedArray[Byte] = wrapByteArray(str.getBytes("utf-8"))

    def keywordString(keyword: String, value: String): String = "$" + keyword + (if (value.isEmpty) "$" else ": " + value + " $")
  }

}
