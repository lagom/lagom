// no need for Cassandra and Kafka on this test
lagomCassandraEnabled in ThisBuild := false
lagomKafkaEnabled in ThisBuild := false

lazy val fooApi = (project in file("foo/api"))
  .settings(libraryDependencies += lagomJavadslApi)

lazy val fooImpl = (project in file("foo/impl"))
  .enablePlugins(LagomJava)
  .dependsOn(fooApi)
  .dependsOn(barApi)

lazy val barApi = (project in file("bar/api"))
  .settings(libraryDependencies += lagomJavadslApi)

lazy val barImpl = (project in file("bar/impl"))
  .enablePlugins(LagomJava)
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
