//#external-instance
lagomKafkaEnabled in ThisBuild := false
lagomKafkaAddress in ThisBuild := "localhost:10000"
//#external-instance

//#local-instance
lagomKafkaEnabled in ThisBuild := false
lagomKafkaPort in ThisBuild := 10000
//#local-instance
