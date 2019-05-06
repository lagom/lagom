/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package play.core.server

import java.io.File
import java.net.InetAddress

import akka.Done
import akka.actor.ActorSystem
import akka.actor.CoordinatedShutdown
import akka.stream.ActorMaterializer
import com.lightbend.lagom.devmode.ssl.LagomDevModeSSLHolder
import com.typesafe.sslconfig.ssl.FakeKeyStore
import play.api.ApplicationLoader.DevContext
import play.api._
import play.core.ApplicationProvider
import play.core.BuildLink
import play.core.SourceMapper
import play.utils.Threads

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.util.control.NonFatal
import scala.util.Failure
import scala.util.Success
import scala.util.Try

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

  def mainDev(
      buildLink: BuildLink,
      httpAddress: String,
      httpPort: Int,
      httpsPort: Int
  ): ReloadableServer = {
    val classLoader = getClass.getClassLoader
    Threads.withContextClassLoader(classLoader) {
      try {
        val process    = new RealServerProcess(args = Seq.empty)
        val path: File = buildLink.projectPath

        val enableSsl = httpsPort > 0

        // The pairs play.server.httpx.{address,port} are read from PlayRegisterWithServiceRegistry
        // to register the service
        val httpsSettings: Map[String, String] =
          if (enableSsl) {
            // in Dev mode we hardcode the keystoreBaseFolder to File(".").
            val keystoreBaseFolder = new File(".")
            val sslHolder          = new LagomDevModeSSLHolder(keystoreBaseFolder)

            Map(
              // In dev mode, `play.server.https.address` and `play.server.http.address` are assigned the same value
              // but both settings are set in case some code specifically read one config setting or the other.
              "play.server.https.address" -> httpAddress, // there's no httpsAddress
              "play.server.https.port"    -> httpsPort.toString,
              // See also com/lightbend/lagom/scaladsl/testkit/ServiceTest.scala
              // These configure the server
              "play.server.https.keyStore.path"     -> sslHolder.keyStoreMetadata.storeFile.getAbsolutePath,
              "play.server.https.keyStore.type"     -> sslHolder.keyStoreMetadata.storeType,
              "play.server.https.keyStore.password" -> String.valueOf(sslHolder.keyStoreMetadata.storePassword),
              // These configure the clients (play-ws and akka-grpc)
              "ssl-config.loose.disableHostnameVerification" -> "true",
              "ssl-config.trustManager.stores.0.path"        -> sslHolder.trustStoreMetadata.storeFile.getAbsolutePath,
              "ssl-config.trustManager.stores.0.type"        -> sslHolder.trustStoreMetadata.storeType,
              "ssl-config.trustManager.stores.0.password"    -> String.valueOf(sslHolder.trustStoreMetadata.storePassword)
            )
          } else Map.empty

        val httpSettings: Map[String, String] =
          Map(
            // The pairs play.server.httpx.{address,port} are read from PlayRegisterWithServiceRegistry
            // to register the service
            "play.server.http.address" -> httpAddress,
            "play.server.http.port"    -> httpPort.toString
          )

        // each user service needs to tune its "play.filters.hosts.allowed" so that Play's
        // AllowedHostFilter (https://www.playframework.com/documentation/2.6.x/AllowedHostsFilter)
        // doesn't block request with header "Host: " with a value "localhost:<someport>". The following
        // setting whitelists 'localhost` for both http/s ports and also 'httpAddress' for both ports too.
        val allowHostsSetting = "play.filters.hosts.allowed" -> {
          val http  = List(s"$httpAddress:$httpPort", s"localhost:$httpPort")
          val https = if (enableSsl) List(s"$httpAddress:$httpsPort", s"localhost:$httpsPort") else Nil
          (http ++ https).asJavaCollection
        }

        // on dev-mode, we often have more than one cluster on the same jvm
        val clusterSettings = "akka.cluster.jmx.multi-mbeans-in-same-jvm" -> "on"

        val dirAndDevSettings: Map[String, AnyRef] =
          ServerConfig.rootDirConfig(path) ++
            buildLink.settings.asScala.toMap ++
            httpSettings ++
            httpsSettings +
            allowHostsSetting +
            clusterSettings
        //            ("play.server.akka.http2.enabled" -> "true") +

        // Use plain Java call here in case of scala classloader mess
        {
          if (System.getProperty("play.debug.classpath") == "true") {
            System.out.println("\n---- Current ClassLoader ----\n")
            System.out.println(this.getClass.getClassLoader)
            System.out.println("\n---- The where is Scala? test ----\n")
            System.out.println(this.getClass.getClassLoader.getResource("scala/Predef$.class"))
          }
        }

        val before  = System.currentTimeMillis()
        val address = InetAddress.getLocalHost
        val after   = System.currentTimeMillis()
        if (after - before > startupWarningThreshold) {
          println(
            play.utils.Colors.red(
              s"WARNING: Retrieving local host name ${address} took more than ${startupWarningThreshold}ms, this can create problems at startup with Lagom"
            )
          )
          println(
            play.utils.Colors
              .red("If you are using macOS, see https://thoeni.io/post/macos-sierra-java/ for a potential solution")
          )
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
                        Option(buildLink.findSource(className, line.map(_.asInstanceOf[java.lang.Integer]).orNull))
                          .flatMap {
                            case Array(file: java.io.File, null)                    => Some((file, None))
                            case Array(file: java.io.File, line: java.lang.Integer) => Some((file, Some(line)))
                            case _                                                  => None
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

                    newApplication.coordinatedShutdown
                      .addTask(CoordinatedShutdown.PhaseBeforeActorSystemTerminate, "force-reload") { () =>
                        // We'll only force a reload if the reason for shutdown is not an Application.stop
                        if (!newApplication.coordinatedShutdown.shutdownReason().contains(ApplicationStoppedReason)) {
                          buildLink.forceReload()
                        }
                        Future.successful(Done)
                      }

                    Play.start(newApplication)

                    Success(newApplication)
                  } catch {
                    // No binary dependency on play-guice
                    case e if e.getClass.getName == "com.google.inject.CreationException" =>
                      lastState = Failure(e)
                      val hint =
                        "Hint: Maybe you have forgot to enable your service Module class via `play.modules.enabled`? (check in your project's application.conf)"
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
            }
          }

          private def logExceptionAndGetResult(path: File, e: Throwable, hint: String = ""): Unit = {
            e.printStackTrace()
            println()
            println(
              play.utils.Colors.red(
                s"Stacktrace caused by project ${path.getName} (filesystem path to project is ${path.getAbsolutePath}).\n${hint}"
              )
            )
          }

          override def handleWebCommand(request: play.api.mvc.RequestHeader) = None
        }

        // Start server with the application
        val serverConfig = ServerConfig(
          rootDir = path,
          port = Some(httpPort),
          sslPort = if (httpsPort > 0) Some(httpsPort) else None,
          address = httpAddress,
          mode = Mode.Dev,
          properties = process.properties,
          configuration =
            Configuration.load(classLoader, System.getProperties, dirAndDevSettings, allowMissingApplicationConf = true)
        )
        // We *must* use a different Akka configuration in dev mode, since loading two actor systems from the same
        // config will lead to resource conflicts, for example, if the actor system is configured to open a remote port,
        // then both the dev mode and the application actor system will attempt to open that remote port, and one of
        // them will fail.
        val devModeAkkaConfig         = serverConfig.configuration.underlying.getConfig("lagom.akka.dev-mode.config")
        val actorSystemName           = serverConfig.configuration.underlying.getString("lagom.akka.dev-mode.actor-system.name")
        val actorSystem: ActorSystem  = ActorSystem(actorSystemName, devModeAkkaConfig)
        val serverCoordinatedShutdown = CoordinatedShutdown(actorSystem)

        // Registering a task that invokes `Play.stop` is necessary for the scenarios where
        // the Application and the Server use separate ActorSystems (e.g. DevMode).
        serverCoordinatedShutdown.addTask(CoordinatedShutdown.PhaseServiceStop, "shutdown-application-dev-mode") { () =>
          implicit val ctx = actorSystem.dispatcher
          val stoppedApp   = appProvider.get.map(Play.stop)
          Future.fromTry(stoppedApp).map(_ => Done)
        }
        val serverContext = ServerProvider.Context(
          serverConfig,
          appProvider,
          actorSystem,
          ActorMaterializer()(actorSystem),
          () => Future.successful(())
        )
        val serverProvider = ServerProvider.fromConfiguration(classLoader, serverConfig.configuration)
        serverProvider.createServer(serverContext)
      } catch {
        case e: ExceptionInInitializerError => throw e.getCause
      }

    }
  }

}
