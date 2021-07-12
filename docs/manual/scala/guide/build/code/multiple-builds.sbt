
//#hello-build
ThisBuild / organization := "com.example"

ThisBuild / scalaVersion := "2.12.10"

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
