import com.lightbend.lagom.sbt.InternalKeys.interactionMode

lazy val root = (project in file(".")).enablePlugins(LagomJava)

scalaVersion := Option(System.getProperty("scala.version")).getOrElse("2.11.7")

interactionMode in ThisBuild := com.lightbend.lagom.sbt.NonBlockingInteractionMode

InputKey[Unit]("isCassandraServerDown") := {
  try {
    DevModeBuild.isCassandraServerReachable()
    throw new RuntimeException("Cassandra is running")
  }
  catch {
    case e: Exception => println("Cassandra is not running")
  }
}

InputKey[Unit]("isCassandraServerUp") := {
  try {
    DevModeBuild.isCassandraServerReachable()
    println("Cassandra was started at the expected address")
  }
  catch {
    case e: Exception => throw new RuntimeException("Cassandra was not started")
  }
}
