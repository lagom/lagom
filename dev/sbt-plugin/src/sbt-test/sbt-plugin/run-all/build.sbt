import com.lightbend.lagom.sbt.InternalKeys.interactionMode

interactionMode in ThisBuild := com.lightbend.lagom.sbt.NonBlockingInteractionMode

val sv = Option(System.getProperty("scala.version")).getOrElse("2.11.7")

lazy val a = (project in file("a")).enablePlugins(LagomJava)
  .settings(Seq(
    sourceDirectory := baseDirectory.value / "src-a",
    scalaVersion := sv
  ))

lazy val b = (project in file("b")).enablePlugins(LagomJava)
  .settings(Seq(
    sourceDirectory := baseDirectory.value / "src-b",
    scalaVersion := sv
  ))

// this isn't a microservice project
lazy val c = (project in file("c"))
  .settings(Seq(
    sourceDirectory := baseDirectory.value / "src-c",
    scalaVersion := sv
  ))

lazy val p = (project in file("p")).enablePlugins(PlayJava && LagomPlay)
  .settings(
    scalaVersion := sv,
    lagomServicePort := 9001,
    routesGenerator := InjectedRoutesGenerator
  )

InputKey[Unit]("verifyReloadsProjA") := {
  val expected = Def.spaceDelimited().parsed.head.toInt
  DevModeBuild.waitForReloads((target in a).value / "reload.log", expected)
}

InputKey[Unit]("verifyReloadsProjB") := {
  val expected = Def.spaceDelimited().parsed.head.toInt
  DevModeBuild.waitForReloads((target in b).value / "reload.log", expected)
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

InputKey[Unit]("assertPlayRequest") := {
  val args = Def.spaceDelimited().parsed
  val uri = args.head
  val expect = args.tail.mkString(" ")

  DevModeBuild.waitForRequestToContain("http://localhost:9001" + uri, expect)
}
