// Copyright (C) 2009-2015 Typesafe Inc. <http://www.typesafe.com>

buildInfoSettings
sourceGenerators in Compile += buildInfo.taskValue

libraryDependencies ++= Seq(
  "org.scala-sbt" % "scripted-plugin" % sbtVersion.value
)

addSbtPlugin("com.eed3si9n" % "sbt-unidoc" % "0.3.3")
addSbtPlugin("de.heikoseeberger" % "sbt-header" % "1.5.1")
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "0.5.0")
addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.3")
addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.0.0")
addSbtPlugin("me.lessis" % "bintray-sbt" % "0.3.0")
// need this for com.typesafe.sbt.preprocess.Preprocess
addSbtPlugin("com.typesafe.sbt" % "sbt-site" % "0.7.1")
addSbtPlugin("org.scalariform" % "sbt-scalariform" % "1.5.1")
addSbtPlugin("com.typesafe.sbt" % "sbt-multi-jvm" % "0.3.8")
addSbtPlugin("com.typesafe.sbteclipse" % "sbteclipse-plugin" % "5.1.0")
