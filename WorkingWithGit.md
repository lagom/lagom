<!--- Copyright (C) Lightbend Inc. <https://www.lightbend.com> -->
# Working with Git

This guide is designed to help new contributors get started with Lagom.  Some of the things mentioned here are conventions that we think are good and make contributing to Lagom easier, but they are certainly not prescriptive, you should use what works best for you.

## Git remotes

We recommend the convention of calling the remote for the official Lagom repository `origin`, and the remote for your fork your username.  This convention works well when sharing code between multiple forks, and is the convention we'll use for all the remaining git commands in this guide.  It is also the convention that works best out of the box with the [GitHub command line tool](https://github.com/github/hub).

## Branches

Typically all work should be done in branches.  If you do work directly on master, then you can only submit one pull request at a time, since if you try to submit a second from master, the second will contain commits from both your first and your second.  Working in branches allows you to isolate pull requests from each other.

It's up to you what you call your branches, some people like to include issue numbers in their branch name, others like to use a hierarchical structure.

### Responding to reviews/build breakages

If your pull request doesn't pass the CI build, if we review it and ask you to update your pull request, or if for any other reason you want to update your pull request, then rather than creating a new commit, amend the existing one.  This can be done by supplying the `--amend` flag when committing:

    git commit --amend

After doing an amend, you'll need to do a force push using the `--force` flag:

    git push yourremote yourbranch --force

## Starting over

Sometimes people find that they get their pull request completely wrong and want to start over.  This is fine, however there is no need to close the original pull request and open a new one.  You can use a force push to push a completely new branch into the pull request.

To start over, make sure you've got the latest changes from Lagom core, and create a new branch from that point:

    git fetch origin
    git checkout -b mynewbranch origin/master

Now make your changes, and then when you're ready to submit a pull request, assuming your old branch was called `myoldbranch`, push your new branch into that old branch in your repository:

    git push yourremote mynewbranch:myoldbranch --force

Now the pull request should be updated with your new branch.

## A word on changing history

You may have heard it said that you shouldn't change git history once you publish it.  Using `rebase` and `commit --amend` both change history, and using `push --force` will publish your changed history.

There are definitely times when git history shouldn't be changed after being published.  The main times are when it's likely that other people have forked your repository, or pulled changes from your repository.  Changing history in those cases will make it impossible for them to safely merge changes from your repo into their repository.  For this reason, we never change history in the official Lagom Framework repository.

However, when it comes to your personal fork, for branches that are just intended to be pull requests, then it's a different matter - the intent of the workflow is that your changes get "published" once they get merged into the master branch.  Before that, the history can be considered a work in progress.
