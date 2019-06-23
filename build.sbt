import java.net.InetSocketAddress
import java.nio.channels.ServerSocketChannel

import com.typesafe.sbt.SbtMultiJvm
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys._
import com.typesafe.tools.mima.core._
import lagom.Protobuf
import lagom.build._

// Turn off "Resolving" log messages that clutter build logs
ivyLoggingLevel in ThisBuild := UpdateLogging.Quiet

def defineSbtVersion(scalaBinVer: String): String = scalaBinVer match {
  case "2.12" => "1.2.8"
  case _      => "0.13.18"
}

def evictionSettings: Seq[Setting[_]] = Seq(
  // This avoids a lot of dependency resolution warnings to be showed.
  // They are not required in Lagom since we have a more strict whitelist
  // of which dependencies are allowed. So it should be safe to not have
  // the build logs polluted with evictions warnings.
  evictionWarningOptions in update := EvictionWarningOptions.default
    .withWarnTransitiveEvictions(false)
    .withWarnDirectEvictions(false)
)

def overridesScalaParserCombinators = Seq(
  dependencyOverrides ++= Dependencies.scalaParserCombinatorOverrides
)

def common: Seq[Setting[_]] = releaseSettings ++ bintraySettings ++ evictionSettings ++ Seq(
  organization := "com.lightbend.lagom",
  // Must be "Apache-2.0", because bintray requires that it is a license that it knows about
  licenses := Seq(("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0.html"))),
  homepage := Some(url("https://www.lagomframework.com/")),
  sonatypeProfileName := "com.lightbend",
  headerLicense := Some(
    HeaderLicense.Custom(
      // When updating, keep in sync with docs/build.sbt configuration
      "Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>"
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
  pomIncludeRepository := { _ =>
    false
  },
  concurrentRestrictions in Global += Tags.limit(Tags.Test, 1),
  scalacOptions in (Compile, doc) ++= (scalaBinaryVersion.value match {
    case "2.12" => Seq("-no-java-comments")
    case _      => Seq.empty
  }),
  // Setting javac options in common allows IntelliJ IDEA to import them automatically
  javacOptions in compile ++= Seq(
    "-encoding",
    "UTF-8",
    "-source",
    "1.8",
    "-target",
    "1.8",
    "-parameters",
    "-Xlint:unchecked",
    "-Xlint:deprecation"
  ),
  LagomPublish.validatePublishSettingsSetting
)

def bintraySettings: Seq[Setting[_]] = Seq(
  bintrayOrganization := Some("lagom"),
  bintrayRepository := "sbt-plugin-releases",
  bintrayPackage := "lagom-sbt-plugin",
  bintrayReleaseOnPublish := false
)

// Customise sbt-dynver's behaviour to make it work with Lagom's tags (which aren't v-prefixed)
dynverVTagPrefix in ThisBuild := false

// Sanity-check: assert that version comes from a tag (e.g. not a too-shallow clone)
// https://github.com/dwijnand/sbt-dynver/#sanity-checking-the-version
Global / onLoad := (Global / onLoad).value.andThen { s =>
  val v = version.value
  if (dynverGitDescribeOutput.value.hasNoTags)
    throw new MessageOnlyException(
      s"Failed to derive version from git tags. Maybe run `git fetch --unshallow`? Version: $v"
    )
  s
}

def releaseSettings: Seq[Setting[_]] = Seq(
  releasePublishArtifactsAction := PgpKeys.publishSigned.value,
  releaseTagName := (version in ThisBuild).value,
  releaseProcess := {
    import ReleaseTransformations._

    Seq[ReleaseStep](
      checkSnapshotDependencies,
      releaseStepCommandAndRemaining("+publishSigned"),
      releaseStepTask(bintrayRelease in thisProjectRef.value),
      releaseStepCommand("sonatypeRelease"),
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
      case Left(msg)  => throw sys.error(s"Invalid programmatic input:\n$msg")
    }
    if (newState.remainingCommands.isEmpty) {
      newState
    } else {
      runCommand(
        newState.remainingCommands.head.commandLine,
        newState.copy(remainingCommands = newState.remainingCommands.tail)
      )
    }
  }

  runCommand(command, originalState.copy(remainingCommands = Nil)).copy(remainingCommands = originalRemaining)
}

def sonatypeSettings: Seq[Setting[_]] = Seq(
  publishTo := sonatypePublishTo.value
)

def runtimeScalaSettings: Seq[Setting[_]] = Seq(
  crossScalaVersions := Dependencies.Versions.Scala,
  scalaVersion := Dependencies.Versions.Scala.head,
  // compile options
  scalacOptions in Compile ++= Seq(
    "-encoding",
    "UTF-8",
    "-target:jvm-1.8",
    "-feature",
    "-unchecked",
    "-Xlog-reflective-calls",
    "-deprecation"
  )
)

def runtimeLibCommon: Seq[Setting[_]] = common ++ sonatypeSettings ++ runtimeScalaSettings ++ Seq(
  Dependencies.validateDependenciesSetting,
  Dependencies.pruneWhitelistSetting,
  Dependencies.dependencyWhitelistSetting,
  // show full stack traces and test case durations
  testOptions in Test += Tests.Argument("-oDF"),
  // -v Log "test run started" / "test started" / "test run finished" events on log level "info" instead of "debug".
  // -a Show stack traces and exception class name for AssertionErrors.
  testOptions += Tests.Argument(TestFrameworks.JUnit, "-v", "-a")
)

val defaultMultiJvmOptions: List[String] = {
  import scala.collection.JavaConverters._
  // multinode.D= and multinode.X= makes it possible to pass arbitrary
  // -D or -X arguments to the forked jvm, e.g.
  // -Djava.net.preferIPv4Stack=true or -Dmultinode.Xmx512m
  val MultinodeJvmArgs = "multinode\\.(D|X)(.*)".r
  val knownPrefix      = Set("akka.", "lagom.")
  val properties = System.getProperties.stringPropertyNames.asScala.toList.collect {
    case MultinodeJvmArgs(a, b) =>
      val value = System.getProperty("multinode." + a + b)
      "-" + a + b + (if (value == "") "" else "=" + value)
    case key if knownPrefix.exists(pre => key.startsWith(pre)) => "-D" + key + "=" + System.getProperty(key)
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
    headerSettings(MultiJvm) ++
    automateHeaderSettings(MultiJvm) ++
    Seq(
      parallelExecution in Test := false,
      MultiJvmKeys.jvmOptions in MultiJvm := databasePortSetting :: defaultMultiJvmOptions,
      // make sure that MultiJvm test are compiled by the default test compilation
      compile in MultiJvm := (compile in MultiJvm).triggeredBy(compile in Test).value,
      // tag MultiJvm tests so that we can use concurrentRestrictions to disable parallel tests
      executeTests in MultiJvm := (executeTests in MultiJvm).tag(Tags.Test).value,
      // make sure that MultiJvm tests are executed by the default test target,
      // and combine the results from ordinary test and multi-jvm tests
      executeTests in Test := {
        val testResults      = (executeTests in Test).value
        val multiNodeResults = (executeTests in MultiJvm).value
        import TestResult.Error
        import TestResult.Failed
        import TestResult.Passed
        val overall = (testResults.overall, multiNodeResults.overall) match {
          case (Passed, Passed)                    => Passed
          case (Failed, Failed)                    => Failed
          case (Error, Error)                      => Error
          case (Passed, Failed) | (Failed, Passed) => Failed
          case (Passed, Error) | (Error, Passed)   => Error
          case (Failed, Error) | (Error, Failed)   => Error
        }
        Tests.Output(
          overall,
          testResults.events ++ multiNodeResults.events,
          testResults.summaries ++ multiNodeResults.summaries
        )
      },
      // change multi-jvm lib folder to reflect the scala version used during crossbuild
      multiRunCopiedClassLocation in MultiJvm := crossbuildMultiJvm.value
    )
}

def macroCompileSettings: Seq[Setting[_]] = Seq(
  compile in Test ~= { a =>
    // Delete classes in "compile" packages after compiling.
    // These are used for compile-time tests and should be recompiled every time.
    val products = (a.asInstanceOf[sbt.internal.inc.Analysis]).relations.allProducts.toSeq ** new SimpleFileFilter(
      _.getParentFile.getName == "compile"
    )
    IO.delete(products.get)
    a
  }
)

val version150 = "1.5.0"
val version151 = "1.5.1"

def mimaSettings(since: String): Seq[Setting[_]] = {
  val versions = Seq(since)
  Seq(
    mimaPreviousArtifacts := {
      scalaVersionFilter(scalaBinaryVersion.value, versions).map { version =>
        organization.value % s"${moduleName.value}_${scalaBinaryVersion.value}" % version
      }.toSet
    },
    mimaBinaryIssueFilters ++= Seq(
      ProblemFilters.exclude[Problem]("com.lightbend.lagom.internal.*"),
      ProblemFilters.exclude[Problem]("com.lightbend.lagom.*Components*"),
      ProblemFilters.exclude[Problem]("com.lightbend.lagom.*Module*"),
      ProblemFilters
        .exclude[DirectMissingMethodProblem]("*lagom.*dsl.persistence.PersistentEntityRegistry.gracefulShutdown"),
      // Remove APIs deprecated in Lagom 1.3.x: https://github.com/lagom/lagom/pull/1967
      ProblemFilters
        .exclude[DirectMissingMethodProblem]("com.lightbend.lagom.javadsl.api.ServiceInfo.getLocatableServices"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("com.lightbend.lagom.javadsl.api.ServiceInfo.this"),
      ProblemFilters.exclude[IncompatibleResultTypeProblem](
        "com.lightbend.lagom.scaladsl.api.ServiceInfo#ServiceInfoImpl.copy$default$2"
      ),
      ProblemFilters
        .exclude[IncompatibleMethTypeProblem]("com.lightbend.lagom.scaladsl.api.ServiceInfo#ServiceInfoImpl.copy"),
      ProblemFilters.exclude[DirectMissingMethodProblem](
        "com.lightbend.lagom.scaladsl.api.ServiceInfo#ServiceInfoImpl.locatableServices"
      ),
      ProblemFilters
        .exclude[IncompatibleMethTypeProblem]("com.lightbend.lagom.scaladsl.api.ServiceInfo#ServiceInfoImpl.this"),
      ProblemFilters
        .exclude[DirectMissingMethodProblem]("com.lightbend.lagom.scaladsl.api.ServiceInfo.locatableServices"),
      ProblemFilters
        .exclude[IncompatibleMethTypeProblem]("com.lightbend.lagom.scaladsl.api.ServiceInfo#ServiceInfoImpl.apply"),
      ProblemFilters.exclude[DirectMissingMethodProblem](
        "com.lightbend.lagom.scaladsl.server.LagomApplicationLoader.describeServices"
      ),
      ProblemFilters.exclude[DirectMissingMethodProblem]("com.lightbend.lagom.scaladsl.server.LagomServer.forServices"),
      ProblemFilters
        .exclude[DirectMissingMethodProblem]("com.lightbend.lagom.scaladsl.server.LagomServer.serviceBindings"),
      ProblemFilters.exclude[ReversedMissingMethodProblem](
        "com.lightbend.lagom.scaladsl.server.LagomServer.serviceBinding"
      ),
      // Remove APIs deprecated in Lagom 1.4.x: https://github.com/lagom/lagom/pull/1987
      ProblemFilters
        .exclude[IncompatibleMethTypeProblem]("com.lightbend.lagom.scaladsl.api.AdditionalConfiguration.++"),
      ProblemFilters
        .exclude[IncompatibleMethTypeProblem]("com.lightbend.lagom.scaladsl.api.AdditionalConfiguration.this"),
      ProblemFilters
        .exclude[IncompatibleMethTypeProblem]("com.lightbend.lagom.scaladsl.client.ConfigurationServiceLocator.this"),
      ProblemFilters
        .exclude[IncompatibleMethTypeProblem]("com.lightbend.lagom.scaladsl.client.CircuitBreakingServiceLocator.this"),
      ProblemFilters
        .exclude[IncompatibleMethTypeProblem]("com.lightbend.lagom.scaladsl.client.RoundRobinServiceLocator.this"),
      ProblemFilters
        .exclude[IncompatibleMethTypeProblem]("com.lightbend.lagom.scaladsl.client.StaticServiceLocator.this"),
      ProblemFilters
        .exclude[IncompatibleMethTypeProblem]("com.lightbend.lagom.javadsl.client.CircuitBreakingServiceLocator.this"),
      ProblemFilters
        .exclude[MissingClassProblem]("com.lightbend.lagom.scaladsl.persistence.cassandra.CassandraContactPoint"),
      ProblemFilters.exclude[MissingClassProblem]("com.lightbend.lagom.scaladsl.persistence.cassandra.CassandraConfig"),
      ProblemFilters
        .exclude[MissingClassProblem]("com.lightbend.lagom.scaladsl.persistence.cassandra.CassandraContactPoint$"),
      ProblemFilters.exclude[MissingClassProblem]("com.lightbend.lagom.javadsl.persistence.cassandra.CassandraConfig"),
      ProblemFilters
        .exclude[MissingClassProblem]("com.lightbend.lagom.javadsl.persistence.cassandra.CassandraContactPoint"),
    )
  )
}

def scalaVersionFilter(scalaBinaryVersion: String, versions: Seq[String]): Seq[String] = {
  // parse version into (major, minor, patch) for comparison
  def Version(version: String): (String, String, String) = version.split(".", 3).toSeq match {
    case Seq(major, minor, patch) => (major, minor, patch)
    case _                        => throw new IllegalArgumentException("version does not match major.minor.patch format")
  }
  import scala.math.Ordering.Implicits._
  val sinceVersion = Version(scalaVersionSince.getOrElse(scalaBinaryVersion, "1.0.0"))
  versions.filter(Version(_) >= sinceVersion)
}

def scalaVersionSince = Map(
  "2.12" -> "1.4.0"
)

val javadslProjects = Seq[Project](
  `api-javadsl`,
  `server-javadsl`,
  `client-javadsl`,
  `broker-javadsl`,
  `kafka-client-javadsl`,
  `kafka-broker-javadsl`,
  `akka-management-javadsl`,
  `akka-discovery-service-locator-javadsl`,
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
  `akka-management-scaladsl`,
  `akka-discovery-service-locator-scaladsl`,
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
  `akka-management-core`,
  `akka-discovery-service-locator-core`,
  `cluster-core`,
  `kafka-client`,
  `kafka-broker`,
  `persistence-core`,
  `persistence-testkit`,
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

val sbtScriptedProjects = Seq[Project](
  `sbt-scripted-tools`,
  `sbt-scripted-library`
)

lazy val root = (project in file("."))
  .settings(name := "lagom")
  .settings(runtimeLibCommon: _*)
  .settings(
    crossScalaVersions := Nil,
    scalaVersion := Dependencies.Versions.Scala.head,
    PgpKeys.publishSigned := {},
    publishLocal := {},
    publishArtifact in Compile := false,
    publish := {}
  )
  .enablePlugins(lagom.UnidocRoot)
  .settings(
    UnidocRoot.settings(javadslProjects.map(Project.projectToRef), scaladslProjects.map(Project.projectToRef)): _*
  )
  .aggregate(
    (javadslProjects ++ scaladslProjects ++ coreProjects ++ otherProjects ++ sbtScriptedProjects)
      .map(Project.projectToRef): _*
  )

def RuntimeLibPlugins = AutomateHeaderPlugin && Sonatype && PluginsAccessor.exclude(BintrayPlugin) && Unidoc
def SbtPluginPlugins  = AutomateHeaderPlugin && BintrayPlugin && PluginsAccessor.exclude(Sonatype)

lazy val api = (project in file("service/core/api"))
  .settings(runtimeLibCommon: _*)
  .settings(mimaSettings(since = version150): _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-api",
    Dependencies.api
  )

lazy val `api-javadsl` = (project in file("service/javadsl/api"))
  .settings(name := "lagom-javadsl-api")
  .settings(runtimeLibCommon: _*)
  .settings(mimaSettings(since = version150): _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    Dependencies.`api-javadsl`
  )
  .dependsOn(api)

lazy val `api-scaladsl` = (project in file("service/scaladsl/api"))
  .settings(name := "lagom-scaladsl-api")
  .settings(runtimeLibCommon: _*)
  .settings(mimaSettings(since = version150): _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    Dependencies.`api-scaladsl`
  )
  .dependsOn(api)

lazy val immutables = (project in file("immutables"))
  .settings(name := "lagom-javadsl-immutables")
  .settings(runtimeLibCommon: _*)
  .settings(mimaSettings(since = version150): _*)
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
  .settings(mimaSettings(since = version150): _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(Dependencies.jackson)
  .dependsOn(`api-javadsl`, immutables % "test->compile")

lazy val `play-json` = (project in file("play-json"))
  .settings(runtimeLibCommon: _*)
  .settings(mimaSettings(since = version150): _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-scaladsl-play-json",
    Dependencies.`play-json`
  )

lazy val `api-tools` = (project in file("api-tools"))
  .settings(runtimeLibCommon: _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    Dependencies.`api-tools`
  )
  .settings(overridesScalaParserCombinators: _*)
  .dependsOn(
    spi,
    `server-javadsl`  % Test,
    `server-scaladsl` % Test
  )

lazy val client = (project in file("service/core/client"))
  .settings(runtimeLibCommon: _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-client",
    Dependencies.client
  )
  .dependsOn(api, spi)

lazy val `client-javadsl` = (project in file("service/javadsl/client"))
  .settings(runtimeLibCommon: _*)
  .settings(mimaSettings(since = version150): _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-javadsl-client",
    Dependencies.`client-javadsl`
  )
  .dependsOn(client, `api-javadsl`, jackson)

lazy val `client-scaladsl` = (project in file("service/scaladsl/client"))
  .settings(runtimeLibCommon: _*)
  .settings(mimaSettings(since = version150): _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(macroCompileSettings: _*)
  .settings(
    name := "lagom-scaladsl-client",
    Dependencies.`client-scaladsl`
  )
  .dependsOn(client, `api-scaladsl`, `macro-testkit` % Test)

lazy val `integration-client-javadsl` = (project in file("service/javadsl/integration-client"))
  .settings(
    name := "lagom-javadsl-integration-client",
    Dependencies.`integration-client-javadsl`
  )
  .settings(runtimeLibCommon: _*)
  .settings(mimaSettings(since = version150): _*)
  .enablePlugins(RuntimeLibPlugins)
  .dependsOn(`client-javadsl`, `service-registry-client-javadsl`, `kafka-client-javadsl`)

lazy val server = (project in file("service/core/server"))
  .settings(
    name := "lagom-server",
    Dependencies.server
  )
  .enablePlugins(RuntimeLibPlugins)
  .settings(runtimeLibCommon: _*)
  .settings(mimaSettings(since = version150): _*)
  .dependsOn(client)

lazy val `server-javadsl` = (project in file("service/javadsl/server"))
  .settings(
    name := "lagom-javadsl-server",
    Dependencies.`server-javadsl`
  )
  .enablePlugins(RuntimeLibPlugins)
  .settings(runtimeLibCommon: _*)
  .settings(mimaSettings(since = version150): _*)
  .dependsOn(`akka-management-javadsl`, server, `client-javadsl`, immutables % "provided")
  // bring jackson closer to the root of the dependency tree to prompt Maven to choose the right version
  .dependsOn(jackson)

lazy val `server-scaladsl` = (project in file("service/scaladsl/server"))
  .settings(
    name := "lagom-scaladsl-server",
    Dependencies.`server-scaladsl`
  )
  .enablePlugins(RuntimeLibPlugins)
  .settings(runtimeLibCommon: _*)
  .settings(mimaSettings(since = version150): _*)
  .dependsOn(`akka-management-scaladsl`, server, `client-scaladsl`, `play-json`)

lazy val `testkit-core` = (project in file("testkit/core"))
  .settings(runtimeLibCommon: _*)
  .settings(mimaSettings(since = version150): _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-core-testkit",
    Dependencies.`testkit-core`
  )
  .settings(overridesScalaParserCombinators: _*)
  .settings(forkedTests: _*)
  .dependsOn(
    `dev-mode-ssl-support`, // TODO: remove this when SSLContext provider is promoted to play or ssl-config
    // Ideally, this would be the other way around,
    // but it will require some more refactoring
    `persistence-testkit`
  )

lazy val `testkit-javadsl` = (project in file("testkit/javadsl"))
  .settings(runtimeLibCommon: _*)
  .settings(mimaSettings(since = version150): _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(forkedTests: _*)
  .settings(
    name := "lagom-javadsl-testkit",
    Dependencies.`testkit-javadsl`
  )
  .settings(overridesScalaParserCombinators: _*)
  .dependsOn(
    `testkit-core`,
    `server-javadsl`,
    `pubsub-javadsl`,
    `broker-javadsl`,
    `dev-mode-ssl-support`, // TODO: remove this when SSLContext provider is promoted to play or ssl-config
    `persistence-core`              % "compile;test->test",
    `persistence-cassandra-javadsl` % "test->test",
    `jackson`                       % "test->test",
    `persistence-jdbc-javadsl`      % Test
  )

lazy val `testkit-scaladsl` = (project in file("testkit/scaladsl"))
  .settings(runtimeLibCommon: _*)
  .settings(mimaSettings(since = version150): _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(forkedTests: _*)
  .settings(overridesScalaParserCombinators: _*)
  .settings(
    name := "lagom-scaladsl-testkit",
    Dependencies.`testkit-scaladsl`
  )
  .dependsOn(
    `testkit-core`,
    `server-scaladsl`,
    `broker-scaladsl`,
    `kafka-broker-scaladsl`,
    `dev-mode-ssl-support`, // TODO: remove this when SSLContext provider is promoted to play or ssl-config
    `persistence-core`               % "compile;test->test",
    `persistence-scaladsl`           % "compile;test->test",
    `persistence-cassandra-scaladsl` % "compile;test->test",
    `persistence-jdbc-scaladsl`      % Test
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
  val javaOptions = Vector("-Xms256M", "-Xmx512M")
  tests.map { test =>
    Tests.Group(
      name = test.name,
      tests = Seq(test),
      runPolicy = Tests.SubProcess(ForkOptions().withRunJVMOptions(javaOptions))
    )
  }
}

lazy val `akka-discovery-service-locator-core` = (project in file("akka-service-locator/core"))
  .settings(runtimeLibCommon: _*)
  .settings(mimaSettings(since = version151): _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-akka-discovery-service-locator-core",
    Dependencies.`lagom-akka-discovery-service-locator-core`
  )

lazy val `akka-discovery-service-locator-javadsl` = (project in file("akka-service-locator/javadsl"))
  .dependsOn(`akka-discovery-service-locator-core`)
  .dependsOn(`client-javadsl`)
  .settings(runtimeLibCommon: _*)
  .settings(mimaSettings(since = version151): _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-javadsl-akka-discovery-service-locator"
  )

lazy val `akka-discovery-service-locator-scaladsl` = (project in file("akka-service-locator/scaladsl"))
  .dependsOn(`akka-discovery-service-locator-core`)
  .dependsOn(`client-scaladsl`)
  .settings(runtimeLibCommon: _*)
  .settings(mimaSettings(since = version151): _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-scaladsl-akka-discovery-service-locator",
    Dependencies.`lagom-akka-discovery-service-locator-scaladsl`
  )
  .dependsOn(`testkit-scaladsl`)

lazy val `akka-management-core` = (project in file("akka-management/core"))
  .settings(runtimeLibCommon: _*)
  .settings(mimaSettings(since = version150): _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-akka-management-core",
    Dependencies.`akka-management-core`
  )
lazy val `akka-management-javadsl` = (project in file("akka-management/javadsl"))
  .dependsOn(`akka-management-core`)
  .settings(runtimeLibCommon: _*)
  .settings(mimaSettings(since = version150): _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-akka-management-javadsl",
    Dependencies.`akka-management-javadsl`
  )
lazy val `akka-management-scaladsl` = (project in file("akka-management/scaladsl"))
  .dependsOn(`akka-management-core`)
  .settings(runtimeLibCommon: _*)
  .settings(mimaSettings(since = version150): _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-akka-management-scaladsl",
    Dependencies.`akka-management-scaladsl`
  )

lazy val `cluster-core` = (project in file("cluster/core"))
  .dependsOn(`akka-management-core`)
  .settings(runtimeLibCommon: _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-cluster-core",
    Dependencies.`cluster-core`
  )

lazy val `cluster-javadsl` = (project in file("cluster/javadsl"))
  .dependsOn(`akka-management-javadsl`, `cluster-core`, jackson)
  .settings(runtimeLibCommon: _*)
  .settings(mimaSettings(since = version150): _*)
  .settings(multiJvmTestSettings: _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-javadsl-cluster",
    Dependencies.`cluster-javadsl`
  )
  .configs(MultiJvm)

lazy val `cluster-scaladsl` = (project in file("cluster/scaladsl"))
  .dependsOn(`akka-management-scaladsl`, `cluster-core`, `play-json`)
  .settings(runtimeLibCommon: _*)
  .settings(mimaSettings(since = version150): _*)
  .settings(multiJvmTestSettings: _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-scaladsl-cluster",
    Dependencies.`cluster-scaladsl`
  )
  .configs(MultiJvm)

lazy val `pubsub-javadsl` = (project in file("pubsub/javadsl"))
  .dependsOn(`cluster-javadsl`)
  .settings(runtimeLibCommon: _*)
  .settings(mimaSettings(since = version150): _*)
  .settings(multiJvmTestSettings: _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-javadsl-pubsub",
    Dependencies.`pubsub-javadsl`
  )
  .configs(MultiJvm)

lazy val `pubsub-scaladsl` = (project in file("pubsub/scaladsl"))
  .dependsOn(`cluster-scaladsl`)
  .settings(runtimeLibCommon: _*)
  .settings(mimaSettings(since = version150): _*)
  .settings(multiJvmTestSettings: _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-scaladsl-pubsub",
    Dependencies.`pubsub-scaladsl`
  )
  .configs(MultiJvm)

lazy val `persistence-core` = (project in file("persistence/core"))
  .dependsOn(`cluster-core`, logback % Test)
  .settings(runtimeLibCommon: _*)
  .settings(mimaSettings(since = version150): _*)
  .settings(Protobuf.settings)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-persistence-core",
    Dependencies.`persistence-core`
  )

lazy val `persistence-testkit` = (project in file("persistence/testkit"))
  .settings(runtimeLibCommon: _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-persistence-testkit",
    Dependencies.`persistence-testkit`
  )

lazy val `persistence-javadsl` = (project in file("persistence/javadsl"))
  .settings(
    name := "lagom-javadsl-persistence",
    Dependencies.`persistence-javadsl`
  )
  .dependsOn(
    `persistence-core` % "compile;test->test",
    `persistence-testkit`,
    jackson,
    `cluster-javadsl`
  )
  .settings(runtimeLibCommon: _*)
  .settings(mimaSettings(since = version150): _*)
  .settings(Protobuf.settings)
  .enablePlugins(RuntimeLibPlugins)

lazy val `persistence-scaladsl` = (project in file("persistence/scaladsl"))
  .settings(
    name := "lagom-scaladsl-persistence",
    Dependencies.`persistence-scaladsl`
  )
  .dependsOn(
    `persistence-core` % "compile;test->test",
    `persistence-testkit`,
    `play-json`,
    `cluster-scaladsl`
  )
  .settings(runtimeLibCommon: _*)
  .settings(mimaSettings(since = version150): _*)
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
    Dependencies.`persistence-cassandra-javadsl`
  )
  .dependsOn(
    `persistence-core`           % "compile;test->test",
    `persistence-javadsl`        % "compile;test->test",
    `persistence-cassandra-core` % "compile;test->test",
    `api-javadsl`
  )
  .settings(runtimeLibCommon: _*)
  .settings(mimaSettings(since = version150): _*)
  .settings(multiJvmTestSettings: _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings()
  .configs(MultiJvm)

lazy val `persistence-cassandra-scaladsl` = (project in file("persistence-cassandra/scaladsl"))
  .settings(
    name := "lagom-scaladsl-persistence-cassandra",
    Dependencies.`persistence-cassandra-scaladsl`
  )
  .dependsOn(
    `persistence-core`           % "compile;test->test",
    `persistence-scaladsl`       % "compile;test->test",
    `persistence-cassandra-core` % "compile;test->test",
    `api-scaladsl`
  )
  .settings(runtimeLibCommon: _*)
  .settings(mimaSettings(since = version150): _*)
  .settings(multiJvmTestSettings: _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings()
  .configs(MultiJvm)

lazy val `persistence-jdbc-core` = (project in file("persistence-jdbc/core"))
  .dependsOn(
    `persistence-core` % "compile;test->test"
  )
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
    `persistence-jdbc-core` % "compile;test->test",
    `persistence-core`      % "compile;test->test",
    `persistence-javadsl`   % "compile;test->test"
  )
  .settings(runtimeLibCommon: _*)
  .settings(mimaSettings(since = version150): _*)
  .settings(multiJvmTestSettings: _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(forkedTests: _*)
  .configs(MultiJvm)

lazy val `persistence-jdbc-scaladsl` = (project in file("persistence-jdbc/scaladsl"))
  .settings(
    name := "lagom-scaladsl-persistence-jdbc",
    Dependencies.`persistence-jdbc-scaladsl`
  )
  .dependsOn(
    `persistence-jdbc-core` % "compile;test->test",
    `persistence-core`      % "compile;test->test",
    `persistence-scaladsl`  % "compile;test->test"
  )
  .settings(runtimeLibCommon: _*)
  .settings(mimaSettings(since = version150): _*)
  .settings(multiJvmTestSettings: _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(forkedTests: _*)
  .configs(MultiJvm)

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
  .settings(mimaSettings(since = version150): _*)
  .dependsOn(`api-javadsl`, `persistence-javadsl`)

lazy val `broker-scaladsl` = (project in file("service/scaladsl/broker"))
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-scaladsl-broker",
    Dependencies.`broker-scaladsl`
  )
  .settings(runtimeLibCommon: _*)
  .settings(mimaSettings(since = version150): _*)
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
  .settings(mimaSettings(since = version150): _*)
  .settings(
    name := "lagom-javadsl-kafka-client",
    Dependencies.`kafka-client-javadsl`
  )
  .dependsOn(`api-javadsl`, `kafka-client`)

lazy val `kafka-client-scaladsl` = (project in file("service/scaladsl/kafka/client"))
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-scaladsl-kafka-client",
    Dependencies.`kafka-client-scaladsl`
  )
  .settings(runtimeLibCommon: _*)
  .settings(mimaSettings(since = version150): _*)
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
  .settings(mimaSettings(since = version150): _*)
  .settings(forkedTests: _*)
  .settings(excludeLog4jFromKafkaServer: _*)
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
    logback        % Test
  )

lazy val `kafka-broker-scaladsl` = (project in file("service/scaladsl/kafka/server"))
  .enablePlugins(RuntimeLibPlugins)
  .settings(runtimeLibCommon: _*)
  .settings(mimaSettings(since = version150): _*)
  .settings(forkedTests: _*)
  .settings(excludeLog4jFromKafkaServer: _*)
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
    logback        % Test
  )

lazy val logback = (project in file("logback"))
  .enablePlugins(RuntimeLibPlugins)
  .settings(runtimeLibCommon: _*)
  .settings(
    name := "lagom-logback",
    Dependencies.logback
  )
  .settings(overridesScalaParserCombinators: _*)

lazy val log4j2 = (project in file("log4j2"))
  .enablePlugins(RuntimeLibPlugins)
  .settings(runtimeLibCommon: _*)
  .settings(
    name := "lagom-log4j2",
    Dependencies.log4j2
  )
  .settings(overridesScalaParserCombinators: _*)

lazy val devEnvironmentProjects = Seq[Project](
  `reloadable-server`,
  `build-tool-support`,
  `sbt-build-tool-support`,
  `sbt-plugin`,
  `maven-plugin`,
  `dev-mode-ssl-support`,
  `service-locator`,
  `service-registration-javadsl`,
  `cassandra-server`,
  `play-integration-javadsl`,
  `service-registry-client-core`,
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
  .settings(overridesScalaParserCombinators: _*)
  .dependsOn(`dev-mode-ssl-support`)

lazy val `build-tool-support` = (project in file("dev") / "build-tool-support")
  .disablePlugins(BintrayPlugin)
  .enablePlugins(AutomateHeaderPlugin && Sonatype)
  .settings(sonatypeSettings: _*)
  .settings(common: _*)
  .settings(
    name := "lagom-build-tool-support",
    publishMavenStyle := true,
    crossScalaVersions := Seq(Dependencies.Versions.Scala.head),
    scalaVersion := Dependencies.Versions.Scala.head,
    crossPaths := false,
    sourceGenerators in Compile += Def.task {
      Generators.version(version.value, (sourceManaged in Compile).value)
    }.taskValue,
    Dependencies.`build-tool-support`
  )

// This is almost the same as `build-tool-support`, but targeting sbt
// while `build-tool-support` targets Maven and possibly other build
// systems. We did something similar for routes compiler in Play:
//
// https://github.com/playframework/playframework/blob/2.6.7/framework/build.sbt#L27-L40
lazy val `sbt-build-tool-support` = (project in file("dev") / "build-tool-support")
  .disablePlugins(BintrayPlugin)
  .enablePlugins(AutomateHeaderPlugin && Sonatype)
  .settings(sonatypeSettings: _*)
  .settings(common: _*)
  .settings(
    name := "lagom-sbt-build-tool-support",
    crossScalaVersions := Dependencies.Versions.SbtScala,
    scalaVersion := Dependencies.Versions.SbtScala.head,
    sbtVersion in pluginCrossBuild := defineSbtVersion(scalaBinaryVersion.value),
    sbtPlugin := true,
    sourceGenerators in Compile += Def.task {
      Generators.version(version.value, (sourceManaged in Compile).value)
    }.taskValue,
    Dependencies.`build-tool-support`,
    target := target.value / "lagom-sbt-build-tool-support"
  )

lazy val `sbt-plugin` = (project in file("dev") / "sbt-plugin")
  .settings(common: _*)
  .settings(scriptedSettings: _*)
  .enablePlugins(SbtPluginPlugins, SbtPlugin)
  .settings(
    name := "lagom-sbt-plugin",
    crossScalaVersions := Dependencies.Versions.SbtScala,
    scalaVersion := Dependencies.Versions.SbtScala.head,
    sbtVersion in pluginCrossBuild := defineSbtVersion(scalaBinaryVersion.value),
    Dependencies.`sbt-plugin`,
    libraryDependencies ++= Seq(
      Defaults
        .sbtPluginExtra(
          "com.typesafe.play" % "sbt-plugin" % Dependencies.Versions.Play,
          CrossVersion.binarySbtVersion((sbtVersion in pluginCrossBuild).value),
          CrossVersion.binaryScalaVersion(scalaVersion.value)
        )
        .exclude("org.slf4j", "slf4j-simple")
    ),
    // This ensure that files in sbt-test are also included
    headerSources in Compile ++= (sbtTestDirectory.value ** ("*.scala" || "*.java")).get,
    scriptedDependencies := {
      val () = scriptedDependencies.value

      // core projects
      val () = (publishLocal in `akka-management-core`).value
      val () = (publishLocal in `akka-management-javadsl`).value
      val () = (publishLocal in `akka-management-scaladsl`).value
      val () = (publishLocal in `api`).value
      val () = (publishLocal in `api-javadsl`).value
      val () = (publishLocal in `api-scaladsl`).value
      val () = (publishLocal in `client`).value
      val () = (publishLocal in `client-javadsl`).value
      val () = (publishLocal in `client-scaladsl`).value
      val () = (publishLocal in `cluster-core`).value
      val () = (publishLocal in `cluster-javadsl`).value
      val () = (publishLocal in `immutables`).value
      val () = (publishLocal in `jackson`).value
      val () = (publishLocal in `logback`).value
      val () = (publishLocal in `persistence-core`).value
      val () = (publishLocal in `persistence-javadsl`).value
      val () = (publishLocal in `persistence-testkit`).value
      val () = (publishLocal in `persistence-cassandra-core`).value
      val () = (publishLocal in `persistence-cassandra-javadsl`).value
      val () = (publishLocal in `play-json`).value
      val () = (publishLocal in `server`).value
      val () = (publishLocal in `server-javadsl`).value
      val () = (publishLocal in `server-scaladsl`).value
      val () = (publishLocal in `spi`).value

      // dev service registry
      val () = (publishLocal in `devmode-scaladsl`).value
      val () = (publishLocal in `play-integration-javadsl`).value
      val () = (publishLocal in `service-locator`).value
      val () = (publishLocal in `service-registration-javadsl`).value
      val () = (publishLocal in `service-registry-client-core`).value
      val () = (publishLocal in `service-registry-client-javadsl`).value

      // dev environment projects
      val () = (publishLocal in `cassandra-server`).value
      val () = (publishLocal in `dev-mode-ssl-support`).value
      val () = (publishLocal in `kafka-server`).value
      val () = (publishLocal in `reloadable-server`).value
      val () = (publishLocal in `sbt-build-tool-support`).value
      val () = publishLocal.value

      // sbt scripted projects
      val () = (publishLocal in `sbt-scripted-library`).value
      val () = (publishLocal in LocalProject("sbt-scripted-tools")).value
    },
    publishTo := {
      val old = publishTo.value
      if (isSnapshot.value) {
        // Bintray doesn't support publishing snapshots, publish to Sonatype snapshots instead
        Some(Opts.resolver.sonatypeSnapshots)
      } else old
    },
    publishMavenStyle := isSnapshot.value
  )
  .dependsOn(`sbt-build-tool-support`)

lazy val `maven-plugin` = (project in file("dev") / "maven-plugin")
  .disablePlugins(BintrayPlugin)
  .enablePlugins(lagom.SbtMavenPlugin && AutomateHeaderPlugin && Sonatype && Unidoc)
  .settings(sonatypeSettings: _*)
  .settings(common: _*)
  .settings(
    name := "Lagom Maven Plugin",
    description := "Provides Lagom development environment support to maven.",
    Dependencies.`maven-plugin`,
    publishMavenStyle := true,
    crossScalaVersions := Seq(Dependencies.Versions.Scala.head),
    scalaVersion := Dependencies.Versions.Scala.head,
    crossPaths := false,
    mavenClasspath := (externalDependencyClasspath in (`maven-launcher`, Compile)).value.map(_.data),
    // This ensure that files in maven-test are also included
    headerSources in Compile ++= (sourceDirectory.value / "maven-test" ** ("*.scala" || "*.java")).get,
    mavenTestArgs := Seq(
      "-Xmx768m",
      "-XX:MaxMetaspaceSize=384m",
      s"-Dlagom.version=${version.value}",
      s"-DarchetypeVersion=${version.value}",
      "-Dorg.slf4j.simpleLogger.showLogName=false",
      "-Dorg.slf4j.simpleLogger.showThreadName=false"
    ),
    pomExtra ~= (existingPomExtra => {
      existingPomExtra ++
        <prerequisites>
        <maven>{Dependencies.Versions.Maven}</maven>
      </prerequisites>
    })
  )
  .dependsOn(`build-tool-support`)

lazy val `maven-launcher` = (project in file("dev") / "maven-launcher")
  .settings(
    name := "lagom-maven-launcher",
    description := "Dummy project, exists only to resolve the maven launcher classpath",
    Dependencies.`maven-launcher`
  )

def scriptedSettings: Seq[Setting[_]] =
  Seq(scriptedLaunchOpts += s"-Dproject.version=${version.value}") ++
    Seq(
      scripted := scripted.tag(Tags.Test).evaluated,
      scriptedLaunchOpts ++= Seq(
        "-Xmx512m",
        "-XX:MaxMetaspaceSize=512m",
        "-Dscala.version=" + sys.props
          .get("scripted.scala.version")
          .getOrElse((scalaVersion in `reloadable-server`).value)
      )
    )

def archetypeVariables(lagomVersion: String) = Map(
  "LAGOM-VERSION" -> lagomVersion
)

val ArchetypeVariablePattern = "%([A-Z-]+)%".r

def archetypeProject(archetypeName: String) =
  Project(s"maven-$archetypeName-archetype", file("dev") / "archetypes" / s"maven-$archetypeName")
    .disablePlugins(BintrayPlugin)
    .enablePlugins(AutomateHeaderPlugin && Sonatype)
    .settings(sonatypeSettings: _*)
    .settings(common: _*)
    .settings(
      name := s"maven-archetype-lagom-$archetypeName",
      autoScalaLibrary := false,
      publishMavenStyle := true,
      crossPaths := false,
      copyResources in Compile := {
        val pomFile = (classDirectory in Compile).value / "archetype-resources" / "pom.xml"
        if (pomFile.exists()) {
          val pomXml    = IO.read(pomFile)
          val variables = archetypeVariables(version.value)
          val newPomXml = ArchetypeVariablePattern.replaceAllIn(
            pomXml,
            m =>
              variables.get(m.group(1)) match {
                case Some(replacement) => replacement
                case None              => m.matched
              }
          )
          IO.write(pomFile, newPomXml)
        }
        (copyResources in Compile).value
      },
      unmanagedResources in Compile := {
        val gitIgnoreFiles = (unmanagedResourceDirectories in Compile).value.flatMap { dirs =>
          (dirs ** (".gitignore")).get
        }
        (unmanagedResources in Compile).value ++ gitIgnoreFiles
      },
      // Don't force copyright headers in Maven archetypes
      excludeFilter in headerResources := "*"
    )

lazy val `maven-java-archetype` = archetypeProject("java")
lazy val `maven-dependencies` = (project in file("dev") / "maven-dependencies")
  .disablePlugins(BintrayPlugin)
  .enablePlugins(AutomateHeaderPlugin && Sonatype)
  .settings(sonatypeSettings: _*)
  .settings(common: _*)
  .settings(
    name := "lagom-maven-dependencies",
    crossPaths := false,
    autoScalaLibrary := false,
    scalaVersion := Dependencies.Versions.Scala.head,
    pomExtra := pomExtra.value :+ {

      val lagomDeps = Def.settingDyn {

        // all Lagom artifacts are cross compiled
        (javadslProjects ++ coreProjects).map {
          project =>
            Def.setting {

              val artifactName = (artifact in project).value.name

              Dependencies.Versions.Scala.map {
                supportedVersion =>
                  // we are sure this won't be a None
                  val crossFunc =
                    CrossVersion(Binary(), supportedVersion, CrossVersion.binaryScalaVersion(supportedVersion)).get
                  // convert artifactName to match the desired scala version
                  val artifactId = crossFunc(artifactName)

                  <dependency>
                  <groupId>{(organization in project).value}</groupId>
                  <artifactId>{artifactId}</artifactId>
                  <version>{(version in project).value}</version>
                </dependency>
              }
            }
        }.join
      }.value

      <dependencyManagement>
          <dependencies>
            {lagomDeps}
            {
        // here we generate all non-Lagom dependencies
        // some are cross compiled, others are simply java deps.
        Dependencies.DependencyWhitelist.value
        // remove any scala-lang deps, they must be included transitively
          .filterNot(_.organization.startsWith("org.scala-lang"))
          .map {
            dep =>
              // bloody hack! We first need to discovery if a module is a scala deps or not
              val moduleCrossVersion = CrossVersion(dep.crossVersion, scalaVersion.value, scalaBinaryVersion.value)

              if (moduleCrossVersion.isEmpty) {
                // if not a Scala dependency, add it as is
                <dependency>
                        <groupId>{dep.organization}</groupId>
                        <artifactId>{dep.name}</artifactId>
                        <version>{dep.revision}</version>
                      </dependency>
              } else {
                // if it's a Scala dependency,
                // generate <dependency> block for each supported scala version
                Dependencies.Versions.Scala.map {
                  supportedVersion =>
                    val crossDep =
                      CrossVersion(supportedVersion, CrossVersion.binaryScalaVersion(supportedVersion))(dep)
                    <dependency>
                          <groupId>{crossDep.organization}</groupId>
                          <artifactId>{crossDep.name}</artifactId>
                          <version>{crossDep.revision}</version>
                        </dependency>
                }
              }
          }
      }
          </dependencies>
        </dependencyManagement>
    },
    // This disables creating jar, source jar and javadocs, and will cause the packaging type to be "pom" when the
    // pom is created
    Classpaths.defaultPackageKeys.map(key => publishArtifact in key := false),
    publishMavenStyle := true, // Disable publishing ("delivering") the ivy.xml file
  )

// This project doesn't get aggregated, it is only executed by the sbt-plugin scripted dependencies
lazy val `sbt-scripted-tools` = (project in file("dev") / "sbt-scripted-tools")
  .disablePlugins(BintrayPlugin)
  .enablePlugins(AutomateHeaderPlugin && Sonatype)
  .settings(sonatypeSettings: _*)
  .settings(common: _*)
  .settings(name := "lagom-sbt-scripted-tools")
  .settings(
    sbtPlugin := true,
    crossScalaVersions := Dependencies.Versions.SbtScala,
    scalaVersion := Dependencies.Versions.SbtScala.head,
    sbtVersion in pluginCrossBuild := defineSbtVersion(scalaBinaryVersion.value)
  )
  .dependsOn(`sbt-plugin`)

// This project also get aggregated, it is only executed by the sbt-plugin scripted dependencies
lazy val `sbt-scripted-library` = (project in file("dev") / "sbt-scripted-library")
  .settings(name := "lagom-sbt-scripted-library")
  .settings(runtimeLibCommon: _*)
  .settings(
    PgpKeys.publishSigned := {},
    publish := {}
  )
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
  .dependsOn(
    `server-javadsl`,
    logback,
    `service-registry-client-javadsl`,
    `dev-mode-ssl-support`,
    `play-json`        % "compile -> test",
    `jackson`          % "compile -> test",
    `devmode-scaladsl` % "compile -> test"
  )

lazy val `dev-mode-ssl-support` = (project in file("dev") / "dev-mode-ssl-support")
  .settings(
    name := "lagom-dev-mode-ssl-support",
    Dependencies.`dev-mode-ssl-support`
  )
  .settings(runtimeLibCommon: _*)
  .settings(overridesScalaParserCombinators: _*)
  .enablePlugins(RuntimeLibPlugins)

lazy val `service-registry-client-core` = (project in file("dev") / "service-registry" / "client-core")
  .settings(
    name := "lagom-service-registry-client-core",
    Dependencies.`service-registry-client-core`
  )
  .settings(runtimeLibCommon: _*)
  .settings(overridesScalaParserCombinators: _*)
  .enablePlugins(RuntimeLibPlugins)
  .dependsOn(logback % Test)

lazy val `service-registry-client-javadsl` = (project in file("dev") / "service-registry" / "client-javadsl")
  .settings(
    name := "lagom-service-registry-client",
    Dependencies.`service-registry-client-javadsl`
  )
  .settings(runtimeLibCommon: _*)
  .enablePlugins(RuntimeLibPlugins)
  .dependsOn(`client-javadsl`, `service-registry-client-core`, immutables % "provided")

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
  .settings(mimaSettings(since = version150): _*)
  .enablePlugins(RuntimeLibPlugins)
  .dependsOn(`client-scaladsl`, `service-registry-client-core`)

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
  .settings(sonatypeSettings)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-cassandra-server",
    Dependencies.`cassandra-server`
  )

lazy val `kafka-server` = (project in file("dev") / "kafka-server")
  .settings(common: _*)
  .settings(runtimeScalaSettings: _*)
  .settings(sonatypeSettings)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-kafka-server",
    Dependencies.`kafka-server`
  )

// kafka-server has a transitive dependency on slf4j-log4j12.
// This is required for running Kafka in development mode, where it runs in its own process and uses log4j 1.2.
// When running broker tests, Kafka is started in process, and its logs need to be routed to logback, which requires
// excluding slf4j-log4j12.
def excludeLog4jFromKafkaServer: Seq[Setting[_]] = Seq(
  libraryDependencies += (projectID in (`kafka-server`, Test)).value.exclude("org.slf4j", "slf4j-log4j12")
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
