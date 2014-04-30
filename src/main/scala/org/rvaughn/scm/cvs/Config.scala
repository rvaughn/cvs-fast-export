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
import org.fud.optparse._

package org.rvaughn.scm.cvs {
  
  object Config extends OptionParser {
    var exportToDir = ""
    var filename = ""
    var lineStyle = "unix"
    var keywords = true
    var logKeywords = true
    var authorsFile = "authors.txt"
    var merges = true
    var logs = false
    var logFilesets = false
    var splitTags = false
    var optimize = false
    var bare = false
    var quiet = false
    var gzip = false
    var blobs = true
    var commits = true
    var tags = true
    var fixTags = false
    var marksImportFile = ""
    var marksExportFile = ""
    var progress = false
    var logFile = ""
    var force = false

    def importMarks = !marksImportFile.isEmpty
    def exportMarks = !marksExportFile.isEmpty
    def exportToRepo = !exportToDir.isEmpty
    def exportToFile = !filename.isEmpty
    def logOutput = !logFile.isEmpty

    banner = "usage: cvs-fast-export [options] CVSDIR"
    separator("options:")
    reqd("-x", "--to-git=DIR", "export directly into git fast-import in repo DIR") { d: String => exportToDir = d }
    reqd("-f", "--to-file=FILE", "export to file") { f: String => filename = f }
    reqd("-l", "--line-style=STYLE", List("unix", "dos", "mac"), "set the line-ending style (dos, mac, unix)") { lineStyle = _ }
    reqd("-a", "--authors=FILE", "read the list of author mappings from FILE") { f: String => authorsFile = f }
    bool("", "--keywords", "perform keyword substitutions") { keywords = _ }
    bool("", "--log-keywords", "perform expansion of log keywords (requires --keywords)") { logKeywords = _ }
    bool("", "--merges", "include merge information in the output") { merges = _ }
    bool("", "--logs", "produce log files for the imported data") { logs = _ }
    bool("", "--log-filesets", "write out the filesets generated during the conversion phase") { logFilesets = _ }
    bool("", "--split-tags", "log each tag in a separate file") { splitTags = _ }
    bool("", "--repack", "optimize the Git repository after import (requires --to-git)") { optimize = _ }
    bool("", "--bare", "create a bare git repo instead of normal (requires --to-git)") { bare = _ }
    bool("", "--blobs", "include blobs in the output") { blobs = _ }
    bool("", "--commits", "include commits in the output") { commits = _ }
    bool("", "--tags", "include tags in the output") { tags = _ }
    bool("", "--fix-tags", "attempt to reorder commits to satisfy tags") { fixTags = _ }
    bool("-z", "--gzip", "gzip exported data") { gzip = _ }
    reqd("", "--import-marks=FILE", "pass the --import-marks option to git-fast-import") { f: String => marksImportFile = f }
    reqd("", "--export-marks=FILE", "pass the --export-marks option to git-fast-import") { f: String => marksExportFile = f }
    flag("", "--force", "pass the --force option to git-fast-import") { () => force = true }
    reqd("", "--log=FILE", "log conversion progress to FILE") { f: String => logFile = f }
    bool("", "--progress", "show detailed conversion progress") { progress = _ }
    flag("-q", "--quiet", "suppress normal progress output (overrides --progress)") { () => quiet = true }
  }

}
