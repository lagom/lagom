# Introduction

Lagom exposes two APIs, Java and Scala, and provides a framework and development environment as a set of libraries and build tool plugins. While the libraries can be consumed from any build tool, you can only take advantage of Lagom's high productivity development environment by using one of the supported build tools, Maven or sbt. You can use Maven with Java or sbt with  Java or Scala.

## Choose your tool 

Maven and sbt both provide dependency management to download the Lagom libraries and plugins for you. Once you create a project, Lagom tool plugins will run your services and the associated Lagom infrastructure with a single command and hot reload when the tool detects code changes.

The Lagom team is committed to providing full support for both sbt and Maven. Choose the tool that will work best for you:

* If you are a Java developer with existing knowledge of [Maven](https://maven.apache.org/), this familiarity can help you get started faster with Lagom. If your organization has existing infrastructure, plugins and best practices built around Maven, that might also make Maven the more practical choice.

* [sbt](https://www.scala-sbt.org/) is a very powerful build tool that allows Lagom's features to be very easily supported and implemented - there are many aspects of the Lagom development environment that work a little smoother and faster in sbt than Maven due to sbt's power and flexibility. For this reason, sbt is the build tool of choice for the maintainers of Lagom.

## Start with a template
 A Lagom system typically includes one or more groups of services. To ([quote](https://twitter.com/jboner/status/699536472442011648) Jonas Bonér):

> One microservice is no microservice - they come in systems.   

Factoring or re-factoring functionality into right-sized services will be critical to the success of your project. And, Lagom's opinionated framework will steer you in the right direction. But, it is a good idea to start small.
 
To simplify your development experience, Lagom provides a Maven archetype and an sbt Giter8 template. Both create a build structure with a simple Hello World service and all of the components necessary to run it. The Lagom template illustrates intra-service communication, providing an example to learn from and a quick way to verify that your project and build tool are set up correctly. 

Later, you can download more complex [[Lagom examples|LagomExamples]] that demonstrate Lagom functionality. Both Maven and sbt integrate with IDEs, but we suggest that you start from the command line with your choice of tool. Once you have a Maven project group or an sbt build, you can integrate these structures into any Java IDE. We have some tips to help you with Eclipse or IntelliJ, two popular IDEs.

>**Note:** Shell terminology and appearance differs between Windows, Linux, and Mac operating systems. Our Getting Started instructions for a command line assume familiarity with a shell and that your account has the appropriate permissions. For example, if you are using a DOS shell, you'll want to run it as administrator. 

To set up your environment and use a template to create your first project from the command line, follow the appropriate steps:

* [[Verify Java prerequisites|JavaPrereqs]]

* [[Create and run Hello World with  Maven|GettingStartedMaven]]

* [[Create and run Hello World with sbt|GettingStartedSbt]]
