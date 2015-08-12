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
import java.io.PrintStream
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.GregorianCalendar
import java.util.TimeZone
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.ListBuffer
import scala.util.matching.Regex

package org.rvaughn.scm.cvs {

  class FileParser(file: File, log: PrintStream) {
    val branchRE = """^(\d+(\.\d+)+)\.0\.(\d+)$""".r
    val tagRE = """^\d+(\.\d+)+$""".r

    val path = file.getPath
    val in = new BufferedRandomAccessFile(file)
    var next = 0

    def getText(pos: Long): List[Array[Byte]] = {
      in.seek(pos)
      rawLines
    }

    def getFile(base: String, name: String, repo: String): CvsFile = {
      try {
        parse(base, name, repo)
      } finally {
        close
      }
    }

    def parse(base: String, name: String, repo: String): CvsFile = {
      try {
        val f = new CvsFile(base, name, repo)
        next = in.read
        admin(f)
        deltas(f)
        // desc is implied
        deltatext(f)
        f
      } catch {
        case ex: Exception => {
          log.println("exception in file " + path)
          throw ex
        }
      }
    }

    def close {
      in.close
    }

    def admin(f: CvsFile): CvsFile = {
      while (next < '0' || next > '9') {
        val n = name
        n match {
          case "symbols" =>
            while (';' != next) {
              val s = symbol
              colon
              val v = num
              v match {
                case branchRE(stem, _, rev) => f.addBranch(s, stem + "." + rev)
                case tagRE(_) => f.addTag(s, v)
                case _ => throw new ParseException("cannot interpret symbol: " + s + ": " + v)
              }
            }
          case "desc" =>
            f.description = string
          case "head" =>
            f.head = value
          case _ =>
            // consume and discard a value
            value
        }
        semicolon
      }
      f
    }

    def deltas(f: CvsFile): CvsFile = {
      while (f.description == "") {
        val rev = new Revision(num, f)
        f.addRevision(rev)
        while ("0123456789".indexOf(next) == -1) {
          val n = name
          n match {
            case "branches" =>
              while (';' != next) {
                rev.addBranch(num)
              }
            case "desc" => 
              f.description = string
              return f
            case "date" => rev.time = parseTime(num)
            case "author" => rev.author = value
            case "state" => rev.state = value
            case "deltatype" => rev.deltatype = value
            case "kopt" => rev.kopt = value
            case "commitid" => rev.commitid = value
            case "filename" => rev.filename = stringOrValue
            case "next" => rev.next = num
            case "permissions" => rev.permissions = num
            // case "bugid" => rev.bugid = value
            case "bugid" => value
            case _ => 
              // can't figure out how to do this in a pattern
              if (n startsWith "mergepoint")
                rev.addMergeParent(stringOrNum)
              else
                throw new ParseException("unknown delta key '" + n + "' with value '" + value + "'")
          }
          semicolon
        }
      }
      f
    }

    val dateformat = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss")
    dateformat.setTimeZone(TimeZone.getTimeZone("UTC"))

    def parseTime(s: String): Long = {
      //val pieces = s.split("[.]")
      //val cal = new GregorianCalendar(TimeZone.getTimeZone("UTC"))
      //cal.set(pieces(0).toInt, pieces(1).toInt, pieces(2).toInt, pieces(3).toInt, pieces(4).toInt, pieces(5).toInt)
      //cal.getTime
      dateformat.parse(s).getTime
    }

    def deltatext(f: CvsFile): CvsFile = {
      while (next != -1) {
        val rev = f.getRevision(num)
        while (next != -1 && "0123456789".indexOf(next) == -1) {
          val n = name
          n match {
            case "log" => rev.comment = comment
            case "text" => 
              // the initial "@" is already held in next, so this position
              // indicates the actual start of text
              rev.deltaOffset = in.tell
              skip_string
            case _ => 
              throw new ParseException("unknown deltatext key '" + n + "' with value '" + value + "'")
              // semicolon
          }
        }
      }
      f
    }

    def name(): String = {
      val b = new StringBuilder
      while (" \t\n\r;".indexOf(next) == -1) {
        b.append(next.toChar)
        next = in.read
      }
      ws
      StringCache(b.toString)
    }

    def value(): String = {
      // call string() if it's a string
      val b = new StringBuilder
      while (';' != next) {
        b.append(next.toChar)
        next = in.read
      }
      // should be able to return None if no value
      StringCache(b.toString)
    }

    def string(): String = {
      val b = new StringBuilder
      // the first @ is already in next - consume it then move on
      next = in.read
      while (next != -1) {
        // if we got a @, then check the next char
        // if it's also a @, output one @, otherwise we've reached the end of the string
        if ('@' == next) {
          next = in.read
          if ('@' != next) {
            ws
            return StringCache(b.toString)
          }
        }
        b.append(next.toChar)
        next = in.read
      }
      StringCache(b.toString)
    }

    def stringOrValue: String = if ('@' == next) string else value

    def stringOrNum: String = if ('@' == next) string else num

    def skip_string() {
      // the first @ is already in next - consume it then move on
      next = in.read
      while (next != -1) {
        // if we got a @, then check the next char
        // if it's also a @, output one @, otherwise we've reached the end of the string
        if ('@' == next) {
          next = in.read
          if ('@' != next) {
            ws
            return
          }
        }
        next = in.read
      }
    }

    def lines(): List[String] = {
      var l: List[String] = List() // built backwards
      var b = new StringBuilder
      // the first @ is already in next - consume it then move on
      next = in.read
      while (next != -1) {
        // if we got a @, then check the next char
        // if it's also a @, output one @, otherwise we've reached the end of the string
        if ('@' == next) {
          next = in.read
          if ('@' != next) {
            ws
            //if (b.length > 0) {
              l = StringCache(b.toString) :: l
            //}
            return l.reverse
          }
        }
        if ('\n' == next) {
          l = StringCache(b.toString) :: l
          b = new StringBuilder
        } else {
          b.append(next.toChar)
        }
        next = in.read
      }
      //if (b.length > 0) {
        l = StringCache(b.toString) :: l
      //}
      l.reverse
    }

    def comment(): List[String] = {
      filterBlanks(decode(rawLines).filter(_ match {
        // remove March Hare crap. way to fuck up free open-source software, guys!
        case "Committed on the Free edition of March Hare Software CVSNT Server." => false
        case "Committed on the Free edition of March Hare Software CVSNT Client." => false
        case "Upgrade to CVS Suite for more features and support:" => false
        case "http://march-hare.com/cvsnt/" => false
        // remove these too
        case "*** empty log message ***" => false
        // allow everything else
        case _ => true
      }).reverse).reverse
    }

    def filterBlanks(lines: List[String]): List[String] = {
      lines match {
        case "" :: l => filterBlanks(l)
        case _ => lines
      }
    }

    def rawLines(): List[Array[Byte]] = {
      var l = List[Array[Byte]]() // built backwards
      var b = new ArrayBuffer[Byte]
      // the first @ is already in next - consume it then move on
      next = in.read
      while (next != -1) {
        // if we got a @, then check the next char
        // if it's also a @, output one @, otherwise we've reached the end of the string
        if ('@' == next) {
          next = in.read
          if ('@' != next) {
            ws
            l ::= b.toArray
            return l.reverse
          }
        }
        if ('\n' == next) {
          b += next.toByte  // keep LFs
          l ::= b.toArray
          b = new ArrayBuffer[Byte]
        } else {
          b += next.toByte
        }
        next = in.read
      }
      l ::= b.toArray
      l.reverse
    }

    def decode(lines: List[Array[Byte]]): List[String] = {
      var l = List[String]() // built backwards
      for (line <- lines) {
        l ::= StringCache(new String(line, "utf-8").stripLineEnd)
      }
      l.reverse
    }

    // this uses a relaxed definition that will allow invalid symbol names
    def symbol(): String = {
      val b = new StringBuilder
      while (" \t\n\r$@,.:;".indexOf(next) == -1) {
        b.append(next.toChar)
        next = in.read
      }
      ws
      StringCache(b.toString)
    }

    def num(): String = {
      val b = new StringBuilder
      while ("0123456789.".indexOf(next) > -1) {
        b.append(next.toChar)
        next = in.read
      }
      ws
      StringCache(b.toString)
    }

    def ws() {
      while (" \t\n\r".indexOf(next) != -1) {
        next = in.read
      }
    }

    def semicolon() {
      if (';' != next) {
        throw new ParseException("expected ';'")
      }
      next = in.read
      ws
    }

    def colon() {
      if (':' != next) {
        throw new ParseException("expected ':'")
      }
      next = in.read
      ws
    }
  }

  object FileParser {

    def applyDelta(seed: List[Array[Byte]], delta: List[Array[Byte]]) = applyDeltaV2(seed, delta)

    def applyDeltaV3(seed: List[Array[Byte]], delta: List[Array[Byte]]): List[Array[Byte]] = {
      val OpLine = new Regex("""([ad])(\d+) (\d+)\n""")
      val out = seed.toBuffer
      var ops = delta.iterator
      var offset = 0
      while (ops.hasNext) {
        val opline = new String(ops.next, "utf-8")
        if (opline != "") {
          val OpLine(op, lineStr, lenStr) = opline
          val line = lineStr.toInt
          val len = lenStr.toInt
          op match {
            case "a" =>
              // "ax y" means add y lines AFTER original line x
              // be careful that x is 1-based, while lineno is 0-based
              out.insertAll(line + offset, ops.take(len).toTraversable)
              offset += len
            case "d" =>
              // "dx y" means delete y lines BEGINNING WITH original line x
              // be careful that x is 1-based, while lineno is 0-based
              out.remove(line + offset - 1, len)
              offset -= len
            case _ => throw new Exception("diff line not understood: " + opline)
          }
        }
      }
      out.toList
    }

    // this appears to be the fastest implementation
    def applyDeltaV2(seed: List[Array[Byte]], delta: List[Array[Byte]]): List[Array[Byte]] = {
      val OpLine = new Regex("""([ad])(\d+) (\d+)\n""")
      val out = ListBuffer[Array[Byte]]()
      var ops = delta.iterator
      var lineno = 0
      while (ops.hasNext) {
        val opline = new String(ops.next, "utf-8")
        if (opline != "") {
          val OpLine(op, lineStr, lenStr) = opline
          val line = lineStr.toInt
          val len = lenStr.toInt
          op match {
            case "a" =>
              // "ax y" means add y lines AFTER original line x
              // be careful that x is 1-based, while lineno is 0-based
              out.appendAll(seed.slice(lineno, line))
              lineno = line
              out.appendAll(ops.take(len))
            case "d" =>
              // "dx y" means delete y lines BEGINNING WITH original line x
              // be careful that x is 1-based, while lineno is 0-based
              out.appendAll(seed.slice(lineno, line - 1))
              lineno = line + len - 1
            case _ => throw new Exception("diff line not understood: " + opline)
          }
        }
      }
      out.appendAll(seed.takeRight(seed.size - lineno))
      out.toList
    }

    def applyDeltaV1(seed: List[Array[Byte]], delta: List[Array[Byte]]): List[Array[Byte]] = {
      val OpLine = new Regex("""([ad])(\d+) (\d+)\n""")
      val out = ListBuffer[Array[Byte]]()
      var deltaline = 0
      var lineno = 0
      while (deltaline < delta.size) {
        val opline = new String(delta(deltaline), "utf-8")
        deltaline += 1
        if (opline != "") {
          val OpLine(op, lineStr, lenStr) = opline
          val line = lineStr.toInt
          val len = lenStr.toInt
          op match {
            case "a" =>
              // "ax y" means add y lines AFTER original line x
              // be careful that x is 1-based, while lineno is 0-based
              while (lineno < line) {
                out += seed(lineno)
                lineno += 1
              }
              val endline = deltaline + len
              while (deltaline < endline) {
                out += delta(deltaline)
                deltaline += 1
              }
            case "d" =>
              // "dx y" means delete y lines BEGINNING WITH original line x
              // be careful that x is 1-based, while lineno is 0-based
              while (lineno < line - 1) {
                out += seed(lineno)
                lineno += 1
              }
              lineno += len
            case _ => throw new Exception("diff line not understood: " + opline)
          }
        }
      }
      while (lineno < seed.size) {
        out += seed(lineno)
        lineno += 1
      }
      out.toList
    }

  }

}
