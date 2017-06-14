import com.lightbend.lagom.sbt.Internal.Keys.interactionMode

lazy val `my-project` = (project in file(".")).enablePlugins(LagomJava)
  .settings(libraryDependencies ++= Seq(lagomJavadslPersistenceCassandra, lagomSbtScriptedLibrary))

scalaVersion := Option(System.getProperty("scala.version")).getOrElse("2.11.7")

interactionMode := com.lightbend.lagom.sbt.NonBlockingInteractionMode

val CassandraJournalPort           = "cassandra-journal.port"
val CassandraSnapshotStorePort     = "cassandra-snapshot-store.port"
val LagomCassandraReadPort         = "lagom.persistence.read-side.cassandra.port"
val InternalActorSystemName        = "lagom.akka.dev-mode.actor-system.name"
val ApplicationActorSystemName     = "play.akka.actor-system"


lazy val injectedCassandraConfig = Def.task { target.value / "injected-config.conf" }

def validate(configFile: java.io.File, key: String, expected: String) = {
  import com.typesafe.config._
  if(!configFile.isFile) throw new RuntimeException(s"No file found at ${configFile.getPath}")
  val config = ConfigFactory.parseFile(configFile)
  val actual = config.getString(key)
  if (expected == actual) {
    println(s"Expected and got $expected")
  } else {
    throw new RuntimeException(s"Expected value of key $key to be $expected but got $actual")
  }	
}

InputKey[Unit]("journalPort") := {
  val expectedValue = Def.spaceDelimited().parsed.head
  expectedValue.toInt // here just to check it doesn't throw
  validate(injectedCassandraConfig.value, CassandraJournalPort, expectedValue)
}

InputKey[Unit]("snapshotStorePort") := {
  val expectedValue = Def.spaceDelimited().parsed.head
  expectedValue.toInt // here just to check it doesn't throw
  validate(injectedCassandraConfig.value, CassandraSnapshotStorePort, expectedValue)
}

InputKey[Unit]("readPort") := {
  val expectedValue = Def.spaceDelimited().parsed.head
  expectedValue.toInt // here just to check it doesn't throw
  validate(injectedCassandraConfig.value, LagomCassandraReadPort, expectedValue)
}

InputKey[Unit]("readInternalActorSystemName") := {
  val expectedValue = Def.spaceDelimited().parsed.head
  validate(injectedCassandraConfig.value, InternalActorSystemName, expectedValue)
}

InputKey[Unit]("readApplicationActorSystemName") := {
  val expectedValue = Def.spaceDelimited().parsed.head
  validate(injectedCassandraConfig.value, ApplicationActorSystemName, expectedValue)
}
