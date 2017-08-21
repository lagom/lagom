
resolvers += Resolver.typesafeIvyRepo("releases")
addSbtPlugin("com.lightbend.markdown" % "sbt-lightbend-markdown" % "1.5.2")

// Needed for bintray configuration code samples
addSbtPlugin("me.lessis" % "bintray-sbt" % "0.3.0")

addSbtPlugin("com.typesafe.sbt" % "sbt-web" % "1.2.2")
addSbtPlugin("com.typesafe.sbt" % "sbt-twirl" % "1.1.1")
addSbtPlugin("com.typesafe.sbt" % "sbt-less" % "1.1.1")
addSbtPlugin("com.typesafe.sbt" % "sbt-uglify" % "1.0.3")

addSbtPlugin("com.lucidchart" % "sbt-scalafmt" % "1.10")
