ThisBuild / organization := "com.example"

// no need for Kafka on this test
ThisBuild / lagomCassandraEnabled := false
ThisBuild / lagomKafkaEnabled := false

val h2Driver = "com.h2database" % "h2" % "1.4.199"
val macwire = "com.softwaremill.macwire" %% "macros" % "2.3.3" % "provided"

lazy val `shopping-cart-scala` = (project in file("."))
  .aggregate(`shopping-cart-api`, `shopping-cart-akka-persistence-typed`, `shopping-cart-lagom-persistence`)

lazy val `shopping-cart-api` = (project in file("shopping-cart-api"))
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslApi
    )
  )

lazy val `shopping-cart-akka-persistence-typed` = (project in file("shopping-cart-akka-persistence-typed"))
  .enablePlugins(LagomScala)
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslPersistenceJdbc,
      lagomScaladslTestKit,
      macwire,
      h2Driver
    )
  )
  .settings(lagomForkedTestSettings)
  .dependsOn(`shopping-cart-api`)

lazy val `shopping-cart-lagom-persistence` = (project in file("shopping-cart-lagom-persistence"))
  .enablePlugins(LagomScala)
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslPersistenceJdbc,
      lagomScaladslTestKit,
      macwire,
      h2Driver
    )
  )
  .settings(lagomForkedTestSettings)
  .dependsOn(`shopping-cart-api`)
