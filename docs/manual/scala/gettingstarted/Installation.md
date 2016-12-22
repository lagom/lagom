# Installation

Lagom is a set of libraries and build tool plugins that provide a framework and development environment. While the libraries can be consumed from any build tool, Lagom's high productivity development environment requires specific build tool support, which will run your services and the associated Lagom infrastructure, hot reloading when code changes are detected, all with a single command.

Lagom's Scala environment supports one build tool, [sbt](http://www.scala-sbt.org). sbt provides dependency management which will download the Lagom libraries and plugins for you, so the only thing to install is sbt.

## Installing a JDK

Before installing sbt, you need to ensure you have a Java Development Kit (JDK) installed on your system.  Lagom requires at least JDK 8.

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

## Installing sbt

Lagom requires at least sbt 0.13.5, but if you want to create new projects using Lagom's supplied templates, you'll need at least sbt 0.13.13, which contains the sbt new command.

sbt can be downloaded from [here](http://www.scala-sbt.org/download.html), while instructions for installing it can be found [here](http://www.scala-sbt.org/release/docs/Setup.html)

To check which version of sbt you are using, run `sbt sbt-version`, for example:

```console
$ sbt sbt-version
[info] Set current project to example (in build file:/home/example/)
[info] 0.13.13
```

## Proxy Setup

If you can only access the web through a proxy, set the `HTTP_PROXY` environment variable to your proxy's URL, for example, `HTTP_PROXY=http://<host>:<port>`.