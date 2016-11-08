//#bintray-plugin
addSbtPlugin("me.lessis" % "bintray-sbt" % "0.3.0")
//#bintray-plugin

//#bintray-publish
// Set this to the organization that you want to publish to
bintrayOrganization in ThisBuild := Some("example-organization")
// This is needed for projects that are not open source
bintrayOmitLicense in ThisBuild := false
//#bintray-publish

//#helloworld-build
organization in ThisBuild := "sample.helloworld"

scalaVersion in ThisBuild := "2.11.7"

lazy val helloworldApi = (project in file("helloworld-api"))
  .settings(version := "1.0")
  .settings(libraryDependencies += lagomJavadslApi)

lazy val helloworldImpl = (project in file("helloworld-impl"))
  .enablePlugins(LagomJava)
  .settings(
    version := "1.0",
    libraryDependencies += lagomJavadslPersistence
  )
  .dependsOn(helloworldApi)
//#helloworld-build

//#helloworld-external
lazy val helloworld = lagomExternalProject("helloworld", "sample.helloworld" %% "helloworld-impl" % "1.0")
//#helloworld-external

//#helloworld-communication
lazy val greetingsApi = (project in file("greetings-api"))
  .settings(libraryDependencies += lagomJavadslApi)

lazy val greetingsImpl = (project in file("greetings-impl")).enablePlugins(LagomJava)
  .settings(libraryDependencies += "sample.helloworld" %% "helloworld-api" % "1.0")
  .dependsOn(greetingsApi)
//#helloworld-communication
