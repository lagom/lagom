lazy val root = (project in file(".")).enablePlugins(LagomJava)

// no need for Cassandra and Kafka on this test
ThisBuild / lagomCassandraEnabled := false
ThisBuild / lagomKafkaEnabled := false

InputKey[Unit]("verifyReloads") := {
  val expected = Def.spaceDelimited().parsed.head.toInt
  val actual   = IO.readLines(target.value / "reload.log").count(_.nonEmpty)
  if (expected == actual) {
    println(s"Expected and got $expected reloads")
  } else {
    throw new RuntimeException(s"Expected $expected reloads but got $actual")
  }
}
