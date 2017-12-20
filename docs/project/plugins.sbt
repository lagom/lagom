lazy val plugins = (project in file(".")).dependsOn(dev)

lazy val dev = ProjectRef(Path.fileProperty("user.dir").getParentFile, "sbt-plugin")

// TODO: Remove this once Play 2.6.10 is released
resolvers += Resolver.sonatypeRepo("snapshots")

resolvers += Resolver.typesafeIvyRepo("releases")
addSbtPlugin("com.lightbend.markdown" %% "sbt-lightbend-markdown" % "1.6.0")

// Needed for bintray configuration code samples
addSbtPlugin("me.lessis" % "bintray-sbt" % "0.3.0")

addSbtPlugin("com.typesafe.sbt" % "sbt-web" % "1.2.2")
addSbtPlugin("com.typesafe.sbt" % "sbt-twirl" % "1.1.1")
addSbtPlugin("com.typesafe.sbt" % "sbt-less" % "1.1.1")
addSbtPlugin("com.typesafe.sbt" % "sbt-uglify" % "1.0.3")
