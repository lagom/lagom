# Lagom Java prerequisites

Before starting with Lagom, check to make sure that you have the correct versions of a Java Development Kit (JDK) and build tool. Your build tool must also have internet access.

Reference these sections for help with:

1. [JDK](#JDK)
1. [Maven](#Maven) or [sbt](#sbt)
1. [Internet access through a proxy](#Internet-access-through-a-proxy)



## JDK

Before installing a build tool, verify that you have a Java Development Kit (JDK), version 8 and that your machine is configured correctly.

Check the JDK version by running `java` and `javac` from the command line:

```
java -version
javac -version

```
For `java`, the console should respond with the major version number of 1.8, the following shows an example of what you would see in the shell with a 1.8.0_162-b12 build:

```
java version "1.8.0_162"
Java(TM) SE Runtime Environment (build 1.8.0_162-b12)
Java HotSpot(TM) 64-Bit Server VM (build 25.162-b12, mixed mode)
```

for `javac`, the console should respond with something similar to:

```
javac 1.8.0_162
```

If you have the correct JDK and the console cannot find `java` or `javac`, search the web for information about setting environment variables on your system. For example, the following pages provide tips for configuring Java:

* [On systems running Linux](https://stackoverflow.com/questions/33860560/how-to-set-java-environment-variables-using-shell-script)
* [On MacOS](http://osxdaily.com/2015/07/28/set-enviornment-variables-mac-os-x/)
* [On Windows systems](https://stackoverflow.com/questions/1672281/environment-variables-for-java-installation)

If you do not have the correct JDK, download it from the  [Oracle website](https://www.oracle.com/technetwork/java/javase/downloads/index.html).


## Maven

Lagom requires Maven 3.6.0 or higher. In addition to verifying the version on the command line, check that your IDE is using the correct version.

To check the Maven version from a command line, enter:

`mvn -version`

To install Maven, see the official [Maven installation page](https://maven.apache.org/install.html).

## sbt

[sbt](https://www.scala-sbt.org) is a build tool for Java and Scala. Lagom recommends using sbt 1.2.1 or later.

In a console, check your version using the `sbt sbtVersion` command:

```
sbt sbtVersion
```
The system should respond with something like the following:

```
[info] Set current project to example (in build file:/home/alice/)
[info] 1.2.1
```
If you do not have the right version of sbt, download it from [the scala-sbt website](https://www.scala-sbt.org/download.html). The [documentation](https://www.scala-sbt.org/release/docs/Setup.html) contains installation instructions.

## Internet access through a proxy
If you access the internet through a proxy, make sure that your build tool can connect to the proxy:

* For sbt, an `HTTP_PROXY` environment variable should point to your proxy's URL, for example, `HTTP_PROXY=http://<host>:<port>`.

* For Maven, see the documentation on [configuring a proxy](https://maven.apache.org/guides/mini/guide-proxies.html).
