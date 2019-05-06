import com.lightbend.lagom.sbt.Internal.Keys.interactionMode

interactionMode in ThisBuild := com.lightbend.lagom.sbt.NonBlockingInteractionMode

lazy val commonSettings = Seq(
  scalaVersion := sys.props.get("scala.version").getOrElse("2.12.8")
)

lazy val fooApi = (project in file("foo/api"))
  .settings(commonSettings: _*)
  .settings(libraryDependencies += lagomJavadslApi)

lazy val fooImpl = (project in file("foo/impl"))
  .enablePlugins(LagomJava)
  .settings(commonSettings: _*)
  .dependsOn(fooApi)
  .dependsOn(barApi)

lazy val barApi = (project in file("bar/api"))
  .settings(commonSettings: _*)
  .settings(libraryDependencies += lagomJavadslApi)

lazy val barImpl = (project in file("bar/impl"))
  .enablePlugins(LagomJava)
  .settings(commonSettings: _*)
  .dependsOn(barApi)

InputKey[Unit]("verifyReloadsFoo") := {
  val expected = Def.spaceDelimited().parsed.head.toInt
  val actual   = IO.readLines((target in fooImpl).value / "reload.log").count(_.nonEmpty)
  if (expected == actual) {
    println(s"Expected and got $expected reloads")
  } else {
    throw new RuntimeException(s"Expected $expected reloads but got $actual")
  }
}

InputKey[Unit]("verifyReloadsBar") := {
  val expected = Def.spaceDelimited().parsed.head.toInt
  val actual   = IO.readLines((target in barImpl).value / "reload.log").count(_.nonEmpty)
  if (expected == actual) {
    println(s"Expected and got $expected reloads")
  } else {
    throw new RuntimeException(s"Expected $expected reloads but got $actual")
  }
}

InputKey[Unit]("callFoo") := {
  val response = DevModeBuild.callFoo()
  val expected = "Greetings from bar service"
  if (response != expected) {
    throw new RuntimeException(s"Expected `${expected}`, received `${response}`")
  }
}
