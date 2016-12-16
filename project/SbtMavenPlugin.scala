package lagom

import java.io.File
import java.nio.file.Files

import sbt._
import sbt.Keys._
import sbt.plugins.JvmPlugin

import scala.util.Try
import scala.xml.{Elem, NodeSeq, PrettyPrinter, XML}

/**
  * Generates the maven plugin descriptor.
  *
  * This has two primary purposes:
  *
  * 1. Generate parts of the descriptor that can be determined from the build. This includes names, descriptions,
  *   dependencies.
  * 2. Reduce duplication. Mojos that depend on the same parameters end up having that parameter configuration
  *   duplicated. This saves on duplication by defining all parameters in one place that can then be referenced.
  */
object SbtMavenPlugin extends AutoPlugin {

  override def trigger = noTrigger
  override def requires = JvmPlugin

  object autoImport {
    val mavenGeneratePluginXml = taskKey[Seq[File]]("Generate the maven plugin xml")
    val mavenTest = inputKey[Unit]("Run the maven tests")
    val mavenTestArgs = settingKey[Seq[String]]("Maven test arguments")
    val mavenClasspath = taskKey[Seq[File]]("The maven classpath")
  }

  import autoImport._

  override def projectSettings = inConfig(Compile)(unscopedSettings) ++ mavenTestSettings

  def unscopedSettings: Seq[Setting[_]] = Seq(
    sourceDirectory in mavenGeneratePluginXml := sourceDirectory.value / "maven",
    sources in mavenGeneratePluginXml :=
      Seq((sourceDirectory in mavenGeneratePluginXml).value / "plugin.xml").filter(_.exists()),

    target in mavenGeneratePluginXml := target.value / "maven-plugin-xml",
    managedResourceDirectories += (target in mavenGeneratePluginXml).value,

    mavenGeneratePluginXml := {
      val files = (sources in mavenGeneratePluginXml).value
      val outDir = (target in mavenGeneratePluginXml).value / "META-INF" / "maven"
      IO.createDirectory(outDir)

      val pid = projectID.value
      val pi = projectInfo.value
      val deps = allDependencies.value
      val sv = scalaVersion.value
      val sbv = scalaBinaryVersion.value
      val log = streams.value.log

      val configHash = Seq(pid.toString, pi.toString, deps.toString, sv, sbv).hashCode()
      val cacheFile = streams.value.cacheDirectory / "maven.plugin.xml.cache"
      val cachedHash = Some(cacheFile).filter(_.exists()).flatMap { file =>
        Try(IO.read(file).toInt).toOption
      }
      val configChanged = cachedHash.forall(_ != configHash)

      val outFiles = files.map { file =>
        val outFile = outDir / file.getName

        if (file.lastModified() > outFile.lastModified() || configChanged) {
          log.info(s"Generating $outFile from template")
          val template = XML.loadFile(file)
          val processed = processTemplate(template, pid, pi, deps, CrossVersion(sv, sbv), log)
          IO.write(outFile, new PrettyPrinter(120, 2).format(processed))
        }
        outFile
      }

      IO.write(cacheFile, configHash.toString)

      outFiles
    },

    resourceGenerators += mavenGeneratePluginXml.taskValue
  )

  def mavenTestSettings: Seq[Setting[_]] = Seq(
    sourceDirectory in mavenTest := sourceDirectory.value / "maven-test",
    mavenTest := {
      import sbt.complete.Parsers._

      val toRun = (OptSpace ~> StringBasic).?.parsed

      runMavenTests((sourceDirectory in mavenTest).value, mavenClasspath.value, mavenTestArgs.value, toRun,
        streams.value.log)
    }
  )

  def runMavenTests(testDirectory: File, mavenClasspath: Seq[File], mavenTestArgs: Seq[String], toRun: Option[String], log: Logger) = {
    val testsToRun = toRun.fold(testDirectory.listFiles().toSeq.filter(_.isDirectory)) { dir =>
      Seq(testDirectory / dir)
    }

    val results = testsToRun.map { test =>
      val mavenExecutions = IO.readLines(test / "test").map(_.trim).filter(_.nonEmpty)

      val testDir = Files.createTempDirectory("maven-test").toFile
      try {
        IO.copyDirectory(test, testDir)

        val args = Seq("-cp", mavenClasspath.map(_.getAbsolutePath).mkString(File.pathSeparator),
          s"-Dmaven.multiModuleProjectDirectory=${testDir.getAbsolutePath}"
        ) ++
          mavenTestArgs ++
          Seq(
            "org.apache.maven.cli.MavenCli"
          )
        log.info(s"Running maven test ${test.getName} with arguments ${args.mkString(" ")}")

        test.getName -> mavenExecutions.foldLeft(true) { (success, execution) =>
          if (success) {
            log.info(s"Executing mvn $execution")
            val rc = Fork.java(ForkOptions(workingDirectory = Some(testDir)), args ++ execution.split(" +"))
            rc == 0
          } else {
            false
          }
        }
      } finally {
        IO.delete(testDir)
      }
    }
    results.collect {
      case (name, false) => name
    } match {
      case Nil => // success
      case failedTests => sys.error(failedTests.mkString("Maven tests failed: ", ",", ""))
    }
  }

  def processTemplate(xml: Elem, moduleID: ModuleID, moduleInfo: ModuleInfo, dependencies: Seq[ModuleID],
                      crossVersion: ModuleID => ModuleID, log: Logger) = {

    // Add project meta data
    val withProjectInfo = Seq(
      "name" -> moduleInfo.nameFormal,
      "description" -> moduleInfo.description,
      "groupId" -> moduleID.organization,
      "artifactId" -> moduleID.name,
      "version" -> moduleID.revision
    ).foldRight(xml) {
      case ((label, value), elem) => prependIfAbsent(elem, createElement(label, value))
    }

    val withDependencies = addChild(withProjectInfo, {
      val deps = dependencies.collect {
        case dep if isRuntimeDep(dep.configurations) =>
          val versioned = crossVersion(dep)
          <dependency>
            <groupId>{versioned.organization}</groupId>
            <artifactId>{versioned.name}</artifactId>
            <version>{versioned.revision}</version>
          </dependency>
      }
      <dependencies>{deps}</dependencies>
    })

    val (withoutParameterManagement, parameterManagement) = parseParameterManagament(withDependencies)

    updateElements(withoutParameterManagement, "mojos") { mojos =>
      updateElements(mojos, "mojo") { mojo =>
        val parameters = (mojo \ "parameters" \ "parameter" \ "name")
          .map(_.text)
          .collect(Function.unlift(parameterManagement.get))

        val configToAdd = parameters.flatMap(_.config)
        val withConfig = addOrUpdateElement(mojo, "configuration",
          Some(configToAdd).filter(_.nonEmpty).map(cta => <configuration>{cta}</configuration>)
        ) { configuration =>

          mergeElements(configuration, configToAdd)
        }

        updateElements(withConfig, "parameters") { parameters =>
          updateElements(parameters, "parameter") { parameter =>
            val name = (parameter \ "name").text
            parameterManagement.get(name) match {
              case Some(param) =>
                mergeElements(parameter, param.paramNode.child)
              case None => parameter
            }
          }
        }

      }
    }
  }

  private def isRuntimeDep(configuration: Option[String]) = {
    configuration.fold(true) {
      case "compile" => true
      case "runtime" => true
      // If it's complex, ignore it
      case _ => false
    }
  }

  private def parseParameterManagament(xml: Elem): (Elem, Map[String, Parameter]) = {
    xml \ "parameterManagement" match {
      case Seq(parameterManagement) =>

        val parameters = (parameterManagement \ "parameter").collect {
          case elem: Elem =>
            val name = (elem \ "name").text
            val (configuration, withoutConfig) = elem.child.partition(_.label == "configuration")
            val config = configuration match {
              case Seq(c) => c.child
              case Nil => NodeSeq.Empty
              case _ => sys.error("Multiple configuration elements: " + elem)
            }
            name -> Parameter(elem.copy(child = withoutConfig), config)
        }.toMap

        (removeElement(xml, "parameterManagement"), parameters)
      case NodeSeq.Empty => (xml, Map.empty)
      case _ => sys.error("Multiple parameterManagement elements")
    }
  }

  private case class Parameter(paramNode: Elem, config: NodeSeq)

  private def updateElements(elem: Elem, label: String)(update: Elem => Elem) = {
    elem.copy(child = elem.child.map {
      case toUpdate: Elem if toUpdate.label == label => update(toUpdate)
      case other => other
    })
  }

  private def mergeElements(elem1: Elem, toMerge: NodeSeq) = {
    val childrenToAdd = toMerge.filterNot(child => elem1.child.exists(_.label == child.label))
    elem1.copy(child = elem1.child ++ childrenToAdd)
  }

  private def addOrUpdateElement(elem: Elem, label: String, ifMissing: => Option[Elem])(update: Elem => Elem) = {
    elem \ label match {
      case Seq(toUpdate: Elem) =>
        updateElements(elem, label)(update)
      case NodeSeq.Empty =>
        ifMissing match {
          case Some(child) => addChild(elem, child)
          case None => elem
        }
      case _ =>
        sys.error(s"Unexpected multiple $label elements in $elem")
    }
  }

  private def removeElement(elem: Elem, label: String) = {
    elem.copy(child = elem.child.filterNot(_.label == label))
  }

  /**
    * Create an element.
    *
    * @param label The element label.
    * @param value The element value.
    */
  private def createElement(label: String, value: String): Elem = {
   <elem>{value}</elem>.copy(label = label)
  }

  /**
    * Prepend the given element to the parent if it's absent.
    *
    * @param parent The parent to add it to.
    * @param elem The element to add.
    */
  private def prependIfAbsent(parent: Elem, elem: Elem) = {
    if (parent.child.exists(_.label == elem.label)) {
      parent
    } else {
      parent.copy(child = elem +: parent.child)
    }
  }

  private def addChild(parent: Elem, elem: Elem) = {
    parent.copy(child = parent.child :+ elem)
  }
}
