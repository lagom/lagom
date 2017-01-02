//#cassandra-users-project
lazy val usersImpl = (project in file("usersImpl")).enablePlugins(LagomJava)
  .settings(
    name := "users-impl",
    lagomCassandraKeyspace := "users"
  )
//#cassandra-users-project

//#cassandra-yaml
lagomCassandraJvmOptions in ThisBuild := (lagomCassandraJvmOptions in ThisBuild).value ++ 
  Seq("-DCassandraLauncher.configResource=<path-to-yaml-config>")
//#cassandra-yaml
