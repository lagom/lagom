//#add-sbt-plugin
addSbtPlugin("com.lightbend.lagom" % "lagom-sbt-plugin" % "X.Y.Z") // replace 'X.Y.Z' with your preferred version (e.g. '1.2.0-RC2').
//#add-sbt-plugin

//#scala-version
scalaVersion in ThisBuild := "2.11.7"
//#scala-version

//#hello-world-api
lazy val helloworldApi = (project in file("helloworld-api"))
  .settings(
    version := "1.0-SNAPSHOT",
    libraryDependencies += lagomJavadslApi
  )
//#hello-world-api

//#hello-world-impl
lazy val helloworldImpl = (project in file("helloworld-impl"))
  .enablePlugins(LagomJava)
  .settings(
    version := "1.0-SNAPSHOT"
  )
  .dependsOn(helloworldApi)
//#hello-world-impl

//#hello-stream
lazy val hellostreamApi = (project in file("hellostream-api"))
  .settings(
    version := "1.0-SNAPSHOT",
    libraryDependencies += lagomJavadslApi
  )

lazy val hellostreamImpl = (project in file("hellostream-impl"))
  .enablePlugins(LagomJava)
  .settings(
    version := "1.0-SNAPSHOT"
  )
  .dependsOn(hellostreamApi, helloworldApi)
//#hello-stream
