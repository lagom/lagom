import com.lightbend.lagom.sbt.Internal.Keys.interactionMode

val macwire = "com.softwaremill.macwire" %% "macros" % "2.2.5" % "provided"

// no need for Cassandra and Kafka on this test
lagomCassandraEnabled in ThisBuild := false
lagomKafkaEnabled in ThisBuild := false

lazy val `a-api` = (project in file("a") / "api")
  .settings(
    libraryDependencies += lagomScaladslApi
  )

lazy val `a-impl` = (project in file("a") / "impl")
  .enablePlugins(LagomScala)
  .settings(
    lagomServiceHttpPort := 10000,
    libraryDependencies += macwire
  )
  .dependsOn(`a-api`)
