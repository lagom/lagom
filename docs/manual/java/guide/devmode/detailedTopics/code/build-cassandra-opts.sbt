
//#cassandra-port
lagomCassandraPort in ThisBuild := 9042
//#cassandra-port

//#cassandra-clean-on-start
lagomCassandraCleanOnStart in ThisBuild := false
//#cassandra-clean-on-start

//#cassandra-jvm-options
lagomCassandraJvmOptions in ThisBuild := 
  Seq("-Xms256m", "-Xmx1024m", "-Dcassandra.jmx.local.port=4099",
    "-DCassandraLauncher.configResource=dev-embedded-cassandra.yaml") // these are actually the default jvm options
//#cassandra-jvm-options

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
