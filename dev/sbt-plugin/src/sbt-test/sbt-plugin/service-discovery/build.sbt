import com.lightbend.lagom.sbt.Internal.Keys.interactionMode

interactionMode in ThisBuild := com.lightbend.lagom.sbt.NonBlockingInteractionMode

scalaVersion in ThisBuild := sys.props.get("scala.version").getOrElse("2.12.10")

val macwire = "com.softwaremill.macwire" %% "macros" % "2.2.5" % "provided"

// no need for Cassandra and Kafka on this test
lagomCassandraEnabled in ThisBuild := false
lagomKafkaEnabled in ThisBuild := false

lazy val `a-api` = (project in file("a") / "api")
  .settings(
    libraryDependencies += lagomScaladslApi
  )

lazy val `a-impl` = (project in file("a") / "impl")
  .enablePlugins(LagomScala)
  .settings(
    lagomServiceHttpPort := 10000,
    libraryDependencies += macwire
  )
  .dependsOn(`a-api`)



InputKey[Unit]("assertRequest") := {
  val args   = Def.spaceDelimited().parsed
  val port   = args(0)
  val path   = args(1)
  val expect = args.drop(2).mkString(" ")

  DevModeBuild.waitForRequestToContain(s"http://localhost:${port}${path}", expect)
}
