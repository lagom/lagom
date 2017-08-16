// Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>

buildInfoSettings
sourceGenerators in Compile += buildInfo.taskValue

libraryDependencies ++= Seq(
  "org.scala-sbt" % "scripted-plugin" % sbtVersion.value
)

addSbtPlugin("com.eed3si9n" % "sbt-unidoc" % "0.3.3")
addSbtPlugin("de.heikoseeberger" % "sbt-header" % "1.8.0")
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "0.5.0")
addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.3")
addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.0.0")
addSbtPlugin("me.lessis" % "bintray-sbt" % "0.3.0")
// need this for com.typesafe.sbt.preprocess.Preprocess
addSbtPlugin("com.typesafe.sbt" % "sbt-site" % "0.7.1")
addSbtPlugin("org.scalariform" % "sbt-scalariform" % "1.6.0")
addSbtPlugin("com.typesafe.sbt" % "sbt-multi-jvm" % "0.3.8")
addSbtPlugin("com.typesafe.sbteclipse" % "sbteclipse-plugin" % "5.2.1")
addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "0.1.13")

addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.8.2")
