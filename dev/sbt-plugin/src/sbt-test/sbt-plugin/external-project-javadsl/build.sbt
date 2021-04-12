import play.sbt.PlayImport

// no need for Cassandra and Kafka on this test
ThisBuild / lagomCassandraEnabled := false
ThisBuild / lagomKafkaEnabled := false

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
