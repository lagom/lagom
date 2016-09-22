
//#kafka-port
lagomKafkaPort in ThisBuild := 10000
lagomKafkaZookeperPort in ThisBuild := 9999
//#kafka-port

//#kafka-properties
lagomKafkaPropertiesFile in ThisBuild := Some(file("path") / "to" / "your" / "own" / "server.properties")
//#kafka-properties

//#kafka-jvm-options
lagomKafkaJvmOptions in ThisBuild := Seq("-Xms256m", "-Xmx1024m") // these are actually the default jvm options
//#kafka-jvm-options

//#kafka-enabled
lagomKafkaEnabled in ThisBuild := false
//#kafka-enabled
