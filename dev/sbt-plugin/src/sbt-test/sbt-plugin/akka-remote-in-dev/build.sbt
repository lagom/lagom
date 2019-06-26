import com.lightbend.lagom.sbt.Internal.Keys.interactionMode

interactionMode in ThisBuild := com.lightbend.lagom.sbt.NonBlockingInteractionMode

scalaVersion in ThisBuild := sys.props.get("scala.version").getOrElse("2.12.8")

val macwire = "com.softwaremill.macwire" %% "macros" % "2.2.5" % "provided"

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
    libraryDependencies ++= Seq(lagomScaladslCluster, macwire)
  )
  .dependsOn(`a-api`)

lazy val `b-api` = (project in file("b") / "api")
  .settings(
    libraryDependencies += lagomScaladslApi
  )


lazy val `b-impl` = (project in file("b") / "impl")
  .enablePlugins(LagomScala)
  .settings(
    lagomServiceHttpPort := 10001,
    libraryDependencies ++= Seq(lagomScaladslCluster, macwire)
  )
  .dependsOn(`b-api`)


InputKey[Unit]("assertRequest") := {
  val args   = Def.spaceDelimited().parsed
  val port   = args(0)
  val path   = args(1)
  val expect = args.drop(2).mkString(" ")

  DevModeBuild.waitForRequestToContain(s"http://localhost:${port}${path}", expect)
}
