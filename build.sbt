
import lagom.akka.discovery.Dependencies

scalaVersion := "2.12.7"

val commonSettings = Seq(
  organization := "com.lightbend.lagom",

  scalacOptions ++= List(
    "-unchecked",
    "-deprecation",
    "-language:_",
    "-encoding", "UTF-8"
  ),
  javacOptions ++= List(
    "-Xlint:unchecked",
    "-Xlint:deprecation"
  )
)



lazy val root = Project(
    id = "service-locator-akka-discovery",
    base = file(".")
  )
  .settings(commonSettings)
  .settings(Dependencies.core)