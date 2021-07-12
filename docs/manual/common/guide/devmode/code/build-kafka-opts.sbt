//#kafka-port
ThisBuild / lagomKafkaPort := 10000
ThisBuild / lagomKafkaZookeeperPort  := 9999
//#kafka-port

//#kafka-properties
ThisBuild / lagomKafkaPropertiesFile :=
  Some((ThisBuild / baseDirectory).value / "project" / "kafka-server.properties")
//#kafka-properties

//#kafka-jvm-options
ThisBuild / lagomKafkaJvmOptions := Seq("-Xms256m", "-Xmx1024m") // these are actually the default jvm options
//#kafka-jvm-options

//#kafka-enabled
ThisBuild / lagomKafkaEnabled := false
//#kafka-enabled

//#external-instance
ThisBuild / lagomKafkaEnabled := false
ThisBuild / lagomKafkaAddress := "localhost:10000"
//#external-instance

//#local-instance
ThisBuild / lagomKafkaEnabled := false
ThisBuild / lagomKafkaPort := 10000
//#local-instance
