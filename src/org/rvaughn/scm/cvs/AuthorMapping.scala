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
import collection.JavaConversions._
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.PrintStream
import java.util.Properties
import scala.collection.mutable.HashMap
import scala.util.matching.Regex

package org.rvaughn.scm.cvs {

  trait AuthorMapping {
    val authors = loadAuthors
    var unknownAuthors = Set[String]()

    def loadAuthors: Map[String, (String, String)] = {
      val map = HashMap[String, (String, String)]()
      val f = new File(Config.authorsFile)
      if (f.exists) {
        val authorRegex = new Regex("""(.*)<(.*)>\s*""")
        val props = new Properties
        val propIO = new FileInputStream(f)
        props.load(propIO)
        propIO.close
        for (author <- props.propertyNames) {
          val authorRegex(name, email) = props.getProperty(author.toString)
          map += (author.toString -> (name.trim, email.trim))
        }
      }
      map.toMap
    }

    def exportAuthors {
      val authorLog = new PrintStream(new FileOutputStream("authors.log"))
      unknownAuthors.toList.sorted.foreach(authorLog.println)
      authorLog.close
    }

    def mapAuthor(author: String): (String, String) = {
      val auth = stripDomain(author).toLowerCase
      if (authors.contains(auth)) {
        authors(auth)
      } else {
        unknownAuthors += auth
        (auth, "")
      }
    }

    def stripDomain(str: String): String = {
      val bs = str.indexOf('\\')
      if (bs >= 0) {
        str.substring(bs + 1)
      } else {
        str
      }
    }
  }

}
