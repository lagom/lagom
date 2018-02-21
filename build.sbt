import java.net.InetSocketAddress
import java.nio.channels.ServerSocketChannel

import sbt.ScriptedPlugin
import com.typesafe.sbt.SbtMultiJvm
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm
import lagom.Protobuf
import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import de.heikoseeberger.sbtheader.{ HeaderKey, HeaderPattern }
import com.typesafe.tools.mima.core._

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
          | * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
          | */
          |""".stripMargin
     ),
     "java" -> (
       HeaderPattern.cStyleBlockComment,
       """|/*
          | * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
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
      publishArtifacts,
      releaseStepTask(bintrayRelease in thisProjectRef.value),
      releaseStepCommand("sonatypeRelease"),
      setNextVersion,
      commitNextVersion,
      pushChanges
    )
  }
)

def runtimeScalaSettings: Seq[Setting[_]] = Seq(
  crossScalaVersions := Seq(Dependencies.ScalaVersion),
  scalaVersion := crossScalaVersions.value.head,
  crossVersion := CrossVersion.binary,
  crossPaths := false,

  // compile options
  scalacOptions in Compile ++= Seq(
    "-encoding",
    "UTF-8",
    "-target:jvm-1.8",
    "-feature",
    "-unchecked",
    "-Xlog-reflective-calls",
    "-Xlint",
    "-deprecation"
  )
)


def runtimeLibCommon: Seq[Setting[_]] = common ++ runtimeScalaSettings ++ Seq(
  Dependencies.validateDependenciesSetting,
  Dependencies.dependencyWhitelistSetting,

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

def multiJvmTestSettings: Seq[Setting[_]] = SbtMultiJvm.multiJvmSettings ++ Seq(
  parallelExecution in Test := false,
  MultiJvmKeys.jvmOptions in MultiJvm := databasePortSetting :: defaultMultiJvmOptions,
  // make sure that MultiJvm test are compiled by the default test compilation
  compile in MultiJvm <<= (compile in MultiJvm) triggeredBy (compile in Test),
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
  }
)

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
      versions.map { version =>
        organization.value % s"${moduleName.value}_${scalaBinaryVersion.value}" % version
      }.toSet
    },
    mimaBinaryIssueFilters += ProblemFilters.excludePackage("com.lightbend.lagom.internal")
  )
}

def since10 = Seq("1.0.0") ++ since11
def since11 = Seq("1.1.0") ++ since12
def since12 = Seq("1.2.2")

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

val otherProjects = Seq[Project](
  `dev-environment`,
  `integration-tests-javadsl`,
  `integration-tests-scaladsl`
)


val sbtScriptedProjects = Seq[Project](
  `sbt-scripted-tools`,
  `sbt-scripted-library`
)

lazy val root = (project in file("."))
  .settings(name := "lagom")
  .settings(runtimeLibCommon: _*)
  .settings(
    PgpKeys.publishSigned := {},
    publishLocal := {},
    publishArtifact in Compile := false,
    publish := {}
  )
  .enablePlugins(lagom.UnidocRoot)
  .settings(UnidocRoot.settings(javadslProjects.map(Project.projectToRef), scaladslProjects.map(Project.projectToRef)): _*)
  .aggregate((javadslProjects ++ scaladslProjects ++ coreProjects ++ otherProjects ++ sbtScriptedProjects).map(Project.projectToRef): _*)

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
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    Dependencies.`api-scaladsl`
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
    Dependencies.`server-scaladsl`
  )
  .enablePlugins(RuntimeLibPlugins)
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
  .dependsOn(`testkit-core`, `server-javadsl`, `pubsub-javadsl`, `broker-javadsl`,
    `persistence-core` % "compile;test->test",
    `persistence-cassandra-javadsl` % "test->test",
    `jackson` % "test->test"
  )

lazy val `testkit-scaladsl` = (project in file("testkit/scaladsl"))
  .settings(runtimeLibCommon: _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(forkedTests: _*)
  .settings(
    name := "lagom-scaladsl-testkit",
    Dependencies.`testkit-scaladsl`
  )
  .dependsOn(`testkit-core`, `server-scaladsl`, `broker-scaladsl`, `persistence-core` % "compile;test->test",
    `persistence-scaladsl` % "compile;test->test", `persistence-cassandra-scaladsl` % "compile;test->test")

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
  .dependsOn(`server-javadsl`, `persistence-cassandra-javadsl`, `pubsub-javadsl`, `testkit-javadsl`, logback,
    `integration-client-javadsl`)

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
      ProblemFilters.exclude[MissingTypesProblem]("com.lightbend.lagom.javadsl.persistence.PersistentEntity$PersistNone")
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
    Dependencies.`persistence-scaladsl`
  )
  .dependsOn(`persistence-core` % "compile;test->test", `play-json`, `cluster-scaladsl`)
  .settings(runtimeLibCommon: _*)
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
  .dependsOn(`persistence-core` % "compile;test->test", `persistence-javadsl` % "compile;test->test",
    `persistence-cassandra-core` % "compile;test->test", `api-javadsl`)
  .settings(runtimeLibCommon: _*)
  .settings(mimaSettings(since12): _*)
  .settings(multiJvmTestSettings: _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings() configs (MultiJvm)

lazy val `persistence-cassandra-scaladsl` = (project in file("persistence-cassandra/scaladsl"))
  .settings(
    name := "lagom-scaladsl-persistence-cassandra",
    Dependencies.`persistence-cassandra-scaladsl`
  )
  .dependsOn(`persistence-core` % "compile;test->test", `persistence-scaladsl` % "compile;test->test",
    `persistence-cassandra-core` % "compile;test->test", `api-scaladsl`)
  .settings(runtimeLibCommon: _*)
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
  .dependsOn(`persistence-jdbc-core`, `persistence-core` % "compile;test->test", `persistence-javadsl` % "compile;test->test")
  .settings(runtimeLibCommon: _*)
  .settings(mimaSettings(since12): _*)
  .settings(multiJvmTestSettings: _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(forkedTests: _*) configs (MultiJvm)

lazy val `persistence-jdbc-scaladsl` = (project in file("persistence-jdbc/scaladsl"))
  .settings(
    name := "lagom-scaladsl-persistence-jdbc",
    Dependencies.`persistence-jdbc-scaladsl`,
    mimaBinaryIssueFilters ++= Seq(
      ProblemFilters.exclude[UpdateForwarderBodyProblem]("com.lightbend.lagom.scaladsl.persistence.jdbc.WriteSideJdbcPersistenceComponents.slickProvider"),
      ProblemFilters.exclude[MissingTypesProblem]("com.lightbend.lagom.scaladsl.persistence.jdbc.ReadSideJdbcPersistenceComponents")
    )
  )
  .dependsOn(`persistence-jdbc-core`, `persistence-core` % "compile;test->test", `persistence-scaladsl` % "compile;test->test")
  .settings(runtimeLibCommon: _*)
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
  .dependsOn(`broker-javadsl`, `kafka-broker`, `kafka-client-javadsl`, `server-javadsl`, `kafka-server` % Test, logback % Test)

lazy val `kafka-broker-scaladsl` = (project in file("service/scaladsl/kafka/server"))
  .enablePlugins(RuntimeLibPlugins)
  .settings(runtimeLibCommon: _*)
  .settings(forkedTests: _*)
  .settings(
    name := "lagom-scaladsl-kafka-broker",
    Dependencies.`kafka-broker-scaladsl`,
    Dependencies.dependencyWhitelist ++= Dependencies.KafkaTestWhitelist
  )
  .dependsOn(`broker-scaladsl`, `kafka-broker`, `kafka-client-scaladsl`, `server-scaladsl`, `kafka-server` % Test, logback % Test)

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

lazy val `dev-environment` = (project in file("dev"))
  .settings(name := "lagom-dev")
  .settings(common: _*)
  .enablePlugins(AutomateHeaderPlugin)
  .aggregate(`build-link`, `reloadable-server`, `build-tool-support`, `sbt-plugin`, `maven-plugin`, `service-locator`,
    `service-registration-javadsl`, `cassandra-server`, `play-integration-javadsl`, `devmode-scaladsl`,
    `service-registry-client-javadsl`,
    `maven-java-archetype`, `maven-dependencies`, `kafka-server`)
  .settings(
    publish := {},
    PgpKeys.publishSigned := {}
  )

lazy val `build-link` = (project in file("dev") / "build-link")
  .settings(common: _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    crossPaths := false,
    autoScalaLibrary := false,
    EclipseKeys.projectFlavor := EclipseProjectFlavor.Java,
    Dependencies.`build-link`
  )

lazy val `reloadable-server` = (project in file("dev") / "reloadable-server")
  .settings(runtimeLibCommon: _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-reloadable-server",
    Dependencies.`reloadable-server`
  )
  .dependsOn(`build-link`)

lazy val `build-tool-support` = (project in file("dev") / "build-tool-support")
  .settings(common: _*)
  .settings(
    name := "lagom-build-tool-support",
    publishMavenStyle := true,
    crossPaths := false,
    sourceGenerators in Compile += Def.task {
      Generators.version(version.value, (sourceManaged in Compile).value)
    }.taskValue,
    Dependencies.`build-tool-support`
  ).dependsOn(`build-link`)

lazy val `sbt-plugin` = (project in file("dev") / "sbt-plugin")
  .settings(common: _*)
  .settings(scriptedSettings: _*)
  .enablePlugins(SbtPluginPlugins)
  .settings(
    name := "lagom-sbt-plugin",
    sbtPlugin := true,
    Dependencies.`sbt-plugin`,
    addSbtPlugin(("com.typesafe.play" % "sbt-plugin" % Dependencies.PlayVersion).exclude("org.slf4j","slf4j-simple")),
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
      scalaVersion := Dependencies.ScalaVersion,
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
    sbtPlugin := true
  ).dependsOn(`sbt-plugin`)

// This project also get aggregated, it is only executed by the sbt-plugin scripted dependencies
lazy val `sbt-scripted-library` = (project in file("dev") / "sbt-scripted-library")
  .settings(name := "lagom-sbt-scripted-library")
  .settings(runtimeLibCommon: _*)
  .dependsOn(`server-javadsl`)

lazy val `service-locator` = (project in file("dev") / "service-registry"/ "service-locator")
  .settings(runtimeLibCommon: _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-service-locator",
    Dependencies.`service-locator`
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
    Dependencies.`cassandra-server`,
    scalaVersion := Dependencies.ScalaVersion
  )

lazy val `kafka-server` = (project in file("dev") / "kafka-server")
  .settings(common: _*)
  .settings(runtimeScalaSettings: _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-kafka-server",
    Dependencies.`kafka-server`,
    scalaVersion := Dependencies.ScalaVersion
  )

// Provides macros for testing macros. Is not aggregated or published.
lazy val `macro-testkit` = (project in file("macro-testkit"))
  .settings(runtimeLibCommon)
  .settings(libraryDependencies ++= Seq(
    "org.scala-lang" % "scala-reflect" % scalaVersion.value
  ))

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
