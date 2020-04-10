lazy val plugins = (project in file(".")).dependsOn(dev)

lazy val dev = ProjectRef(Path.fileProperty("user.dir").getParentFile, "sbt-plugin")

resolvers += Resolver.typesafeIvyRepo("releases") // sbt 1.3 regression
addSbtPlugin("com.lightbend.markdown" %% "sbt-lightbend-markdown" % "1.8.0")

addSbtPlugin("org.foundweekends" % "sbt-bintray" % "0.5.6")

addSbtPlugin("com.typesafe.sbt" % "sbt-web"    % "1.4.4")
addSbtPlugin("com.typesafe.sbt" % "sbt-twirl"  % "1.5.0") // sync with project/Dependencies.scala
addSbtPlugin("com.typesafe.sbt" % "sbt-less"   % "1.1.2")
addSbtPlugin("com.typesafe.sbt" % "sbt-uglify" % "2.0.0")

addSbtPlugin("de.heikoseeberger" % "sbt-header"         % "5.5.0")
addSbtPlugin("com.lightbend.sbt" % "sbt-java-formatter" % "0.5.1")
addSbtPlugin("org.scalameta"     % "sbt-scalafmt"       % "2.3.2")
