import play.sbt.PlayImport

scalaVersion in ThisBuild := sys.props.get("scala.version").getOrElse("2.12.10")

// no need for Cassandra and Kafka on this test
lagomCassandraEnabled in ThisBuild := false
lagomKafkaEnabled in ThisBuild := false

// This is the build for the external project that we will publishLocal so that it can then be imported,
// it doesn't use the Lagom plugin that way it won't be run when we run runAll
lazy val a = (project in file("a"))
  .settings(
    organization := "com.example",
    name := "a-scala",
    version := "1.0-SNAPSHOT",
    libraryDependencies ++= Seq(
      lagomScaladslServer,
      lagomScaladslDevMode,
      PlayImport.component("play-netty-server")
    )
  )

// And here's where we import the above project as an external project.
lazy val `external-a` = lagomExternalScaladslProject("a-external", "com.example" %% "a-scala" % "1.0-SNAPSHOT")
