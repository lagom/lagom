//#cassandra-port
lagomCassandraPort in ThisBuild := 9042
//#cassandra-port

//#cassandra-clean-on-start
lagomCassandraCleanOnStart in ThisBuild := true
//#cassandra-clean-on-start

//#cassandra-jvm-options
lagomCassandraJvmOptions in ThisBuild :=
  Seq("-Xms256m", "-Xmx1024m", "-Dcassandra.jmx.local.port=4099") // these are actually the default jvm options
//#cassandra-jvm-options

//#cassandra-yaml-config
lagomCassandraYamlFile in ThisBuild :=
  Some((baseDirectory in ThisBuild).value / "project" / "cassandra.yaml")
//#cassandra-yaml-config

//#cassandra-boot-waiting-time
import scala.concurrent.duration._ // Mind that the import is needed.
lagomCassandraMaxBootWaitingTime in ThisBuild := 0.seconds
//#cassandra-boot-waiting-time

//#cassandra-enabled
lagomCassandraEnabled in ThisBuild := false
//#cassandra-enabled

//#local-instance
lagomCassandraEnabled in ThisBuild := false
lagomUnmanagedServices in ThisBuild := Map("cas_native" -> "tcp://localhost:9042")
//#local-instance
