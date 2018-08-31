import com.lightbend.lagom.sbt.Internal.Keys.interactionMode

lazy val root = (project in file(".")).enablePlugins(LagomJava)

scalaVersion := sys.props.get("scala.version").getOrElse("2.12.6")

interactionMode in ThisBuild := com.lightbend.lagom.sbt.NonBlockingInteractionMode

lagomServiceHttpPort := 7080
lagomServiceHttpsPort := 7443

InputKey[Unit]("makeRequest") := {
  val args = Def.spaceDelimited("<url> <status> ...").parsed
  val path :: status :: headers = args
  DevModeBuild.verifyResourceContains(path, status.toInt, Seq.empty, 0)
}
