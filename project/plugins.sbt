// Copyright (C) Lightbend Inc. <https://www.lightbend.com>

addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.9.0")

addSbtPlugin("com.eed3si9n" % "sbt-unidoc" % "0.4.3")

// the plugins used during release can have an impact on default values
// of the build. To validate your changes on the release plugins don't
// affect the release process, review https://github.com/lagom/lagom/issues/1496#issuecomment-408398508
addSbtPlugin("de.heikoseeberger" % "sbt-header"   % "5.6.0")
addSbtPlugin("org.xerial.sbt"    % "sbt-sonatype" % "3.9.2")
addSbtPlugin("com.github.gseitz" % "sbt-release"  % "1.0.13")
addSbtPlugin("com.jsuereth"      % "sbt-pgp"      % "2.0.1")
addSbtPlugin("org.foundweekends" % "sbt-bintray"  % "0.5.6")

addSbtPlugin("com.typesafe.sbt" % "sbt-multi-jvm"   % "0.4.0")
addSbtPlugin("com.typesafe"     % "sbt-mima-plugin" % "0.7.0")

addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.9.2")
addSbtPlugin("com.lightbend"    % "sbt-whitesource"      % "0.1.18")

addSbtPlugin("com.lightbend.sbt" % "sbt-java-formatter" % "0.5.1")
addSbtPlugin("org.scalameta"     % "sbt-scalafmt"       % "2.3.4")
addSbtPlugin("com.dwijnand"      % "sbt-dynver"         % "4.0.0")

addSbtPlugin("com.lightbend.akka" % "sbt-akka-version-check" % "0.1")
