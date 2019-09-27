import com.lightbend.lagom.sbt.Internal.Keys.interactionMode


scalaVersion in ThisBuild := sys.props.get("scala.version").getOrElse("2.12.9")

interactionMode in ThisBuild := com.lightbend.lagom.sbt.NonBlockingInteractionMode

val lombok = "org.projectlombok" % "lombok" % "1.18.8"
val macwire = "com.softwaremill.macwire" %% "macros" % "2.3.0" % "provided"
val scalaTest = "org.scalatest" %% "scalatest" % "3.0.4" % Test


lagomCassandraEnabled in ThisBuild := true
// no need for Kafka on this test
lagomKafkaEnabled in ThisBuild := false

lazy val `projections-happpy-path` = (project in file(".")).aggregate(`hello-javadsl`, `hello-scaladsl`)

lazy val `hello-javadsl` = (project in file("hello-javadsl"))
  .enablePlugins(LagomJava)
  .settings(
    lagomServiceHttpPort := 10001,
    Seq(javacOptions in Compile += "-parameters"),
    libraryDependencies ++= Seq(
      lagomJavadslApi,
      lagomJavadslPersistenceCassandra,
      lagomLogback,
      lagomJavadslTestKit,
      lombok
    )
  )
  .settings(lagomForkedTestSettings)

lazy val `hello-scaladsl` = (project in file("hello-scaladsl"))
  .enablePlugins(LagomScala)
  .settings(
    lagomServiceHttpPort := 10002,
    libraryDependencies ++= Seq(
      lagomScaladslApi,
      lagomScaladslPersistenceCassandra,
      lagomScaladslTestKit,
      lagomLogback,
      macwire,
      scalaTest
    )
  )
  .settings(lagomForkedTestSettings)


InputKey[Unit]("makeRequest") := {
  val args                      = Def.spaceDelimited("<url> <status> ...").parsed
  val path :: headers = args
  DevModeBuild.makeRequest(path)
}

InputKey[Unit]("assertRequest") := {
  val args   = Def.spaceDelimited().parsed
  val port   = args(0)
  val path   = args(1)
  val expect = args.drop(2).mkString(" ")

  DevModeBuild.waitForRequestToContain(s"http://localhost:${port}${path}", expect)
}
