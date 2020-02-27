import java.net.InetSocketAddress
import java.nio.channels.ServerSocketChannel

import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys._
import com.typesafe.tools.mima.core._
import lagom.Protobuf
import lagom.build._
import org.scalafmt.sbt.ScalafmtPlugin

// Turn off "Resolving" log messages that clutter build logs
ivyLoggingLevel in ThisBuild := UpdateLogging.Quiet

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
      "Copyright (C) Lightbend Inc. <https://www.lightbend.com>"
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
    "-parameters",
    "-Xlint:unchecked",
    "-Xlint:deprecation"
  ) ++ akka.JavaVersion.sourceAndTarget(akka.CrossJava.Keys.fullJavaHomes.value("8")),
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
      releaseStepCommand("sonatypeBundleRelease"),
      releaseStepTask(bintrayRelease in thisProjectRef.value),
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

def publishMavenStyleSettings: Seq[Setting[_]] = Seq(
  publishMavenStyle := true,
  crossScalaVersions := Seq(Dependencies.Versions.Scala.head),
  scalaVersion := Dependencies.Versions.Scala.head,
  crossPaths := false,
)

def sonatypeSettings: Seq[Setting[_]] = Seq(
  publishTo := sonatypePublishToBundle.value,
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

def sbtScalaSettings: Seq[Setting[_]] = Seq(
  crossScalaVersions := Dependencies.Versions.Scala,
  scalaVersion := Dependencies.Versions.Scala.head,
)

def runtimeLibCommon: Seq[Setting[_]] = common ++ sonatypeSettings ++ runtimeScalaSettings ++ Seq(
  Dependencies.validateDependenciesSetting,
  Dependencies.allowedPruneSetting,
  Dependencies.allowDependenciesSetting,
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

  "-Xmx256m" :: properties
}

def databasePortSetting: List[String] = {
  def gimmePort = {
    val serverSocket = ServerSocketChannel.open().socket()
    try {
      serverSocket.bind(new InetSocketAddress("127.0.0.1", 0))
      serverSocket.getLocalPort
    } finally serverSocket.close()
  }
  List(
    s"-Djavadsl.database.port=$gimmePort",
    s"-Dscaladsl.database.port=$gimmePort",
  )
}

def multiJvm(project: Project): Project = {
  project
    .enablePlugins(MultiJvmPlugin)
    .configs(MultiJvm)
    .settings(inConfig(MultiJvm)(ScalafmtPlugin.scalafmtConfigSettings))
    .settings {
      // change multi-jvm lib folder to reflect the scala version used during crossbuild
      // must be done using a dynamic setting because we must read crossTarget.value
      def crossbuildMultiJvm: Def.Initialize[File] = Def.settingDyn {
        val path = crossTarget.value.getName
        Def.setting {
          target.apply { targetFile =>
            new File(targetFile, path + "/multi-run-copied-libraries")
          }.value
        }
      }

      forkedTests ++
        // enabling HeaderPlugin in MultiJvm requires two sets of settings.
        // see https://github.com/sbt/sbt-header/issues/37
        headerSettings(MultiJvm) ++
        Seq(
          parallelExecution in Test := false,
          parallelExecution in MultiJvm := false,
          // -o D(report the duration of the tests) F(show full stack traces)
          // -u select the JUnit XML reporter
          scalatestOptions in MultiJvm := Seq("-oDF", "-u", (target.value / "test-reports").getAbsolutePath),
          MultiJvmKeys.jvmOptions in MultiJvm := databasePortSetting ::: defaultMultiJvmOptions,
          // tag MultiJvm tests so that we can use concurrentRestrictions to disable parallel tests
          executeTests in MultiJvm := (executeTests in MultiJvm).tag(Tags.Test).value,
          // change multi-jvm lib folder to reflect the scala version used during crossbuild
          multiRunCopiedClassLocation in MultiJvm := crossbuildMultiJvm.value
        )
    }
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

val previousVersions = Seq("1.6.1")

val noMima = mimaPreviousArtifacts := Set.empty
val mimaSettings: Seq[Setting[_]] = {
  Seq(
    mimaPreviousArtifacts := previousVersions.map { version =>
      val suffix   = if (crossPaths.value && !sbtPlugin.value) s"_${scalaBinaryVersion.value}" else ""
      val moduleID = organization.value % s"${moduleName.value}$suffix" % version

      // For sbt plugins if that is the case for the current subproject
      val sbtBV   = (sbtBinaryVersion in pluginCrossBuild).value
      val scalaBV = (scalaBinaryVersion in pluginCrossBuild).value

      if (sbtPlugin.value) Defaults.sbtPluginExtra(moduleID, sbtBV, scalaBV)
      else moduleID
    }.toSet,
    mimaBinaryIssueFilters ++= Seq(
      // Drop sbt 0.13
      ProblemFilters.exclude[MissingClassProblem]("sbt.LagomLoad"),
      ProblemFilters.exclude[MissingClassProblem]("sbt.LagomLoad$"),
      ProblemFilters.exclude[MissingClassProblem]("com.lightbend.lagom.sbt.LagomPluginCompat"),
      ProblemFilters.exclude[MissingClassProblem]("com.lightbend.lagom.sbt.LagomReloadableServiceCompat$autoImport"),
      ProblemFilters.exclude[MissingClassProblem]("com.lightbend.lagom.sbt.DynamicProjectAdder"),
      ProblemFilters.exclude[MissingClassProblem]("com.lightbend.lagom.sbt.DynamicProjectAdder$"),
      ProblemFilters.exclude[MissingClassProblem]("com.lightbend.lagom.sbt.LagomReloadableServiceCompat"),
      ProblemFilters.exclude[MissingTypesProblem]("com.lightbend.lagom.sbt.LagomPlugin$"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("com.lightbend.lagom.sbt.LagomPlugin.getPollInterval"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("com.lightbend.lagom.sbt.LagomImport.getForkOptions"),
      ProblemFilters.exclude[MissingTypesProblem]("com.lightbend.lagom.sbt.LagomImport$"),
      ProblemFilters.exclude[MissingTypesProblem]("com.lightbend.lagom.sbt.LagomReloadableService$autoImport$"),
      ProblemFilters.exclude[MissingClassProblem]("com.lightbend.lagom.sbt.LagomReloadableServiceCompat$"),
      ProblemFilters.exclude[MissingClassProblem]("com.lightbend.lagom.sbt.LagomImportCompat"),
      ProblemFilters.exclude[MissingTypesProblem]("com.lightbend.lagom.sbt.run.RunSupport$"),
      ProblemFilters.exclude[MissingClassProblem]("com.lightbend.lagom.sbt.run.RunSupportCompat"),
    )
  )
}

val javadslProjects = Seq[ProjectReference](
  `api-javadsl`,
  `server-javadsl`,
  `client-javadsl`,
  `broker-javadsl`,
  `kafka-client-javadsl`,
  `kafka-broker-javadsl`,
  `akka-management-javadsl`,
  `akka-discovery-service-locator-javadsl`,
  `cluster-javadsl`,
  `projection-javadsl`,
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

val scaladslProjects = Seq[ProjectReference](
  `api-scaladsl`,
  `client-scaladsl`,
  `broker-scaladsl`,
  `kafka-client-scaladsl`,
  `kafka-broker-scaladsl`,
  `server-scaladsl`,
  `akka-management-scaladsl`,
  `akka-discovery-service-locator-scaladsl`,
  `cluster-scaladsl`,
  `projection-scaladsl`,
  `persistence-scaladsl`,
  `persistence-cassandra-scaladsl`,
  `persistence-jdbc-scaladsl`,
  `pubsub-scaladsl`,
  `testkit-scaladsl`,
  `devmode-scaladsl`,
  `play-json`
)

val coreProjects = Seq[ProjectReference](
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
  `projection-core`,
  `persistence-core`,
  `persistence-testkit`,
  `persistence-cassandra-core`,
  `persistence-jdbc-core`,
  `testkit-core`,
  logback,
  log4j2
)

val publishScriptedDependencies = taskKey[Unit]("Publish scripted dependencies")

val otherProjects = devEnvironmentProjects ++ Seq[ProjectReference](
  `integration-tests-javadsl`,
  `integration-tests-scaladsl`,
  `macro-testkit`
)

val sbtScriptedProjects = Seq[ProjectReference](
  `sbt-scripted-tools`,
  `sbt-scripted-library`
)

lazy val root = (project in file("."))
  .settings(name := "lagom")
  .settings(runtimeLibCommon, noMima)
  .settings(
    crossScalaVersions := Nil,
    scalaVersion := Dependencies.Versions.Scala.head,
    PgpKeys.publishSigned := {},
    publishLocal := {},
    publishArtifact in Compile := false,
    publish := {}
  )
  .enablePlugins(lagom.UnidocRoot)
  .settings(UnidocRoot.settings(javadslProjects, scaladslProjects, `projection-core`))
  .aggregate((javadslProjects ++ scaladslProjects ++ coreProjects ++ otherProjects ++ sbtScriptedProjects): _*)

def SonatypeOnly      = Sonatype && PluginsAccessor.exclude(BintrayPlugin)
def RuntimeLibPlugins = SonatypeOnly && HeaderPlugin && Unidoc

lazy val api = (project in file("service/core/api"))
  .configure(withLagomVersion)
  .settings(runtimeLibCommon, mimaSettings)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-api",
    Dependencies.api
  )

lazy val `api-javadsl` = (project in file("service/javadsl/api"))
  .settings(name := "lagom-javadsl-api")
  .settings(runtimeLibCommon, mimaSettings)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    Dependencies.`api-javadsl`
  )
  .dependsOn(api)

lazy val `api-scaladsl` = (project in file("service/scaladsl/api"))
  .settings(name := "lagom-scaladsl-api")
  .settings(runtimeLibCommon, mimaSettings)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    Dependencies.`api-scaladsl`
  )
  .dependsOn(api)

lazy val immutables = (project in file("immutables"))
  .settings(name := "lagom-javadsl-immutables")
  .settings(runtimeLibCommon, mimaSettings)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    Dependencies.immutables
  )

lazy val spi = (project in file("spi"))
  .settings(name := "lagom-spi")
  .settings(runtimeLibCommon, mimaSettings)
  .enablePlugins(RuntimeLibPlugins)

lazy val jackson = (project in file("jackson"))
  .settings(name := "lagom-javadsl-jackson")
  .settings(runtimeLibCommon, mimaSettings)
  .enablePlugins(RuntimeLibPlugins)
  .settings(Dependencies.jackson)
  .dependsOn(`api-javadsl`, immutables % "test->compile")

lazy val `play-json` = (project in file("play-json"))
  .settings(runtimeLibCommon, mimaSettings)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-scaladsl-play-json",
    Dependencies.`play-json`
  )

lazy val `api-tools` = (project in file("api-tools"))
  .settings(runtimeLibCommon, mimaSettings)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    Dependencies.`api-tools`
  )
  .settings(overridesScalaParserCombinators)
  .dependsOn(
    spi,
    `server-javadsl`  % Test,
    `server-scaladsl` % Test
  )

lazy val client = (project in file("service/core/client"))
  .settings(runtimeLibCommon, mimaSettings)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-client",
    Dependencies.client
  )
  .dependsOn(api, spi)

lazy val `client-javadsl` = (project in file("service/javadsl/client"))
  .settings(runtimeLibCommon, mimaSettings)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-javadsl-client",
    Dependencies.`client-javadsl`
  )
  .dependsOn(client, `api-javadsl`, jackson)

lazy val `client-scaladsl` = (project in file("service/scaladsl/client"))
  .settings(runtimeLibCommon, mimaSettings)
  .enablePlugins(RuntimeLibPlugins)
  .settings(macroCompileSettings)
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
  .settings(runtimeLibCommon, mimaSettings)
  .enablePlugins(RuntimeLibPlugins)
  .dependsOn(`client-javadsl`, `service-registry-client-javadsl`, `kafka-client-javadsl`)

lazy val server = (project in file("service/core/server"))
  .settings(
    name := "lagom-server",
    Dependencies.server
  )
  .enablePlugins(RuntimeLibPlugins)
  .settings(runtimeLibCommon, mimaSettings)
  .dependsOn(client)

lazy val `server-javadsl` = (project in file("service/javadsl/server"))
  .settings(
    name := "lagom-javadsl-server",
    Dependencies.`server-javadsl`
  )
  .enablePlugins(RuntimeLibPlugins)
  .settings(runtimeLibCommon, mimaSettings)
  .dependsOn(`akka-management-javadsl`, server, `client-javadsl`, immutables % "provided")
  // bring jackson closer to the root of the dependency tree to prompt Maven to choose the right version
  .dependsOn(jackson)

lazy val `server-scaladsl` = (project in file("service/scaladsl/server"))
  .settings(
    name := "lagom-scaladsl-server",
    Dependencies.`server-scaladsl`
  )
  .enablePlugins(RuntimeLibPlugins)
  .settings(runtimeLibCommon, mimaSettings)
  .dependsOn(`akka-management-scaladsl`, server, `client-scaladsl`, `play-json`)

lazy val `testkit-core` = (project in file("testkit/core"))
  .settings(runtimeLibCommon, mimaSettings)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-core-testkit",
    Dependencies.`testkit-core`
  )
  .settings(overridesScalaParserCombinators, forkedTests)
  .dependsOn(
    `dev-mode-ssl-support`,
    // Ideally, this would be the other way around, but it will require some more refactoring
    `persistence-testkit`
  )

lazy val `testkit-javadsl` = (project in file("testkit/javadsl"))
  .settings(runtimeLibCommon, mimaSettings, forkedTests)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-javadsl-testkit",
    Dependencies.`testkit-javadsl`
  )
  .settings(overridesScalaParserCombinators)
  .dependsOn(
    `testkit-core`,
    `server-javadsl`,
    `pubsub-javadsl`,
    `broker-javadsl`,
    `dev-mode-ssl-support`,
    `persistence-core`              % "compile;test->test",
    `persistence-cassandra-javadsl` % "test->test",
    `jackson`                       % "test->test",
    `persistence-jdbc-javadsl`      % Test
  )

lazy val `testkit-scaladsl` = (project in file("testkit/scaladsl"))
  .settings(runtimeLibCommon, mimaSettings, forkedTests)
  .enablePlugins(RuntimeLibPlugins)
  .settings(overridesScalaParserCombinators)
  .settings(
    name := "lagom-scaladsl-testkit",
    Dependencies.`testkit-scaladsl`
  )
  .dependsOn(
    `testkit-core`,
    `server-scaladsl`,
    `broker-scaladsl`,
    `kafka-broker-scaladsl`,
    `dev-mode-ssl-support`,
    `persistence-core`               % "compile;test->test",
    `persistence-scaladsl`           % "compile;test->test",
    `persistence-cassandra-scaladsl` % "compile->test;test->test",
    `persistence-jdbc-scaladsl`      % Test
  )

lazy val `integration-tests-javadsl` = (project in file("service/javadsl/integration-tests"))
  .settings(runtimeLibCommon, noMima, forkedTests)
  .enablePlugins(HeaderPlugin)
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
  .settings(runtimeLibCommon, noMima, forkedTests)
  .enablePlugins(HeaderPlugin)
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
  .settings(runtimeLibCommon, mimaSettings)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-akka-discovery-service-locator-core",
    Dependencies.`lagom-akka-discovery-service-locator-core`
  )

lazy val `akka-discovery-service-locator-javadsl` = (project in file("akka-service-locator/javadsl"))
  .dependsOn(`akka-discovery-service-locator-core`)
  .dependsOn(`client-javadsl`)
  .settings(runtimeLibCommon, mimaSettings)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-javadsl-akka-discovery-service-locator"
  )

lazy val `akka-discovery-service-locator-scaladsl` = (project in file("akka-service-locator/scaladsl"))
  .dependsOn(`akka-discovery-service-locator-core`)
  .dependsOn(`client-scaladsl`)
  .settings(runtimeLibCommon, mimaSettings)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-scaladsl-akka-discovery-service-locator",
    Dependencies.`lagom-akka-discovery-service-locator-scaladsl`
  )
  .dependsOn(`testkit-scaladsl` % Test)

lazy val `akka-management-core` = (project in file("akka-management/core"))
  .settings(runtimeLibCommon, mimaSettings)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-akka-management-core",
    Dependencies.`akka-management-core`
  )
lazy val `akka-management-javadsl` = (project in file("akka-management/javadsl"))
  .dependsOn(`akka-management-core`)
  .settings(runtimeLibCommon, mimaSettings)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-akka-management-javadsl",
    Dependencies.`akka-management-javadsl`
  )
lazy val `akka-management-scaladsl` = (project in file("akka-management/scaladsl"))
  .dependsOn(`akka-management-core`)
  .settings(runtimeLibCommon, mimaSettings)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-akka-management-scaladsl",
    Dependencies.`akka-management-scaladsl`
  )

lazy val `cluster-core` = (project in file("cluster/core"))
  .configure(withLagomVersion)
  .dependsOn(`akka-management-core`)
  .settings(runtimeLibCommon, mimaSettings, Protobuf.settings)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-cluster-core",
    Dependencies.`cluster-core`
  )
  .configure(multiJvm)

lazy val `cluster-javadsl` = (project in file("cluster/javadsl"))
  .dependsOn(`akka-management-javadsl`, `cluster-core`, jackson)
  .settings(runtimeLibCommon, mimaSettings)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-javadsl-cluster",
    Dependencies.`cluster-javadsl`
  )

lazy val `cluster-scaladsl` = (project in file("cluster/scaladsl"))
  .dependsOn(`akka-management-scaladsl`, `cluster-core`, `play-json`)
  .settings(runtimeLibCommon, mimaSettings)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-scaladsl-cluster",
    Dependencies.`cluster-scaladsl`
  )

lazy val `pubsub-javadsl` = (project in file("pubsub/javadsl"))
  .dependsOn(
    `cluster-core` % "compile;multi-jvm->multi-jvm",
    `cluster-javadsl`
  )
  .settings(runtimeLibCommon, mimaSettings)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-javadsl-pubsub",
    Dependencies.`pubsub-javadsl`
  )
  .configure(multiJvm)

lazy val `pubsub-scaladsl` = (project in file("pubsub/scaladsl"))
  .dependsOn(
    `cluster-core` % "compile;multi-jvm->multi-jvm",
    `cluster-scaladsl`
  )
  .settings(runtimeLibCommon, mimaSettings)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-scaladsl-pubsub",
    Dependencies.`pubsub-scaladsl`
  )
  .configure(multiJvm)

lazy val `projection-core` = (project in file("projection/core"))
  .dependsOn(
    `cluster-core` % "compile;multi-jvm->multi-jvm",
    logback        % Test
  )
  .settings(runtimeLibCommon, mimaSettings, Protobuf.settings)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-projection-core",
    Dependencies.`projection-core`
  )
  .configure(multiJvm)

lazy val `projection-scaladsl` = (project in file("projection/scaladsl"))
  .dependsOn(`projection-core`, `cluster-scaladsl`, logback % Test)
  .settings(runtimeLibCommon, mimaSettings)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-scaladsl-projection",
    Dependencies.`projection-scaladsl`
  )

lazy val `projection-javadsl` = (project in file("projection/javadsl"))
  .dependsOn(`projection-core`, `cluster-javadsl`, logback % Test)
  .settings(runtimeLibCommon, mimaSettings)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-javadsl-projection",
    Dependencies.`projection-javadsl`
  )

lazy val `persistence-core` = (project in file("persistence/core"))
  .dependsOn(`cluster-core` % "compile;test->test", logback % Test)
  .settings(runtimeLibCommon, mimaSettings, Protobuf.settings)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-persistence-core",
    Dependencies.`persistence-core`
  )

lazy val `persistence-testkit` = (project in file("persistence/testkit"))
  .settings(runtimeLibCommon, mimaSettings)
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
    `cluster-core` % "compile;multi-jvm->multi-jvm",
    `cluster-javadsl`,
    `projection-javadsl`
  )
  .settings(runtimeLibCommon, mimaSettings, Protobuf.settings)
  .enablePlugins(RuntimeLibPlugins)
  .configure(multiJvm)

lazy val `persistence-scaladsl` = (project in file("persistence/scaladsl"))
  .settings(
    name := "lagom-scaladsl-persistence",
    Dependencies.`persistence-scaladsl`
  )
  .dependsOn(
    `persistence-core` % "compile;test->test",
    `persistence-testkit`,
    `play-json`,
    `cluster-core` % "compile;multi-jvm->multi-jvm",
    `cluster-scaladsl`,
    `projection-scaladsl`
  )
  .settings(runtimeLibCommon, mimaSettings, Protobuf.settings)
  .enablePlugins(RuntimeLibPlugins)
  .configure(multiJvm)

lazy val `persistence-cassandra-core` = (project in file("persistence-cassandra/core"))
  .dependsOn(`persistence-core` % "compile;test->test")
  .settings(runtimeLibCommon, mimaSettings)
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
    `persistence-javadsl`        % "compile;test->test;multi-jvm->multi-jvm",
    `persistence-cassandra-core` % "compile;test->test",
    `api-javadsl`
  )
  .settings(runtimeLibCommon, mimaSettings)
  .enablePlugins(RuntimeLibPlugins)
  .configure(multiJvm)

lazy val `persistence-cassandra-scaladsl` = (project in file("persistence-cassandra/scaladsl"))
  .settings(
    name := "lagom-scaladsl-persistence-cassandra",
    Dependencies.`persistence-cassandra-scaladsl`
  )
  .dependsOn(
    `persistence-core`           % "compile;test->test",
    `persistence-scaladsl`       % "compile;test->test;multi-jvm->multi-jvm",
    `persistence-cassandra-core` % "compile;test->test",
    `api-scaladsl`
  )
  .settings(runtimeLibCommon, mimaSettings)
  .enablePlugins(RuntimeLibPlugins)
  .configure(multiJvm)

lazy val `persistence-jdbc-core` = (project in file("persistence-jdbc/core"))
  .dependsOn(
    `persistence-core` % "compile;test->test"
  )
  .settings(runtimeLibCommon, mimaSettings, forkedTests)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-persistence-jdbc-core",
    Dependencies.`persistence-jdbc-core`
  )
  .configure(multiJvm)

lazy val `persistence-jdbc-javadsl` = (project in file("persistence-jdbc/javadsl"))
  .settings(
    name := "lagom-javadsl-persistence-jdbc",
    Dependencies.`persistence-jdbc-javadsl`
  )
  .dependsOn(
    `persistence-jdbc-core` % "compile;test->test",
    `persistence-core`      % "compile;test->test",
    `persistence-javadsl`   % "compile;test->test;multi-jvm->multi-jvm"
  )
  .settings(runtimeLibCommon, mimaSettings, forkedTests)
  .enablePlugins(RuntimeLibPlugins)
  .configure(multiJvm)

lazy val `persistence-jdbc-scaladsl` = (project in file("persistence-jdbc/scaladsl"))
  .settings(
    name := "lagom-scaladsl-persistence-jdbc",
    Dependencies.`persistence-jdbc-scaladsl`
  )
  .dependsOn(
    `persistence-jdbc-core` % "compile;test->test",
    `persistence-core`      % "compile;test->test",
    `persistence-scaladsl`  % "compile;test->test;multi-jvm->multi-jvm"
  )
  .settings(runtimeLibCommon, mimaSettings, forkedTests)
  .enablePlugins(RuntimeLibPlugins)
  .configure(multiJvm)

lazy val `persistence-jpa-javadsl` = (project in file("persistence-jpa/javadsl"))
  .dependsOn(`persistence-jdbc-javadsl` % "compile;test->test")
  .settings(runtimeLibCommon, mimaSettings, forkedTests)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-javadsl-persistence-jpa",
    Dependencies.`persistence-jpa-javadsl`,
    Dependencies.allowedDependencies ++= Dependencies.JpaTestWhitelist
  )

lazy val `broker-javadsl` = (project in file("service/javadsl/broker"))
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-javadsl-broker",
    Dependencies.`broker-javadsl`
  )
  .settings(runtimeLibCommon, mimaSettings)
  .dependsOn(`api-javadsl`, `persistence-javadsl`)

lazy val `broker-scaladsl` = (project in file("service/scaladsl/broker"))
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-scaladsl-broker",
    Dependencies.`broker-scaladsl`
  )
  .settings(runtimeLibCommon, mimaSettings)
  .dependsOn(`api-scaladsl`, `persistence-scaladsl`)

lazy val `kafka-client` = (project in file("service/core/kafka/client"))
  .enablePlugins(RuntimeLibPlugins)
  .settings(runtimeLibCommon, mimaSettings, forkedTests)
  .settings(
    name := "lagom-kafka-client",
    Dependencies.`kafka-client`
  )
  .dependsOn(`api`)

lazy val `kafka-client-javadsl` = (project in file("service/javadsl/kafka/client"))
  .enablePlugins(RuntimeLibPlugins)
  .settings(runtimeLibCommon, mimaSettings)
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
  .settings(runtimeLibCommon, mimaSettings)
  .dependsOn(`api-scaladsl`, `kafka-client`)

lazy val `kafka-broker` = (project in file("service/core/kafka/server"))
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    mimaSettings,
    name := "lagom-kafka-broker",
    Dependencies.`kafka-broker`
  )
  .settings(runtimeLibCommon)
  .dependsOn(`api`, `persistence-core`, `projection-core`, `kafka-client`)

lazy val `kafka-broker-javadsl` = (project in file("service/javadsl/kafka/server"))
  .enablePlugins(RuntimeLibPlugins)
  .settings(runtimeLibCommon, mimaSettings, forkedTests)
  .settings(
    name := "lagom-javadsl-kafka-broker",
    Dependencies.`kafka-broker-javadsl`,
    generateKafkaServerClasspathForTests("com.lightbend.lagom.internal.javadsl.broker.kafka"),
  )
  .dependsOn(
    `broker-javadsl`,
    `kafka-broker`,
    `kafka-client-javadsl`,
    `server-javadsl`,
    logback             % Test,
    `server-containers` % Test,
  )

lazy val `kafka-broker-scaladsl` = (project in file("service/scaladsl/kafka/server"))
  .enablePlugins(RuntimeLibPlugins)
  .settings(runtimeLibCommon, mimaSettings, forkedTests)
  .settings(
    name := "lagom-scaladsl-kafka-broker",
    Dependencies.`kafka-broker-scaladsl`,
    generateKafkaServerClasspathForTests("com.lightbend.lagom.scaladsl.kafka.broker"),
  )
  .dependsOn(
    `broker-scaladsl`,
    `kafka-broker`,
    `kafka-client-scaladsl`,
    `server-scaladsl`,
    logback             % Test,
    `server-containers` % Test,
  )

lazy val logback = (project in file("logback"))
  .enablePlugins(RuntimeLibPlugins)
  .settings(runtimeLibCommon, mimaSettings)
  .settings(
    name := "lagom-logback",
    Dependencies.logback
  )
  .settings(overridesScalaParserCombinators)

lazy val log4j2 = (project in file("log4j2"))
  .enablePlugins(RuntimeLibPlugins)
  .settings(runtimeLibCommon, mimaSettings)
  .settings(
    name := "lagom-log4j2",
    Dependencies.log4j2
  )
  .settings(overridesScalaParserCombinators)

lazy val devEnvironmentProjects = Seq[ProjectReference](
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
  `server-containers`,
  `kafka-server`
)

lazy val `dev-environment` = (project in file("dev"))
  .settings(name := "lagom-dev")
  .settings(common, noMima)
  .enablePlugins(HeaderPlugin)
  .aggregate(devEnvironmentProjects: _*)
  .settings(
    crossScalaVersions := Nil,
    scalaVersion := Dependencies.Versions.Scala.head,
    PgpKeys.publishSigned := {},
    publishLocal := {},
    publishArtifact in Compile := false,
    publish := {}
  )

lazy val `reloadable-server` = (project in file("dev") / "reloadable-server")
  .settings(runtimeLibCommon, mimaSettings)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-reloadable-server",
    Dependencies.`reloadable-server`
  )
  .settings(overridesScalaParserCombinators)
  .dependsOn(`dev-mode-ssl-support`)

lazy val `server-containers` = (project in file("dev") / "server-containers")
  .enablePlugins(HeaderPlugin, SonatypeOnly)
  .settings(
    common,
    mimaSettings,
    runtimeScalaSettings,
    name := "lagom-server-containers",
    resolvers += Resolver.sbtPluginRepo("releases"), // weird sbt-pgp/lagom docs/vegemite issue
    Dependencies.`server-containers`,
    publishMavenStyle := true,
    sonatypeSettings,
    // must support both 2.10 for sbt 0.13 and 2.13 for 2.13 tests
    crossScalaVersions := (Dependencies.Versions.Scala ++ Dependencies.Versions.SbtScala).distinct,
  )

def withLagomVersion(p: Project): Project =
  p.settings(
    sourceGenerators in Compile += Def.task {
      Generators.version(
        version.value,
        Dependencies.Versions.Akka,
        Dependencies.Versions.AkkaHttp,
        Dependencies.Versions.Play,
        (sourceManaged in Compile).value
      )
    }.taskValue,
  )

def sharedBuildToolSupportSetup(p: Project): Project =
  withLagomVersion(p)
    .enablePlugins(HeaderPlugin, SonatypeOnly)
    .settings(sonatypeSettings, common, mimaSettings)
    .settings(
      name := s"lagom-${thisProject.value.id}",
      Dependencies.`build-tool-support`,
    )
    .dependsOn(`server-containers`)

lazy val `build-tool-support` = (project in file("dev") / "build-tool-support")
  .configure(sharedBuildToolSupportSetup)
  .settings(
    publishMavenStyle := true,
    crossScalaVersions := Seq(Dependencies.Versions.Scala.head),
    scalaVersion := Dependencies.Versions.Scala.head,
    crossPaths := false,
  )

// This is almost the same as `build-tool-support`, but targeting sbt
// while `build-tool-support` targets Maven and possibly other build
// systems. We did something similar for routes compiler in Play:
//
// https://github.com/playframework/playframework/blob/2.6.7/framework/build.sbt#L27-L40
lazy val `sbt-build-tool-support` = (project in file("dev") / "build-tool-support")
  .configure(sharedBuildToolSupportSetup)
  .settings(
    crossScalaVersions := Dependencies.Versions.SbtScala,
    scalaVersion := Dependencies.Versions.SbtScala.head,
    sbtVersion in pluginCrossBuild := Dependencies.Versions.TargetSbt1,
    sbtPlugin := true,
    scriptedDependencies := (()),
    target := target.value / "lagom-sbt-build-tool-support",
  )

lazy val `sbt-plugin` = (project in file("dev") / "sbt-plugin")
  .settings(common, mimaSettings, scriptedSettings)
  .enablePlugins(HeaderPlugin, BintrayPlugin && PluginsAccessor.exclude(Sonatype), SbtPlugin)
  .settings(
    name := "lagom-sbt-plugin",
    crossScalaVersions := Dependencies.Versions.SbtScala,
    scalaVersion := Dependencies.Versions.SbtScala.head,
    sbtVersion in pluginCrossBuild := Dependencies.Versions.TargetSbt1,
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
    scriptedDependencies := (()),
    publishScriptedDependencies := {
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
      val () = (publishLocal in `cluster-scaladsl`).value
      val () = (publishLocal in `projection-core`).value
      val () = (publishLocal in `projection-scaladsl`).value
      val () = (publishLocal in `projection-javadsl`).value
      val () = (publishLocal in `immutables`).value
      val () = (publishLocal in `jackson`).value
      val () = (publishLocal in `logback`).value
      val () = (publishLocal in `persistence-core`).value
      val () = (publishLocal in `persistence-javadsl`).value
      val () = (publishLocal in `persistence-scaladsl`).value
      val () = (publishLocal in `persistence-testkit`).value
      val () = (publishLocal in `persistence-cassandra-core`).value
      val () = (publishLocal in `persistence-cassandra-javadsl`).value
      val () = (publishLocal in `persistence-cassandra-scaladsl`).value
      val () = (publishLocal in `persistence-jdbc-core`).value
      val () = (publishLocal in `persistence-jdbc-scaladsl`).value
      val () = (publishLocal in `persistence-jdbc-javadsl`).value
      val () = (publishLocal in `persistence-jpa-javadsl`).value
      val () = (publishLocal in `play-json`).value
      val () = (publishLocal in `server`).value
      val () = (publishLocal in `server-javadsl`).value
      val () = (publishLocal in `server-scaladsl`).value
      val () = (publishLocal in `spi`).value
      val () = (publishLocal in `testkit-core`).value
      val () = (publishLocal in `broker-javadsl`).value
      val () = (publishLocal in `broker-scaladsl`).value
      val () = (publishLocal in `kafka-broker`).value
      val () = (publishLocal in `kafka-client`).value
      val () = (publishLocal in `kafka-broker-scaladsl`).value
      val () = (publishLocal in `kafka-client-scaladsl`).value
      val () = (publishLocal in `pubsub-javadsl`).value
      val () = (publishLocal in `pubsub-scaladsl`).value
      val () = (publishLocal in `testkit-javadsl`).value
      val () = (publishLocal in `testkit-scaladsl`).value

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
      val () = (publishLocal in `server-containers`).value
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
  .enablePlugins(lagom.SbtMavenPlugin, HeaderPlugin, SonatypeOnly, Unidoc)
  .settings(sonatypeSettings, common, mimaSettings, publishMavenStyleSettings)
  .settings(
    name := "Lagom Maven Plugin",
    description := "Provides Lagom development environment support to maven.",
    Dependencies.`maven-plugin`,
    mavenClasspath := (externalDependencyClasspath in (`maven-launcher`, Compile)).value.map(_.data),
    // This ensure that files in maven-test are also included
    headerSources in Compile ++= (sourceDirectory.value / "maven-test" ** ("*.scala" || "*.java")).get,
    mavenTestArgs := Seq(
      "-Xmx768m",
      "-XX:MaxMetaspaceSize=384m",
      "-Dhttps.protocols=TLSv1,TLSv1.1,TLSv1.2", // avoid TLS 1.3 => issues w/ jdk 11
      s"-Dlagom.version=${version.value}",
      s"-DarchetypeVersion=${version.value}",
      "-Dorg.slf4j.simpleLogger.showLogName=false",
      "-Dorg.slf4j.simpleLogger.showThreadName=false"
    ),
    pomExtra ~= (existingPomExtra => {
      existingPomExtra ++
        <prerequisites>
          <maven>{CrossVersion.partialVersion(Dependencies.Versions.Maven).get.productIterator.mkString(".")}</maven>
        </prerequisites>
    })
  )
  .dependsOn(`build-tool-support`)

lazy val `maven-launcher` = (project in file("dev") / "maven-launcher")
  .settings(
    sbtScalaSettings,
    name := "lagom-maven-launcher",
    description := "Dummy project, exists only to resolve the maven launcher classpath",
    Dependencies.`maven-launcher`
  )

def scriptedSettings: Seq[Setting[_]] =
  Seq(scriptedLaunchOpts += s"-Dproject.version=${version.value}") ++
    Seq(
      scripted := scripted.tag(Tags.Test).evaluated,
      scriptedBufferLog := false,
      scriptedLaunchOpts ++= Seq(
        "-Xmx512m",
        "-XX:MaxMetaspaceSize=512m",
        "-Dscala.version=" + sys.props
          .get("scripted.scala.version")
          .getOrElse((scalaVersion in `reloadable-server`).value),
        s"-Dsbt.boot.directory=${file(sys.props("user.home")) / ".sbt" / "boot"}",
      ) ++ sys.props.get("lagom.build.akka.version").map(v => s"-Dlagom.build.akka.version=$v").toSeq
    )

def archetypeVariables(lagomVersion: String) = Map(
  "LAGOM-VERSION" -> lagomVersion
)

val ArchetypeVariablePattern = "%([A-Z-]+)%".r

def archetypeProject(archetypeName: String) =
  Project(s"maven-$archetypeName-archetype", file("dev") / "archetypes" / s"maven-$archetypeName")
    .enablePlugins(HeaderPlugin, SonatypeOnly)
    .settings(sonatypeSettings, common, mimaSettings, sbtScalaSettings, publishMavenStyleSettings)
    .settings(
      name := s"maven-archetype-lagom-$archetypeName",
      autoScalaLibrary := false,
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
  .enablePlugins(HeaderPlugin, SonatypeOnly)
  .settings(sonatypeSettings, common, noMima, sbtScalaSettings, publishMavenStyleSettings)
  .settings(
    name := "lagom-maven-dependencies",
    autoScalaLibrary := false,
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
        Dependencies.AllowedDependencies.value
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
  )

// This project doesn't get aggregated, it is only executed by the sbt-plugin scripted dependencies
lazy val `sbt-scripted-tools` = (project in file("dev") / "sbt-scripted-tools")
  .enablePlugins(HeaderPlugin, SonatypeOnly)
  .settings(sonatypeSettings, common, mimaSettings)
  .settings(
    name := "lagom-sbt-scripted-tools",
    sbtPlugin := true,
    scriptedDependencies := (()),
    crossScalaVersions := Dependencies.Versions.SbtScala,
    scalaVersion := Dependencies.Versions.SbtScala.head,
    sbtVersion in pluginCrossBuild := Dependencies.Versions.TargetSbt1,
  )
  .dependsOn(`sbt-plugin`)

// This project also get aggregated, it is only executed by the sbt-plugin scripted dependencies
lazy val `sbt-scripted-library` = (project in file("dev") / "sbt-scripted-library")
  .settings(runtimeLibCommon, noMima)
  .settings(
    name := "lagom-sbt-scripted-library",
    PgpKeys.publishSigned := {},
    publish := {}
  )
  .dependsOn(`server-javadsl`)

lazy val `service-locator` = (project in file("dev") / "service-registry" / "service-locator")
  .settings(runtimeLibCommon, mimaSettings)
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
  .settings(runtimeLibCommon, mimaSettings, overridesScalaParserCombinators)
  .enablePlugins(RuntimeLibPlugins)

lazy val `service-registry-client-core` = (project in file("dev") / "service-registry" / "client-core")
  .settings(
    name := "lagom-service-registry-client-core",
    Dependencies.`service-registry-client-core`
  )
  .settings(runtimeLibCommon, mimaSettings, overridesScalaParserCombinators)
  .enablePlugins(RuntimeLibPlugins)
  .dependsOn(logback % Test)

lazy val `service-registry-client-javadsl` = (project in file("dev") / "service-registry" / "client-javadsl")
  .settings(
    name := "lagom-service-registry-client",
    Dependencies.`service-registry-client-javadsl`
  )
  .settings(runtimeLibCommon, mimaSettings)
  .enablePlugins(RuntimeLibPlugins)
  .dependsOn(`client-javadsl`, `service-registry-client-core`, immutables % "provided")

lazy val `service-registration-javadsl` = (project in file("dev") / "service-registry" / "registration-javadsl")
  .settings(
    name := "lagom-service-registration",
    Dependencies.`service-registration-javadsl`
  )
  .settings(runtimeLibCommon, mimaSettings)
  .enablePlugins(RuntimeLibPlugins)
  .dependsOn(`server-javadsl`, `service-registry-client-javadsl`)

lazy val `devmode-scaladsl` = (project in file("dev") / "service-registry" / "devmode-scaladsl")
  .settings(
    name := "lagom-scaladsl-dev-mode",
    Dependencies.`devmode-scaladsl`
  )
  .settings(runtimeLibCommon, mimaSettings)
  .enablePlugins(RuntimeLibPlugins)
  .dependsOn(`client-scaladsl`, `service-registry-client-core`)

lazy val `play-integration-javadsl` = (project in file("dev") / "service-registry" / "play-integration-javadsl")
  .settings(
    name := "lagom-javadsl-play-integration",
    Dependencies.`play-integration-javadsl`
  )
  .settings(runtimeLibCommon, mimaSettings)
  .enablePlugins(RuntimeLibPlugins)
  .dependsOn(`service-registry-client-javadsl`)

lazy val `cassandra-server` = (project in file("dev") / "cassandra-server")
  .settings(common, mimaSettings, runtimeScalaSettings, sonatypeSettings)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-cassandra-server",
    Dependencies.`cassandra-server`
  )

lazy val `kafka-server` = (project in file("dev") / "kafka-server")
  .settings(common, mimaSettings, runtimeScalaSettings, sonatypeSettings)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-kafka-server",
    crossScalaVersions -= Dependencies.Versions.Scala213, // No Kafka for Scala 2.13, use Kafka for Scala 2.12
    Dependencies.`kafka-server`
  )

// kafka-server is used to run Kafka in dev mode and in the kafka broker tests, in its own process
// build-tool-support handles this for dev mode
// and there's a smaller version in the kafka broker specs
def generateKafkaServerClasspathForTests(packageName: String): Seq[Setting[_]] = Def.settings(
  BuildInfoPlugin.buildInfoDefaultSettings,
  BuildInfoPlugin.buildInfoScopedSettings(Test),
  Test / buildInfoPackage := packageName,
  Test / buildInfoObject := "TestBuildInfo",
  Test / buildInfoKeys := Seq[BuildInfoKey](fullClasspath in (`kafka-server`, Compile), target),
)

def excludeLog4jFromKafkaServer: Seq[Setting[_]] = Seq(
  libraryDependencies += (projectID in (`kafka-server`, Test)).value.exclude("org.slf4j", "slf4j-log4j12")
)

// Provides macros for testing macros. Is not published.
lazy val `macro-testkit` = (project in file("macro-testkit"))
  .settings(runtimeLibCommon, noMima)
  .settings(
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-reflect" % scalaVersion.value
    ),
    PgpKeys.publishSigned := {},
    publish := {}
  )
