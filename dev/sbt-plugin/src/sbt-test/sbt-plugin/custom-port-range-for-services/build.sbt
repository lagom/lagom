import com.lightbend.lagom.sbt.Internal.Keys.interactionMode

lagomServicesPortRange in ThisBuild := PortRange(10000, 10003)

lazy val a = (project in file("a"))
  .enablePlugins(LagomJava)
  .settings(sourceDirectory := baseDirectory.value / "src-a")

lazy val b = (project in file("b"))
  .enablePlugins(LagomJava)
  .settings(sourceDirectory := baseDirectory.value / "src-b")

InputKey[Unit]("verifyPortProjA") := {
  val expected = Def.spaceDelimited().parsed.head.toInt
  val actual   = (lagomServiceHttpPort in a).value
  if (expected == actual) {
    println(s"Expected and got $expected port")
  } else {
    throw new RuntimeException(s"Expected $expected port but got $actual")
  }
}

InputKey[Unit]("verifyPortProjB") := {
  val expected = Def.spaceDelimited().parsed.head.toInt
  val actual   = (lagomServiceHttpPort in b).value
  if (expected == actual) {
    println(s"Expected and got $expected port")
  } else {
    throw new RuntimeException(s"Expected $expected port but got $actual")
  }
}
