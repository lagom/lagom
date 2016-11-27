import com.lightbend.lagom.sbt.Internal.Keys.interactionMode

interactionMode in ThisBuild := com.lightbend.lagom.sbt.NonBlockingInteractionMode

scalaVersion in ThisBuild := Option(System.getProperty("scala.version")).getOrElse("2.11.7")

val macwire = "com.softwaremill.macwire" %% "macros" % "2.2.5" % "provided"

lazy val `a-api` = (project in file("a") / "api")
  .settings(
    libraryDependencies += lagomScaladslApi
  )

lazy val `a-impl` = (project in file("a") / "impl").enablePlugins(LagomScala)
  .settings(
    lagomServicePort := 10000,
    libraryDependencies += macwire
  )
  .dependsOn(`a-api`)

lazy val `b-api` = (project in file("b") / "api")
  .settings(
    libraryDependencies += lagomScaladslApi
  )

lazy val `b-impl` = (project in file("b") / "impl").enablePlugins(LagomScala)
  .settings(
    lagomServicePort := 10001,
    libraryDependencies += macwire
  )
  .dependsOn(`b-api`, `a-api`)

// this isn't a microservice project
lazy val c = (project in file("c"))
  .settings(
    sourceDirectory := baseDirectory.value / "src-c",
    libraryDependencies += macwire
  )

lazy val p = (project in file("p")).enablePlugins(PlayJava && LagomPlay)
  .settings(
    lagomServicePort := 9001,
    routesGenerator := InjectedRoutesGenerator,
    libraryDependencies ++= Seq(
      lagomScaladslServer,
      macwire
    )
  )

InputKey[Unit]("verifyReloadsProjA") := {
  val expected = Def.spaceDelimited().parsed.head.toInt
  DevModeBuild.waitForReloads((target in `a-impl`).value / "reload.log", expected)
}

InputKey[Unit]("verifyReloadsProjB") := {
  val expected = Def.spaceDelimited().parsed.head.toInt
  DevModeBuild.waitForReloads((target in `b-impl`).value / "reload.log", expected)
}

InputKey[Unit]("verifyNoReloadsProjC") := {
  try {
    val actual = IO.readLines((target in c).value / "reload.log").count(_.nonEmpty)
    throw new RuntimeException(s"Found a reload file, but there should be none!")
  }
  catch {
    case e: Exception => () // if we are here it's all good
  }
}

InputKey[Unit]("assertRequest") := {
  val args = Def.spaceDelimited().parsed
  val port = args(0)
  val path = args(1)
  val expect = args.drop(2).mkString(" ")

  DevModeBuild.waitForRequestToContain(s"http://localhost:${port}${path}", expect)
}
