# Installation

Lagom consists of:

* an [sbt](http://www.scala-sbt.org) plugin
* libraries
* optional [Activator](https://www.lightbend.com/activator/) project templates

## Activator and sbt

[sbt](http://www.scala-sbt.org) is a build tool for Java and Scala. Activator adds a few features to sbt, notably the ability to start new projects from templates available online.

Because Activator includes sbt, you don't need to install anything except a JDK and Activator.  Activator will retrieve the Lagom plugin and libraries for use within your project.

Note that if you aren't using the optional project templates, you don't strictly need Activator; sbt itself is enough.  The examples in this manual always show the `activator` command, but unless the command is `activator new`, you can always substitute `activator` by `sbt`.

## Activator version (Read me!)

Older installations of Activator may cause OutOfMemoryException when using the Lagom development environment. To avoid this issue, make sure you are using the Activator package version 1.3.10+. Here is how you can check what version of the Activator package you are using on Linux systems (for Windows users, just check what's in your `%PATH%` environment variable):

```console
$ which activator
/opt/activator-1.3.10-minimal/bin/activator
```

The important bit is that the <version> appended after `activator-<version>` must be at least 1.13.10 for Lagom to properly work. 

Follow [this link](https://www.lightbend.com/activator/download) to download the latest activator bundle.

## Quick setup

If you already have Activator and JDK 8, you can skip ahead to [[Getting Started|GettingStarted]].

Otherwise, follow these steps:

1. **Ensure** you have Oracle JDK 8 installed on your system.
2. **Download** latest [Activator](https://www.lightbend.com/activator/download)
3. **Extract** it to a location where you have access.
4. **Add** the Activator installation directory to your `PATH`.

Some package managers can handle steps 2, 3, and 4 for you. For example, if you use Homebrew on Mac OS X, then `brew install typesafe-activator` and you're done.

More detailed information on these steps follows.

### JDK details

Lagom requires JDK 8 or later. Early JDK 8 releases had bugs that could affect Lagom, so you'll want to use a recent build.

You can check whether you have JDK 8 by running `java` and `javac` from the command line:

```
$ java -version
java version "1.8.0_74"
Java(TM) SE Runtime Environment (build 1.8.0_74-b02)
Java HotSpot(TM) 64-Bit Server VM (build 25.74-b02, mixed mode)
$ javac -version
javac 1.8.0_74
```

If you don't have java and javac, you can get it from the [Oracle Java downloads page](http://www.oracle.com/technetwork/java/javase/downloads/index.html).

### Activator details

To install Activator, download the [latest version](https://www.lightbend.com/activator/download) and extract it to a location that you have write access to.

Activator needs write access to its installation directory.  On Linux/Unix installations, this means that you shouldn't extract it to somewhere that you don't have write access to, for example `/opt` or `/usr/local`.

The `activator` script needs to be executable.  If not, you can make it executable by running `chmod u+x /path/to/activator`.

#### Adding the activator installation to your PATH

For convenience, you should add the Activator installation directory to your system `PATH`:

* On Linux/Unix, this can be done by adding `export PATH=/path/to/activator:$PATH` to your `.bashrc` file.
* On Windows, add `;C:\path\to\activator` to your `PATH` environment variable. Do not use a path with spaces.

#### Proxy Setup

If you can only access the web through a proxy, set the `HTTP_PROXY` environment variable to your proxy's URL, for example, `HTTP_PROXY=http://<host>:<port>`.
