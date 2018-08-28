// Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>

libraryDependencies ++= Seq(
  "org.scala-sbt" % "scripted-plugin" % sbtVersion.value
)

addSbtPlugin("com.eed3si9n" % "sbt-unidoc" % "0.3.3")

// the plugins used during release can have an impact on default values
// of the build. To validate your changes on the release plugins don't
// affect the release process, review https://github.com/lagom/lagom/issues/1496#issuecomment-408398508
addSbtPlugin("de.heikoseeberger" % "sbt-header" % "5.0.0")
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "2.3")
addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.9")
addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.1.1")
addSbtPlugin("org.foundweekends" % "sbt-bintray" % "0.5.4")


addSbtPlugin("com.typesafe.sbt" % "sbt-multi-jvm" % "0.4.0")
addSbtPlugin("org.scalariform" % "sbt-scalariform" % "1.8.2")
addSbtPlugin("com.typesafe.sbteclipse" % "sbteclipse-plugin" % "5.2.1")
addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "0.3.0")
addSbtPlugin("com.eed3si9n" % "sbt-doge" % "0.1.5")

addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.9.0")
addSbtPlugin("com.lightbend" % "sbt-whitesource" % "0.1.11")

enablePlugins(BuildInfoPlugin)
