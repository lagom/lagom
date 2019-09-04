import com.lightbend.lagom.sbt.Internal.Keys.interactionMode


scalaVersion := sys.props.get("scala.version").getOrElse("2.12.9")

interactionMode in ThisBuild := com.lightbend.lagom.sbt.NonBlockingInteractionMode

val lombok = "org.projectlombok" % "lombok" % "1.18.8"

lagomKafkaEnabled in ThisBuild := false
lagomCassandraEnabled in ThisBuild := true


lazy val `projectinos-happpy-path` = (project in file(".")).aggregate(`hello-impl`)

lazy val `hello-impl` = (project in file("hello"))
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
