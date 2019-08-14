organization in ThisBuild := "com.example"
version in ThisBuild := "1.0-SNAPSHOT"

// the Scala version that will be used for cross-compiled libraries
scalaVersion in ThisBuild := sys.props.get("scala.version").getOrElse("2.12.9")

lazy val `server-backend-switch` = (project in file("."))
  .aggregate(`apis`, `netty-impl`, `akka-http-impl`)

lazy val `apis` = (project in file("apis"))
  .settings(
    libraryDependencies ++= Seq(
      lagomJavadslApi
    )
  )

lazy val `akka-http-impl` = (project in file("akka-http-impl"))
  .enablePlugins(LagomJava)
  .settings(lagomForkedTestSettings: _*)
  .dependsOn(`apis`)

lazy val `netty-impl` = (project in file("netty-impl"))
  .enablePlugins(LagomJava, LagomNettyServer)
  .disablePlugins(LagomAkkaHttpServer)
  .settings(lagomForkedTestSettings: _*)
  .dependsOn(`apis`)

lagomCassandraEnabled in ThisBuild := false
lagomKafkaEnabled in ThisBuild := false
