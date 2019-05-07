//#kafka-port
lagomKafkaPort in ThisBuild := 10000
lagomKafkaZookeeperPort in ThisBuild := 9999
//#kafka-port

//#kafka-properties
lagomKafkaPropertiesFile in ThisBuild :=
  Some((baseDirectory in ThisBuild).value / "project" / "kafka-server.properties")
//#kafka-properties

//#kafka-jvm-options
lagomKafkaJvmOptions in ThisBuild := Seq("-Xms256m", "-Xmx1024m") // these are actually the default jvm options
//#kafka-jvm-options

//#kafka-enabled
lagomKafkaEnabled in ThisBuild := false
//#kafka-enabled

//#external-instance
lagomKafkaEnabled in ThisBuild := false
lagomKafkaAddress in ThisBuild := "localhost:10000"
//#external-instance

//#local-instance
lagomKafkaEnabled in ThisBuild := false
lagomKafkaPort in ThisBuild := 10000
//#local-instance
