// Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>

addSbtPlugin("com.eed3si9n" % "sbt-unidoc" % "0.4.2")
addSbtPlugin("de.heikoseeberger" % "sbt-header" % "5.2.0")
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "2.3")
addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.9")
addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.1.1")
addSbtPlugin("org.foundweekends" % "sbt-bintray" % "0.5.4")
addSbtPlugin("com.typesafe.sbt" % "sbt-multi-jvm" % "0.4.0")
addSbtPlugin("org.scalariform" % "sbt-scalariform" % "1.8.2")
addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "0.3.0")

addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.9.0")
addSbtPlugin("com.lightbend" % "sbt-whitesource" % "0.1.14")

enablePlugins(BuildInfoPlugin)
