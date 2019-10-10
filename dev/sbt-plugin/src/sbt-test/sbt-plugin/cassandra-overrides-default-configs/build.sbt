lazy val `my-project` = (project in file("."))
  .enablePlugins(LagomJava)
  .settings(
    libraryDependencies ++= Seq(lagomJavadslPersistenceCassandra, lagomSbtScriptedLibrary)
  )

lagomCassandraEnabled in ThisBuild := true
// no need for Kafka on this test
lagomKafkaEnabled in ThisBuild := false

val CassandraJournalKeyspace       = "cassandra-journal.keyspace"
val CassandraJournalPort           = "cassandra-journal.port"
val CassandraSnapshotStoreKeyspace = "cassandra-snapshot-store.keyspace"
val CassandraSnapshotStorePort     = "cassandra-snapshot-store.port"
val LagomCassandraReadKeyspace     = "lagom.persistence.read-side.cassandra.keyspace"
val LagomCassandraReadPort         = "lagom.persistence.read-side.cassandra.port";

lazy val injectedCassandraConfig = Def.task { target.value / "injected-cassandra.conf" }

def validate(configFile: java.io.File, key: String, expected: String) = {
  import com.typesafe.config._
  if (!configFile.isFile) throw new RuntimeException(s"No file found at ${configFile.getPath}")
  val config = ConfigFactory.parseFile(configFile)
  val actual = config.getString(key)
  if (expected == actual) {
    println(s"Expected and got $expected")
  } else {
    throw new RuntimeException(s"Expected value of key $key to be $expected but got $actual")
  }
}

InputKey[Unit]("journalKeyspace") := {
  val expectedValue = Def.spaceDelimited().parsed.head
  validate(injectedCassandraConfig.value, CassandraJournalKeyspace, expectedValue)
}

InputKey[Unit]("journalPort") := {
  val expectedValue = Def.spaceDelimited().parsed.head
  expectedValue.toInt // here just to check it doesn't throw
  validate(injectedCassandraConfig.value, CassandraJournalPort, expectedValue)
}

InputKey[Unit]("snapshotStoreKeyspace") := {
  val expectedValue = Def.spaceDelimited().parsed.head
  validate(injectedCassandraConfig.value, CassandraSnapshotStoreKeyspace, expectedValue)
}

InputKey[Unit]("snapshotStorePort") := {
  val expectedValue = Def.spaceDelimited().parsed.head
  expectedValue.toInt // here just to check it doesn't throw
  validate(injectedCassandraConfig.value, CassandraSnapshotStorePort, expectedValue)
}

InputKey[Unit]("readKeyspace") := {
  val expectedValue = Def.spaceDelimited().parsed.head
  validate(injectedCassandraConfig.value, LagomCassandraReadKeyspace, expectedValue)
}

InputKey[Unit]("readPort") := {
  val expectedValue = Def.spaceDelimited().parsed.head
  expectedValue.toInt // here just to check it doesn't throw
  validate(injectedCassandraConfig.value, LagomCassandraReadPort, expectedValue)
}
