/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.sbt

import com.lightbend.lagom.dev.Reloader.DevServer
import com.lightbend.lagom.sbt.run.RunSupport
import com.typesafe.sbt.packager.archetypes.JavaAppPackaging
import java.io.Closeable

import com.lightbend.lagom.dev.{ Colors, _ }
import com.lightbend.lagom.dev.PortAssigner.{ Port, ProjectName }
import play.dev.filewatch.FileWatchService
import play.sbt._
import play.sbt.PlayImport.PlayKeys
import sbt._
import sbt.Def.Initialize
import sbt.Keys._
import sbt.plugins.{ CorePlugin, IvyPlugin, JvmPlugin }

/**
 * Base plugin for Lagom projects. Declares common settings for both Java and Scala based Lagom projects.
 */
object Lagom extends AutoPlugin {
  override def requires = LagomReloadableService && JavaAppPackaging
  val autoImport = LagomImport
}

/**
 * The main plugin for Lagom Java projects. To use this the plugin must be made available to your project
 * via sbt's enablePlugins mechanism e.g.:
 * {{{
 *   lazy val root = project.in(file(".")).enablePlugins(LagomJava)
 * }}}
 */
object LagomJava extends AutoPlugin {
  override def requires = Lagom
  override def trigger = noTrigger

  import LagomPlugin.autoImport._

  override def projectSettings = LagomSettings.defaultSettings ++ Seq(
    Keys.run in Compile := {
      val service = lagomRun.value
      val log = state.value.log
      SbtConsoleHelper.printStartScreen(log, service)
      SbtConsoleHelper.blockUntilExit(log, Internal.Keys.interactionMode.value, service._2)
    },
    libraryDependencies ++= Seq(
      LagomImport.lagomJavadslServer,
      PlayImport.component("play-netty-server")
    ) ++ LagomImport.lagomJUnitDeps ++ devServiceLocatorDependencies.value,
    // Configure sbt junit-interface: https://github.com/sbt/junit-interface
    testOptions in Test += Tests.Argument(TestFrameworks.JUnit, "-v", "-a")
  )

  // service locator dependencies are injected into services only iff dev service locator is enabled
  private lazy val devServiceLocatorDependencies = Def.setting {
    if (LagomPlugin.autoImport.lagomServiceLocatorEnabled.value)
      Seq(
        LagomImport.component("lagom-service-registry-client"),
        LagomImport.component("lagom-service-registration")
      ).map(_ % Internal.Configs.DevRuntime)
    else
      Seq.empty
  }

}

/**
 * The main plugin for Lagom Java projects. To use this the plugin must be made available to your project
 * via sbt's enablePlugins mechanism e.g.:
 * {{{
 *   lazy val root = project.in(file(".")).enablePlugins(LagomJava)
 * }}}
 */
object LagomScala extends AutoPlugin {
  override def requires = Lagom
  override def trigger = noTrigger

  import LagomPlugin.autoImport._

  override def projectSettings = LagomSettings.defaultSettings ++ Seq(
    Keys.run in Compile := {
      val service = lagomRun.value
      val log = state.value.log
      SbtConsoleHelper.printStartScreen(log, service)
      SbtConsoleHelper.blockUntilExit(log, Internal.Keys.interactionMode.value, service._2)
    },
    libraryDependencies ++= Seq(
      LagomImport.lagomScaladslServer,
      LagomImport.lagomScaladslDevMode,
      PlayImport.component("play-netty-server")
    )
  )
}

/**
 * Allows a Play service to be run from Lagom's dev mode.
 *
 * By enabling this along with a Play plugin, it ensures that when you runAll, it will start the Play app sign well.
 *
 * {{{
 *   lazy val playJavaApp = project.in(file(".")).enablePlugins(PlayJava, LagomPlay)
 * }}}
 */
object LagomPlay extends AutoPlugin {
  override def requires = LagomReloadableService && Play
  override def trigger = noTrigger

  import LagomReloadableService.autoImport._

  override def projectSettings = Seq(
    // Run the Play reload task when Lagom requests a reload
    lagomReload := PlayInternalKeys.playReload.value,

    // Play wants to add the assets classloader to the application classloader
    lagomClassLoaderDecorator := PlayInternalKeys.playAssetsClassLoader.value,

    // Watch the files that Play wants to watch
    lagomWatchDirectories := PlayKeys.playMonitoredFiles.value
  )
}

/**
 * This plugin will automatically be enabled if using PlayJava and LagomPlay, to add the play integration
 */
object LagomPlayJava extends AutoPlugin {
  override def requires = LagomPlay && PlayJava
  override def trigger = allRequirements

  override def projectSettings = Seq(
    libraryDependencies ++= (
      // lagom-play-integration takes care of registering a stock Play app to the
      // Lagom development service locator. The dependency is needed only if the
      // development service locator is enabled.
      if (LagomPlugin.autoImport.lagomServiceLocatorEnabled.value) {
        Seq(LagomImport.component("lagom-javadsl-play-integration") % Internal.Configs.DevRuntime)
      } else {
        Seq.empty
      }
    )
  )
}

/**
 * This plugin will automatically be enabled if using PlayScala and LagomPlay, to add the play integration
 */
object LagomPlayScala extends AutoPlugin {
  override def requires = LagomPlay && PlayScala
  override def trigger = allRequirements

  override def projectSettings = Seq(
    libraryDependencies += LagomImport.lagomScaladslDevMode
  )
}

/**
 * An external project, that gets run when you run runAll.
 */
object LagomExternalProject extends AutoPlugin {
  override def requires = LagomPlugin
  override def trigger = noTrigger

  import LagomPlugin.autoImport._

  override def projectSettings = Seq(
    Keys.run in Compile := {
      val service = lagomRun.value
      val log = state.value.log
      SbtConsoleHelper.printStartScreen(log, service)
      SbtConsoleHelper.blockUntilExit(log, Internal.Keys.interactionMode.value, service._2)
    },
    lagomRun <<= Def.taskDyn {
      RunSupport.nonReloadRunTask(LagomPlugin.managedSettings.value).map(name.value -> _)
    }
  )
}

/**
 * Plugin that implements running reloadable services in Lagom.
 */
object LagomReloadableService extends AutoPlugin {
  override def requires = LagomPlugin
  override def trigger = noTrigger

  object autoImport {
    val lagomReload = taskKey[sbt.inc.Analysis]("Executed when sources of changed, to recompile (and possibly reload) the app")
    val lagomReloaderClasspath = taskKey[Classpath]("The classpath that gets used to create the reloaded classloader")
    val lagomClassLoaderDecorator = taskKey[ClassLoader => ClassLoader]("Function that decorates the Lagom classloader. Can be used to inject things into the classpath.")
    val lagomWatchDirectories = taskKey[Seq[File]]("The directories that Lagom should be watching")
  }

  import LagomPlugin.autoImport._
  import autoImport._

  override def projectSettings = Seq(
    lagomRun := {
      val service = runLagomTask.value
      // eagerly loads the service
      service.reload()
      // install a listener that will take care of reloading on classpath's changes
      service.addChangeListener(() => service.reload())
      (name.value, service)
    },
    lagomReload <<= Def.taskDyn {
      (compile in Compile).all(
        ScopeFilter(
          inDependencies(thisProjectRef.value)
        )
      ).map(_.reduceLeft(_ ++ _))
    },

    lagomReloaderClasspath <<= Classpaths.concatDistinct(exportedProducts in Runtime, internalDependencyClasspath in Runtime),
    lagomClassLoaderDecorator := identity,

    lagomWatchDirectories <<= Def.taskDyn {
      val projectRef = thisProjectRef.value

      import com.typesafe.sbt.web.Import._
      def filter = ScopeFilter(
        inDependencies(projectRef),
        inConfigurations(Compile, Assets)
      )

      Def.task {
        val allDirectories =
          (unmanagedSourceDirectories ?? Nil).all(filter).value.flatten ++
            (unmanagedResourceDirectories ?? Nil).all(filter).value.flatten

        val existingDirectories = allDirectories.filter(_.exists)

        // Filter out directories that are sub paths of each other, by sorting them lexicographically, then folding, excluding
        // entries if the previous entry is a sub path of the current
        import java.nio.file.Path
        val distinctDirectories = existingDirectories
          .map(_.getCanonicalFile.toPath)
          .sorted
          .foldLeft(List.empty[Path]) { (result, next) =>
            result.headOption match {
              case Some(previous) if next.startsWith(previous) => result
              case _ => next :: result
            }
          }

        distinctDirectories.map(_.toFile)
      }
    }
  )

  private lazy val runLagomTask: Initialize[Task[DevServer]] = Def.taskDyn {
    RunSupport.reloadRunTask(LagomPlugin.managedSettings.value)
  }
}

/**
 * Any service that can be run in Lagom should enable this plugin.
 */
object LagomPlugin extends AutoPlugin {
  import scala.concurrent.duration._

  override def requires = JvmPlugin

  override def trigger = noTrigger

  object autoImport {
    type PortRange = PortAssigner.PortRange

    object PortRange {
      def apply(min: Int, max: Int): PortRange = PortAssigner.PortRange(min, max)
    }

    val lagomRun = taskKey[(String, DevServer)]("Run a Lagom service")
    val runAll = taskKey[Unit]("Run all Lagom services")

    val lagomServicesPortRange = settingKey[PortRange]("Port range used to assign a port to each Lagom service in the build")

    val lagomFileWatchService = settingKey[FileWatchService]("The file watch service to use")
    val lagomDevSettings = settingKey[Seq[(String, String)]]("Settings that should be passed to a Lagom app in dev mode")
    val lagomServicePort = taskKey[Int]("The port that the Lagom service should run on")

    // service locator tasks and settings
    val lagomUnmanagedServices = settingKey[Map[String, String]]("External services name and address known by the service location")
    val lagomServiceLocatorUrl = settingKey[String]("URL of the service locator")
    val lagomServiceLocatorPort = settingKey[Int]("Port used by the service locator")
    val lagomServiceGatewayPort = settingKey[Int]("Port used by the service gateway")
    val lagomServiceLocatorEnabled = settingKey[Boolean]("Enable/Disable the service locator")
    val lagomServiceLocatorStart = taskKey[Unit]("Start the service locator")
    val lagomServiceLocatorStop = taskKey[Unit]("Stop the service locator")

    // cassandra tasks and settings
    val lagomCassandraStart = taskKey[Unit]("Start the local cassandra server")
    val lagomCassandraStop = taskKey[Unit]("Stop the local cassandra server")
    val lagomCassandraPort = settingKey[Int]("Port used by the local cassandra server")
    val lagomCassandraEnabled = settingKey[Boolean]("Enable/Disable the cassandra server")
    val lagomCassandraCleanOnStart = settingKey[Boolean]("Wipe the cassandra database before starting")
    val lagomCassandraKeyspace = settingKey[String]("Cassandra keyspace used by a Lagom service")
    val lagomCassandraJvmOptions = settingKey[Seq[String]]("JVM options used by the forked cassandra process")
    val lagomCassandraMaxBootWaitingTime = settingKey[FiniteDuration]("Max waiting time to start cassandra")

    // kafka tasks and settings
    val lagomKafkaStart = taskKey[Unit]("Start the local kafka server")
    val lagomKafkaStop = taskKey[Unit]("Stop the local kafka server")
    val lagomKafkaPropertiesFile = settingKey[Option[File]]("Properties file used by the local kafka broker (file's location is relative to the project's root)")
    val lagomKafkaEnabled = settingKey[Boolean]("Enable/Disable the kafka server")
    val lagomKafkaCleanOnStart = settingKey[Boolean]("Wipe the kafka log before starting")
    val lagomKafkaJvmOptions = settingKey[Seq[String]]("JVM options used by the forked kafka process")
    val lagomKafkaZookeperPort = settingKey[Int]("Port used by the local zookeper server (kafka requires zookeeper)")
    val lagomKafkaPort = settingKey[Int]("Port used by the local kafka broker")
    val lagomKafkaAddress = settingKey[String]("Address of the kafka brokers (comma-separated list)")

    /** Allows to integrate an external Lagom project in the current build, so that when runAll is run, this service is also started.*/
    def lagomExternalProject(name: String, module: ModuleID): Project =
      Project(name, file("target") / "lagom-external-projects" / name).
        enablePlugins(LagomExternalProject).
        settings(Seq(libraryDependencies += module))
  }

  import autoImport._

  private lazy val cassandraKeyspaceConfig: Initialize[Map[String, String]] = Def.setting {
    val keyspace = lagomCassandraKeyspace.value
    LagomConfig.cassandraKeySpace(keyspace)
  }

  private val serviceLocatorProject = Project("lagom-internal-meta-project-service-locator", file("."),
    configurations = Configurations.default,
    settings = CorePlugin.projectSettings ++ IvyPlugin.projectSettings ++ JvmPlugin.projectSettings ++ Seq(
    scalaVersion := "2.11.7",
    libraryDependencies += LagomImport.component("lagom-service-locator"),
    lagomServiceLocatorStart in ThisBuild := startServiceLocatorTask.value,
    lagomServiceLocatorStop in ThisBuild := Servers.ServiceLocator.tryStop(new SbtLoggerProxy(state.value.log))
  ))

  private val cassandraProject = Project("lagom-internal-meta-project-cassandra", file("."),
    configurations = Configurations.default,
    settings = CorePlugin.projectSettings ++ IvyPlugin.projectSettings ++ JvmPlugin.projectSettings ++ Seq(
    scalaVersion := "2.11.7",
    libraryDependencies += LagomImport.component("lagom-cassandra-server"),
    lagomCassandraStart in ThisBuild := startCassandraServerTask.value,
    lagomCassandraStop in ThisBuild := Servers.CassandraServer.tryStop(new SbtLoggerProxy(state.value.log))
  ))

  private val kafkaServerProject = Project("lagom-internal-meta-project-kafka", file("."),
    configurations = Configurations.default,
    settings = CorePlugin.projectSettings ++ IvyPlugin.projectSettings ++ JvmPlugin.projectSettings ++ Seq(
    scalaVersion := "2.11.7",
    libraryDependencies += LagomImport.component("lagom-kafka-server"),
    lagomKafkaStart in ThisBuild := startKafkaServerTask.value,
    lagomKafkaStop in ThisBuild := Servers.KafkaServer.tryStop(new SbtLoggerProxy(state.value.log))
  ))

  private val projectPortMap = AttributeKey[Map[ProjectName, Port]]("lagomProjectPortMap")
  private val defaultPortRange = PortRange(0xc000, 0xffff)

  override def globalSettings = Seq(
    onLoad := onLoad.value andThen assignProjectsPort andThen DynamicProjectAdder.addProjects(serviceLocatorProject, cassandraProject, kafkaServerProject)
  )

  private def assignProjectsPort(state: State): State = {
    val extracted = Project.extract(state)

    val scope = Scope(Select(ThisBuild), Global, Global, Global)
    val portRange = extracted.structure.data.get(scope, lagomServicesPortRange.key)
      .getOrElse(defaultPortRange)
    val oldPortMap = state.get(projectPortMap).getOrElse(Map.empty)

    // build the map at most once
    val projects = extracted.currentUnit.defined
    val lagomProjects = (for {
      (id, proj) <- projects
      if proj.autoPlugins.toSet.contains(LagomPlugin)
      projName <- (name in ProjectRef(extracted.currentUnit.unit.uri, id)).get(extracted.structure.data).toSeq
    } yield {
      ProjectName(projName)
    })(collection.breakOut)
    val portMap = oldPortMap ++ PortAssigner.computeProjectsPort(portRange, lagomProjects)
    state.put(projectPortMap, portMap)
  }

  private def assignedPortFor(name: ProjectName, state: State): Port = {
    (for {
      map <- state.get(projectPortMap)
      port <- map.get(name)
    } yield port).getOrElse(Port.Unassigned)
  }

  private def dontAggregate(keys: Scoped*): Seq[Setting[_]] = keys.map(aggregate in _ := false)

  override def buildSettings = super.buildSettings ++ Seq(
    lagomUnmanagedServices := Map.empty,
    lagomServicesPortRange := defaultPortRange,
    lagomServiceLocatorEnabled := true,
    lagomServiceLocatorPort := 8000,
    lagomServiceGatewayPort := 9000,
    lagomServiceLocatorUrl := s"http://localhost:${lagomServiceLocatorPort.value}",
    lagomCassandraEnabled := true,
    lagomCassandraPort := 4000, // If you change the default make sure to also update the play/reference-overrides.conf in the persistence project
    lagomCassandraCleanOnStart := false,
    lagomCassandraJvmOptions := Seq("-Xms256m", "-Xmx1024m", "-Dcassandra.jmx.local.port=4099", "-DCassandraLauncher.configResource=dev-embedded-cassandra.yaml"),
    lagomCassandraMaxBootWaitingTime := 20.seconds,
    lagomKafkaEnabled := true,
    lagomKafkaPropertiesFile := None,
    lagomKafkaZookeperPort := 2181,
    lagomKafkaPort := 9092,
    lagomKafkaCleanOnStart := false,
    lagomKafkaAddress := s"localhost:${lagomKafkaPort.value}",
    lagomKafkaJvmOptions := Seq("-Xms256m", "-Xmx1024m"),
    runAll <<= runServiceLocatorAndMicroservicesTask,
    Internal.Keys.interactionMode := PlayConsoleInteractionMode,
    lagomDevSettings := Nil
  ) ++
    // This is important as we want to evaluate these tasks exactly once.
    // Without it, we could be evaluating the same task multiple times, for each aggregated project.
    dontAggregate(
      runAll,
      lagomServiceLocatorStart,
      lagomServiceLocatorStop,
      lagomCassandraStart,
      lagomCassandraStop,
      lagomKafkaStart,
      lagomKafkaStop
    )

  override def projectSettings = Seq(
    lagomFileWatchService := {
      FileWatchService.defaultWatchService(target.value, pollInterval.value, new SbtLoggerProxy(sLog.value))
    },
    lagomCassandraKeyspace := LagomConfig.normalizeCassandraKeyspaceName(name.value),
    lagomServicePort := LagomPlugin.assignedPortFor(ProjectName(name.value), state.value).value,
    Internal.Keys.stop := {
      Internal.Keys.interactionMode.value match {
        case nonBlocking: PlayNonBlockingInteractionMode => nonBlocking.stop()
        case _ => throw new RuntimeException("Play interaction mode must be non blocking to stop it")
      }
    },
    ivyConfigurations ++= Seq(Internal.Configs.DevRuntime),
    PlaySettings.manageClasspath(Internal.Configs.DevRuntime),

    libraryDependencies +=
      LagomImport.component("lagom-reloadable-server") % Internal.Configs.DevRuntime
  )

  private lazy val startServiceLocatorTask = Def.taskDyn {
    if ((lagomServiceLocatorEnabled in ThisBuild).value) {

      Def.task {
        val unmanagedServices: Map[String, String] =
          if ((lagomCassandraEnabled in ThisBuild).value) {
            StaticServiceLocations.withCassandraLocation(lagomCassandraPort.value, lagomUnmanagedServices.value)
          } else {
            lagomUnmanagedServices.value
          }

        val serviceLocatorPort = lagomServiceLocatorPort.value
        val serviceGatewayPort = lagomServiceGatewayPort.value
        val urls = (managedClasspath in Compile).value.files.map(_.toURI.toURL).toArray
        val scala211 = scalaInstance.value
        val log = new SbtLoggerProxy(state.value.log)
        Servers.ServiceLocator.start(log, scala211.loader, urls, serviceLocatorPort, serviceGatewayPort, unmanagedServices)
      }
    } else {
      Def.task {
        val log = state.value.log
        log.info(s"Service locator won't be started because the build setting `${lagomServiceLocatorEnabled.key.label}` is set to `false`")
      }
    }
  }

  private lazy val startCassandraServerTask = Def.taskDyn {
    if ((lagomCassandraEnabled in ThisBuild).value) {
      Def.task {
        val port = lagomCassandraPort.value
        val cleanOnStart = lagomCassandraCleanOnStart.value
        val classpath = (managedClasspath in Compile).value.map(_.data)
        val jvmOptions = lagomCassandraJvmOptions.value
        val maxWaiting = lagomCassandraMaxBootWaitingTime.value
        val log = new SbtLoggerProxy(state.value.log)
        Servers.CassandraServer.start(log, classpath, port, cleanOnStart, jvmOptions, maxWaiting)
      }
    } else {
      Def.task {
        val log = state.value.log
        log.info(s"Cassandra won't be started because the build setting `${lagomCassandraEnabled.key.label}` is set to `false`")
      }
    }
  }

  private lazy val startKafkaServerTask = Def.taskDyn {
    if ((lagomKafkaEnabled in ThisBuild).value) {
      Def.task {
        val log = new SbtLoggerProxy(state.value.log)
        val zooKeeperPort = lagomKafkaZookeperPort.value
        val kafkaPort = lagomKafkaPort.value
        val kafkaPropertiesFile: Option[File] = {
          lagomKafkaPropertiesFile.value match {
            case None =>
              log.debug("Kafka will start using the default server.properties included in Lagom.")
              None
            case file @ Some(propertiesFile) =>
              if (propertiesFile.exists()) {
                log.debug {
                  """You have provided an absolute path to a Kafka properties file. I will use the provided path,
                   | but consider using a relative path instead of an absolute one. If you decide to use a relative path,
                   | make sure the provided path is relative to this project's build root directory.""".stripMargin
                }
                file
              } else {
                val rootDir = (Keys.baseDirectory in ThisBuild).value
                val file = rootDir / propertiesFile.getPath
                if (file.exists()) Some(file)
                else {
                  log.warn {
                    s"""No properties file can be found at the provided path ${propertiesFile.getPath}. Hence, the local Kafka
                      | server will be started with the default server.properties included in Lagom. To remove this warning, you
                      | shall update the path provided in the sbt key ${lagomKafkaPropertiesFile.key.label} to point an existing
                      | properties file.""".stripMargin
                  }
                  None
                }
              }
          }
        }
        val classpath = (managedClasspath in Compile).value.map(_.data)
        val jvmOptions = lagomKafkaJvmOptions.value
        val targetDir = target.value
        val cleanOnStart = lagomKafkaCleanOnStart.value

        Servers.KafkaServer.start(log, classpath, kafkaPort, zooKeeperPort, kafkaPropertiesFile, jvmOptions, targetDir, cleanOnStart)
      }
    } else {
      Def.task {
        val log = state.value.log
        log.info(s"Kafka won't be started because the build setting `${lagomKafkaEnabled.key.label}` is set to `false`")
      }
    }
  }

  private lazy val runServiceLocatorAndMicroservicesTask: Initialize[Task[Unit]] = Def.taskDyn {
    val startInfrastructure = Def.taskDyn {
      lagomKafkaStart.value
      Def.sequential(lagomCassandraStart, lagomServiceLocatorStart)
    }
    Def.sequential(startInfrastructure, runAllMicroservicesTask)
  }

  private def runAllMicroservicesTask: Initialize[Task[Unit]] = Def.taskDyn {
    val projects = microservicesProjects.value
    val filter = ScopeFilter(inProjects(projects: _*))
    // Services are going to be started without a specific order. Whether we will need to take into consideration
    // services' dependencies is not something clear yet.
    val runningServiceTasks = lagomRun.all(filter)

    Def.task {
      val log = state.value.log
      val runningServices = runningServiceTasks.value
      if (runningServices.isEmpty) log.info("There are no Lagom projects to run")
      else {
        SbtConsoleHelper.printStartScreen(log, runningServices: _*)
        SbtConsoleHelper.blockUntilExit(log, Internal.Keys.interactionMode.value, runningServices.map(_._2): _*)
      }
    }
  }

  /** Projects that have the Microservice plugin enabled. */
  private lazy val microservicesProjects: Initialize[Task[Seq[ProjectRef]]] = Def.task {
    val structure = buildStructure.value
    val projects = structure.allProjectRefs
    for {
      projRef <- projects
      proj <- Project.getProject(projRef, structure).toList
      autoPlugin <- proj.autoPlugins if autoPlugin == LagomPlugin
    } yield projRef
  }

  private lazy val serviceLocatorConfiguration: Initialize[Map[String, String]] = Def.setting {
    if (lagomServiceLocatorEnabled.value)
      Map(LagomConfig.ServiceLocatorUrl -> lagomServiceLocatorUrl.value)
    else
      Map.empty
  }

  private lazy val cassandraServerConfiguration: Initialize[Map[String, String]] = Def.setting {
    val port = lagomCassandraPort.value
    LagomConfig.cassandraPort(port)
  }

  private lazy val kafkaServerConfiguration: Initialize[Map[String, String]] = Def.setting {
    Map(LagomConfig.KafkaAddress -> lagomKafkaAddress.value)
  }

  private lazy val actorSystemsConfig: Initialize[Map[String, String]] = Def.setting {
    LagomConfig.actorSystemConfig(name.value)
  }

  private[sbt] lazy val managedSettings: Initialize[Map[String, String]] = Def.setting {
    serviceLocatorConfiguration.value ++ cassandraServerConfiguration.value ++
      cassandraKeyspaceConfig.value ++ kafkaServerConfiguration.value ++ actorSystemsConfig.value
  }
}

object LagomLogback extends AutoPlugin {
  override def requires = LagomPlugin

  // add this plugin automatically if Lagom is added.
  override def trigger = AllRequirements

  override def projectSettings = Seq(
    libraryDependencies += LagomImport.lagomLogback
  )
}

object LagomLog4j2 extends AutoPlugin {
  override def requires = LagomPlugin

  override def projectSettings = Seq(
    libraryDependencies += LagomImport.lagomLog4j2
  )
}

private[sbt] object SbtConsoleHelper {
  private val consoleHelper = new ConsoleHelper(new Colors("sbt.log.noformat"))
  def printStartScreen(log: Logger, services: (String, DevServer)*): Unit =
    consoleHelper.printStartScreen(new SbtLoggerProxy(log), services.map {
      case (name, service) => name -> service.url()
    })

  def blockUntilExit(log: Logger, interaction: play.sbt.PlayInteractionMode, services: Closeable*): Unit = {
    interaction match {
      case nonBlocking: PlayNonBlockingInteractionMode =>
        // If we are running in non blocking mode then the running services will be closed when executing the `playStop` task.
        nonBlocking.start(new Closeable {
          override def close(): Unit = services.foreach(_.close())
        })
      case _ =>
        consoleHelper.blockUntilExit()
        consoleHelper.shutdownAsynchronously(new SbtLoggerProxy(log), services)
    }
  }
}

/**
 *  This is useful for testing, as it allows to start and stop the servers programmatically
 *  (and not on user's input, as it's usually done in development mode).
 */
object NonBlockingInteractionMode extends PlayNonBlockingInteractionMode {
  object NullLogger extends Logger {
    def trace(t: => Throwable): Unit = ()
    def success(message: => String): Unit = ()
    def log(level: Level.Value, message: => String): Unit = ()
  }

  import scala.collection.immutable.HashSet
  // Note: all accesses to this variable are guarded by this' class instance lock.
  private var runningServers: Set[Closeable] = HashSet.empty

  /**
   * Start the server, if not already started.
   *
   * @param server A callback to start the server, that returns a closeable to stop it
   */
  override def start(server: => Closeable): Unit = synchronized {
    val theServer = server

    if (runningServers(theServer)) println("Noop: This server was already started")
    else runningServers += theServer
  }

  /**
   * Stop all running servers.
   */
  override def stop(): Unit = synchronized {
    if (runningServers.size > 1) println("Stopping all servers")
    else if (runningServers.size == 1) println("Stopping server")
    else println("No running server to stop")

    runningServers.foreach(_.close())
    // Also stop all running embedded servers
    Servers.tryStop(new SbtLoggerProxy(NullLogger))
    runningServers = HashSet.empty
  }
}
