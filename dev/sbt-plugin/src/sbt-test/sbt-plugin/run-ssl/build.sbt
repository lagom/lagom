ThisBuild / lagomServiceEnableSsl := true

// no need for Cassandra and Kafka on this test
ThisBuild / lagomCassandraEnabled := false
ThisBuild / lagomKafkaEnabled := false

lazy val root = (project in file(".")).enablePlugins(LagomJava)

InputKey[Unit]("makeRequest") := {
  val args                      = Def.spaceDelimited("<url> <status> ...").parsed
  val path :: status :: headers = args
  DevModeBuild.verifyResourceContains(path, status.toInt, Seq.empty, 0)
}
