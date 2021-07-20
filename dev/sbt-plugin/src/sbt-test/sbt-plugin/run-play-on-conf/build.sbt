// no need for Cassandra and Kafka on this test
ThisBuild / lagomCassandraEnabled := false
ThisBuild / lagomKafkaEnabled := false

lazy val p = (project in file("p"))
  .enablePlugins(PlayJava && LagomPlay)
  .settings(
    lagomServiceHttpPort := 9001,
    routesGenerator := InjectedRoutesGenerator,
    libraryDependencies ++= Seq(lagomJavadslClient, lagomJavadslApi)
  )
