
//#cassandra-yaml
lagomCassandraJvmOptions in ThisBuild := (lagomCassandraJvmOptions in ThisBuild).value ++
  Seq("-DCassandraLauncher.configResource=<path-to-yaml-config>")
//#cassandra-yaml
