/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.dev

import java.io.{ Closeable, File }
import java.net.URL
import java.security.{ AccessController, PrivilegedAction }
import java.time.Instant
import java.util
import java.util.{ Timer, TimerTask }
import java.util.concurrent.atomic.AtomicReference

import play.api.PlayException
import play.core.{ Build, BuildLink }
import play.core.server.ReloadableServer
import play.dev.filewatch.{ FileWatchService, SourceModificationWatch, WatchState }

import scala.collection.JavaConverters._
import better.files.{ File => _, _ }

object Reloader {

  sealed trait CompileResult
  case class CompileSuccess(sources: Map[String, Source], classpath: Seq[File]) extends CompileResult
  case class CompileFailure(exception: PlayException) extends CompileResult

  case class Source(file: File, original: Option[File])

  private val accessControlContext = AccessController.getContext

  /**
   * Execute f with context ClassLoader of Reloader
   */
  private def withReloaderContextClassLoader[T](f: => T): T = {
    val thread = Thread.currentThread
    val oldLoader = thread.getContextClassLoader
    // we use accessControlContext & AccessController to avoid a ClassLoader leak (ProtectionDomain class)
    AccessController.doPrivileged(new PrivilegedAction[T]() {
      def run: T = {
        try {
          thread.setContextClassLoader(classOf[Reloader].getClassLoader)
          f
        } finally {
          thread.setContextClassLoader(oldLoader)
        }
      }
    }, accessControlContext)
  }

  private def urls(cp: Seq[File]): Array[URL] = cp.map(_.toURI.toURL).toArray

  /**
   * Play dev server
   */
  trait DevServer extends Closeable {
    val buildLink: BuildLink

    /** Allows to register a listener that will be triggered a monitored file is changed. */
    def addChangeListener(f: () => Unit): Unit

    /** Reloads the application.*/
    def reload(): Unit

    /** URL at which the application is running (if started) */
    def url(): String
  }

  /**
   * Start the Lagom server in dev mode.
   */
  def startDevMode(
    parentClassLoader: ClassLoader, dependencyClasspath: Seq[File],
    reloadCompile: () => CompileResult, classLoaderDecorator: ClassLoader => ClassLoader,
    monitoredFiles: Seq[File], fileWatchService: FileWatchService, projectPath: File,
    devSettings: Seq[(String, String)], httpPort: Int, reloadLock: AnyRef
  ): DevServer = {
    /*
     * We need to do a bit of classloader magic to run the Play application.
     *
     * There are six classloaders:
     *
     * 1. buildLoader, the classloader of the build tool plugin (sbt/maven lagom plugin).
     * 2. parentClassLoader, a possibly shared classloader that may contain artifacts
     *    that are known to not share state, eg Scala itself.
     * 3. delegatingLoader, a special classloader that overrides class loading
     *    to delegate shared classes for build link to the buildLoader, and accesses
     *    the reloader.currentApplicationClassLoader for resource loading to
     *    make user resources available to dependency classes.
     * 4. applicationLoader, contains the application dependencies. Has the
     *    delegatingLoader as its parent. Classes from the commonLoader and
     *    the delegatingLoader are checked for loading first.
     * 5. decoratedClassloader, allows the classloader to be decorated.
     * 6. reloader.currentApplicationClassLoader, contains the user classes
     *    and resources. Has applicationLoader as its parent, where the
     *    application dependencies are found, and which will delegate through
     *    to the buildLoader via the delegatingLoader for the shared link.
     *    Resources are actually loaded by the delegatingLoader, where they
     *    are available to both the reloader and the applicationLoader.
     *    This classloader is recreated on reload. See PlayReloader.
     *
     * Someone working on this code in the future might want to tidy things up
     * by splitting some of the custom logic out of the URLClassLoaders and into
     * their own simpler ClassLoader implementations. The curious cycle between
     * applicationLoader and reloader.currentApplicationClassLoader could also
     * use some attention.
     */

    val buildLoader = this.getClass.getClassLoader

    /**
     * ClassLoader that delegates loading of shared build link classes to the
     * buildLoader. Also accesses the reloader resources to make these available
     * to the applicationLoader, creating a full circle for resource loading.
     */
    lazy val delegatingLoader: ClassLoader = new DelegatingClassLoader(parentClassLoader, Build.sharedClasses.asScala.toSet, buildLoader, reloader.getClassLoader _)

    lazy val applicationLoader = new NamedURLClassLoader("LagomDependencyClassLoader", urls(dependencyClasspath), delegatingLoader)
    lazy val decoratedLoader = classLoaderDecorator(applicationLoader)

    lazy val reloader = new Reloader(reloadCompile, decoratedLoader, projectPath, devSettings, monitoredFiles, fileWatchService, reloadLock)

    val server = {
      val mainClass = applicationLoader.loadClass("play.core.server.LagomReloadableDevServerStart")
      val mainDev = mainClass.getMethod("mainDevHttpMode", classOf[BuildLink], classOf[Int])
      mainDev.invoke(null, reloader, httpPort: java.lang.Integer).asInstanceOf[ReloadableServer]
    }

    new DevServer {
      val buildLink: BuildLink = reloader
      def addChangeListener(f: () => Unit): Unit = reloader.addChangeListener(f)
      def reload(): Unit = server.reload()
      def close(): Unit = {
        server.stop()
        reloader.close()
      }
      def url(): String = server.mainAddress().getHostName + ":" + server.mainAddress().getPort
    }
  }

  /**
   * Start the Lagom server without hot reloading
   */
  def startNoReload(parentClassLoader: ClassLoader, dependencyClasspath: Seq[File], buildProjectPath: File,
                    devSettings: Seq[(String, String)], httpPort: Int): DevServer = {
    val buildLoader = this.getClass.getClassLoader

    lazy val delegatingLoader: ClassLoader = new DelegatingClassLoader(
      parentClassLoader,
      Build.sharedClasses.asScala.toSet, buildLoader, () => Some(applicationLoader)
    )
    lazy val applicationLoader = new NamedURLClassLoader("LagomDependencyClassLoader", urls(dependencyClasspath),
      delegatingLoader)

    val _buildLink = new BuildLink {
      private val initialized = new java.util.concurrent.atomic.AtomicBoolean(false)
      override def reload(): AnyRef = {
        if (initialized.compareAndSet(false, true)) applicationLoader
        else null // this means nothing to reload
      }
      override def projectPath(): File = buildProjectPath
      override def settings(): util.Map[String, String] = devSettings.toMap.asJava
      override def forceReload(): Unit = ()
      override def findSource(className: String, line: Integer): Array[AnyRef] = null
    }

    val mainClass = applicationLoader.loadClass("play.core.server.LagomReloadableDevServerStart")
    val mainDev = mainClass.getMethod("mainDevHttpMode", classOf[BuildLink], classOf[Int])
    val server = mainDev.invoke(null, _buildLink, httpPort: java.lang.Integer).asInstanceOf[ReloadableServer]

    server.reload() // it's important to initialize the server

    new Reloader.DevServer {
      val buildLink: BuildLink = _buildLink

      /** Allows to register a listener that will be triggered a monitored file is changed. */
      def addChangeListener(f: () => Unit): Unit = ()

      /** Reloads the application.*/
      def reload(): Unit = ()

      /** URL at which the application is running (if started) */
      def url(): String = server.mainAddress().getHostName + ":" + server.mainAddress().getPort

      def close(): Unit = server.stop()
    }
  }

}

import Reloader._

class Reloader(
  reloadCompile:    () => CompileResult,
  baseLoader:       ClassLoader,
  val projectPath:  File,
  devSettings:      Seq[(String, String)],
  monitoredFiles:   Seq[File],
  fileWatchService: FileWatchService,
  reloadLock:       AnyRef
) extends BuildLink {

  // The current classloader for the application
  @volatile private var currentApplicationClassLoader: Option[ClassLoader] = None
  // Flag to force a reload on the next request.
  // This is set if a compile error occurs, and also by the forceReload method on BuildLink, which is called for
  // example when evolutions have been applied.
  @volatile private var forceReloadNextTime = false
  // Whether any source files have changed since the last request.
  @volatile private var changed = false
  // The last successful compile results. Used for rendering nice errors.
  @volatile private var currentSourceMap = Option.empty[Map[String, Source]]
  // A watch state for the classpath. Used to determine whether anything on the classpath has changed as a result
  // of compilation, and therefore a new classloader is needed and the app needs to be reloaded.
  @volatile private var watchState: WatchState = WatchState.empty

  // Stores the most recent time that a file was changed
  private val fileLastChanged = new AtomicReference[Instant]()

  // Create the watcher, updates the changed boolean when a file has changed.
  private val watcher = fileWatchService.watch(monitoredFiles, () => {

    changed = true
    onChange()
  })
  private val classLoaderVersion = new java.util.concurrent.atomic.AtomicInteger(0)

  private val quietTimeTimer = new Timer("reloader-timer", true)

  private val listeners = new java.util.concurrent.CopyOnWriteArrayList[() => Unit]()

  private val quietPeriodMs = 200l
  private def onChange(): Unit = {
    val now = Instant.now()
    fileLastChanged.set(now)
    // set timer task
    quietTimeTimer.schedule(new TimerTask {
      override def run(): Unit = quietPeriodFinished(now)
    }, quietPeriodMs)
  }

  private def quietPeriodFinished(start: Instant): Unit = {
    // If our start time is equal to the most recent start time stored, then execute the handlers and set the most
    // recent time to null, otherwise don't do anything.
    if (fileLastChanged.compareAndSet(start, null)) {
      import scala.collection.JavaConverters._
      listeners.iterator().asScala.foreach(listener => listener())
    }
  }

  def addChangeListener(f: () => Unit): Unit = listeners.add(f)

  /**
   * Contrary to its name, this doesn't necessarily reload the app.  It is invoked on every request, and will only
   * trigger a reload of the app if something has changed.
   *
   * Since this communicates across classloaders, it must return only simple objects.
   *
   *
   * @return Either
   * - Throwable - If something went wrong (eg, a compile error).
   * - ClassLoader - If the classloader has changed, and the application should be reloaded.
   * - null - If nothing changed.
   */
  def reload: AnyRef = {
    reloadLock.synchronized {
      if (changed || forceReloadNextTime || currentSourceMap.isEmpty || currentApplicationClassLoader.isEmpty) {
        val shouldReload = forceReloadNextTime

        changed = false
        forceReloadNextTime = false

        // use Reloader context ClassLoader to avoid ClassLoader leaks in sbt/scala-compiler threads
        Reloader.withReloaderContextClassLoader {
          // Run the reload task, which will trigger everything to compile
          reloadCompile() match {
            case CompileFailure(exception) =>
              // We force reload next time because compilation failed this time
              forceReloadNextTime = true
              exception

            case CompileSuccess(sourceMap, classpath) =>

              currentSourceMap = Some(sourceMap)

              // We only want to reload if the classpath has changed.  Assets don't live on the classpath, so
              // they won't trigger a reload.
              // Use the SBT watch service, passing true as the termination to force it to break after one check
              val (_, newState) = SourceModificationWatch.watch(() => classpath.iterator
                .filter(_.exists()).flatMap(_.toScala.listRecursively), 0, watchState)(true)
              // SBT has a quiet wait period, if that's set to true, sources were modified
              val triggered = newState.awaitingQuietPeriod
              watchState = newState

              if (triggered || shouldReload || currentApplicationClassLoader.isEmpty) {
                // Create a new classloader
                val version = classLoaderVersion.incrementAndGet
                val name = "ReloadableClassLoader(v" + version + ")"
                val urls = Reloader.urls(classpath)
                val loader = new NamedURLClassLoader(name, urls, baseLoader)
                currentApplicationClassLoader = Some(loader)
                loader
              } else {
                null // null means nothing changed
              }
          }
        }
      } else {
        null // null means nothing changed
      }
    }
  }

  lazy val settings: util.Map[String, String] = {
    import scala.collection.JavaConverters._
    devSettings.toMap.asJava
  }

  def forceReload() {
    forceReloadNextTime = true
  }

  def findSource(className: String, line: java.lang.Integer): Array[java.lang.Object] = {
    val topType = className.split('$').head
    currentSourceMap.flatMap { sources =>
      sources.get(topType).map { source =>
        Array[java.lang.Object](source.original.getOrElse(source.file), line)
      }
    }.orNull
  }

  def runTask(task: String): AnyRef =
    throw new UnsupportedOperationException("This BuildLink does not support running arbitrary tasks")

  def close(): Unit = {
    currentApplicationClassLoader = None
    currentSourceMap = None
    watcher.stop()
    quietTimeTimer.cancel()
  }

  def getClassLoader: Option[ClassLoader] = currentApplicationClassLoader
}
