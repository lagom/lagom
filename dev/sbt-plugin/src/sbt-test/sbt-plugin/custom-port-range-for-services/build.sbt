import com.lightbend.lagom.sbt.Internal.Keys.interactionMode

interactionMode in ThisBuild := com.lightbend.lagom.sbt.NonBlockingInteractionMode

lagomServicesPortRange in ThisBuild := PortRange(10000, 10001)

lazy val a = (project in file("a"))
  .enablePlugins(LagomJava)
  .settings(
    Seq(
      sourceDirectory := baseDirectory.value / "src-a",
      scalaVersion := sys.props.get("scala.version").getOrElse("2.12.8")
    )
  )

lazy val b = (project in file("b"))
  .enablePlugins(LagomJava)
  .settings(
    Seq(
      sourceDirectory := baseDirectory.value / "src-b",
      scalaVersion := sys.props.get("scala.version").getOrElse("2.12.8")
    )
  )

InputKey[Unit]("verifyPortProjA") := {
  val expected = Def.spaceDelimited().parsed.head.toInt
  val actual   = (lagomServicePort in a).value
  if (expected == actual) {
    println(s"Expected and got $expected port")
  } else {
    throw new RuntimeException(s"Expected $expected port but got $actual")
  }
}

InputKey[Unit]("verifyPortProjB") := {
  val expected = Def.spaceDelimited().parsed.head.toInt
  val actual   = (lagomServicePort in b).value
  if (expected == actual) {
    println(s"Expected and got $expected port")
  } else {
    throw new RuntimeException(s"Expected $expected port but got $actual")
  }
}
