
//#hello-build
organization in ThisBuild := "com.example"

scalaVersion in ThisBuild := "2.12.10"

lazy val `hello-api` = (project in file("hello-api"))
  .settings(version := "1.0")
  .settings(libraryDependencies += lagomJavadslApi)

lazy val `hello-impl` = (project in file("hello-impl"))
  .enablePlugins(LagomJava)
  .settings(
    version := "1.0",
    libraryDependencies += lagomJavadslPersistence
  )
  .dependsOn(`hello-api`)
//#hello-build

//#hello-external
lazy val hello = lagomExternalJavadslProject("hello", "com.example" %% "hello-impl" % "1.0")
//#hello-external

//#hello-communication
lazy val `greetings-api` = (project in file("greetings-api"))
  .settings(libraryDependencies += lagomJavadslApi)

lazy val `greetings-impl` = (project in file("greetings-impl"))
  .enablePlugins(LagomJava)
  .settings(libraryDependencies += "com.example" %% "hello-api" % "1.0")
  .dependsOn(`greetings-api`)
//#hello-communication
