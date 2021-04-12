lazy val root = (project in file(".")).enablePlugins(LagomJava)

ThisBuild / lagomUnmanagedServices := Map("externalservice" -> "http://localhost:6000")

libraryDependencies += lagomJavadslPersistenceCassandra

// no need for Kafka on this test
ThisBuild / lagomKafkaEnabled := false
