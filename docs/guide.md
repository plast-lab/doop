# Doop Coding Guidelines

----

# Feature Branches

We use branches for developing or testing new features. The (main) **default** branch should always represent a stable version of the framework.

Useful commands

```
#!bash
hg branch feature-X               # creates a new branch called 'feature-X'
hg branch                         # displays current branch
hg branches                       # displays all **open** branches
hg branches -c                    # displays **all** branches (even closed ones)
hg up default                     # changes current branch to default
```

### Push changes from default branch to feature-X

When the feature branch needs changes from the default branch (e.g. for a bug fix)

```
#!bash
hg up feature-X
hg merge default
hg ci -m 'Push changes from default branch to feature-X'

```

### Push changes from feature-X branch to default

When changes from the feature branch are stable enough to appear in the default branch. Development may continue in the feature branch.

```
#!bash
hg up default
hg merge feature-X
hg ci -m 'Push changes to default branch from feature-X'
```

### Close a branch

When a specific feature branch is no longer developed, either because it is aborted or because it was merged in the default branch (and is considered finished).

```
#!bash
hg up feature-X
hg ci -m 'Close feature-X branch' --close-branch
```

----

# Tags

Tagging is useful when we need to bookmark certain commits (e.g. the commit after a conference submission).

```
#!bash
hg tag conference-ABC-2016        # creates a tag for the current commit
hg tags                           # displays **all** tags
hg up feature-X-v1.0              # updates to a different tagged commit
```

----

# Multiple remotes (public Doop repository)
### !!! CAUTION !!! -- Skip if not certain

We can also push commits directly to the public Doop repository. Please, be **extra careful** performing the following.

Add the following in your `.hg/hgrc` under the section `[paths]`. Keep in mind that this file is not under version control.

`public = ssh://hg@bitbucket.org/yanniss/doop`

```
#!bash
hg push                           # pushes changes to the default remote (this repository)
hg push default                   # pushes changes to the default remote (this repository)
hg push --branch default public   # pushes changes **only** from the default branch, to the public repository
```

If we omit `--branch default` in the last command, changes from **ALL** branches will be pushed to the public repository!

----

# Style Guidelines

* Don't include timestamps in comments
* Don't include comments regarding licencing issues. License is covered in [LICENSE](LICENSE)
* Don't include comments regarding authors. All authors should be referenced in [COLLABORATORS](COLLABORATORS)
* Read [How to write a Git Commit Message](http://chris.beams.io/posts/git-commit/). The lessons from the article can be applied to mercurial as well.
