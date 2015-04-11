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
import java.util.TimeZone
import scala.collection.mutable.HashMap

package org.rvaughn.scm.cvs {

  trait DataFormatting {
    val tz = TimeZone.getDefault
    val zones = new HashMap[Int, String]
    
    def formatTime(time: Long): String = {
      time / 1000 + " " + timeZoneString(time)
    }

    def timeZoneString(time: Long): String = {
      val offset = tz.getOffset(time)
      if (!zones.contains(offset)) {
        val str = new StringBuilder
        str.append(if (offset < 0) "-" else "+")
        val mins = offset.abs / 60000
        val hr = mins / 60
        val min = mins % 60
        if (hr < 10) str.append("0")
        str.append(hr)
        if (min < 10) str.append("0")
        str.append(min)
        zones += (offset -> str.toString)
      }
      zones(offset)
    }

    def quotedPath(path: String): String = {
      path.replace("\\", "/").replace("\"", "\\\"").replace("\n", "\\n")
    }
  }

}
