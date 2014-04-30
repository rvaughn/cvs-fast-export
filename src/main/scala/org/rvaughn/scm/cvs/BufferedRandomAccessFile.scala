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
import java.io.RandomAccessFile

package org.rvaughn.scm.cvs {

  // this is a very specialized file reader - it has not been adapted for general use
  // it is deliberately NOT derived from the standard I/O classes so that it
  // cannot be used in inappropriate ways.
  class BufferedRandomAccessFile(file: File) {
    val in = new RandomAccessFile(file, "r")

    var buffered = 0
    var fPosition = 0l
    var bPosition = 0
    val buffer = new Array[Byte](4096)

    def close {
      in.close
    }

    def read: Int = {
      if (buffered == -1) {
        -1
      } else if (bPosition < buffered) {
        val b = buffer(bPosition)
        bPosition += 1
        // stupid Java... whoever heard of signed bytes?
        b.toInt & 0x00ff
      } else {
        fPosition = in.getFilePointer
        bPosition = 0
        buffered = in.read(buffer)
        read
      }
    }

    def tell: Long = {
      fPosition + bPosition
    }

    def seek(pos: Long) {
      in.seek(pos)
      buffered = 0
      bPosition = 0
    }
  }

}
