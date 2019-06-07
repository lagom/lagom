<!--- Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com> -->
# Lagom contributor guidelines

## Prerequisites

Before making a contribution, it is important to make sure that the change you wish to make and the approach you wish to take will likely be accepted, otherwise you may end up doing a lot of work for nothing.  If the change is only small, for example, if it's a documentation change or a simple bugfix, then it's likely to be accepted with no prior discussion.  However, new features, or bigger refactorings should first be discussed on the [our forums](https://discuss.lightbend.com/c/lagom).  Additionally, there are issues labels you can use to navigate issues that a good start to contribute:

- [`help wanted`](https://github.com/lagom/lagom/labels/help%20wanted)
- [`type:community`](https://github.com/lagom/lagom/labels/type%3Acommunity)
- [`good first issue`](https://github.com/lagom/lagom/labels/good%20first%20issue)

## Development tips

- Use sbt launcher 1.2.x or later, which will automatically read JVM options from `.jvmopts`.
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
        - Global state
        - Public mutable state
        - Implicit conversions
        - ThreadLocal
        - Locks
        - Casting
        - Introducing new, heavy external dependencies
    5. The Lagom API design rules are the following:
        - Features are forever, always think about whether a new feature really belongs to the core framework or if it should be implemented as a module
        - Code must conform to standard style guidelines and pass all tests
        - Features and documentation must be provided for both Scala and Java API (unless they only make sense for one of the languages)
        - Java APIs should go to `com.lightbend.lagom.javadsl.xxxxx` in `xxxxx-javadsl` sbt project (the sbt project might be in a folder named xxxx/javadsl or similar)
        - Scala APIs should go to `com.lightbend.lagom.scaladsl.xxxxx` in `xxxxx-scaladsl` sbt project (the sbt project might be in a folder named xxxx/scaladsl or similar)
    6. Basic local validation:
        - Not use `@author` tags since it does not encourage [Collective Code Ownership](https://www.extremeprogramming.org/rules/collective.html).
        - Run `bin/local-pr-validation.sh` to ensure all files are formatted and have the copyright header.
3. Submit a pull request.

If the pull request does not meet the above requirements then the code should **not** be merged into master, or even reviewed - regardless of how good or important it is. No exceptions.
