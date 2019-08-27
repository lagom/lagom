lazy val plugins = (project in file(".")).dependsOn(dev)

lazy val dev = ProjectRef(Path.fileProperty("user.dir").getParentFile, "sbt-plugin")

addSbtPlugin("com.lightbend.markdown" %% "sbt-lightbend-markdown" % "1.7.0")

addSbtPlugin("org.foundweekends" % "sbt-bintray" % "0.5.5")

addSbtPlugin("com.typesafe.sbt" % "sbt-web"    % "1.4.4")
addSbtPlugin("com.typesafe.sbt" % "sbt-twirl"  % "1.5.0-M3")
addSbtPlugin("com.typesafe.sbt" % "sbt-less"   % "1.1.2")
addSbtPlugin("com.typesafe.sbt" % "sbt-uglify" % "2.0.0")

addSbtPlugin("de.heikoseeberger" % "sbt-header"         % "5.2.0")
addSbtPlugin("com.lightbend.sbt" % "sbt-java-formatter" % "0.4.4")
addSbtPlugin("org.scalameta"     % "sbt-scalafmt"       % "2.0.4")
