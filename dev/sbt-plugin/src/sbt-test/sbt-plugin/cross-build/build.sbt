import com.lightbend.lagom.sbt._
enablePlugins(LagomJava)
Internal.Keys.interactionMode in ThisBuild := NonBlockingInteractionMode
scalaVersion := sys.props.get("scala.version").getOrElse("2.12.9")
