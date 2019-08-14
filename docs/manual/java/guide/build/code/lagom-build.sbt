//#add-sbt-plugin
addSbtPlugin("com.lightbend.lagom" % "lagom-sbt-plugin" % "X.Y.Z") // replace 'X.Y.Z' with your preferred version (e.g. '1.2.0-RC2').
//#add-sbt-plugin

//#scala-version
scalaVersion in ThisBuild := "2.12.9"
//#scala-version

//#hello-api
lazy val `hello-api` = (project in file("hello-api"))
  .settings(
    version := "1.0-SNAPSHOT",
    libraryDependencies += lagomJavadslApi
  )
//#hello-api

//#hello-impl
lazy val `hello-impl` = (project in file("hello-impl"))
  .enablePlugins(LagomJava)
  .settings(
    version := "1.0-SNAPSHOT"
  )
  .dependsOn(`hello-api`)
//#hello-impl

//#hello-stream
lazy val `hello-stream-api` = (project in file("hello-stream-api"))
  .settings(
    version := "1.0-SNAPSHOT",
    libraryDependencies += lagomJavadslApi
  )

lazy val `hello-stream-impl` = (project in file("hello-stream-impl"))
  .enablePlugins(LagomJava)
  .settings(
    version := "1.0-SNAPSHOT"
  )
  .dependsOn(`hello-stream-api`, `hello-api`)
//#hello-stream

//#hello-stream-netty
lazy val `hello-stream-netty-impl` = (project in file("hello-stream-nettyimpl"))
  .enablePlugins(LagomJava, LagomNettyServer) // Adds LagomNettyServer
  .disablePlugins(LagomAkkaHttpServer)        // Removes LagomAkkaHttpServer
  .settings(
    version := "1.0-SNAPSHOT"
  )
  .dependsOn(`hello-stream-api`, `hello-api`)
//#hello-stream-netty
