# Introduction and prerequisites

Lagom exposes two APIs, Java and Scala, and provides a framework and development environment as a set of libraries and build tool plugins. While the libraries can be consumed from any build tool, you can only take advantage of Lagom's high productivity development environment by using one of the supported build tools, Maven or sbt. 

We recommend using [sbt](http://www.scala-sbt.org) as the build tool for the Lagom Scala API. The sbt build tool provides dependency management, which downloads the Lagom libraries and plugins for you. When you create an sbt build, Lagom tool plugins will run your services and the associated Lagom infrastructure with a single command and hot reload when the tool detects code changes.

Factoring or re-factoring functionality into right-sized services will be critical to the success of your project. And Lagom's opinionated framework will steer you in the right direction. But, it is a good idea to start small. For this reason, Lagom provides a Giter8 template that sets up a build structure for a Hello World application. The template contains two services to demonstrate intra-service communication, because, ([to quote](https://twitter.com/jboner/status/699536472442011648) Jonas Bonér):

> One microservice is no microservice - they come in systems.

The template also gives you a quick way to verify that your project and build tool are set up correctly. Later, you can download more complex [[Lagom examples|LagomExamples]] that demonstrate Lagom functionality. 
 
We also suggest that you start from the command line. After using the template to create an sbt build, you can integrate it into any IDE. The documentation provides tips to help you with Eclipse or IntelliJ, two popular IDEs. 
 
Before trying the template, make sure that your environment conforms to Lagom prerequisites:

* Java Development Kit (JDK), version 8 or higher. 
* sbt 0.13.5 or higher (To create new projects using Lagom-supplied templates, use sbt 0.13.13 or higher, which contains the sbt new command.)
* Internet access (If using a proxy, verify that an HTTP_PROXY environment variable points to the correct location)

For more details on verifying or installing prerequisites see the following sections:

* [JDK](#JDK)
* [Installing sbt](#sbt)
* [Proxy setup](#Proxy-setup)

When your environment is ready, follow the instructions for [[Creating and running Hello World|GettingStarted]].

## JDK

Before installing sbt, you need to ensure you have a Java Development Kit (JDK) installed on your system.  Lagom requires at least JDK 8.

You can check whether you have JDK 8 by running `java -version` and `javac -version` from the command line.

The `java -version` command should return messages similar to the following:

```
java version "1.8.0_74"
Java(TM) SE Runtime Environment (build 1.8.0_74-b02)
Java HotSpot(TM) 64-Bit Server VM (build 25.74-b02, mixed mode)
```
The `javac -version` command should return a message similar to:

```
javac 1.8.0_74
```
If you have the correct JDK and the console cannot find `java` or `javac`, search the web for information about setting environment variables on your system. For example, the following pages provide tips for configuring Java:
* [On systems running Linux](http://stackoverflow.com/questions/33860560/how-to-set-java-environment-variables-using-shell-script)
* [On MacOS](http://osxdaily.com/2015/07/28/set-enviornment-variables-mac-os-x/)
* [On Windows systems](http://stackoverflow.com/questions/1672281/environment-variables-for-java-installation)

If you don't have the correct version, you can get it from the [Oracle Java downloads page](http://www.oracle.com/technetwork/java/javase/downloads/index.html).

## sbt

Lagom requires at least sbt 0.13.5, but if you want to create new projects using Lagom's supplied templates, you'll need at least sbt 0.13.13, which contains the sbt `new` command. (You'll need 0.13.13 to follow the steps in this getting started documentation.)

sbt can be downloaded from [here](http://www.scala-sbt.org/download.html), while instructions for installing it can be found [here](http://www.scala-sbt.org/release/docs/Setup.html)

To check which version of sbt you are using, run `sbt sbt-version` from the command line. The console messages should look similar to the following :

```
[info] Set current project to example (in build file:/home/example/)
[info] 0.13.13
```

## Proxy setup

If you can only access the web through a proxy, create and/or set the `HTTP_PROXY` environment variable on your machine  to your proxy's URL, for example, `HTTP_PROXY=http://<host>:<port>`.