import com.lightbend.lagom.sbt.Internal.Keys.interactionMode
import com.lightbend.lagom.sbt.Internal

scalaVersion in ThisBuild := Option(System.getProperty("scala.version")).getOrElse("2.11.7")

interactionMode in ThisBuild := com.lightbend.lagom.sbt.NonBlockingInteractionMode

lazy val lagomProj = (project in file("lagomProj")).enablePlugins(LagomJava)
  .settings(
    name := "lagom-dist-proj",
    version := "1.0-SNAPSHOT",
    libraryDependencies ++= Seq(lagomJavadslPersistenceCassandra, lagomSbtScriptedLibrary)
  )

lazy val playProj = (project in file("playProj")).enablePlugins(PlayJava, LagomPlay)
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

InputKey[Unit]("absence") := {
  val pathRegex = Def.spaceDelimited().parsed.head
  import java.nio.file.Files
  import java.nio.file.Paths
  import scala.collection.JavaConversions._
  val path = Paths.get(pathRegex)
  val files = Files.newDirectoryStream(path.getParent(), path.getFileName().toString()).iterator.toList
  if(files.nonEmpty)
    throw new RuntimeException(s"Found a file matching the provided file pattern `$pathRegex`!")
}

lazy val checkDevRuntimeClasspath = inputKey[Unit]("Checks presence of a jar in the DevRuntime classpath")

checkDevRuntimeClasspath := checkDevClasspathTask(Internal.Configs.DevRuntime).evaluated

def checkDevClasspathTask(config: Configuration): Def.Initialize[InputTask[Unit]] = Def.inputTaskDyn {
  val parsed = Def.spaceDelimited().parsed
  val projName = parsed.head
  val name = parsed.last
  checkClasspath(projName, name, config)
}

def checkClasspath(projName: String, name: String, config: Configuration): Def.Initialize[Task[Unit]] = Def.task {
  val cp = classpathOf(projName, config).value
  val names = cp.files.map(_.getName)
  val matches = names.filter(_ contains name)
  if (matches.isEmpty)
    throw new RuntimeException(s"No match in the ${config.name} classpath for jar `$name`")
}

def classpathOf(projName: String, config: Configuration) = Def.taskDyn {
  val structure = buildStructure.value
  val projRef = ProjectRef(structure.root, projName)
  managedClasspath in projRef in config
}
