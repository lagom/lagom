// Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>

addSbtPlugin("com.eed3si9n" % "sbt-unidoc" % "0.4.2")

// the plugins used during release can have an impact on default values
// of the build. To validate your changes on the release plugins don't
// affect the release process, review https://github.com/lagom/lagom/issues/1496#issuecomment-408398508
addSbtPlugin("de.heikoseeberger" % "sbt-header"   % "5.2.0")
addSbtPlugin("org.xerial.sbt"    % "sbt-sonatype" % "2.3")
addSbtPlugin("com.github.gseitz" % "sbt-release"  % "1.0.11")
addSbtPlugin("com.jsuereth"      % "sbt-pgp"      % "1.1.2")
addSbtPlugin("org.foundweekends" % "sbt-bintray"  % "0.5.4")

addSbtPlugin("com.typesafe.sbt" % "sbt-multi-jvm"   % "0.4.0")
addSbtPlugin("com.typesafe"     % "sbt-mima-plugin" % "0.3.0")

addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.9.0")
addSbtPlugin("com.lightbend"    % "sbt-whitesource"      % "0.1.14")

addSbtPlugin("com.lightbend.sbt" % "sbt-java-formatter" % "0.4.4")

enablePlugins(BuildInfoPlugin)
