cvs-fast-export
===============

cvs-fast-export converts and exports CVS repositories using Git's
fast-export format, producing output suitable for importing to Git. It
is fast, deterministic and thorough, making it a good alternative to
Git's built-in cvsimport command.

cvs-fast-export attempts to produce the exact same commit output
every time it is run, so that it can be used to import a live CVS
repository into the same Git repository repeatedly. There are a few
known cases where it is not possible for cvs-fast-export to produce
the same Git commits multiple times - see the *Limitations* section
for more detail.


Building from Source
--------------------

Use the included [Gradle](http://www.gradle.org) wrapper to build
cvs-fast-export.

To build a single runnable jar file, run:

    gradlew bundle

To build an installation zip file with a start script and individual
jars, run:

    gradlew distZip

The first command will create `build/libs/cvs-fast-export-bundle.jar`,
and the second will create `build/distributions/cvs-fast-export.zip`.


Usage
-----

```
cvs-fast-export [OPTIONS] CVSDIR
cvs-fast-export [OPTIONS] --to-git=GITDIR CVSDIR
cvs-fast-export [OPTIONS] --to-file=FILE CVSDIR
```

In the first form, cvs-fast-export will convert CVSDIR and output to
stdout. You must redirect the output in this case.

In the second form, cvs-fast-export will invoke `git fast-import` to
create or update the Git repository in GITDIR.

In the third form, cvs-fast-export will convert CVSDIR and output to
the file named by FILE. The file will be overwritten if it already
exists.

In all cases, CVSDIR must be a path to a directory containing part of
a CVS repository. The directory does not need to contain a complete
repository, and in particular does not need to have a CVSROOT
directory.  It is generally more useful to convert individual CVS
modules than entire repositories anyway.

If you will be expanding CVS keywords during export, it is a good idea
to ensure that CVSDIR points to the same path that CVS uses for the
repository. This will ensure that any keywords containing paths expand
to the same values when exported by cvs-fast-export that they have
when checked out by CVS.

cvs-fast-export does not work with working directories or remote
repositories.  Both the CVS and Git repositories must be
local. Although cvs-fast-export does not modify CVS archive files, it
is recommended that you work from a copy of your repository files
rather than from the originals, especially if the repository is live
and changing.


Option Summary
--------------

A brief summary of the available options follows. See the *Options*
section for complete details.

    -x, --to-git=GITDIR              export directly into git fast-import in repo GITDIR
    -f, --to-file=FILE               export to file
    -l, --line-style=STYLE           set the line-ending style (dos, mac, unix)
    -a, --authors=FILE               read the list of author mappings from FILE
        --[no-]keywords              perform keyword substitutions
        --[no-]log-keywords          perform expansion of log keywords (requires --keywords)
        --[no-]merges                include merge information in the output
        --[no-]logs                  produce log files for the imported data
        --[no-]log-filesets          write out the filesets generated during the conversion phase
        --[no-]split-tags            log each tag in a separate file
        --[no-]repack                optimize the Git repository after import (requires --to-git)
        --[no-]bare                  create a bare git repo instead of normal (requires --to-git)
        --[no-]blobs                 include blobs in the output
        --[no-]commits               include commits in the output
        --[no-]tags                  include tags in the output
        --[no-]fix-tags              attempt to reorder commits to satisfy tags
    -z, --[no-]gzip                  gzip exported data
        --import-marks=FILE          pass the --import-marks option to git-fast-import
        --export-marks=FILE          pass the --export-marks option to git-fast-import
        --force                      pass the --force option to git-fast-import
        --log=FILE                   log conversion progress to FILE
        --[no-]progress              show detailed conversion progress
    -q, --quiet                      suppress normal progress output (overrides --progress)
        --exclude=ANTPATTERN         exclude files and folders matching this ant pattern
    -h, --help                       Show this message

Options
-------

#### -x, --to-git=GITDIR

This option tells cvs-fast-export to write its output directly into a
Git repository.  GITDIR will be created if it does not exist,
otherwise it must already contain a Git repository to be
updated. Because cvs-fast-export invokes the git command to perform
the import, git must be installed and on the current path.


#### -f, --to-file=FILE

This option tells cvs-fast-export to write its output to FILE instead
of to stdout. The file will be overwritten if it already exists.


#### -l, --line-style=STYLE

Set the default line-ending style for exported source. The allowed
values are `dos`, `mac` or `unix`. Any line-ending options set for
individual CVS archives will override this setting.

Defaults to `unix`.


#### -a, --authors=FILE

Translate CVS committer names to Git authors/committers.  FILE must be
a Java properties file encoded in Western(ISO 8859-1) containing one author mapping per line in the
format:

    lowercased CVS name = Git name <email addr>

Lines beginning with a hash mark ('#') and blank lines will be
ignored.

CVSNT will sometimes include domain names in the committer name, eg.
`DOMAIN\username`. These domain names, if present, should be omitted
from the author mapping file.

If this option is omitted, or for any CVS committers that do not have
a mapping defined, cvs-fast-export will copy the CVS committer name to
Git as-is, and will leave the email address empty.

For example:

    # translate CVS users to Git authors
    alice.coder = Alice Coder <alice@coders.com>
    samtester = Sam S. Tester <sammy@somewhere.com>


#### --[no-]keywords

This option tells cvs-fast-export to expand any CVS keywords - except
for the `$Log$` keyword - in the imported files in the same way that
CVS does. Keywords will appear expanded in the output data.

The following keywords are supported:

    $Id$
    $Header$
    $CVSHeader$
    $Revision$
    $Source$
    $Author$
    $Date$
    $RCSFile$
    $Branch$
    $CommitId$
    $State$

Keywords are expanded according to the option flags for each CVS
archive. In other words, files with the `kkv` option flags will get
keywords expanded, but files with the `kb` option flags will not.

At this time, only the `kkv` and `kb` expansion modes are honored, as
are the `L`, `D`, `M`, `B`, `u`, and `t` flags. Other modes and flags
are silently ignored.

*Note:* Some keywords include a path once expanded. In order for these
to match the values you would get from a CVS checkout, you must import
your CVS archives from an absolute path that matches your CVSROOT
path. For example, if your CVSROOT is
`:pserver:me@cvsrepo:/cvs/projects/MyProjects`, then you must import
your CVS archives from the `/cvs/projects/MyProjects` directory. If
you choose not to expand keywords or don't care that the keyword paths
match, then you can import from any directory.

Use **--no-keywords** to disable keyword expansion.

Keyword expansion is enabled by default.


#### --[no-]log-keywords

This option tells cvs-fast-export to also expand `$Log$` keywords.
The **--keywords** option must also be enabled for this to take
effect.

The `$Log$` keyword affects files much more significantly than other
keywords and may be problematic at times. Therefore this option allows
you to enable or disable expansion of the `$Log$` keyword
independently of the others.

*Note:* `$Log$` keywords expanded by cvs-fast-export might not exactly
match the output from CVS.

Use **--no-log-keywords** to disable `$Log$` keyword expansion.

Expansion of `$Log$` keywords is enabled by default.


#### --[no-]merges

This option tells cvs-fast-export to include branch merges in the
exported commit graph. This can sometimes produce odd results since
CVS merges do not necessarily affect the whole repository at once.
Use **--no-merges** to disable merge commits if you get strange export
results.

This option does not affect the content of exported commits, only the
commit graph.

Merge commits are enabled by default.


#### --[no-]logs

This option causes cvs-fast-export to write several log files while importing
commit data from CVS. These detail the commit structure that cvs-fast-export
will later convert and export.

The log files are usually very large and are only useful for debugging
cvs-fast-export itself. You should not normally use this option.

Log files are disabled by default.


#### --[no-]log-filesets

This option causes cvs-fast-export to write a log file detailing the
filesets it creates during export. These detail the state of the
entire repository after each Git commit.

This option is only useful for debugging cvs-fast-export and should
not normally be used.

Fileset logging is disabled by default.


#### --[no-]split-tags

This option modifies the **--logs** functionality to write a separate
log file for each CVS tag, instead of logging all tags in a single
file. This can sometimes be useful if the CVS repository contains
hundreds of tags and thousands of files.

This option is only useful for debugging cvs-fast-export and should
not normally be used.

This option is disabled by default.


#### --[no-]repack

This option tells cvs-fast-export to optimize the Git repository after
export, using the `git repack -adf` command.

This option has no effect unless **--to-git** is enabled.

This option is disabled by default.


#### --[no-]bare

This option tells cvs-fast-export to create a bare Git repository
instead of creating a repository and working directory.

This option has no effect unless **--to-git** is enabled.

This option is disabled by default.


#### --[no-]blobs, --[no-]commits, --[no-]tags

These options control what kind of data cvs-fast-export will export to
Git: blobs (file contents), commits, and tags respectively. Blobs and
commits are both required for a working Git repository, but tags are
optional.

These options are not usually needed, but can be used to convert CVS
repositories in pieces. For example, it can sometimes be useful to
export blobs and commits separately, and then combine them in a single
Git repository later.

All data is exported by default.


#### --[no-]fix-tags

This option tells cvs-fast-export to try rearranging commits if
necessary to make tags fit on the main commit graph.

For each tag, cvs-fast-export will normally try to find an existing
commit where the overall state of the repository exactly matches the
tagged file revisions. If it is able to find such a commit, it will
place the Git tag there. If it is not able to find a match, it will
instead create a "tag branch" with a single commit reflecting the
exact contents of the tag.  In some cases, it is possible to avoid
creating these "tag branches" by swapping one or two commits.

In no case will cvs-fast-export rearrange commits in a way that will
violate the original revision sequence for any individual file. Most
such swaps will only occur over two or three commits.

This option is disabled by default.


#### -z, --[no-]gzip

This option tells cvs-fast-export to gzip its exported data if either
the **--to-file** option or the default output is used. It has no
effect if the **--to-git** option is used.

This option is disabled by default.


#### --import-marks=FILE, --export-marks=FILE

When **--to-git** is used, cvs-fast-export passes these options to the
`git fast-import` command. Otherwise, they are ignored.

These options can be useful if you are splitting up the export with
the **--blobs**, **--commits**, and **--tags** options, or if you are
integrating cvs-fast-export with other repository manipulation tools.
They are not needed for most repository conversions.

Please refer to the Git fast-import documentation for details about
marks.

By default, cvs-fast-export does not pass these options to git.


#### --force

When **--to-git** is used, cvs-fast-import passes this option to the
`git fast-import` command. Otherwise it is ignored.

This option will allow cvs-fast-export to overwrite existing Git
branches, even if that means discarding previous commits. This can be
useful when converting a CVS repository repeatedly.

By default, cvs-fast-export can only update branches if no commits
would be discarded.


#### --log=FILE

This option tells cvs-fast-export to log conversion process to FILE
instead of the console.

By default, cvs-fast-export will output conversion messages to stderr.


#### --[no-]progress

This option tells cvs-fast-export to report more detail about the 
conversion process. In particular, it enables counters for each
step of the conversion.

By default, cvs-fast-export only reports basic information and
warnings.


#### -q, --quiet

This option suppresses all console output. This overrides the
**--progress** option, if present. This option does not affect the
**--log** option, so the two can be combined to write progress
information to file, but suppress it in the console.

By default, cvs-fast-export will report conversion progress.


#### --exclude=ANTPATTERN
This option tells cvs-fast-export to exclude all files and folders
matching the ANTPATTERN. This option can be used several times to
exclude multiple patterns. 
The pattern matcher is using the Apache Ant syntax. 
See http://ant.apache.org/manual/dirtasks.html#patterns for more information.



#### -h, --help

This option tells cvs-fast-export to print an option summary and quit.


Limitations
-----------

It is entirely possible - likely even - that you may encounter special
cases in your CVSNT repositories that cvs-fast-export does not
handle correctly.  If you report these to me, I will see what I can do
to improve it.

cvs-fast-export was developed to convert CVSNT repositories - it is
unlikely to produce ideal results with vanilla CVS repositories, and
may not work with them at all.

Any CVSNT rename operations will be ignored. These are stored outside
the normal CVS archive files and are very difficult to reconcile with
commit history.  cvs-fast-export will correctly rename files in later
commits, but any tags on renamed files might not reflect the correct
name.

cvs-fast-export attempts to produce a stable, deterministic commit
graph when run multiple times, but certain actions may cause it to
produce different results:

  * The few most recent commits on a branch may change if new changes
    have been committed in CVS. This happens most frequently when
    multiple committers submit changes without updating first.
  
  * Using the `cvs admin` command to edit the history of individual
    files will change all Git commits after that point.

  * Manually adding or removing archives from the CVS repository will
    likely change all git commits.

  * Changing the files or revisions pointed to by a tag (or "moving" a
    tag) will change the git tag reference.

  * Using the **--fix-tags** option can cause recent commits, or all
    commits after a recently added tag, to be reordered.

  * Adding a file to or removing a file from a CVS branch will cause
    the corresponding Git branch to change.

If you will be converting from CVS repeatedly, and also committing
directly to the destination repository, I recommend that you create
unique branches in Git and commit only to those. cvs-fast-export
*will* overwrite any changes you make to branches it exports.

Keyword expansion is currently incomplete and only covers the most
common options, notably the `kkv` and `kb` modes, as well as
line-ending translations and the `ku` Unicode option.

Disclaimer
----------

Use at your own risk - always make a copy of your CVS repositories first; 
never use your originals for conversion.

This is my first significant project using Scala, and is kind of a
learning ground. In very many cases in this code, I fall back to
imperative programming rather than attempt to find a functional way to
solve a problem. The resulting program works well, but should not be
used a model for how to write Scala code.


Acknowledgements
----------------

cvs-fast-export includes OptionParser by Curt Sellmer.

See https://github.com/sellmerfud/optparse for the original.


License
-------

Copyright 2014 Roger Vaughn

Permission is hereby granted, free of charge, to any person obtaining
a copy of this software and associated documentation files (the
"Software"), to deal in the Software without restriction, including
without limitation the rights to use, copy, modify, merge, publish,
distribute, sublicense, and/or sell copies of the Software, and to
permit persons to whom the Software is furnished to do so, subject to
the following conditions:

The above copyright notice and this permission notice shall be
included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
