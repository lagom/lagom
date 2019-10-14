import com.lightbend.lagom.sbt._
enablePlugins(LagomJava)
scalaVersion := sys.props.get("scala.version").getOrElse("2.12.10")
