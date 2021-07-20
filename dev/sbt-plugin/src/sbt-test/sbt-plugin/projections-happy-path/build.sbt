val lombok = "org.projectlombok" % "lombok" % "1.18.18"
val macwire = "com.softwaremill.macwire" %% "macros" % "2.3.3" % "provided"
val scalaTest = "org.scalatest" %% "scalatest" % "3.1.2" % Test

ThisBuild / lagomCassandraEnabled := true
// no need for Kafka on this test
ThisBuild / lagomKafkaEnabled := false

lazy val `projections-happpy-path` = (project in file(".")).aggregate(`hello-javadsl`, `hello-scaladsl`)

lazy val `hello-javadsl` = (project in file("hello-javadsl"))
  .enablePlugins(LagomJava)
  .settings(
    lagomServiceHttpPort := 10001,
    Seq(Compile / javacOptions += "-parameters"),
    libraryDependencies ++= Seq(
      lagomJavadslApi,
      lagomJavadslPersistenceCassandra,
      lagomLogback,
      lagomJavadslTestKit,
      lombok
    )
  )
  .settings(lagomForkedTestSettings)

lazy val `hello-scaladsl` = (project in file("hello-scaladsl"))
  .enablePlugins(LagomScala)
  .settings(
    lagomServiceHttpPort := 10002,
    libraryDependencies ++= Seq(
      lagomScaladslApi,
      lagomScaladslPersistenceCassandra,
      lagomScaladslTestKit,
      lagomLogback,
      macwire,
      scalaTest
    )
  )
  .settings(lagomForkedTestSettings)
