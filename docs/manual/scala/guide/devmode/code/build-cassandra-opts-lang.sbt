

//#cassandra-users-project
lazy val usersImpl = (project in file("usersImpl")).enablePlugins(LagomScala)
  .settings(name := "users-impl")
//#cassandra-users-project

//#cassandra-users-project-with-keyspace
lazy val usersImplKeyspaced = (project in file("usersImpl")).enablePlugins(LagomScala)
  .settings(
    name := "users-impl",
    lagomCassandraKeyspace := "users"
  )
//#cassandra-users-project-with-keyspace
