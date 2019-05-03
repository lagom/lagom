import com.lightbend.lagom.sbt.Internal.Keys.interactionMode

lazy val root = (project in file(".")).enablePlugins(LagomJava)

scalaVersion := sys.props.get("scala.version").getOrElse("2.12.8")

interactionMode in ThisBuild := com.lightbend.lagom.sbt.NonBlockingInteractionMode

InputKey[Unit]("verifyReloads") := {
  val expected = Def.spaceDelimited().parsed.head.toInt
  val actual   = IO.readLines(target.value / "reload.log").count(_.nonEmpty)
  if (expected == actual) {
    println(s"Expected and got $expected reloads")
  } else {
    throw new RuntimeException(s"Expected $expected reloads but got $actual")
  }
}
