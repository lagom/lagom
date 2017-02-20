<!--- Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com> -->
# Lagom contributor guidelines

## Prerequisites

Before making a contribution, it is important to make sure that the change you wish to make and the approach you wish to take will likely be accepted, otherwise you may end up doing a lot of work for nothing.  If the change is only small, for example, if it's a documentation change or a simple bug fix, then it's likely to be accepted with no prior discussion.  However, new features, or bigger refactorings should first be discussed in the [contributors chat](https://gitter.im/lagom/contributors).  Additionally, any issues with the [community label](https://github.com/lagom/lagom/labels/community) have been agreed to be a change that will likely be accepted.

## Development tips

- Use sbt launcher 0.13.13 or later, which will automatically read JVM options from `.jvmopts`.
- If using IntelliJ IDEA, import `lagom/docs/build.sbt` rather than `lagom/build.sbt`. This ensures that the source code and build dependencies in the docs project are also imported.

## Pull request procedure

1. Make sure you have signed the [Lightbend CLA](https://www.lightbend.com/contribute/cla); if not, sign it online.
2. Ensure that your contribution meets the following guidelines:
    1. Live up to the current code standard:
        - Not violate [DRY](http://programmer.97things.oreilly.com/wiki/index.php/Don%27t_Repeat_Yourself).
        - [Boy Scout Rule](http://programmer.97things.oreilly.com/wiki/index.php/The_Boy_Scout_Rule) needs to have been applied.
    2. Regardless of whether the code introduces new features or fixes bugs or regressions, it must have comprehensive tests.  This includes when modifying existing code that isn't tested.
    3. The code must be well documented using the Play flavour of markdown with extracted code snippets (see the [Play documentation guidelines](https://playframework.com/documentation/latest/Documentation).)  Each API change must have the corresponding documentation change.
    4. Implementation-wise, the following things should be avoided as much as possible:
        * Global state
        * Public mutable state
        * Implicit conversions
        * ThreadLocal
        * Locks
        * Casting
        * Introducing new, heavy external dependencies
    5. The Lagom API design rules are the following:
        * Features are forever, always think about whether a new feature really belongs to the core framework or if it should be implemented as a module
        * Code must conform to standard style guidelines and pass all tests
        * Features and documentation must be provided for both Scala and Java API (unless they only make sense for one of the languages)
        * Java APIs should go to `com.lightbend.lagom.javadsl.xxxxx` in `xxxxx-javadsl` sbt project (the sbt project might be in a folder named xxxx/javadsl or similar)
        * Scala APIs should go to `com.lightbend.lagom.scaladsl.xxxxx` in `xxxxx-scaladsl` sbt project (the sbt project might be in a folder named xxxx/scaladsl or similar)
    6. New files must:
        * Have a Lightbend copyright header in the style of ``Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>``. Running `sbt compile` will automatically add missing copyright headers.
        * Not use ``@author`` tags since it does not encourage [Collective Code Ownership](http://www.extremeprogramming.org/rules/collective.html).
3. Ensure that your commits are squashed.  See the [working with git guide](WorkingWithGit.md) for more information.
4. Submit a pull request.

If the pull request does not meet the above requirements then the code should **not** be merged into master, or even reviewed - regardless of how good or important it is. No exceptions.
