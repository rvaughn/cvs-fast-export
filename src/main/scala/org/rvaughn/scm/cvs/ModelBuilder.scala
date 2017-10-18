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
import java.util.Date
import scala.collection.mutable.MultiMap
import java.io.PrintStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.io.PrintWriter
import java.io.File

package org.rvaughn.scm.cvs {

  class ModelBuilder(log: PrintStream, con: PrintStream) {
    var files: Map[String, CvsFile] = Map()
    var commits: Map[String, Commit] = Map()
    var tags: Map[String, Tag] = Map()
    var branches: Map[String, Branch] = Map()

    // commit ID to fileset
    var filesets: Map[String, Fileset] = Map()
    // branch name to commit
    var heads: Map[String, Commit] = Map()
    // fileset hash to commit
    var commitForHash: Map[String, Commit] = Map()
    // commit id to fileset hash
    var hashForCommit: Map[String, String] = Map()

    branches += ("TRUNK" -> new Branch("TRUNK"))
    filesets += ("root" -> new Fileset)
    heads += ("TRUNK" -> null)

    def parse(source: RepoParser) {
      log.println("parsing repository...")
      add(source)
    }

    def build: Model = {
      log.println("mapping commits onto branches...")
      mapCommitsToBranches
      log.println("resolving branch roots...")
      resolveBranchRoots
      log.println("building commit graph...")
      buildCommitGraph
      log.println("mapping tags to commits...")
      mapTagsToCommits
      // log.println("done")

      val model = new Model
      model.files = files
      model.commits = commits
      model.tags = tags
      model.branches = branches
      model
    }

    def build(source: RepoParser): Model = {
      parse(source)
      build
    }

    //### extract log data #################################

    def add(source: RepoParser) {
      val parseLog = 
        if (Config.logs) {
          new PrintStream(new FileOutputStream("parse.log"))
        } else {
          null
        }
      var fileCount = 0
      source.foreach(f => {
        if (Config.logs) {
          parseLog.println(f)
        }
        fileCount += 1
        con.print("\rparsing file " + fileCount + " of " + source.fileCount)
        add(f)
      })
      con.println
      if (Config.logs) {
        parseLog.close
      }
    }

    def add(file: CvsFile) {
      buildFile(file)
      extractTags(file)
      extractBranches(file)
      file.cleanup
    }

    def buildFile(file: CvsFile): CvsFile = { 
      for (rev <- file.revisions.values) {
        //val rev = buildRevision(r)
        if (!rev.isBranchAddRoot) {
          val commit = buildCommit(rev)
          commits += (commit.id -> commit)
          // use the commit's version:
          rev.comment = commit.comment
        }
      }
      files += (file.name -> file)
      file
   }

    def buildRevision(rev: Revision): Revision = {
      // special case - a dead 1.1 revision indicates an add on a branch.
      // we need to synthesize an earlier commit to make our model work.
      if (rev.isBranchAddRoot) {
        rev.commitid += "-branch-add"
        rev.time -= 1
      }
      rev
    }

    def buildCommit(rev: Revision): Commit = {
      val commit = if (commits.contains(rev.commitid)) {
        commits(rev.commitid)
      } else {
        val commit = new Commit(rev.commitid)
        commit.author = rev.author
        commit.comment = rev.comment
        commit
      }
      commit += rev
      if (rev.time > commit.time) {
        commit.time = rev.time
      }
      commit
    }

    def extractTags(file: CvsFile) {
      for ((name, number) <- file.tags) {
        val tag = if (tags.contains(name)) {
          tags(name)
        } else {
          new Tag(name)
        }
        try {
          val rev = file.revisions(number)
          if (rev.isLive) {
            tag += file.revisions(number)
          }
          tags += (name -> tag)
        } catch {
          case ex: NoSuchElementException => 
            warn("could not find revision " + number + " for tag " + name + " in file " + file.name)
        }
      }
    }

    def extractBranches(file: CvsFile) {
      // note: TRUNK revisions aren't annotated as "branches" in the log,
      // so this never gets invoked for them at all.
      for ((number, name) <- file.branches) {
        val branch = if (branches.contains(name)) {
          branches(name)
        } else {
          new Branch(name)
        }
        try {
          val root = rootOfBranch(number)
          branch.roots += file.revisions(root)
          branches += (name -> branch)
        } catch {
          case ex: NoSuchElementException =>
            warn("could not find root revision " + number + " for branch " + name + " in file " + file.name)
        }
      }
    }

    //### commit-to-branch mapping #########################

    def mapCommitsToBranches {
      // the size can change, so cache the original
      val commitSize = commits.size
      for ((commit, index) <- commits.values.zipWithIndex) {
        con.print("\rmapping commit " + (index + 1) + " of " + commitSize)
        val branchRevs = commit.revisions.groupBy(rev => {
          try {
            rev.branch
          } catch {
            case ex: IllegalArgumentException => {
              // we found a branch revision with no branch tag. make one.
              warn(ex.getMessage)
              // use the commit name here, so that all files in this commit get
              // correlated onto the same branch
              val name = "fixup_branch__" + commit.id
              val branch = if (branches.contains(name)) {
                branches(name)
              } else {
                new Branch(name)
              }
              branch.roots += rev.sibling(rev.branchedFrom)
              branches += (name -> branch)
              // make sure we record the branch name in the file, too
              rev.file.addBranch(name, rev.branchNumber)
              name
            }
          }
        })
        if (branchRevs.size == 1) {
          commit.branch = branchRevs.keys.head
        } else {
          warn("commit across multiple branches at commit " + commit.id + ": " + branchRevs.keys.mkString(", "))
          warn("synthesizing " + (branchRevs.size - 1) + " commit(s) to resolve")
          for ((branchName, revs) <- branchRevs) {
            val copy = new Commit(commit.id + "-" + branchName)
            copy.synthetic = true
            copy.author = commit.author
            copy.comment = commit.comment
            copy.branch = branchName
            copy.time = commit.time
            copy.revisions = revs
            revs.foreach(rev => rev.commitid = copy.id)
            commits += (copy.id -> copy)
          }
          commits -= commit.id
        }
      }
      con.println
    }

    //### branch roots #####################################

    def resolveBranchRoots {
      for ((b, index) <- branches.values.zipWithIndex) {
        con.print("\rmapping branch " + (index + 1) + " of " + branches.size)
        if (b.name != "TRUNK") {
          resolveBranch(b)
        }
      }
      con.println
    }

    def resolveBranch(branch: Branch) {
      // find the first commit on this branch
      val branchCommits = commits.values.filter(c => c.branch == branch.name)
      if (branchCommits.size == 0) {
        // the branch was created but never committed to
        // this also means that all recorded roots must be real roots and not adds
        // we create a special-case tag to hold these so that they get exported properly
        val branchTag = new Tag(branch.name)
        branchTag.isBranchTag = true
        branchTag.revisions = branch.roots
        tags += (branchTag.name -> branchTag)
      } else {
        val firstCommit = branchCommits.minBy(_.time)

        // we'll think of the first commit time as the branch creation time
        branch.time = firstCommit.time
        firstCommit.isBranchStart = true

        // if we have a tag with the same name, absorb the tag revs into our
        // branch and discard the tag. this normally happens when someone
        // attempts to commit into a sticky tag, but can also happen when foreign
        // files are explicitly tagged with the same name as the branch tag.
        // we don't handle exotic cases where someone deliberately moves a branch tag.
        if (tags.contains(branch.name)) {
          warn("found both a tag and a branch for: " + branch.name + ", copying tagged files into the branch")
          val tag = tags(branch.name)
          tags -= tag.name

          // we handle this two different ways. if the branch commits occur after
          // the tagged revisions, then we assume that someone attempted to commit
          // into a sticky tag. in that case, add all of the tagged revisions into
          // the branch roots.

          // if, instead the tagged revisions occur after the branch started, then
          // we assume that they were deliberately tagged. in this case create fixup
          // commits to copy the tagged revisions into the active branch.

          // the theory is that the tagged revs aren't actually "on" the branch in CVS
          // terms, but any attempt to check out the branch by name will also check out
          // the tagged revs (and vice-versa), so CVS will behave as if they are branched.
          // so we put them on the branch for real to mimic the user-visible behavior.

          val latestTagRev = tag.revisions.maxBy(_.time)
          if (latestTagRev.time < branch.time) {
            branch.roots ++= tag.revisions
          } else {
            warn("synthesizing commit to resolve")
            val tagRevsByCommit = tag.revisions.groupBy(r => commits(r.commitid))
            tagRevsByCommit.foreach(p => synthesizeBranchTagCommit(p._1, p._2, branch.name))
          }
        }

        // the minimum revision time of the first commit is the initial
        // pivot point that lets us partition the branch root revisions
        // into those the branch was created from and those that were
        // added to the branch later.
        // we take the minimum revision time instead of the commit time
        // because we need all roots to occur strictly BEFORE this commit.
        // we also need to explicitly handle those revisions that
        // indicate files first added to the branch.
        var pivot = firstCommit.revisions.map(_.time).min
        var rootPart = branch.roots.partition(r => r.time < pivot && !r.isBranchAddRoot)
        var roots = rootPart._1
        var adds = rootPart._2
        
        // now find and sort in chronological order all of those
        // commits that contributed branch root revisions.
        val rootsByCommit = roots.groupBy(r => commits(r.commitid))
        val rootCommits = rootsByCommit.keys.toList.sortBy(_.time)

        // now walk through those commits in order and compare their
        // revisions with the branch roots. if any commit conflicts
        // with (has newer revisions than) the branch roots, it becomes
        // the new pivot point. if no commits conflict, then the
        // original pivot is valid, and if the very first commit
        // conflicts, then bail because we're screwed.
        val index = rootCommits.indexWhere(c => conflictsWeak(c.revisions, roots))
        if (index > -1) {
          if (index == 0) throw new Exception("branch " + branch.name + " has no non-conflicting root commits")
          pivot = rootCommits(index).revisions.map(_.time).min
          rootPart = branch.roots.partition(r => r.time < pivot)
          roots = rootPart._1
          adds = rootPart._2
        }
        
        // partitioning is complete, so save 'em off
        branch.roots = roots
        branch.adds = adds
      }

      if (branch.roots.size == 0) {
        // this rarely happens, and only when a branch is spontaneously created.
        // such a branch has no parent, and no initial files.
        // this can happen when trying to check in new files to a tag - the branch
        // is created to hold the new files, but existing files do not get branch-tagged.
        // in this case, the "root" is really the dead-add commit on the TRUNK branch.
        warn("branch " + branch.name + " created spontaneously")
      }
    }

    // create a phony commit to fold foreign revisions into the branch
    def synthesizeBranchTagCommit(origCommit: Commit, revs: Set[Revision], branch: String) {
      val newCommit = new Commit("branch-tag-fixup--" + origCommit.id)
      newCommit.synthetic = true
      newCommit.author = origCommit.author
      newCommit.comment = origCommit.comment
      newCommit.isTagFixup = false // it's not a fixup because we're not resetting the branch
      newCommit.time = origCommit.time
      newCommit.branch = branch
      newCommit.isBranchStart = false
      newCommit.revisions = revs

      // record the commit
      commits += (newCommit.id -> newCommit)

      // note that the revisions are not duplicated and do not have their
      // commitIds updated - this may cause problems later.
    }

    //### commit graph #####################################

    def buildCommitGraph {
      var tips: Set[Commit] = Set()
      val history = commits.values.toList.sortBy(_.time)
      for ((commit, index) <- history.zipWithIndex) {
        con.print("\rmapping commit " + (index + 1) + " of " + history.size)
        commit.parent = findParent(commit)
        if (Config.merges) {
          commit.merges = findMerges(commit)
        }

        if (!heads.contains(commit.branch)) {
          // newborn branch - check it
          val branch = branches(commit.branch)
          branch.setParent(commit.parent, branches)
          validateBranch(branch)
        }

        val fileset = verifyAndApplyChanges(commit)
        val hash = livesetHash(fileset.revisions)

        filesets += (commit.id -> fileset)
        hashForCommit += (commit.id -> hash)
        if (!commit.isBranchAdd) {
          // as a special case, don't add fileset hashes for branch adds - 
          // the fileset is guaranteed equal to an existing one, 
          // because it adds no new files
          if (commitForHash.contains(hash)) {
            // the same (live) fileset occurs more than once.
            // in this case, we'll keep a record of the latest commit
            // with this fileset.
            // this is unusual in CVS, because it means the exact same
            // revisions of the same files, but isn't really a problem.
            // note that different revisions of files with identical 
            // contents will actually result in different filesets.
            warn("found existing fileset for new commit: " + commit.id + " at old commit: " + commitForHash(hashForCommit(commit.id)).id)
          }
          commitForHash += (hash -> commit)
        }
        if (commit.parent != null) {
          tips -= commit.parent
        }
        heads += (commit.branch -> commit)
        tips += commit
      }

      // wrap up - look for orphaned heads
      for (branch <- branches.keys) {
        if (heads.contains(branch)) {
          // branches that have been created but never committed to will not have heads -
          // this is OK.
          tips -= heads(branch)
          heads -= branch
        }
      }
      for (commit <- tips) {
        // this can't occur in CVS - big error if we find it
        warn("orphaned head at commit " + commit.id)
      }

      con.println
    }

    def findParent(commit: Commit): Commit = {
      if (heads.contains(commit.branch)) {
        heads(commit.branch)
      } else {
        findBranchPoint(branches(commit.branch))
      }
    }

    // merges are only advisory and will not contribute to
    // data handling, so we don't care if they aren't perfect
    def findMerges(commit: Commit): Set[Commit] = {
      val merges = commit.revisions.flatMap(r => r.merges.map(m => r.sibling(m).commitid)).toSet
      if (merges.size > 0) {
        // this isn't quite right, but we're trying to find a minimal set here
        val mergeCommits = merges.map(m => commits(m))
        val latest = mergeCommits.maxBy(_.time)
        mergeCommits.filterNot(_.isAncestorOf(latest))
      } else {
        Set()
      }
    }

    def findBranchPoint(branch: Branch): Commit = {
      val hash = livesetHash(branch.roots)
      if (commitForHash.contains(hash)) {
        commitForHash(hash)
      } else if (branch.roots.size > 0) {
        // no matching fileset for roots, something is very wrong.
        // create a standalone branch based on the root files.
        warn("no root found for branch " + branch.name + ", synthesizing new commit")
        synthesizeBranchCommit(branch)
      } else {
        null
      }
    }

    def synthesizeBranchCommit(branch: Branch): Commit = {
      // redo roots and adds - we may have broken them earlier
      // fold adds back into roots
      branch.roots ++= branch.adds
      // find the first branch commit and repartition roots based on its timestamp
      val branchCommits = commits.values.filter(c => c.branch == branch.name)
      val firstCommit = branchCommits.minBy(_.time)
      firstCommit.isBranchStart = false // not anymore
      val pivot = firstCommit.revisions.map(_.time).min
      val rootPart = branch.roots.partition(r => r.time < pivot && !r.isBranchAddRoot)
      branch.roots = rootPart._1
      branch.adds = rootPart._2
      // now find the latest commit that contributed root revs
      val rootsByCommit = branch.roots.groupBy(r => commits(r.commitid))
      val rootCommits = rootsByCommit.keys.toList.sortBy(_.time)
      val latestCommit = rootCommits.last

      // now make a new one
      val newCommit = new Commit("branch-fixup--" + branch.name)
      newCommit.synthetic = true
      newCommit.author = "system"
      newCommit.comment = List("branch fixup commit created during import")
      // newCommit.isTagFixup = false
      // newCommit.parent = null
      newCommit.isTagFixup = true
      newCommit.parent = latestCommit
      newCommit.time = latestCommit.time + 1
      newCommit.branch = branch.name
      newCommit.isBranchStart = true
      newCommit.revisions = liveRevisions(branch.roots)

      // record the commit
      commits += (newCommit.id -> newCommit)
      
      // record the fileset
      val fileset = revisionMapByFile(newCommit.revisions)
      val hash = filesetHash(fileset.revisions)
      filesets += (newCommit.id -> fileset)
      hashForCommit += (newCommit.id -> hash)
      if (commitForHash.contains(hash)) {
        warn("found existing fileset for new commit: " + newCommit.id + " at old commit: " + commitForHash(hash).id)
      }
      commitForHash += (hash -> newCommit)

      // add the branch to heads, using the fixup commit
      branch.setParent(latestCommit, branches)
      heads += (branch.name -> latestCommit)

      newCommit
    }

    def validateBranch(branch: Branch) {
      val deadRoots = branch.roots.filter(_.dead)
      if (deadRoots.size > 0) {
        warn("branch " + branch.name + " contains dead root revisions:" + deadRoots.map(_.revString).mkString("\n    ", "\n    ", ""))
      }
    }

    def verifyAndApplyChanges(commit: Commit): Fileset = {
      val fileset = getInitialFileset(commit)
      verifyChanges(commit, fileset)
      applyChanges(commit, fileset)
    }

    def getInitialFileset(commit: Commit): Fileset = {
      if (commit.hasParent) {
        filesets(commit.parent.id)
      } else {
        filesets("root")
      }
    }

    def applyChanges(commit: Commit, fileset: Fileset): Fileset = {
      fileset ++ commit.revisions
    }

    def verifyChanges(commit: Commit, fileset: Fileset) {
      val pathset = getPathset(fileset)
      for (rev <- commit.revisions if !commit.synthetic) {
        if (rev.isAdded) {
          validateUniqueAdd(commit, rev, fileset)
          validateUniqueFilename(commit, rev, pathset)
          validateBranchMatch(commit, rev, fileset)
        } else if (rev.isChanged) {
          validateChangeHasParent(commit, rev, fileset)
          validateChangeIsSequential(commit, rev, fileset)
        } else if (rev.isRemoved) {
          validateRemovalHasParent(commit, rev, fileset)
        }
        if (rev.isRenamed) {
          validateRenameHasParent(commit, rev, fileset)
          validateUniqueRename(commit, rev, pathset)
        }
      }
    }

    def getPathset(fileset: Fileset): Set[String] = {
      liveRevisions(fileset.revisions).map(_.path)
    }

    def validateUniqueFilename(commit: Commit, rev: Revision, pathset: Set[String]) {
      // if this is a first rev on a branch, ignore it - it should "duplicate" its root rev.
      if (pathset.contains(rev.path) && !rev.isFirstBranchChange) {
        warn("attempting to add a pathname that already exists: " + rev.path + ", at commit: " + commit.id)
      }
    }

    def validateUniqueRename(commit: Commit, rev: Revision, pathset: Set[String]) {
      if (pathset.contains(rev.path)) {
        warn("attempting to rename a file to a name that already exists: " + rev.path + ", at commit: " + commit.id)
      }
    }

    def validateUniqueAdd(commit: Commit, rev: Revision, fileset: Fileset) {
      // if the revision has a predecessor in the fileset, it must be on a different branch, or dead
      if (fileset.contains(rev.file)) {
        val last = fileset(rev.file)
        if (rev.branch == last.branch && last.isLive) {
          warn("attempting to add a file that already exists, new: " + rev.revString + ", existing: " + last.number)
        }
      }
    }

    def validateChangeHasParent(commit: Commit, rev: Revision, fileset: Fileset) {
      if (!fileset.contains(rev.file)) {
        warn("attempting to change a non-existing file: " + rev.revString)
      }
    }

    def validateRemovalHasParent(commit: Commit, rev: Revision, fileset: Fileset) {
      // we allow "removals" of 1.1 revs - that indicates an add on a branch
      if (!rev.isRoot && !fileset.contains(rev.file)) {
        warn("attempting to delete a non-existing file: " + rev.revString)
        rev.isFixup = true
      }
    }

    def validateRenameHasParent(commit: Commit, rev: Revision, fileset: Fileset) {
      if (!fileset.contains(rev.file)) {
        warn("attempting to rename a non-existing file: " + rev.revString)
        rev.isFixup = true
      }
    }

    def validateChangeIsSequential(commit: Commit, rev: Revision, fileset: Fileset) {
      // we must have previously validated that a last exists
      val last = fileset(rev.file)
      if (rev.increment.toInt != last.increment.toInt + 1) {
        warn("change does not follow revision sequence: " + last.revString + " => " + rev.number)
      }
    }

    def validateBranchMatch(commit: Commit, rev: Revision, fileset: Fileset) {
      if (rev.branch != commit.branch) {
        warn("new branch revision doesn't match its commit branch: " + rev.revString + " (" +
             rev.branch + ") => (" + commit.branch + ")")
      }
    }

    def explainDifferences(tagrevs: Traversable[Revision], liverevs: Traversable[Revision]) {
      val tagset = revisionMapByFile(tagrevs)
      val liveset = revisionMapByFile(liverevs)
      val tagfiles = tagset.files.toList
      val livefiles = liveset.files.toList

      val missingFiles = tagfiles.diff(livefiles).sortBy(_.name)
      val extraFiles = livefiles.diff(tagfiles).sortBy(_.name)
      val oldRevs = tagrevs.filter(r => liveset.contains(r.file) && r.isAncestorOf(liveset(r.file))).toList.sortBy(_.file.name).map(r => (r, liveset(r.file)))
      val newRevs = tagrevs.filter(r => liveset.contains(r.file) && r.isDescendantOf(liveset(r.file))).toList.sortBy(_.file.name).map(r => (r, liveset(r.file)))
      // val otherRevs = tagrevs.filter(r => liveset.contains(r.file) && r != liveset(r.file)).sortBy(_.file.name).map(r => (r, liveset(r.file)))

      if (missingFiles.size > 0) {
        warn("tagged files that are not present in the current set:\n    " +
                missingFiles.map(f =>  tagset(f).revString).mkString("\n    "))
      }

      if (extraFiles.size > 0) {
        warn("files that are in the current set but missing from the tag:\n    " +
                extraFiles.map(f => liveset(f).revString).mkString("\n    "))
      }

      if (oldRevs.size > 0) {
        warn("files that are tagged at revisions that are older than current:\n    " +
                oldRevs.map(p => p._1.revString + " (current: " + p._2.number + ")").mkString("\n    "))
      }

      if (newRevs.size > 0) {
        warn("files that are tagged at revisions that are newer than current:\n    " +
                newRevs.map(p => p._1.revString + " (current: " + p._2.number + ")").mkString("\n    "))
      }

      /*
      if (otherRevs.size > 0) {
        warn("files that are tagged at other revisions than current:\n    " +
                otherRevs.map(p => p._1.revString + " (current: " + p._2.number + ")").mkString("\n    "))
      }
      */
    }

    def reportFilesets {
      var count = 0
      val dir = new File("filesets");
      dir.mkdir;
      for ((id, fileset) <- filesets) {
        count += 1
        con.print("\rlogging fileset " + count + " of " + filesets.size)
        val out = new PrintWriter(new FileOutputStream("filesets/" + id))
        out.println("commit: " + id)
        out.println(fileset)
        out.close
      }
      con.println
    }

    //### tag to commit mapping ############################

    def mapTagsToCommits {
      for ((tag, index) <- tags.values.zipWithIndex) {
        con.print("\rmapping tag " + (index + 1) + " of " + tags.size)
        val hash = livesetHash(tag.revisions)
        if (!commitForHash.contains(hash)) {
          if (Config.fixTags && reorderCommitsForTag(tag, hash)) {
            warn("commits reordered to fix tag " + tag.name)
          } else {
            warn("no parent found for tag " + tag.name + ", synthesizing new commit")
            synthesizeTagCommit(tag)
          }
          // make sure we have a commit now
          if (!commitForHash.contains(hash)) {
            throw new Exception("tag fixup failed for tag: " + tag.name)
          }
        }
        val commit = commitForHash(hash)
        tag.commit = commit
      }
      con.println
    }

    // note that reordering commits at this point can "break" other tags or branches.
    // we do not check for such cases here!
    def reorderCommitsForTag(tag: Tag, hash: String): Boolean = {
        swapOneParentCommit(tag, hash)
    }

    def swapOneParentCommit(tag: Tag, hash: String): Boolean = {
      val tagCommit = commits(tag.revisions.maxBy(_.time).commitid)
      val rogueCommit = tagCommit.parent
      val baseCommit = rogueCommit.parent
      val baseFileset = filesets(baseCommit.id)
      val tagFileset = applyChanges(tagCommit, baseFileset)
      val tagHash = livesetHash(tagFileset.revisions)
      if (hash == tagHash && commitsAreDistinct(rogueCommit, tagCommit)) {
        val rogueFileset = applyChanges(rogueCommit, tagFileset)
        val rogueHash = livesetHash(rogueFileset.revisions)
        // delete old hash-to-commit entries
        commitForHash -= hashForCommit(tagCommit.id)
        commitForHash -= hashForCommit(rogueCommit.id)
        // add new fileset mappings
        addFileset(tagCommit, tagFileset, tagHash)
        addFileset(rogueCommit, rogueFileset, rogueHash)
        // reset parents
        swapCommits(tagCommit, rogueCommit)
        true
      } else {
        false
      }
    }

    def addFileset(commit: Commit, fileset: Fileset, hash: String) {
      filesets += (commit.id -> fileset)
      hashForCommit += (commit.id -> hash)
      commitForHash += (hash -> commit)
    }

    def swapCommits(child: Commit, parent: Commit) {
      child.parent = parent.parent
      commits.values.foreach(c => {
        if (c.isChildOf(child)) {
          c.parent = parent
        }
      })
      parent.parent = child
    }

    // commits are distinct if they have no files in common
    // this is important because we've already determined the dependency order for commits, 
    // so if the commits share a file, they are dependent and cannot be reordered
    def commitsAreDistinct(commit1: Commit, commit2: Commit): Boolean = {
      !commit1.revisions.exists(rev1 => commit2.revisions.exists(rev2 => rev1.file == rev2.file))
    }

    // create a phony commit to match this tag's fileset
    // branch off of the current commit and just build the necessary fileset.
    // we do not record any merges since they will not be accurate anyway.
    def synthesizeTagCommit(tag: Tag) {
      val rootCommit = commits(tag.revisions.maxBy(_.time).commitid)
      val tagCommit = new Commit("tag-fixup--" + tag.name)
      tagCommit.synthetic = true
      tagCommit.author = "system"
      tagCommit.comment = List("tag fixup commit created during import")
      tagCommit.isTagFixup = true
      tagCommit.parent = rootCommit
      tagCommit.time = rootCommit.time
      tagCommit.branch = "tag_fixup__" + tag.name
      tagCommit.isBranchStart = true
      tagCommit.revisions = liveRevisions(tag.revisions)

      //val tagBranch = new Branch(commit.branch)
      //tagBranch.time = tagCommit.time
      //tagBranch.root = tag.commit
      //tagBranch.parent = branches(tag.commit.branch)

      // record the branch
      //branches += (tagBranch.name -> tagBranch)

      // record the commit
      commits += (tagCommit.id -> tagCommit)
      
      // record the fileset
      hashForCommit += (tagCommit.id -> filesetHash(tagCommit.revisions))
      if (commitForHash.contains(hashForCommit(tagCommit.id))) {
        warn("found existing fileset for new commit: " + tagCommit.id + " at old commit: " + commitForHash(hashForCommit(tagCommit.id)).id)
      }
      commitForHash += (hashForCommit(tagCommit.id) -> tagCommit)

      // update the parent commit
      // do we need to?

      // update the tag
      // tag.commit = tagCommit

      // don't include these in branches, heads or tips - they're not "real" branches
    }

    //### utility methods ##################################

    def warn(msg: Any) {
      con.print("\r")
      log.println("warning: " + msg)
    }

    def revisionMapByFile(revs: Traversable[Revision]): Fileset = {
      new Fileset ++ revs
    }
    
    // a fileset conflicts weakly if it contains any revisions newer than their
    // corresponding revisions in the reference set.
    //
    // for example:
    //   a = [['a', '1.1'], ['b', '1.3'], ['c', '1.5']]
    //   b = [['a', '1.2']]
    //   c = [['b', '1.3']]
    //   d = [['c', '1.1']]
    //   e = [['d', '1.9']]
    //   conflicts_weak?(b, a) => true
    //   conflicts_weak?(c, a) => false
    //   conflicts_weak?(d, a) => false
    //   conflicts_weak?(e, a) => false
    def conflictsWeak(testset: Traversable[Revision], refset: Traversable[Revision]): Boolean = {
      val refmap = revisionMapByFile(refset)
      testset.find(r => refmap.contains(r.file) && r.isDescendantOf(refmap(r.file))) != None
    }
    
    // a fileset conflicts strongly if it contains any revisions that are either
    // newer than their reference counterparts, or are not represented in the
    // reference set at all.
    // (or that is missing revisions found in the reference set?)
    //
    // for example:
    //   a = [['a', '1.1'], ['b', '1.3'], ['c', '1.5']]
    //   b = [['a', '1.2']]
    //   c = [['b', '1.3']]
    //   d = [['c', '1.1']]
    //   e = [['d', '1.9']]
    //   conflicts_strong?(b, a) => true
    //   conflicts_strong?(c, a) => false
    //   conflicts_strong?(d, a) => false
    //   conflicts_strong?(e, a) => true
    def conflictsStrong(testset: Traversable[Revision], refset: Traversable[Revision]): Boolean = {
      val refmap = revisionMapByFile(refset)
      testset.find(r => !refmap.contains(r.file) || r.isDescendantOf(refmap(r.file))) != None
    }

    def liveRevisions(revs: Traversable[Revision]): Set[Revision] = {
      revs.filter(!_.dead).toSet
    }

    def filesetsAreEqual(a: Traversable[Revision], b: Traversable[Revision]): Boolean = {
      a.toSet equals b.toSet
    }

    def filesetHash(fileset: Traversable[Revision]): String = {
      // there may be a performance hit associated with creating a new
      // digest engine, but this is best for concurrency
      val md = MessageDigest.getInstance("SHA-1")
      fileset.map(_.revString).toList.sorted.foreach(s => md.update(s.getBytes))
      hexString(md.digest)
    }

    def livesetHash(revs: Traversable[Revision]): String = filesetHash(liveRevisions(revs))

    def hexString(bytes: Array[Byte]): String = {
      val s = new StringBuilder()
      for (i <- 0 until bytes.length) {
        val b = bytes(i).toInt & 0xff
        if (b < 10) {
          s.append("0")
        }
        s.append(b.toHexString)
      }
      s.toString
    }

    def rootOfBranch(num: String): String = {
      val digits = num.split("[.]")
      digits.init.mkString(".")
    }
  }

}
