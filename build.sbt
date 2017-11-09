import java.net.InetSocketAddress
import java.nio.channels.ServerSocketChannel

import sbt.ScriptedPlugin
import com.typesafe.sbt.SbtMultiJvm
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys._
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm
import lagom.Protobuf
import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import de.heikoseeberger.sbtheader.{ HeaderKey, HeaderPattern }
import com.typesafe.tools.mima.core._

// Turn off "Resolving" log messages that clutter build logs
ivyLoggingLevel in ThisBuild := UpdateLogging.Quiet

def common: Seq[Setting[_]] = releaseSettings ++ bintraySettings ++ Seq(
  organization := "com.lightbend.lagom",
  // Must be "Apache-2.0", because bintray requires that it is a license that it knows about
  licenses := Seq(("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0.html"))),
  homepage := Some(url("https://www.lagomframework.com/")),
  sonatypeProfileName := "com.lightbend",
  headers := headers.value ++ Map(
     "scala" -> (
       HeaderPattern.cStyleBlockComment,
       """|/*
          | * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
          | */
          |""".stripMargin
     ),
     "java" -> (
       HeaderPattern.cStyleBlockComment,
       """|/*
          | * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
          | */
          |""".stripMargin
     )
  ),

  pomExtra := {
    <scm>
      <url>https://github.com/lagom/lagom</url>
      <connection>scm:git:git@github.com:lagom/lagom.git</connection>
    </scm>
    <developers>
      <developer>
        <id>lagom</id>
        <name>Lagom Contributors</name>
        <url>https://github.com/lagom</url>
      </developer>
    </developers>
  },
  pomIncludeRepository := { _ => false },

  concurrentRestrictions in Global += Tags.limit(Tags.Test, 1),

  scalacOptions in (Compile, doc) ++= (scalaBinaryVersion.value match {
    case "2.12" => Seq("-no-java-comments")
    case _ => Seq.empty
  }),

  // Setting javac options in common allows IntelliJ IDEA to import them automatically
  javacOptions in compile ++= Seq(
    "-encoding", "UTF-8",
    "-source", "1.8",
    "-target", "1.8",
    "-parameters",
    "-Xlint:unchecked",
    "-Xlint:deprecation"
  ),

  ScalariformKeys.preferences in Compile  := formattingPreferences,
  ScalariformKeys.preferences in Test     := formattingPreferences,
  ScalariformKeys.preferences in MultiJvm := formattingPreferences
)

def bintraySettings: Seq[Setting[_]] = Seq(
  bintrayOrganization := Some("lagom"),
  bintrayRepository := "sbt-plugin-releases",
  bintrayPackage := "lagom-sbt-plugin",
  bintrayReleaseOnPublish := false
)

def releaseSettings: Seq[Setting[_]] = Seq(
  releasePublishArtifactsAction := PgpKeys.publishSigned.value,
  releaseTagName := (version in ThisBuild).value,
  releaseProcess := {
    import ReleaseTransformations._

    Seq[ReleaseStep](
      checkSnapshotDependencies,
      inquireVersions,
      setReleaseVersion,
      commitReleaseVersion,
      tagRelease,
      releaseStepCommandAndRemaining("+publishSigned"),
      releaseStepTask(bintrayRelease in thisProjectRef.value),
      releaseStepCommand("sonatypeRelease"),
      setNextVersion,
      commitNextVersion,
      pushChanges
    )
  }
)

/**
 * sbt release's releaseStepCommand does not execute remaining commands, which sbt-doge relies on
 */
def releaseStepCommandAndRemaining(command: String): State => State = { originalState =>
  import sbt.complete.Parser

  // Capture current remaining commands
  val originalRemaining = originalState.remainingCommands

  def runCommand(command: String, state: State): State = {
    val newState = Parser.parse(command, state.combinedParser) match {
      case Right(cmd) => cmd()
      case Left(msg) => throw sys.error(s"Invalid programmatic input:\n$msg")
    }
    if (newState.remainingCommands.isEmpty) {
      newState
    } else {
      runCommand(newState.remainingCommands.head, newState.copy(remainingCommands = newState.remainingCommands.tail))
    }
  }

  runCommand(command, originalState.copy(remainingCommands = Nil)).copy(remainingCommands = originalRemaining)
}

def runtimeScalaSettings: Seq[Setting[_]] = Seq(
  crossScalaVersions := Dependencies.ScalaVersions,
  scalaVersion := Dependencies.ScalaVersions.head
)

def runtimeLibCommon: Seq[Setting[_]] = common ++ runtimeScalaSettings ++ Seq(
  Dependencies.validateDependenciesSetting,
  Dependencies.pruneWhitelistSetting,
  Dependencies.dependencyWhitelistSetting,

  // compile options
  scalacOptions in Compile ++= Seq(
    "-encoding",
    "UTF-8",
    "-target:jvm-1.8",
    "-feature",
    "-unchecked",
    "-Xlog-reflective-calls",
    "-deprecation"
  ),

  incOptions := incOptions.value.withNameHashing(true),

  // show full stack traces and test case durations
  testOptions in Test += Tests.Argument("-oDF"),
  // -v Log "test run started" / "test started" / "test run finished" events on log level "info" instead of "debug".
  // -a Show stack traces and exception class name for AssertionErrors.
  testOptions += Tests.Argument(TestFrameworks.JUnit, "-v", "-a")
)

def formattingPreferences = {
  import scalariform.formatter.preferences._
  FormattingPreferences()
    .setPreference(RewriteArrowSymbols, false)
    .setPreference(AlignParameters, true)
    .setPreference(AlignSingleLineCaseStatements, true)
    .setPreference(SpacesAroundMultiImports, true)
}

val defaultMultiJvmOptions: List[String] = {
  import scala.collection.JavaConverters._
  // multinode.D= and multinode.X= makes it possible to pass arbitrary
  // -D or -X arguments to the forked jvm, e.g.
  // -Djava.net.preferIPv4Stack=true or -Dmultinode.Xmx512m
  val MultinodeJvmArgs = "multinode\\.(D|X)(.*)".r
  val knownPrefix = Set("akka.", "lagom.")
  val properties = System.getProperties.propertyNames.asScala.toList.collect {
    case MultinodeJvmArgs(a, b) =>
      val value = System.getProperty("multinode." + a + b)
      "-" + a + b + (if (value == "") "" else "=" + value)
    case key: String if knownPrefix.exists(pre => key.startsWith(pre)) => "-D" + key + "=" + System.getProperty(key)
  }

  "-Xmx128m" :: properties
}

def databasePortSetting: String = {
  val serverSocket = ServerSocketChannel.open().socket()
  serverSocket.bind(new InetSocketAddress("127.0.0.1", 0))
  val port = serverSocket.getLocalPort
  serverSocket.close()
  s"-Ddatabase.port=$port"
}


def multiJvmTestSettings: Seq[Setting[_]] = {

  // change multi-jvm lib folder to reflect the scala version used during crossbuild
  // must be done using a dynamic setting because we must read crossTarget.value
  def crossbuildMultiJvm = Def.settingDyn {
    val path = crossTarget.value.getName
    Def.setting {
      target.apply { targetFile =>
        new File(targetFile, path + "/multi-run-copied-libraries")
      }.value
    }
  }

  SbtMultiJvm.multiJvmSettings ++
    forkedTests ++
    // enabling HeaderPlugin in MultiJvm requires two sets of settings.
    // see https://github.com/sbt/sbt-header/issues/37
    HeaderPlugin.settingsFor(MultiJvm) ++
    AutomateHeaderPlugin.automateFor(MultiJvm) ++
    inConfig(MultiJvm)(SbtScalariform.configScalariformSettings) ++
    (compileInputs in(MultiJvm, compile) := {
      (compileInputs in(MultiJvm, compile)) dependsOn (scalariformFormat in MultiJvm)
    }.value) ++
    Seq(
      parallelExecution in Test := false,
      MultiJvmKeys.jvmOptions in MultiJvm := databasePortSetting :: defaultMultiJvmOptions,
      // make sure that MultiJvm test are compiled by the default test compilation
      compile in MultiJvm := ((compile in MultiJvm) triggeredBy (compile in Test)).value,
      // tag MultiJvm tests so that we can use concurrentRestrictions to disable parallel tests
      executeTests in MultiJvm := ((executeTests in MultiJvm) tag Tags.Test).value,
      // make sure that MultiJvm tests are executed by the default test target,
      // and combine the results from ordinary test and multi-jvm tests
      executeTests in Test := {
        val testResults = (executeTests in Test).value
        val multiNodeResults = (executeTests in MultiJvm).value
        val overall =
          if (testResults.overall.id < multiNodeResults.overall.id)
            multiNodeResults.overall
          else
            testResults.overall
        Tests.Output(overall,
          testResults.events ++ multiNodeResults.events,
          testResults.summaries ++ multiNodeResults.summaries)
      },
      // change multi-jvm lib folder to reflect the scala version used during crossbuild
      multiRunCopiedClassLocation in MultiJvm := crossbuildMultiJvm.value
    )
}

def macroCompileSettings: Seq[Setting[_]] = Seq(
  compile in Test ~= { a =>
    // Delete classes in "compile" packages after compiling.
    // These are used for compile-time tests and should be recompiled every time.
    val products = a.relations.allProducts.toSeq ** new SimpleFileFilter(_.getParentFile.getName == "compile")
    IO.delete(products.get)
    a
  }
)

def mimaSettings(versions: Seq[String]): Seq[Setting[_]] = {
  Seq(
    mimaPreviousArtifacts := {
      scalaVersionFilter(scalaBinaryVersion.value, versions).map { version =>
        organization.value % s"${moduleName.value}_${scalaBinaryVersion.value}" % version
      }.toSet
    },
    mimaBinaryIssueFilters += ProblemFilters.excludePackage("com.lightbend.lagom.internal")
  )
}

def scalaVersionFilter(scalaBinaryVersion: String, versions: Seq[String]): Seq[String] = {
  // parse version into (major, minor, patch) for comparison
  def Version(version: String): (String, String, String) = version.split(".", 3).toSeq match {
    case Seq(major, minor, patch) => (major, minor, patch)
    case _ => throw new IllegalArgumentException("version does not match major.minor.patch format")
  }
  import scala.math.Ordering.Implicits._
  val sinceVersion = Version(scalaVersionSince.getOrElse(scalaBinaryVersion, "1.0.0"))
  versions.filter(Version(_) >= sinceVersion)
}

def scalaVersionSince = Map(
  "2.12" -> "1.4.0"
)

def since10 = Seq("1.0.0") ++ since11
def since11 = Seq("1.1.0") ++ since12
def since12 = Seq("1.2.2") ++ since13
def since13 = Seq("1.3.3")

val javadslProjects = Seq[Project](
  `api-javadsl`,
  `server-javadsl`,
  `client-javadsl`,
  `broker-javadsl`,
  `kafka-client-javadsl`,
  `kafka-broker-javadsl`,
  `cluster-javadsl`,
  `persistence-javadsl`,
  `persistence-cassandra-javadsl`,
  `persistence-jdbc-javadsl`,
  `persistence-jpa-javadsl`,
  `pubsub-javadsl`,
  jackson,
  `testkit-javadsl`,
  immutables,
  `integration-client-javadsl`
)

val scaladslProjects = Seq[Project](
  `api-scaladsl`,
  `client-scaladsl`,
  `broker-scaladsl`,
  `kafka-client-scaladsl`,
  `kafka-broker-scaladsl`,
  `server-scaladsl`,
  `cluster-scaladsl`,
  `persistence-scaladsl`,
  `persistence-cassandra-scaladsl`,
  `persistence-jdbc-scaladsl`,
  `pubsub-scaladsl`,
  `testkit-scaladsl`,
  `devmode-scaladsl`,
  `play-json`
)

val coreProjects = Seq[Project](
  `api-tools`,
  api,
  client,
  server,
  spi,
  `cluster-core`,
  `kafka-client`,
  `kafka-broker`,
  `persistence-core`,
  `persistence-cassandra-core`,
  `persistence-jdbc-core`,
  `testkit-core`,
  logback,
  log4j2
)

val otherProjects = devEnvironmentProjects ++ Seq[Project](
  `integration-tests-javadsl`,
  `integration-tests-scaladsl`,
  `macro-testkit`
)

lazy val root = (project in file("."))
  .settings(name := "lagom")
  .settings(runtimeLibCommon: _*)
  .enablePlugins(CrossPerProjectPlugin)
  .settings(
    crossScalaVersions := Dependencies.ScalaVersions,
    scalaVersion := Dependencies.ScalaVersions.head,
    PgpKeys.publishSigned := {},
    publishLocal := {},
    publishArtifact in Compile := false,
    publish := {}
  )
  .enablePlugins(lagom.UnidocRoot)
  .settings(UnidocRoot.settings(javadslProjects.map(Project.projectToRef), scaladslProjects.map(Project.projectToRef)): _*)
  .settings(
    whitesourceProduct in ThisBuild               := "Lightbend Reactive Platform",
    whitesourceAggregateProjectName in ThisBuild  := sys.env.getOrElse("WHITESOURCE_PROJECT_NAME", default = "invalid"),
    whitesourceAggregateProjectToken in ThisBuild := sys.env.getOrElse("WHITESOURCE_PROJECT_TOKEN", default = "invalid")
  )
  .aggregate((javadslProjects ++ scaladslProjects ++ coreProjects ++ otherProjects).map(Project.projectToRef): _*)

  credentials += Credentials(realm = "whitesource",
      host = "whitesourcesoftware.com",
      userName = "",
      passwd = sys.env.getOrElse("WHITESOURCE_PASSWORD", default = "invalid"))

def RuntimeLibPlugins = AutomateHeaderPlugin && Sonatype && PluginsAccessor.exclude(BintrayPlugin)
def SbtPluginPlugins = AutomateHeaderPlugin && BintrayPlugin && PluginsAccessor.exclude(Sonatype)

lazy val api = (project in file("service/core/api"))
  .settings(runtimeLibCommon: _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-api",
    Dependencies.api
  )


lazy val `api-javadsl` = (project in file("service/javadsl/api"))
  .settings(name := "lagom-javadsl-api")
  .settings(runtimeLibCommon: _*)
  .settings(mimaSettings(since10): _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    Dependencies.`api-javadsl`
  ).dependsOn(api)

lazy val `api-scaladsl` = (project in file("service/scaladsl/api"))
  .settings(name := "lagom-scaladsl-api")
  .settings(runtimeLibCommon: _*)
  .settings(mimaSettings(since13): _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    Dependencies.`api-scaladsl`,
    mimaBinaryIssueFilters ++= Seq(
      // see https://github.com/lagom/lagom/pull/881
      ProblemFilters.exclude[IncompatibleResultTypeProblem]("com.lightbend.lagom.scaladsl.api.AdditionalConfiguration.configuration"),

      ProblemFilters.exclude[ReversedMissingMethodProblem]("com.lightbend.lagom.scaladsl.api.ServiceLocator.locateAll"),
      ProblemFilters.exclude[ReversedMissingMethodProblem]("com.lightbend.lagom.scaladsl.api.broker.Subscriber.withMetadata"),

      // see https://github.com/lagom/lagom/pull/1021
      ProblemFilters.exclude[MissingClassProblem]("com.lightbend.lagom.scaladsl.api.deser.PathParamSerializer$NamedPathParamSerializer")
    )
  ).dependsOn(api)

lazy val immutables = (project in file("immutables"))
  .settings(name := "lagom-javadsl-immutables")
  .settings(mimaSettings(since10): _*)
  .settings(runtimeLibCommon: _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    Dependencies.immutables
  )

lazy val spi = (project in file("spi"))
  .settings(name := "lagom-spi")
  .settings(runtimeLibCommon: _*)
  .enablePlugins(RuntimeLibPlugins)

lazy val jackson = (project in file("jackson"))
  .settings(name := "lagom-javadsl-jackson")
  .settings(runtimeLibCommon: _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    Dependencies.jackson
  )
  .dependsOn(`api-javadsl`, immutables % "test->compile")

lazy val `play-json` = (project in file("play-json"))
  .settings(runtimeLibCommon: _*)
  .settings(mimaSettings(since13): _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-scaladsl-play-json",
    Dependencies.`play-json`,
    mimaBinaryIssueFilters ++= Seq(
      // see https://github.com/lagom/lagom/pull/1071
      ProblemFilters.exclude[IncompatibleResultTypeProblem]("com.lightbend.lagom.scaladsl.playjson.JsonMigration.transform")
    )
  )

lazy val `api-tools` = (project in file("api-tools"))
  .settings(runtimeLibCommon: _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    Dependencies.`api-tools`
  )
  .dependsOn(
    spi,
    `server-javadsl` % Test,
    `server-scaladsl` % Test
  )


lazy val client = (project in file("service/core/client"))
  .settings(runtimeLibCommon: _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-client",
    Dependencies.client
  ).dependsOn(api, spi)

lazy val `client-javadsl` = (project in file("service/javadsl/client"))
  .settings(runtimeLibCommon: _*)
  .settings(mimaSettings(since10): _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-javadsl-client",
    Dependencies.`client-javadsl`
  )
  .dependsOn(client, `api-javadsl`, jackson)

lazy val `client-scaladsl` = (project in file("service/scaladsl/client"))
  .settings(runtimeLibCommon: _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(mimaSettings(since13): _*)
  .settings(macroCompileSettings: _*)
  .settings(
    name := "lagom-scaladsl-client",
    Dependencies.`client-scaladsl`,
    mimaBinaryIssueFilters ++= Seq(
      ProblemFilters.exclude[ReversedMissingMethodProblem]("com.lightbend.lagom.scaladsl.client.CircuitBreakerComponents.circuitBreakersPanel"),
      ProblemFilters.exclude[InheritedNewAbstractMethodProblem]("com.lightbend.lagom.scaladsl.api.LagomConfigComponent.config")
    )
  )
  .dependsOn(client, `api-scaladsl`, `macro-testkit` % Test)

lazy val `integration-client-javadsl` = (project in file("service/javadsl/integration-client"))
  .settings(
    name := "lagom-javadsl-integration-client",
    Dependencies.`integration-client-javadsl`
  )
  .settings(mimaSettings(since10): _*)
  .settings(runtimeLibCommon: _*)
  .enablePlugins(RuntimeLibPlugins)
  .dependsOn(`client-javadsl`, `service-registry-client-javadsl`, `kafka-client-javadsl`)

lazy val server = (project in file("service/core/server"))
  .settings(
    name := "lagom-server",
    Dependencies.server
  )
  .enablePlugins(RuntimeLibPlugins)
  .settings(runtimeLibCommon: _*)
  .dependsOn(client)


lazy val `server-javadsl` = (project in file("service/javadsl/server"))
  .settings(
    name := "lagom-javadsl-server",
    Dependencies.`server-javadsl`
  )
  .enablePlugins(RuntimeLibPlugins)
  .settings(mimaSettings(since10): _*)
  .settings(runtimeLibCommon: _*)
  .dependsOn(server, `client-javadsl`, immutables % "provided")
  // bring jackson closer to the root of the dependency tree to prompt Maven to choose the right version
  .dependsOn(jackson)

lazy val `server-scaladsl` = (project in file("service/scaladsl/server"))
  .settings(
    name := "lagom-scaladsl-server",
    Dependencies.`server-scaladsl`,
    mimaBinaryIssueFilters ++= Seq(
      // see https://github.com/lagom/lagom/pull/888 for justification for this breaking change
      ProblemFilters.exclude[IncompatibleResultTypeProblem]("com.lightbend.lagom.scaladsl.server.LagomServerBuilder.buildRouter"),
      ProblemFilters.exclude[IncompatibleResultTypeProblem]("com.lightbend.lagom.scaladsl.server.LagomServiceBinding.router"),
      ProblemFilters.exclude[ReversedMissingMethodProblem]("com.lightbend.lagom.scaladsl.server.LagomServiceBinding.router"),
      ProblemFilters.exclude[IncompatibleResultTypeProblem]("com.lightbend.lagom.scaladsl.server.LagomServer.router"),
      ProblemFilters.exclude[ReversedMissingMethodProblem]("com.lightbend.lagom.scaladsl.server.LagomServer.router")
    )
  )
  .enablePlugins(RuntimeLibPlugins)
  .settings(mimaSettings(since13): _*)
  .settings(runtimeLibCommon: _*)
  .dependsOn(server, `client-scaladsl`, `play-json`)

lazy val `testkit-core` = (project in file("testkit/core"))
  .settings(runtimeLibCommon: _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-core-testkit",
    Dependencies.`testkit-core`
  ).settings(forkedTests: _*)


lazy val `testkit-javadsl` = (project in file("testkit/javadsl"))
  .settings(runtimeLibCommon: _*)
  .settings(mimaSettings(since10): _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(forkedTests: _*)
  .settings(
    name := "lagom-javadsl-testkit",
    Dependencies.`testkit-javadsl`,
    mimaBinaryIssueFilters ++= Seq(
      ProblemFilters.exclude[IncompatibleTemplateDefProblem]("com.lightbend.lagom.javadsl.testkit.ServiceTest$Setup"),
      ProblemFilters.exclude[MissingClassProblem]("com.lightbend.lagom.javadsl.testkit.ServiceTest$Setup$")
    )
  )
  .dependsOn(
    `testkit-core`,
    `server-javadsl`,
    `pubsub-javadsl`,
    `broker-javadsl`,
    `persistence-core` % "compile;test->test",
    `persistence-cassandra-javadsl` % "test->test",
    `jackson` % "test->test"
  )

lazy val `testkit-scaladsl` = (project in file("testkit/scaladsl"))
  .settings(runtimeLibCommon: _*)
  .settings(mimaSettings(since13): _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(forkedTests: _*)
  .settings(
    name := "lagom-scaladsl-testkit",
    Dependencies.`testkit-scaladsl`,
    mimaBinaryIssueFilters ++= Seq(
      ProblemFilters.exclude[DirectMissingMethodProblem]("com.lightbend.lagom.scaladsl.testkit.TopicStub#SubscriberStub.this"),

      // See https://github.com/lagom/lagom/pull/1081 for justification for this breaking change
      ProblemFilters.exclude[DirectMissingMethodProblem]("com.lightbend.lagom.scaladsl.testkit.PersistentEntityTestDriver.runOne")
    )
  )
  .dependsOn(
    `testkit-core`,
    `server-scaladsl`,
    `broker-scaladsl`,
    `persistence-core` % "compile;test->test",
    `persistence-scaladsl` % "compile;test->test",
    `persistence-cassandra-scaladsl` % "compile;test->test"
  )

lazy val `integration-tests-javadsl` = (project in file("service/javadsl/integration-tests"))
  .settings(runtimeLibCommon: _*)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(forkedTests: _*)
  .settings(
    name := "lagom-javadsl-integration-tests",
    Dependencies.`integration-tests-javadsl`,
    PgpKeys.publishSigned := {},
    publish := {}
  )
  .dependsOn(
    `server-javadsl`,
    `persistence-cassandra-javadsl`,
    `pubsub-javadsl`,
    `testkit-javadsl`,
    logback,
    `integration-client-javadsl`
  )

lazy val `integration-tests-scaladsl` = (project in file("service/scaladsl/integration-tests"))
  .settings(runtimeLibCommon: _*)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(forkedTests: _*)
  .settings(
    name := "lagom-scaladsl-integration-tests",
    Dependencies.`integration-tests-scaladsl`,
    PgpKeys.publishSigned := {},
    publish := {}
  )
  .dependsOn(`server-scaladsl`, logback, `testkit-scaladsl`)

// for forked tests
def forkedTests: Seq[Setting[_]] = Seq(
  fork in Test := true,
  concurrentRestrictions in Global += Tags.limit(Tags.Test, 1),
  javaOptions in Test ++= Seq("-Xms256M", "-Xmx512M"),
  testGrouping in Test := singleTestsGrouping((definedTests in Test).value)
)

// group tests, a single test per group
def singleTestsGrouping(tests: Seq[TestDefinition]) = {
  // We could group non Cassandra tests into another group
  // to avoid new JVM for each test, see http://www.scala-sbt.org/release/docs/Testing.html
  val javaOptions = Seq("-Xms256M", "-Xmx512M")
  tests map { test =>
    new Tests.Group(
      name = test.name,
      tests = Seq(test),
      runPolicy = Tests.SubProcess(ForkOptions(runJVMOptions = javaOptions)))
  }
}

lazy val `cluster-core` = (project in file("cluster/core"))
  .settings(runtimeLibCommon: _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-cluster-core",
    Dependencies.`cluster-core`
  )

lazy val `cluster-javadsl` = (project in file("cluster/javadsl"))
  .dependsOn(`cluster-core`, jackson)
  .settings(runtimeLibCommon: _*)
  .settings(mimaSettings(since10): _*)
  .settings(multiJvmTestSettings: _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-javadsl-cluster",
    Dependencies.`cluster-javadsl`
  ) configs (MultiJvm)

lazy val `cluster-scaladsl` = (project in file("cluster/scaladsl"))
  .dependsOn(`cluster-core`, `play-json`)
  .settings(runtimeLibCommon: _*)
  .settings(mimaSettings(since13): _*)
  .settings(multiJvmTestSettings: _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-scaladsl-cluster",
    Dependencies.`cluster-scaladsl`
  ) configs (MultiJvm)

lazy val `pubsub-javadsl` = (project in file("pubsub/javadsl"))
  .dependsOn(`cluster-javadsl`)
  .settings(runtimeLibCommon: _*)
  .settings(mimaSettings(since10): _*)
  .settings(multiJvmTestSettings: _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-javadsl-pubsub",
    Dependencies.`pubsub-javadsl`
  ) configs (MultiJvm)

lazy val `pubsub-scaladsl` = (project in file("pubsub/scaladsl"))
  .dependsOn(`cluster-scaladsl`)
  .settings(runtimeLibCommon: _*)
  .settings(mimaSettings(since13): _*)
  .settings(multiJvmTestSettings: _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-scaladsl-pubsub",
    Dependencies.`pubsub-scaladsl`
  ) configs (MultiJvm)

lazy val `persistence-core` = (project in file("persistence/core"))
  .dependsOn(`cluster-core`)
  .settings(runtimeLibCommon: _*)
  .settings(Protobuf.settings)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-persistence-core",
    Dependencies.`persistence-core`
  )

lazy val `persistence-javadsl` = (project in file("persistence/javadsl"))
  .settings(
    name := "lagom-javadsl-persistence",
    mimaBinaryIssueFilters ++= Seq(
      ProblemFilters.exclude[MissingClassProblem]("com.lightbend.lagom.javadsl.persistence.PersistenceModule$InitServiceLocatorHolder"),
      ProblemFilters.exclude[MissingClassProblem]("com.lightbend.lagom.javadsl.persistence.PersistenceModule$"),
      // See https://github.com/lagom/lagom/pull/405 for justification for this breaking change,
      // and verification that it causes no binary compatibility problems in practice.
      ProblemFilters.exclude[IncompatibleTemplateDefProblem]("com.lightbend.lagom.javadsl.persistence.PersistentEntity$Persist"),
      ProblemFilters.exclude[MissingTypesProblem]("com.lightbend.lagom.javadsl.persistence.PersistentEntity$PersistOne"),
      ProblemFilters.exclude[MissingTypesProblem]("com.lightbend.lagom.javadsl.persistence.PersistentEntity$PersistAll"),
      ProblemFilters.exclude[MissingTypesProblem]("com.lightbend.lagom.javadsl.persistence.PersistentEntity$PersistNone"),

      // Deprecated in 1.2.0, deleted due to other binary incompatibility introduced when Akka 2.5.0 was upgraded.
      ProblemFilters.exclude[MissingClassProblem]("com.lightbend.lagom.javadsl.persistence.testkit.TestUtil"),
      ProblemFilters.exclude[MissingClassProblem]("com.lightbend.lagom.javadsl.persistence.testkit.TestUtil$"),
      ProblemFilters.exclude[MissingClassProblem]("com.lightbend.lagom.javadsl.persistence.testkit.TestUtil$AwaitPersistenceInit"),

      // package private
      ProblemFilters.exclude[IncompatibleTemplateDefProblem]("com.lightbend.lagom.javadsl.persistence.PersistentEntity$PersistNone")
    ),
    Dependencies.`persistence-javadsl`
  )
  .dependsOn(`persistence-core` % "compile;test->test", jackson, `cluster-javadsl`)
  .settings(runtimeLibCommon: _*)
  .settings(mimaSettings(since12): _*)
  .settings(Protobuf.settings)
  .enablePlugins(RuntimeLibPlugins)

lazy val `persistence-scaladsl` = (project in file("persistence/scaladsl"))
  .settings(
    name := "lagom-scaladsl-persistence",
    Dependencies.`persistence-scaladsl`,
    mimaBinaryIssueFilters ++= Seq(
      ProblemFilters.exclude[IncompatibleMethTypeProblem]("com.lightbend.lagom.scaladsl.persistence.testkit.AbstractTestUtil#AwaitPersistenceInit.persist"),
      ProblemFilters.exclude[IncompatibleMethTypeProblem]("com.lightbend.lagom.scaladsl.persistence.testkit.AbstractTestUtil#AwaitPersistenceInit.persistAsync")
    )
  )
  .dependsOn(`persistence-core` % "compile;test->test", `play-json`, `cluster-scaladsl`)
  .settings(runtimeLibCommon: _*)
  .settings(mimaSettings(since13): _*)
  .settings(Protobuf.settings)
  .enablePlugins(RuntimeLibPlugins)

lazy val `persistence-cassandra-core` = (project in file("persistence-cassandra/core"))
  .dependsOn(`persistence-core` % "compile;test->test")
  .settings(runtimeLibCommon: _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-persistence-cassandra-core",
    Dependencies.`persistence-cassandra-core`
  )

lazy val `persistence-cassandra-javadsl` = (project in file("persistence-cassandra/javadsl"))
  .settings(
    name := "lagom-javadsl-persistence-cassandra",
    mimaBinaryIssueFilters ++= Seq(
      ProblemFilters.exclude[DirectMissingMethodProblem]("com.lightbend.lagom.javadsl.persistence.cassandra.CassandraSession.this"),
      ProblemFilters.exclude[FinalClassProblem]("com.lightbend.lagom.javadsl.persistence.cassandra.CassandraSession")
    ),
    Dependencies.`persistence-cassandra-javadsl`
  )
  .dependsOn(
    `persistence-core` % "compile;test->test",
    `persistence-javadsl` % "compile;test->test",
    `persistence-cassandra-core` % "compile;test->test",
    `api-javadsl`
  )
  .settings(runtimeLibCommon: _*)
  .settings(mimaSettings(since12): _*)
  .settings(multiJvmTestSettings: _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings() configs (MultiJvm)

lazy val `persistence-cassandra-scaladsl` = (project in file("persistence-cassandra/scaladsl"))
  .settings(
    name := "lagom-scaladsl-persistence-cassandra",
    Dependencies.`persistence-cassandra-scaladsl`,
    mimaBinaryIssueFilters ++= Seq(
      ProblemFilters.exclude[ReversedMissingMethodProblem]("com.lightbend.lagom.scaladsl.persistence.cassandra.ReadSideCassandraPersistenceComponents.testCasReadSideSettings"),
      ProblemFilters.exclude[IncompatibleMethTypeProblem]("com.lightbend.lagom.scaladsl.persistence.cassandra.testkit.TestUtil#AwaitPersistenceInit.persist"),
      ProblemFilters.exclude[IncompatibleMethTypeProblem]("com.lightbend.lagom.scaladsl.persistence.cassandra.testkit.TestUtil#AwaitPersistenceInit.persistAsync")
    )
  )
  .dependsOn(
    `persistence-core` % "compile;test->test",
    `persistence-scaladsl` % "compile;test->test",
    `persistence-cassandra-core` % "compile;test->test",
    `api-scaladsl`
  )
  .settings(runtimeLibCommon: _*)
  .settings(mimaSettings(since13): _*)
  .settings(multiJvmTestSettings: _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings() configs (MultiJvm)


lazy val `persistence-jdbc-core` = (project in file("persistence-jdbc/core"))
  .dependsOn(`persistence-core` % "compile;test->test")
  .settings(runtimeLibCommon: _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(forkedTests: _*)
  .settings(
    name := "lagom-persistence-jdbc-core",
    Dependencies.`persistence-jdbc-core`
  )

lazy val `persistence-jdbc-javadsl` = (project in file("persistence-jdbc/javadsl"))
  .settings(
    name := "lagom-javadsl-persistence-jdbc",
    Dependencies.`persistence-jdbc-javadsl`
  )
  .dependsOn(
    `persistence-jdbc-core`,
    `persistence-core` % "compile;test->test",
    `persistence-javadsl` % "compile;test->test",
    logback % Test
  )
  .settings(runtimeLibCommon: _*)
  .settings(mimaSettings(since12): _*)
  .settings(multiJvmTestSettings: _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(forkedTests: _*) configs (MultiJvm)

lazy val `persistence-jdbc-scaladsl` = (project in file("persistence-jdbc/scaladsl"))
  .settings(
    name := "lagom-scaladsl-persistence-jdbc",
    Dependencies.`persistence-jdbc-scaladsl`
  )
  .dependsOn(
    `persistence-jdbc-core`,
    `persistence-core` % "compile;test->test",
    `persistence-scaladsl` % "compile;test->test",
    logback % Test
  )
  .settings(runtimeLibCommon: _*)
  .settings(mimaSettings(since13): _*)
  .settings(multiJvmTestSettings: _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(forkedTests: _*) configs (MultiJvm)

lazy val `persistence-jpa-javadsl` = (project in file("persistence-jpa/javadsl"))
  .dependsOn(`persistence-jdbc-javadsl` % "compile;test->test")
  .settings(runtimeLibCommon: _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(forkedTests: _*)
  .settings(
    name := "lagom-javadsl-persistence-jpa",
    Dependencies.`persistence-jpa-javadsl`,
    Dependencies.dependencyWhitelist ++= Dependencies.JpaTestWhitelist
  )

lazy val `broker-javadsl` = (project in file("service/javadsl/broker"))
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-javadsl-broker",
    Dependencies.`broker-javadsl`
  )
  .settings(runtimeLibCommon: _*)
  .settings(mimaSettings(since12): _*)
  .dependsOn(`api-javadsl`, `persistence-javadsl`)

lazy val `broker-scaladsl` = (project in file("service/scaladsl/broker"))
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-scaladsl-broker",
    Dependencies.`broker-scaladsl`
  )
  .settings(runtimeLibCommon: _*)
  .settings(mimaSettings(since13): _*)
  .dependsOn(`api-scaladsl`, `persistence-scaladsl`)

lazy val `kafka-client` = (project in file("service/core/kafka/client"))
  .enablePlugins(RuntimeLibPlugins)
  .settings(runtimeLibCommon: _*)
  .settings(forkedTests: _*)
  .settings(
    name := "lagom-kafka-client",
    Dependencies.`kafka-client`
  )
  .dependsOn(`api`)

lazy val `kafka-client-javadsl` = (project in file("service/javadsl/kafka/client"))
  .enablePlugins(RuntimeLibPlugins)
  .settings(runtimeLibCommon: _*)
  .settings(mimaSettings(since12): _*)
  .settings(
    name := "lagom-javadsl-kafka-client",
    Dependencies.`kafka-client-javadsl`,
    mimaBinaryIssueFilters ++= Seq(
      ProblemFilters.exclude[MissingTypesProblem]("com.lightbend.lagom.javadsl.broker.kafka.KafkaTopicFactory"),
      // Needed to add service locator to Kafka topic factory, which required changing
      // the public constructor. Since this will generally only ever be invoked by Guice,
      // there should be no bin compat problem here.
      ProblemFilters.exclude[DirectMissingMethodProblem]("com.lightbend.lagom.javadsl.broker.kafka.KafkaTopicFactory.this")
    )
  )
  .dependsOn(`api-javadsl`, `kafka-client`)

lazy val `kafka-client-scaladsl` = (project in file("service/scaladsl/kafka/client"))
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-scaladsl-kafka-client",
    Dependencies.`kafka-client-scaladsl`
  )
  .settings(runtimeLibCommon: _*)
  .settings(mimaSettings(since13): _*)
  .dependsOn(`api-scaladsl`, `kafka-client`)

lazy val `kafka-broker` = (project in file("service/core/kafka/server"))
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-kafka-broker",
    Dependencies.`kafka-broker`
  )
  .settings(runtimeLibCommon: _*)
  .dependsOn(`api`, `persistence-core`, `kafka-client`)

lazy val `kafka-broker-javadsl` = (project in file("service/javadsl/kafka/server"))
  .enablePlugins(RuntimeLibPlugins)
  .settings(runtimeLibCommon: _*)
  .settings(mimaSettings(since12): _*)
  .settings(forkedTests: _*)
  .settings(
    name := "lagom-javadsl-kafka-broker",
    Dependencies.`kafka-broker-javadsl`,
    Dependencies.dependencyWhitelist ++= Dependencies.KafkaTestWhitelist
  )
  .dependsOn(
    `broker-javadsl`,
    `kafka-broker`,
    `kafka-client-javadsl`,
    `server-javadsl`,
    `kafka-server` % Test,
    logback % Test
  )

lazy val `kafka-broker-scaladsl` = (project in file("service/scaladsl/kafka/server"))
  .enablePlugins(RuntimeLibPlugins)
  .settings(runtimeLibCommon: _*)
  .settings(mimaSettings(since13): _*)
  .settings(forkedTests: _*)
  .settings(
    name := "lagom-scaladsl-kafka-broker",
    Dependencies.`kafka-broker-scaladsl`,
    Dependencies.dependencyWhitelist ++= Dependencies.KafkaTestWhitelist
  )
  .dependsOn(
    `broker-scaladsl`,
    `kafka-broker`,
    `kafka-client-scaladsl`,
    `server-scaladsl`,
    `kafka-server` % Test,
    logback % Test
  )

lazy val logback = (project in file("logback"))
  .enablePlugins(RuntimeLibPlugins)
  .settings(runtimeLibCommon: _*)
  .settings(
    name := "lagom-logback",
    Dependencies.logback
  )

lazy val log4j2 = (project in file("log4j2"))
  .enablePlugins(RuntimeLibPlugins)
  .settings(runtimeLibCommon: _*)
  .settings(
    name := "lagom-log4j2",
    Dependencies.log4j2
  )

lazy val devEnvironmentProjects = Seq[Project](
  `reloadable-server`,
  `build-tool-support`,
  `sbt-plugin`,
  `maven-plugin`,
  `service-locator`,
  `service-registration-javadsl`,
  `cassandra-server`,
  `play-integration-javadsl`,
  `devmode-scaladsl`,
  `service-registry-client-javadsl`,
  `maven-java-archetype`,
  `maven-dependencies`,
  `kafka-server`
)

lazy val `dev-environment` = (project in file("dev"))
  .settings(name := "lagom-dev")
  .settings(common: _*)
  .enablePlugins(AutomateHeaderPlugin)
  .aggregate(devEnvironmentProjects.map(Project.projectToRef): _*)
  .settings(
    publish := {},
    PgpKeys.publishSigned := {}
  )

lazy val `reloadable-server` = (project in file("dev") / "reloadable-server")
  .settings(runtimeLibCommon: _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-reloadable-server",
    Dependencies.`reloadable-server`
  )

lazy val `build-tool-support` = (project in file("dev") / "build-tool-support")
  .settings(common: _*)
  .settings(
    name := "lagom-build-tool-support",
    publishMavenStyle := true,
    crossScalaVersions := Dependencies.SbtScalaVersions,
    scalaVersion := Dependencies.SbtScalaVersions.head,
    crossPaths := false,
    sourceGenerators in Compile += Def.task {
      Generators.version(version.value, (sourceManaged in Compile).value)
    }.taskValue,
    Dependencies.`build-tool-support`
  )

lazy val `sbt-plugin` = (project in file("dev") / "sbt-plugin")
  .settings(common: _*)
  .settings(scriptedSettings: _*)
  .enablePlugins(SbtPluginPlugins)
  .settings(
    name := "lagom-sbt-plugin",
    sbtPlugin := true,
    crossScalaVersions := Dependencies.SbtScalaVersions,
    scalaVersion := Dependencies.SbtScalaVersions.head,
    Dependencies.`sbt-plugin`,
    addSbtPlugin(
      ("com.typesafe.play" % "sbt-plugin" % Dependencies.PlayVersion)
        .exclude("org.slf4j", "slf4j-simple")),
    scriptedDependencies := {
      val () = scriptedDependencies.value
      val () = publishLocal.value
      val () = (publishLocal in `service-locator`).value
      val () = (publishLocal in LocalProject("sbt-scripted-tools")).value
      val () = (publishLocal in `sbt-scripted-library`).value
    },
    publishTo := {
      if (isSnapshot.value) {
        // Bintray doesn't support publishing snapshots, publish to Sonatype snapshots instead
        Some(Opts.resolver.sonatypeSnapshots)
      } else publishTo.value
    },
    publishMavenStyle := isSnapshot.value
  ).dependsOn(`build-tool-support`)

lazy val `maven-plugin` = (project in file("dev") / "maven-plugin")
  .enablePlugins(lagom.SbtMavenPlugin)
  .settings(common: _*)
  .settings(
    name := "Lagom Maven Plugin",
    description := "Provides Lagom development environment support to maven.",
    Dependencies.`maven-plugin`,
    publishMavenStyle := true,
    crossPaths := false,
    mavenClasspath := (externalDependencyClasspath in (`maven-launcher`, Compile)).value.map(_.data),
    mavenTestArgs := Seq(
      "-Xmx768m",
      "-XX:MaxMetaspaceSize=384m",
      s"-Dlagom.version=${version.value}",
      s"-DarchetypeVersion=${version.value}",
      s"-Dplay.version=${Dependencies.PlayVersion}",
      s"-Dakka.version=${Dependencies.AkkaVersion}",
      s"-Dscala.binary.version=${(scalaBinaryVersion in `api-javadsl`).value}",
      "-Dorg.slf4j.simpleLogger.showLogName=false",
      "-Dorg.slf4j.simpleLogger.showThreadName=false"
    )
  ).dependsOn(`build-tool-support`)

lazy val `maven-launcher` = (project in file("dev") / "maven-launcher")
    .settings(
      name := "lagom-maven-launcher",
      description := "Dummy project, exists only to resolve the maven launcher classpath",
      EclipseKeys.projectFlavor := EclipseProjectFlavor.Java,
      Dependencies.`maven-launcher`
    )

def scriptedSettings: Seq[Setting[_]] = ScriptedPlugin.scriptedSettings ++
  Seq(scriptedLaunchOpts += s"-Dproject.version=${version.value}") ++
  Seq(
    scripted := scripted.tag(Tags.Test).evaluated,
    scriptedLaunchOpts ++= Seq(
      "-Xmx768m",
      "-XX:MaxMetaspaceSize=384m",
      "-Dscala.version=" + sys.props.get("scripted.scala.version").getOrElse((scalaVersion in `reloadable-server`).value)
    )
  )

def archetypeVariables(lagomVersion: String) = Map(
  "LAGOM-VERSION" -> lagomVersion
)

val ArchetypeVariablePattern = "%([A-Z-]+)%".r

def archetypeProject(archetypeName: String) =
  Project(s"maven-$archetypeName-archetype", file("dev") / "archetypes" / s"maven-$archetypeName")
    .settings(common: _*)
    .settings(
      name := s"maven-archetype-lagom-$archetypeName",
      autoScalaLibrary := false,
      publishMavenStyle := true,
      crossPaths := false,
      copyResources in Compile := {
        val pomFile = (classDirectory in Compile).value / "archetype-resources" / "pom.xml"
        if (pomFile.exists()) {
          val pomXml = IO.read(pomFile)
          val variables = archetypeVariables(version.value)
          val newPomXml = ArchetypeVariablePattern.replaceAllIn(pomXml, m =>
            variables.get(m.group(1)) match {
              case Some(replacement) => replacement
              case None => m.matched
            }
          )
          IO.write(pomFile, newPomXml)
        }
        (copyResources in Compile).value
      },
      unmanagedResources in Compile := {
        val gitIgnoreFiles = (unmanagedResourceDirectories in Compile).value flatMap { dirs =>
          ( dirs ** (".gitignore") ).get
        }
        (unmanagedResources in Compile).value ++ gitIgnoreFiles
      },
      // Don't force copyright headers in Maven archetypes
      HeaderKey.excludes := Seq("*")
    ).disablePlugins(EclipsePlugin)

lazy val `maven-java-archetype` = archetypeProject("java")
lazy val `maven-dependencies` = (project in file("dev") / "maven-dependencies")
    .settings(common: _*)
    .settings(
      name := "lagom-maven-dependencies",
      crossPaths := false,
      autoScalaLibrary := false,
      scalaVersion := Dependencies.ScalaVersions.head,
      pomExtra := pomExtra.value :+ {
        val lagomDeps = Def.settingDyn {
          val sv = scalaVersion.value
          val sbv = scalaBinaryVersion.value
          (javadslProjects ++ coreProjects).map { project =>
            Def.setting {
              val cross = CrossVersion((crossVersion in project).value, sv, sbv)
              val artifactName = (artifact in project).value.name
              val artifactId = cross.fold(artifactName)(_(artifactName))
              <dependency>
                <groupId>{(organization in project).value}</groupId>
                <artifactId>{artifactId}</artifactId>
                <version>{(version in project).value}</version>
              </dependency>
            }
          }.join
        }.value

        <dependencyManagement>
          <dependencies>
            {lagomDeps}
            {Dependencies.DependencyWhitelist.value.map { dep =>
              val crossDep = CrossVersion(scalaVersion.value, scalaBinaryVersion.value)(dep)
              <dependency>
                <groupId>{crossDep.organization}</groupId>
                <artifactId>{crossDep.name}</artifactId>
                <version>{crossDep.revision}</version>
              </dependency>
            }}
          </dependencies>
        </dependencyManagement>
      }
    ).settings(
      // This disables creating jar, source jar and javadocs, and will cause the packaging type to be "pom" when the
      // pom is created
      Classpaths.defaultPackageKeys.map(key => publishArtifact in key := false): _*
    )

// This project doesn't get aggregated, it is only executed by the sbt-plugin scripted dependencies
lazy val `sbt-scripted-tools` = (project in file("dev") / "sbt-scripted-tools")
  .settings(name := "lagom-sbt-scripted-tools")
  .settings(common: _*)
  .settings(
    sbtPlugin := true,
    crossScalaVersions := Dependencies.SbtScalaVersions,
    scalaVersion := Dependencies.SbtScalaVersions.head
  ).dependsOn(`sbt-plugin`)

// This project also get aggregated, it is only executed by the sbt-plugin scripted dependencies
lazy val `sbt-scripted-library` = (project in file("dev") / "sbt-scripted-library")
  .settings(name := "lagom-sbt-scripted-library")
  .settings(runtimeLibCommon: _*)
  .dependsOn(`server-javadsl`)

lazy val `service-locator` = (project in file("dev") / "service-registry" / "service-locator")
  .settings(runtimeLibCommon: _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-service-locator",
    Dependencies.`service-locator`,
    // Need to ensure that the service locator uses the Lagom dependency management
    pomExtra := pomExtra.value :+ {
      <dependencyManagement>
        <dependencies>
          <dependency>
            <groupId>{organization.value}</groupId>
            <artifactId>lagom-maven-dependencies</artifactId>
            <version>{version.value}</version>
            <scope>import</scope>
            <type>pom</type>
          </dependency>
        </dependencies>
      </dependencyManagement>
    }
  )
  .dependsOn(`server-javadsl`, logback, `service-registry-client-javadsl`)

lazy val `service-registry-client-javadsl` = (project in file("dev") / "service-registry" / "client-javadsl")
  .settings(
    name := "lagom-service-registry-client",
    Dependencies.`service-registry-client-javadsl`
  )
  .settings(runtimeLibCommon: _*)
  .enablePlugins(RuntimeLibPlugins)
  .dependsOn(`client-javadsl`, immutables % "provided")

lazy val `service-registration-javadsl` = (project in file("dev") / "service-registry" / "registration-javadsl")
  .settings(
    name := "lagom-service-registration",
    Dependencies.`service-registration-javadsl`
  )
  .settings(runtimeLibCommon: _*)
  .enablePlugins(RuntimeLibPlugins)
  .dependsOn(`server-javadsl`, `service-registry-client-javadsl`)

lazy val `devmode-scaladsl` = (project in file("dev") / "service-registry" / "devmode-scaladsl")
  .settings(
    name := "lagom-scaladsl-dev-mode",
    Dependencies.`devmode-scaladsl`
  )
  .settings(runtimeLibCommon: _*)
  .settings(mimaSettings(since13): _*)
  .enablePlugins(RuntimeLibPlugins)
  .dependsOn(`client-scaladsl`)

lazy val `play-integration-javadsl` = (project in file("dev") / "service-registry" / "play-integration-javadsl")
  .settings(
    name := "lagom-javadsl-play-integration",
    Dependencies.`play-integration-javadsl`
  )
  .settings(runtimeLibCommon: _*)
  .enablePlugins(RuntimeLibPlugins)
  .dependsOn(`service-registry-client-javadsl`)

lazy val `cassandra-server` = (project in file("dev") / "cassandra-server")
  .settings(common: _*)
  .settings(runtimeScalaSettings: _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-cassandra-server",
    Dependencies.`cassandra-server`
  )

lazy val `kafka-server` = (project in file("dev") / "kafka-server")
  .settings(common: _*)
  .settings(runtimeScalaSettings: _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-kafka-server",
    Dependencies.`kafka-server`
  )

// Provides macros for testing macros. Is not published.
lazy val `macro-testkit` = (project in file("macro-testkit"))
  .settings(runtimeLibCommon)
  .settings(
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-reflect" % scalaVersion.value
    ),
    PgpKeys.publishSigned := {},
    publish := {}
  )

// We can't just run a big aggregated mimaReportBinaryIssues due to
// https://github.com/typesafehub/migration-manager/issues/163
// Travis doesn't provide us enough memory to do so. So instead, we
// run the binary compatibility checks one at a time, which works
// around the issue.
commands += Command.command("mimaCheckOneAtATime") { state =>
  val extracted = Project.extract(state)
  val results = (javadslProjects ++ scaladslProjects).map { project =>
    println(s"Checking binary compatibility for ${project.id}")
    try {
      extracted.runTask(mimaReportBinaryIssues in project, state)
      true
    } catch {
      case scala.util.control.NonFatal(e) => false
    }
  }

  if (results.contains(false)) {
    throw new FeedbackProvidedException {}
  }
  state
}
