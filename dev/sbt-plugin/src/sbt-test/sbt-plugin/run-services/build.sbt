lazy val root = (project in file(".")).enablePlugins(LagomJava)

lagomUnmanagedServices in ThisBuild := Map("externalservice" -> "http://localhost:6000")

libraryDependencies += lagomJavadslPersistenceCassandra

// no need for Kafka on this test
lagomKafkaEnabled in ThisBuild := false
