/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package play.core.server

import java.io.File
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.lightbend.lagom.sbt.server.ReloadableServer
import play.api._
import play.api.libs.concurrent.ActorSystemProvider
import play.core.{ ApplicationProvider, BuildLink, SourceMapper }
import play.utils.Threads
import scala.concurrent.{ Await, Future }
import scala.concurrent.duration._
import scala.util.control.NonFatal
import scala.util.{ Failure, Success, Try }
import scala.collection.JavaConverters._

/**
 * Used to start servers in 'dev' mode, a mode where the application
 * is reloaded whenever its source changes.
 */
object LagomReloadableDevServerStart {

  /**
   * Provides an HTTPS-only server for the dev environment.
   *
   * <p>This method uses simple Java types so that it can be used with reflection by code
   * compiled with different versions of Scala.
   */
  def mainDevOnlyHttpsMode(
    buildLink:   BuildLink,
    httpsPort:   Int,
    httpAddress: String
  ): ReloadableServer = {
    mainDev(buildLink, None, Some(httpsPort), httpAddress)
  }

  /**
   * Provides an HTTP server for the dev environment
   *
   * <p>This method uses simple Java types so that it can be used with reflection by code
   * compiled with different versions of Scala.
   */
  def mainDevHttpMode(
    buildLink: BuildLink,
    httpPort:  Int
  ): ReloadableServer = {
    mainDev(buildLink, Some(httpPort), None, "0.0.0.0")
  }

  private def mainDev(
    buildLink:   BuildLink,
    httpPort:    Option[Int],
    httpsPort:   Option[Int],
    httpAddress: String
  ): ReloadableServer = {
    val classLoader = getClass.getClassLoader
    Threads.withContextClassLoader(classLoader) {
      try {
        val process = new RealServerProcess(args = Seq.empty)
        val path: File = buildLink.projectPath

        val dirAndDevSettings: Map[String, String] = ServerConfig.rootDirConfig(path) ++ buildLink.settings.asScala.toMap ++
          (httpPort.toList.map("play.server.http.port" -> _.toString).toMap) +
          ("play.server.http.address" -> httpAddress)

        // Use plain Java call here in case of scala classloader mess
        {
          if (System.getProperty("play.debug.classpath") == "true") {
            System.out.println("\n---- Current ClassLoader ----\n")
            System.out.println(this.getClass.getClassLoader)
            System.out.println("\n---- The where is Scala? test ----\n")
            System.out.println(this.getClass.getClassLoader.getResource("scala/Predef$.class"))
          }
        }

        // First delete the default log file for a fresh start (only in Dev Mode)
        try {
          new File(path, "logs/application.log").delete()
        } catch {
          case NonFatal(_) =>
        }

        // Configure the logger for the first time.
        // This is usually done by Application itself when it's instantiated, which for other types of ApplicationProviders,
        // is usually instantiated along with or before the provider.  But in dev mode, no application exists initially, so
        // configure it here.
        LoggerConfigurator(classLoader) match {
          case Some(loggerConfigurator) =>
            loggerConfigurator.init(path, Mode.Dev)
          case None =>
            println("No play.logger.configurator found: logging must be configured entirely by the application.")
        }

        // Create reloadable ApplicationProvider
        val appProvider = new ApplicationProvider {

          var lastState: Try[Application] = Failure(new PlayException("Not initialized", "?"))

          override def current: Option[Application] = lastState.toOption

          def get: Try[Application] = {

            synchronized {

              // Let's load the application on another thread
              // as we are now on the Netty IO thread.
              //
              // Because we are on DEV mode here, it doesn't really matter
              // but it's more coherent with the way it works in PROD mode.
              implicit val ec = play.core.Execution.internalContext
              Await.result(scala.concurrent.Future {

                val reloaded = buildLink.reload match {
                  case NonFatal(t)     => Failure(t)
                  case cl: ClassLoader => Success(Some(cl))
                  case null            => Success(None)
                }

                reloaded.flatMap { maybeClassLoader =>

                  val maybeApplication: Option[Try[Application]] = maybeClassLoader.map { projectClassloader =>
                    try {

                      if (lastState.isSuccess) {
                        println()
                        println(play.utils.Colors.magenta("--- (RELOAD) ---"))
                        println()
                      }

                      // First, stop the old application if it exists
                      lastState.foreach(Play.stop)

                      // Create the new environment
                      val environment = Environment(path, projectClassloader, Mode.Dev)
                      val sourceMapper = new SourceMapper {
                        def sourceOf(className: String, line: Option[Int]) = {
                          Option(buildLink.findSource(className, line.map(_.asInstanceOf[java.lang.Integer]).orNull)).flatMap {
                            case Array(file: java.io.File, null) => Some((file, None))
                            case Array(file: java.io.File, line: java.lang.Integer) => Some((file, Some(line)))
                            case _ => None
                          }
                        }
                      }

                      val newApplication = Threads.withContextClassLoader(projectClassloader) {
                        val context = ApplicationLoader.createContext(environment, dirAndDevSettings, Some(sourceMapper))
                        val loader = ApplicationLoader(context)
                        loader.load(context)
                      }

                      Play.start(newApplication)

                      Success(newApplication)
                    } catch {
                      case e: com.google.inject.CreationException =>
                        lastState = Failure(e)
                        val hint = "Hint: Maybe you have forgot to enable your service Module class via `play.modules.enabled`? (check in your project's application.conf)"
                        logExceptionAndGetResult(path, e, hint)
                        lastState

                      case e: PlayException => {
                        lastState = Failure(e)
                        logExceptionAndGetResult(path, e)
                        lastState
                      }
                      case NonFatal(e) => {
                        lastState = Failure(UnexpectedException(unexpected = Some(e)))
                        logExceptionAndGetResult(path, e)
                        lastState
                      }
                      case e: LinkageError => {
                        lastState = Failure(UnexpectedException(unexpected = Some(e)))
                        logExceptionAndGetResult(path, e)
                        lastState
                      }
                    }
                  }

                  maybeApplication.flatMap(_.toOption).foreach { app =>
                    lastState = Success(app)
                  }

                  maybeApplication.getOrElse(lastState)
                }

              }, Duration.Inf)
            }
          }

          private def logExceptionAndGetResult(path: File, e: Throwable, hint: String = ""): Unit = {
            e.printStackTrace()
            println()
            println(play.utils.Colors.red(s"Stacktrace caused by project ${path.getName} (filesystem path to project is ${path.getAbsolutePath}).\n${hint}"))
          }

          override def handleWebCommand(request: play.api.mvc.RequestHeader) = None
        }

        // Start server with the application
        val serverConfig = ServerConfig(
          rootDir = path,
          port = httpPort,
          sslPort = httpsPort,
          address = httpAddress,
          mode = Mode.Dev,
          properties = process.properties,
          configuration = Configuration.load(classLoader, System.getProperties, dirAndDevSettings, allowMissingApplicationConf = true)
        )
        // We *must* use a different Akka configuration in dev mode, since loading two actor systems from the same
        // config will lead to resource conflicts, for example, if the actor system is configured to open a remote port,
        // then both the dev mode and the application actor system will attempt to open that remote port, and one of
        // them will fail.
        val devModeAkkaConfig = serverConfig.configuration.underlying.getConfig("lagom.akka.dev-mode.config")
        val actorSystemName = serverConfig.configuration.underlying.getString("lagom.akka.dev-mode.actor-system.name")
        val actorSystem = ActorSystem(actorSystemName, devModeAkkaConfig)
        val serverContext = ServerProvider.Context(serverConfig, appProvider, actorSystem,
          ActorMaterializer()(actorSystem), () => {
            // The execution context won't be needed after merging
            // https://github.com/playframework/playframework/pull/5506
            import scala.concurrent.ExecutionContext.Implicits.global
            actorSystem.terminate().map(_ => ())
          })
        val serverProvider = ServerProvider.fromConfiguration(classLoader, serverConfig.configuration)
        val server = serverProvider.createServer(serverContext)
        val reloadableServer = new ReloadableServer(server) {
          def reload(): Unit = appProvider.get
        }
        reloadableServer
      } catch {
        case e: ExceptionInInitializerError => throw e.getCause
      }

    }
  }

}
