// no need for Cassandra and Kafka on this test
lagomCassandraEnabled in ThisBuild := false
lagomKafkaEnabled in ThisBuild := false

lazy val p = (project in file("p"))
  .enablePlugins(PlayJava && LagomPlay)
  .settings(
    lagomServiceHttpPort := 9001,
    routesGenerator := InjectedRoutesGenerator,
    libraryDependencies ++= Seq(lagomJavadslClient, lagomJavadslApi)
  )
