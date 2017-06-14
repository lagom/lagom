import play.sbt.PlayImport

scalaVersion in ThisBuild := Option(System.getProperty("scala.version")).getOrElse("2.11.7")

lagomCassandraEnabled in ThisBuild := false
lagomKafkaEnabled in ThisBuild := false

// This is the build for the external project that we will publishLocal so that it can then be imported,
// it doesn't use the Lagom plugin that way it won't be run when we run runAll
lazy val a = (project in file("a"))
  .settings(
    organization := "com.example",
    name := "a-java",
    version := "1.0-SNAPSHOT",
    libraryDependencies ++= Seq(
      lagomJavadslServer,
      PlayImport.component("play-netty-server")
    )
  )

// And here's where we import the above project as an external project.
lazy val `external-a` = lagomExternalJavadslProject("a-external", "com.example" %% "a-java" % "1.0-SNAPSHOT")