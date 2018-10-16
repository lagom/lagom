package lagom.akka.discovery

import sbt._
import sbt.Keys._

object Dependencies {

  object Versions {

    val lagom14 = "1.4.0"
    val AkkaManagement = "0.18.0"
    val scalaTest = "3.0.5"
  }

  object Compile {
    val akkaDiscovery = "com.lightbend.akka.discovery" %% "akka-discovery" % Versions.AkkaManagement
    val lagomJavaDslClient = "com.lightbend.lagom" %% "lagom-javadsl-client" % Versions.lagom14 % "provided"
  }

  object Test {
    val scalaTest = "org.scalatest" %% "scalatest" % Versions.scalaTest % "test" // ApacheV2
  }

  private val deps = libraryDependencies

  val core = deps ++= Seq(
    Compile.akkaDiscovery, 
    Compile.lagomJavaDslClient,
    Test.scalaTest
  )

  
}
