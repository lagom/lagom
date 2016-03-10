import com.lightbend.lagom.sbt.InternalKeys.interactionMode

lazy val root = (project in file(".")).enablePlugins(LagomJava)

scalaVersion := Option(System.getProperty("scala.version")).getOrElse("2.11.7")

interactionMode in ThisBuild := com.lightbend.lagom.sbt.NonBlockingInteractionMode

lagomUnmanagedServices in ThisBuild := Map("/externalservice" -> "http://localhost:6000")

InputKey[Unit]("isServiceLocatorDown") := {
  try {
    DevModeBuild.isServiceLocatorReachable()
    throw new RuntimeException("Service locator is running")
  }
  catch {
    case e: Exception => println("Service locator is not running")
  }
}

InputKey[Unit]("isServiceLocatorUp") := {
  try {
    DevModeBuild.isServiceLocatorReachable()
    println("Service locator was started at the expected address")
  }
  catch {
    case e: Exception => throw new RuntimeException("Service locator was not started")
  }
}

InputKey[Unit]("isFooServiceRegistered") := {
  try {
    if (DevModeBuild.isFooServiceRegistered()) println("Location of the Foo service is known by the service locator")
    else throw new RuntimeException("Service locator doesn't know about the Foo service")
  }
  catch {
    case e: Exception => throw new RuntimeException("Service locator doesn't know about the Foo service")
  }
}

InputKey[Unit]("isFooServiceUnregistered") := {
  try {
    DevModeBuild.isFooServiceRegistered()
    throw new RuntimeException("Foo service appears to be still registered in the service locator")
  }
  catch {
    case e: Exception => println("Foo service is not registered in the service locator")
  }
}

InputKey[Unit]("isExternalServiceRegistered") := {
  try {
    if (DevModeBuild.isExternalServiceRegistered()) println("Location of the external service is known by the service locator")
    else throw new RuntimeException("Service locator doesn't know about the external service")
  }
  catch {
    case e: Exception => throw new RuntimeException("Service locator doesn't know about the external service")
  }
}
