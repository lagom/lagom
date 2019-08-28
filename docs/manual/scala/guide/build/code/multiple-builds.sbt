//#bintray-plugin
addSbtPlugin("me.lessis" % "bintray-sbt" % "0.3.0")
//#bintray-plugin

//#bintray-publish
// Set this to the organization that you want to publish to
bintrayOrganization in ThisBuild := Some("example-organization")
// This is needed for projects that are not open source
bintrayOmitLicense in ThisBuild := false
//#bintray-publish

//#hello-build
organization in ThisBuild := "com.example"

scalaVersion in ThisBuild := "2.12.9"

lazy val `hello-api` = (project in file("hello-api"))
  .settings(version := "1.0")
  .settings(libraryDependencies += lagomScaladslApi)

lazy val `hello-impl` = (project in file("hello-impl"))
  .enablePlugins(LagomScala)
  .settings(
    version := "1.0",
    libraryDependencies += lagomScaladslPersistence
  )
  .dependsOn(`hello-api`)
//#hello-build

//#hello-external
lazy val hello = lagomExternalScaladslProject("hello", "com.example" %% "hello-impl" % "1.0")
//#hello-external

//#hello-communication
lazy val `greetings-api` = (project in file("greetings-api"))
  .settings(libraryDependencies += lagomScaladslApi)

lazy val greetingsImpl = (project in file("greetings-impl"))
  .enablePlugins(LagomScala)
  .settings(libraryDependencies += "com.example" %% "hello-api" % "1.0")
  .dependsOn(`greetings-api`)
//#hello-communication
