# Installation

Lagom is a set of libraries and build tool plugins that provide a framework and development environment. While the libraries can be consumed from any build tool, Lagom's high productivity development environment requires specific build tool support, which will run your services and the associated Lagom infrastructure, hot reloading when code changes are detected, all with a single command.

Lagom supports two different build tools, [Maven](https://maven.apache.org/) and [sbt](http://www.scala-sbt.org). These tools both provide dependency management which will download the Lagom libraries and plugins for you, so the only thing to install is one of these tools. Which you use is up to you.

sbt is a very powerful build tool that allows Lagom's features to be very easily supported and implemented - there are many aspects of the Lagom development environment that work a little smoother and faster in sbt than Maven due to sbt's power and flexibility. For this reason, sbt is the build tool of choice for the maintainers of Lagom.

However, many Java developers will find that their existing knowledge and familiarity of Maven will allow them to get started a lot faster with Lagom. Additionally, their organization may have existing infrastructure, plugins and best practices built around Maven that they want to take advantage of, making Maven a practical selection.

Either way, the Lagom team is committed to providing full support for both sbt and Maven, so either choice is a good choice.

## Installing a JDK

Before installing a build tool, you need to ensure you have a Java Development Kit (JDK) installed on your system.  Lagom requires at least JDK 8.

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

## Installing Maven

To install Maven, see the official [Maven installation page](https://maven.apache.org/install.html).

There are many other ways to install Maven, Lagom should work with Maven no matter how it was installed, as long as it is a recent Maven version (we recommend at least Maven 3.3).

## Installing sbt

[sbt](http://www.scala-sbt.org) is a build tool for Java and Scala. In addition to sbt, another tool, Activator adds a few features to sbt, notably the ability to start new projects from templates available online.

Because Activator includes sbt, if you install Activator, you don't need to install anything else.

Note that if you aren't using the optional project templates, you don't strictly need Activator; sbt itself is enough.  The examples in this manual always show the `activator` command, but unless the command is `activator new`, you can always substitute `activator` by `sbt`.

### Activator version (Read me!)

Older installations of Activator may cause OutOfMemoryException when using the Lagom development environment. To avoid this issue, make sure you are using the Activator package version 1.3.10+. Here is how you can check what version of the Activator package you are using on Linux systems (for Windows users, just check what's in your `%PATH%` environment variable):

```console
$ which activator
/opt/activator-1.3.10-minimal/bin/activator
```

The important bit is that the <version> appended after `activator-<version>` must be at least 1.3.10 for Lagom to properly work. 

Follow [this link](https://www.lightbend.com/activator/download) to download the latest activator bundle.

### Quick setup

If you already have Activator and JDK 8, you can skip ahead to [[Getting Started|GettingStarted]].

Otherwise, follow these steps:

1. **Ensure** you have Oracle JDK 8 installed on your system.
2. **Download** latest [Activator](https://www.lightbend.com/activator/download)
3. **Extract** it to a location where you have access.
4. **Add** the Activator installation directory to your `PATH`.

Some package managers can handle steps 2, 3, and 4 for you. For example, if you use Homebrew on Mac OS X, then `brew install typesafe-activator` and you're done.

More detailed information on these steps follows.

#### Activator details

To install Activator, download the [latest version](https://www.lightbend.com/activator/download) and extract it to a location that you have write access to.

Activator needs write access to its installation directory.  On Linux/Unix installations, this means that you shouldn't extract it to somewhere that you don't have write access to, for example `/opt` or `/usr/local`.

The `activator` script needs to be executable.  If not, you can make it executable by running `chmod u+x /path/to/activator`.

##### Adding the activator installation to your PATH

For convenience, you should add the Activator installation directory to your system `PATH`:

* On Linux/Unix, this can be done by adding `export PATH=/path/to/activator:$PATH` to your `.bashrc` file.
* On Windows, add `;C:\path\to\activator` to your `PATH` environment variable. Do not use a path with spaces.

##### Proxy Setup

If you can only access the web through a proxy, set the `HTTP_PROXY` environment variable to your proxy's URL, for example, `HTTP_PROXY=http://<host>:<port>`.
