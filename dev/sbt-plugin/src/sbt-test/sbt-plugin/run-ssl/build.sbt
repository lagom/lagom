import com.lightbend.lagom.sbt.Internal.Keys.interactionMode

lazy val root = (project in file(".")).enablePlugins(LagomJava)

scalaVersion := sys.props.get("scala.version").getOrElse("2.12.10")

interactionMode in ThisBuild := com.lightbend.lagom.sbt.NonBlockingInteractionMode

lagomServiceEnableSsl in ThisBuild := true

// no need for Cassandra and Kafka on this test
lagomCassandraEnabled in ThisBuild := false
lagomKafkaEnabled in ThisBuild := false

InputKey[Unit]("makeRequest") := {
  val args                      = Def.spaceDelimited("<url> <status> ...").parsed
  val path :: status :: headers = args
  DevModeBuild.verifyResourceContains(path, status.toInt, Seq.empty, 0)
}
