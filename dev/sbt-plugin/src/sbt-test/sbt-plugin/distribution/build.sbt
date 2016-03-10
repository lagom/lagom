import com.lightbend.lagom.sbt.InternalKeys.interactionMode

scalaVersion in ThisBuild := Option(System.getProperty("scala.version")).getOrElse("2.11.7")

interactionMode in ThisBuild := com.lightbend.lagom.sbt.NonBlockingInteractionMode

lazy val lagomProj = (project in file("lagomProj")).enablePlugins(LagomJava)
  .settings(
    name := "lagom-dist-proj",
    version := "1.0-SNAPSHOT"
  )

lazy val playProj = (project in file("playProj")).enablePlugins(PlayScala, LagomPlay)
  .settings(
    name := "play-dist-proj",
    version := "1.0-SNAPSHOT",
    routesGenerator := InjectedRoutesGenerator
  )

val checkStartScriptLagomProj = taskKey[Unit]("checkStartScriptLagomProj")
val checkStartScriptPlayProj = taskKey[Unit]("checkStartScriptPlayProj")

checkStartScriptLagomProj := checkStartScriptTask(lagomProj)
checkStartScriptPlayProj := checkStartScriptTask(playProj)

def checkStartScriptTask(p: Project) = Def.task {
  val startScript = ((target in p).value) / "universal" / "stage" / "bin" / ((name in p).value)
  def startScriptError(contents: String, msg: String) = {
    println("Error in start script, dumping contents:")
    println(contents)
    sys.error(msg)
  }
  val contents = IO.read(startScript)
  val lines = IO.readLines(startScript)
  if (!contents.contains( """app_mainclass=("play.core.server.ProdServerStart")""")) {
    startScriptError(contents, "Cannot find the declaration of the main class in the script")
  }
  val appClasspath = lines.find(_ startsWith "declare -r app_classpath")
      .getOrElse( startScriptError(contents, "Start script doesn't declare app_classpath"))
}
