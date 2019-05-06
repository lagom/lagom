//#test-dependencies
libraryDependencies ++= Seq(
  lagomScaladslTestKit,
  "org.scalatest" %% "scalatest" % "3.0.1" % Test
)
//#test-dependencies

//#scala-test-val
val scalaTest = "org.scalatest" %% "scalatest" % "3.0.1" % "test"
//#scala-test-val

//#test-dependencies-val
libraryDependencies ++= Seq(
  lagomScaladslTestKit,
  scalaTest
)
//#test-dependencies-val

//#fork
lazy val `hello-impl` = (project in file("hello-impl"))
  .enablePlugins(LagomScala)
  .settings(lagomForkedTestSettings: _*)
  .settings(
    // ...
  )
//#fork
