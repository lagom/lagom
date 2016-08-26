
//#kafka-port
lagomKafkaPort in ThisBuild := 10000
lagomKafkaZookeperPort in ThisBuild := 9999
//#kafka-port

//#kafka-properties
lagomKafkaPropertiesFile in ThisBuild := "path" / "to" / "your" / "own" / "server.properties"
//#kafka-properties

//#kafka-jvm-options
lagomKafkaJvmOptions in ThisBuild := Seq("-Xms256m", "-Xmx1024m") // these are actually the default jvm options
//#kafka-jvm-options

//#cassandra-boot-waiting-time
import scala.concurrent.duration._ // Mind that the import is needed.
lagomCassandraMaxBootWaitingTime in ThisBuild := 0.seconds
//#cassandra-boot-waiting-time

//#cassandra-enabled
lagomCassandraEnabled in ThisBuild := false
//#cassandra-enabled

//#cassandra-users-project
lazy val usersImpl = (project in file("usersImpl")).enablePlugins(LagomJava)
  .settings(name := "users-impl")
//#cassandra-users-project
