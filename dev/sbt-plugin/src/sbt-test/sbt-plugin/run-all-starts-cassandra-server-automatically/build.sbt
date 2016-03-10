import com.lightbend.lagom.sbt.InternalKeys.interactionMode

lazy val root = (project in file(".")).enablePlugins(LagomJava)
  .settings(libraryDependencies ++= Seq(
    lagomJavadslPersistence,
    lagomJavadslImmutables
  ))

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

InputKey[Unit]("isHelloServiceRegistered") := {
  try {
    if (DevModeBuild.isHelloServiceRegistered()) println("Location of the Hello service is known by the service locator")
    else throw new RuntimeException("Service locator doesn't know about the Hello service")
  }
  catch {
    case e: Exception => throw new RuntimeException("Service locator doesn't know about the Hello service")
  }
}

InputKey[Unit]("getHello") := {
  val response = DevModeBuild.getHello()
  val expected = "Hello, test!"
  if (response != expected) {
    throw new RuntimeException(s"Expected `${expected}`, received `${response}`")
  }
}
