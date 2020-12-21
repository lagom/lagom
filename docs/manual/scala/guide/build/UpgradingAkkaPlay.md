# Using newer versions of Akka and Play Framework

Lagom relies on Akka and Play Framework for some of its
implementation.

The versions of Akka and Play that ship with Lagom should
usually be sufficient to run a Lagom application. However,
in some cases it can be useful to upgrade them while staying
on the same version of Lagom.
This is usually possible when Akka and Play maintains binary
compatibility between releases.

When you are using sbt, you can force new versions of Akka
and Play by adding the following to your `build.sbt`:

```
val akkaVersion = "2.5.31"
val akkaHttpVersion = "10.1.12"
val playVersion = "2.7.6"

libraryDependencies in ThisBuild ++= Seq(
  "com.typesafe.akka" %% "akka-discovery" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-sharding" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-tools" % akkaVersion,
  "com.typesafe.akka" %% "akka-coordination" % akkaVersion,
  "com.typesafe.akka" %% "akka-distributed-data" % akkaVersion,
  "com.typesafe.akka" %% "akka-persistence" % akkaVersion,
  "com.typesafe.akka" %% "akka-persistence-query" % akkaVersion,
  "com.typesafe.akka" %% "akka-protobuf" % akkaVersion,
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,

  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-core" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,

  "com.typesafe.play" %% "play" % playVersion,
)
```

And also update the version of the play plugin in `project/plugins.sbt`:

```
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % playVersion)
```
