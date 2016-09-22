lazy val proj = (project in file("")).enablePlugins(LagomJava)
  .settings(
  //#kafka-broker-dependency
  libraryDependencies += lagomJavadslKafkaBroker
  //#kafka-broker-dependency
  ,
  //#kafka-cassandra-store-dependency
  libraryDependencies += lagomJavadslKafkaCassandraStore
  //#kafka-cassandra-store-dependency
  )
