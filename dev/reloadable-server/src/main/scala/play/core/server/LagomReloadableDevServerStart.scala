/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package play.core.server

import java.io.File
import java.net.InetAddress

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import play.api.ApplicationLoader.DevContext
import play.api._
import play.core.{ ApplicationProvider, BuildLink, SourceMapper }
import play.core.server.ReloadableServer
import play.utils.Threads

import scala.collection.JavaConverters._
import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.concurrent.duration._
import scala.util.control.NonFatal
import scala.util.{ Failure, Success, Try }

/**
 * Used to start servers in 'dev' mode, a mode where the application
 * is reloaded whenever its source changes.
 */
object LagomReloadableDevServerStart {

  /**
   * A threshold for retrieving the current hostname.
   *
   * If Lagom startup takes too long, it can cause a number of issues and we try to detect it using
   * InetAddress.getLocalHost. If it takes longer than this threshold, it might be a signal
   * of a well-known problem with MacOS that might cause issues with Lagom.
   */
  private val startupWarningThreshold = 1000L

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
    buildLink:   BuildLink,
    httpAddress: String,
    httpPort:    Int
  ): ReloadableServer = {
    mainDev(buildLink, Some(httpPort), None, httpAddress)
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

        val dirAndDevSettings: Map[String, String] =
          ServerConfig.rootDirConfig(path) ++
            buildLink.settings.asScala.toMap ++
            httpPort.toList.map("play.server.http.port" -> _.toString).toMap +
            ("play.server.http.address" -> httpAddress) +
            {
              // on dev-mode, we often have more than one cluster on the same jvm
              "akka.cluster.jmx.multi-mbeans-in-same-jvm" -> "on"
            }

        // Use plain Java call here in case of scala classloader mess
        {
          if (System.getProperty("play.debug.classpath") == "true") {
            System.out.println("\n---- Current ClassLoader ----\n")
            System.out.println(this.getClass.getClassLoader)
            System.out.println("\n---- The where is Scala? test ----\n")
            System.out.println(this.getClass.getClassLoader.getResource("scala/Predef$.class"))
          }
        }

        val before = System.currentTimeMillis()
        val address = InetAddress.getLocalHost
        val after = System.currentTimeMillis()
        if (after - before > startupWarningThreshold) {
          println(play.utils.Colors.red("WARNING: Retrieving local host name ${address} took more than ${startupWarningThreshold}ms, this can create problems at startup with Lagom"))
          println(play.utils.Colors.red("If you are using macOS, see https://thoeni.io/post/macos-sierra-java/ for a potential solution"))
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
              // this whole Await.result(Future{}) thing has been revisited in Play
              // but the issue is not yet fully addressed there neither
              // see issues:
              // https://github.com/playframework/playframework/pull/7627
              // and https://github.com/playframework/playframework/pull/7644
              //
              // we should reconsider if still need to have our own reloadable server
              implicit val ec = ExecutionContext.global
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
                        val context = ApplicationLoader.Context.create(
                          environment = environment,
                          initialSettings = dirAndDevSettings,
                          devContext = Some(DevContext(sourceMapper, buildLink))
                        )
                        val loader = ApplicationLoader(context)
                        loader.load(context)
                      }

                      Play.start(newApplication)

                      Success(newApplication)
                    } catch {
                      // No binary dependency on play-guice
                      case e if e.getClass.getName == "com.google.inject.CreationException" =>
                        lastState = Failure(e)
                        val hint = "Hint: Maybe you have forgot to enable your service Module class via `play.modules.enabled`? (check in your project's application.conf)"
                        logExceptionAndGetResult(path, e, hint)
                        lastState

                      case e: PlayException =>
                        lastState = Failure(e)
                        logExceptionAndGetResult(path, e)
                        lastState

                      case NonFatal(e) =>
                        lastState = Failure(UnexpectedException(unexpected = Some(e)))
                        logExceptionAndGetResult(path, e)
                        lastState

                      case e: LinkageError =>
                        lastState = Failure(UnexpectedException(unexpected = Some(e)))
                        logExceptionAndGetResult(path, e)
                        lastState

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
        val serverContext = ServerProvider.Context(serverConfig, appProvider, actorSystem, ActorMaterializer()(actorSystem),
          () => actorSystem.terminate())
        val serverProvider = ServerProvider.fromConfiguration(classLoader, serverConfig.configuration)
        serverProvider.createServer(serverContext)
      } catch {
        case e: ExceptionInInitializerError => throw e.getCause
      }

    }
  }

}
