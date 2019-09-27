import com.lightbend.lagom.sbt.Internal.Keys.interactionMode

interactionMode in ThisBuild := com.lightbend.lagom.sbt.NonBlockingInteractionMode

scalaVersion in ThisBuild := sys.props.get("scala.version").getOrElse("2.12.9")

// no need for Cassandra and Kafka on this test
lagomCassandraEnabled in ThisBuild := false
lagomKafkaEnabled in ThisBuild := false

lazy val p = (project in file("p"))
  .enablePlugins(PlayJava && LagomPlay)
  .settings(
    lagomServiceHttpPort := 9001,
    routesGenerator := InjectedRoutesGenerator,
    libraryDependencies ++= Seq(lagomJavadslClient, lagomJavadslApi)
  )

InputKey[Unit]("assertRequest") := {
  val args   = Def.spaceDelimited().parsed
  val port   = args(0)
  val path   = args(1)
  val expect = args.drop(2).mkString(" ")

  DevModeBuild.waitForRequestToContain(s"http://localhost:${port}${path}", expect)
}
