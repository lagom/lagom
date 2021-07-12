//#cassandra-port
ThisBuild / lagomCassandraPort := 9042
//#cassandra-port

//#cassandra-clean-on-start
ThisBuild /lagomCassandraCleanOnStart := true
//#cassandra-clean-on-start

//#cassandra-jvm-options
ThisBuild / lagomCassandraJvmOptions:=
  Seq("-Xms256m", "-Xmx1024m", "-Dcassandra.jmx.local.port=4099") // these are actually the default jvm options
//#cassandra-jvm-options

//#cassandra-yaml-config
ThisBuild / lagomCassandraYamlFile:=
  Some((ThisBuild/ baseDirectory).value / "project" / "cassandra.yaml")
//#cassandra-yaml-config

//#cassandra-boot-waiting-time
import scala.concurrent.duration._ // Mind that the import is needed.
ThisBuild / lagomCassandraMaxBootWaitingTime := 0.seconds
//#cassandra-boot-waiting-time

//#cassandra-enabled
ThisBuild / lagomCassandraEnabled := false
//#cassandra-enabled

//#local-instance
ThisBuild / lagomCassandraEnabled := false
ThisBuild / lagomUnmanagedServices := Map("cas_native" -> "tcp://localhost:9042")
//#local-instance
