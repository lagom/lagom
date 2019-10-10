val netty = "io.netty" % "netty" % "3.10.6.Final"
val macwire = "com.softwaremill.macwire" %% "macros" % "2.3.3" % "provided"

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
    libraryDependencies ++= Seq(lagomScaladslCluster, macwire, netty)
  )
  .dependsOn(`a-api`)

lazy val `b-api` = (project in file("b") / "api")
  .settings(
    libraryDependencies += lagomScaladslApi
  )


lazy val `b-impl` = (project in file("b") / "impl")
  .enablePlugins(LagomScala)
  .settings(
    lagomServiceHttpPort := 10001,
    libraryDependencies ++= Seq(lagomScaladslCluster, macwire, netty)
  )
  .dependsOn(`b-api`)
