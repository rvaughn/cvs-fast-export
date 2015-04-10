/*
 * Copyright (c) 2015 Roger Vaughn
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
package org.rvaughn.scm.cvs;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;

// git requires LF-only endings in git-fast-import commands.
// this class is a hacky way to get around platform line-ending issues.
// I should probably create a custom stream writer instead.
class UnixPrintStream extends PrintStream {
    boolean autoFlush = true;
    // TODO: make this setable
    String lineSeparator = "\n";

    public UnixPrintStream(File file) throws FileNotFoundException {
        super(file);
    }

    public UnixPrintStream(File file, String csn) throws FileNotFoundException, UnsupportedEncodingException {
        super(file, csn);
    }

    public UnixPrintStream(OutputStream out) {
        super(out);
    }
    
    public UnixPrintStream(OutputStream out, boolean autoFlush) {
        super(out, autoFlush);
        this.autoFlush = autoFlush;
    }
    
    public UnixPrintStream(OutputStream out, boolean autoFlush, String encoding) throws UnsupportedEncodingException {
        super(out, autoFlush, encoding);
        this.autoFlush = autoFlush;
    }
    
    public UnixPrintStream(String fileName) throws FileNotFoundException {
        super(fileName);
    }
    
    public UnixPrintStream(String fileName, String csn) throws FileNotFoundException, UnsupportedEncodingException {
        super(fileName, csn);
    }
    
    @Override
    public void println() {
        super.print(lineSeparator);
        if (autoFlush) {
            super.flush();
        }
    }

    @Override
    public void println(boolean x) {
        super.print(x);
        println();
    }

    @Override
    public void println(char x) {
        super.print(x);
        println();
    }

    @Override
    public void println(char[] x) {
        super.print(x);
        println();
    }

    @Override
    public void println(double x) {
        super.print(x);
        println();
    }

    @Override
    public void println(float x) {
        super.print(x);
        println();
    }

    @Override
    public void println(int x) {
        super.print(x);
        println();
    }

    @Override
    public void println(long x) {
        super.print(x);
        println();
    }

    @Override
    public void println(Object x) {
        super.print(x);
        println();
    }

    @Override
    public void println(String x) {
        super.print(x);
        println();
    }
}
