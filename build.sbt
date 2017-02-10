import java.net.InetSocketAddress
import java.nio.channels.ServerSocketChannel

import sbt.ScriptedPlugin
import com.typesafe.sbt.SbtMultiJvm
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm
import lagom.Protobuf
import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import de.heikoseeberger.sbtheader.HeaderPattern
import com.typesafe.tools.mima.core._

val PlayVersion = "2.5.10"
val AkkaVersion = "2.4.16"
val AkkaPersistenceCassandraVersion = "0.22"
val ScalaTestVersion = "3.0.1"
val JacksonVersion = "2.7.8"
val CassandraAllVersion = "3.0.9"
val GuavaVersion = "19.0"
val MavenVersion = "3.3.9"
val NettyVersion = "4.0.40.Final"
val KafkaVersion = "0.10.0.1"
val AkkaStreamKafka = "0.13"
val Log4j = "1.2.17"
val ScalaJava8CompatVersion = "0.7.0"

val scalaTest = "org.scalatest" %% "scalatest" % ScalaTestVersion
val guava = "com.google.guava" % "guava" % GuavaVersion
val log4J = "log4j" % "log4j" % Log4j
val scalaJava8Compat = "org.scala-lang.modules" %% "scala-java8-compat" % ScalaJava8CompatVersion

def common: Seq[Setting[_]] = releaseSettings ++ bintraySettings ++ Seq(
  organization := "com.lightbend.lagom",
  // Must be "Apache-2.0", because bintray requires that it is a license that it knows about
  licenses := Seq(("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0.html"))),
  homepage := Some(url("http://www.lagomframework.com/")),
  sonatypeProfileName := "com.lightbend",
  headers := headers.value ++ Map(
     "scala" -> (
       HeaderPattern.cStyleBlockComment,
       """|/*
          | * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
          | */
          |""".stripMargin
     ),
     "java" -> (
       HeaderPattern.cStyleBlockComment,
       """|/*
          | * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
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
    <dependencyManagement>
      <dependencies>
        {
        // Here we force the version of every Akka and Netty dependency, since Mavens transitive dependency resolution
        // strategy is so incredibly lame, without using dependency management to force it, you can count on always
        // getting the version you least want.
        // Eventually we should automate this somehow to force the versions of all dependencies to be the same as what
        // sbt resolves, but that's going to require some sbt voodoo. For now, this does the job well enough.
        // todo - put this in a parent pom rather than in each project
        Seq("buffer", "codec", "codec-http", "common", "handler", "transport", "transport-native-epoll").map { nettyDep =>
          <dependency>
            <groupId>io.netty</groupId>
            <artifactId>netty-{nettyDep}</artifactId>
            <version>{NettyVersion}</version>
          </dependency>
        } ++ Seq("actor", "cluster-sharding", "cluster-tools", "cluster", "persistence-query-experimental", "persistence", "protobuf", "remote", "slf4j", "stream-testkit", "stream", "testkit").map { akkaDep =>
          <dependency>
            <groupId>com.typesafe.akka</groupId>
            <artifactId>akka-{akkaDep}_{scalaBinaryVersion.value}</artifactId>
            <version>{AkkaVersion}</version>
          </dependency>
        }
        }
      </dependencies>
    </dependencyManagement>
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
  )
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

def runtimeLibCommon: Seq[Setting[_]] = common ++ Seq(
  crossScalaVersions := Seq("2.11.8"),
  scalaVersion := crossScalaVersions.value.head,
  crossVersion := CrossVersion.binary,
  crossPaths := false,

  dependencyOverrides += "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
  dependencyOverrides += "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion,

  // compile options
  scalacOptions in Compile ++= Seq("-encoding", "UTF-8", "-target:jvm-1.8", "-feature", "-unchecked", "-Xlog-reflective-calls", "-Xlint", "-deprecation"),

  incOptions := incOptions.value.withNameHashing(true),

  // show full stack traces and test case durations
  testOptions in Test += Tests.Argument("-oDF"),
  // -v Log "test run started" / "test started" / "test run finished" events on log level "info" instead of "debug".
  // -a Show stack traces and exception class name for AssertionErrors.
  testOptions += Tests.Argument(TestFrameworks.JUnit, "-v", "-a"),

  ScalariformKeys.preferences in Compile  := formattingPreferences,
  ScalariformKeys.preferences in Test     := formattingPreferences,
  ScalariformKeys.preferences in MultiJvm := formattingPreferences
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
  .aggregate(javadslProjects.map(Project.projectToRef): _*)
  .aggregate(scaladslProjects.map(Project.projectToRef): _*)
  .aggregate(coreProjects.map(Project.projectToRef): _*)
  .aggregate(otherProjects.map(Project.projectToRef): _*)

def RuntimeLibPlugins = AutomateHeaderPlugin && Sonatype && PluginsAccessor.exclude(BintrayPlugin) 
def SbtPluginPlugins = AutomateHeaderPlugin && BintrayPlugin && PluginsAccessor.exclude(Sonatype) 

lazy val api = (project in file("service/core/api"))
  .settings(runtimeLibCommon: _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-api",
    libraryDependencies ++= Seq(
      "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.4",
      "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
      "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion,
      "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
      "com.typesafe.play" %% "play" % PlayVersion
    )
  )


lazy val `api-javadsl` = (project in file("service/javadsl/api"))
  .settings(name := "lagom-javadsl-api")
  .settings(runtimeLibCommon: _*)
  .settings(mimaSettings(since10): _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.play" %% "play-java" % PlayVersion,
      // An explicit depnedency is added on Guava because mavens resolution rule is stupid - it doesn't use the most
      // recent version in the tree, it uses the version that's closest to the root of the tree. So this puts the
      // version we need closer to the root of the tree.
      guava,
      "org.pcollections" % "pcollections" % "2.1.2",
      scalaTest % Test,
      "com.fasterxml.jackson.module" % "jackson-module-parameter-names" % JacksonVersion % Test
    )
  ).dependsOn(api)

lazy val `api-scaladsl` = (project in file("service/scaladsl/api"))
  .settings(name := "lagom-scaladsl-api")
  .settings(runtimeLibCommon: _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    libraryDependencies ++= Seq(
      scalaTest % Test
    )
  ).dependsOn(api)

lazy val immutables = (project in file("immutables"))
  .settings(name := "lagom-javadsl-immutables")
  .settings(mimaSettings(since10): _*)
  .settings(runtimeLibCommon: _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    libraryDependencies += "org.immutables" % "value" % "2.3.2"
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
    libraryDependencies ++= Seq(      
      "com.fasterxml.jackson.module" % "jackson-module-parameter-names" % JacksonVersion,
      "com.fasterxml.jackson.core" % "jackson-core" % JacksonVersion,
      "com.fasterxml.jackson.core" % "jackson-annotations" % JacksonVersion,
      "com.fasterxml.jackson.core" % "jackson-databind" % JacksonVersion,
      "com.fasterxml.jackson.datatype" % "jackson-datatype-pcollections" % JacksonVersion,
      "com.fasterxml.jackson.datatype" % "jackson-datatype-guava" % JacksonVersion,
      "com.fasterxml.jackson.datatype" % "jackson-datatype-jdk8" % JacksonVersion,
      "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310" % JacksonVersion,
      "com.typesafe.akka" %% "akka-testkit" % AkkaVersion % "test",
      scalaTest % Test,
      "com.novocode" % "junit-interface" % "0.11" % "test")
  )
  .dependsOn(`api-javadsl`, immutables % "test->compile")

lazy val `play-json` = (project in file("play-json"))
  .settings(runtimeLibCommon: _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-scaladsl-play-json",
    libraryDependencies ++= Seq(
      "com.typesafe.play" %% "play-json" % PlayVersion,
      "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
      "com.typesafe.akka" %% "akka-testkit" % AkkaVersion,
      scalaTest % Test
    )
  )

lazy val `api-tools` = (project in file("api-tools"))
  .settings(runtimeLibCommon: _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.play" %% "play" % PlayVersion,
      scalaTest % Test
    )
  )
  .dependsOn(
    spi,
    `server-javadsl` % Test
  )

lazy val client = (project in file("service/core/client"))
  .settings(runtimeLibCommon: _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    name := "lagom-client",
    libraryDependencies ++= Seq(
      "com.typesafe.play" %% "play-ws" % PlayVersion,
      "io.dropwizard.metrics" % "metrics-core" % "3.1.2"
    )
  ).dependsOn(api, spi)

lazy val `client-javadsl` = (project in file("service/javadsl/client"))
  .settings(runtimeLibCommon: _*)
  .settings(mimaSettings(since10): _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(name := "lagom-javadsl-client")
  .dependsOn(client, `api-javadsl`, jackson)

lazy val `client-scaladsl` = (project in file("service/scaladsl/client"))
  .settings(runtimeLibCommon: _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(macroCompileSettings: _*)
  .settings(
    name := "lagom-scaladsl-client",
    libraryDependencies ++= Seq(
      scalaTest % Test
    )
  )
  .dependsOn(client, `api-scaladsl`, `macro-testkit` % Test)

lazy val `integration-client-javadsl` = (project in file("service/javadsl/integration-client"))
  .settings(name := "lagom-javadsl-integration-client")
  .settings(mimaSettings(since10): _*)
  .settings(runtimeLibCommon: _*)
  .enablePlugins(RuntimeLibPlugins)
  .dependsOn(`client-javadsl`, `service-registry-client-javadsl`, `kafka-client-javadsl`)

lazy val server = (project in file("service/core/server"))
  .settings(
    name := "lagom-server",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor" % AkkaVersion
    )
  )
  .enablePlugins(RuntimeLibPlugins)
  .settings(runtimeLibCommon: _*)
  .dependsOn(client)


lazy val `server-javadsl` = (project in file("service/javadsl/server"))
  .settings(
    name := "lagom-javadsl-server",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
      guava
    )
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
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
      scalaTest % Test
    )
  )
  .enablePlugins(RuntimeLibPlugins)
  .settings(runtimeLibCommon: _*)
  .dependsOn(server, `client-scaladsl`, `play-json`)

lazy val `testkit-core` = (project in file("testkit/core"))
  .settings(name := "lagom-core-testkit")
  .settings(runtimeLibCommon: _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
      "com.typesafe.akka" %% "akka-stream" % AkkaVersion
    )
  ).settings(forkedTests: _*)


lazy val `testkit-javadsl` = (project in file("testkit/javadsl"))
  .settings(name := "lagom-javadsl-testkit")
  .settings(runtimeLibCommon: _*)
  .settings(mimaSettings(since10): _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(forkedTests: _*)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.play" %% "play-netty-server" % PlayVersion,
      "org.apache.cassandra" % "cassandra-all" % CassandraAllVersion exclude("io.netty", "netty-all"),
      "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
      "com.typesafe.akka" %% "akka-stream-testkit" % AkkaVersion,
      "com.typesafe.akka" %% "akka-persistence-cassandra" % AkkaPersistenceCassandraVersion,
      scalaTest % Test,
      scalaJava8Compat
    ),
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
  .settings(name := "lagom-scaladsl-testkit")
  .settings(runtimeLibCommon: _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(forkedTests: _*)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.play" %% "play-netty-server" % PlayVersion,
      "org.apache.cassandra" % "cassandra-all" % CassandraAllVersion exclude("io.netty", "netty-all"),
      "com.typesafe.akka" %% "akka-stream-testkit" % AkkaVersion,
      "com.typesafe.akka" %% "akka-persistence-cassandra" % AkkaPersistenceCassandraVersion,
      scalaTest % Test
    )
  )
  .dependsOn(`testkit-core`, `server-scaladsl`, `broker-scaladsl`, `persistence-core` % "compile;test->test",
    `persistence-scaladsl` % "compile;test->test", `persistence-cassandra-scaladsl` % "compile;test->test")

lazy val `integration-tests-javadsl` = (project in file("service/javadsl/integration-tests"))
  .settings(name := "lagom-javadsl-integration-tests")
  .settings(runtimeLibCommon: _*)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(forkedTests: _*)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.play" %% "play-netty-server" % PlayVersion,
      "com.novocode" % "junit-interface" % "0.11" % "test",
      scalaTest
    ),
    PgpKeys.publishSigned := {},
    publish := {}
  )
  .dependsOn(`server-javadsl`, `persistence-cassandra-javadsl`, `pubsub-javadsl`, `testkit-javadsl`, logback,
    `integration-client-javadsl`)

lazy val `integration-tests-scaladsl` = (project in file("service/scaladsl/integration-tests"))
  .settings(name := "lagom-scaladsl-integration-tests")
  .settings(runtimeLibCommon: _*)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(forkedTests: _*)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.play" %% "play-netty-server" % PlayVersion,
      "com.novocode" % "junit-interface" % "0.11" % "test",
      scalaTest
    ),
    PgpKeys.publishSigned := {},
    publish := {}
  )
  .dependsOn(`server-scaladsl`, logback, `testkit-scaladsl`)

// for forked tests, necessary for Cassandra
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
  .settings(name := "lagom-cluster-core")
  .settings(runtimeLibCommon: _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-cluster" % AkkaVersion,
      "com.typesafe.akka" %% "akka-testkit" % AkkaVersion % "test",
      scalaTest % Test,
      "com.novocode" % "junit-interface" % "0.11" % "test"
    )
  )

lazy val `cluster-javadsl` = (project in file("cluster/javadsl"))
  .settings(name := "lagom-javadsl-cluster")
  .dependsOn(`cluster-core`, jackson)
  .settings(runtimeLibCommon: _*)
  .settings(mimaSettings(since10): _*)
  .settings(multiJvmTestSettings: _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-testkit" % AkkaVersion % "test",
      "com.typesafe.akka" %% "akka-multi-node-testkit" % AkkaVersion % "test",
      scalaJava8Compat,
      scalaTest % Test,
      "com.novocode" % "junit-interface" % "0.11" % "test",
      "com.google.inject" % "guice" % "4.0"
    )
  ) configs (MultiJvm)

lazy val `cluster-scaladsl` = (project in file("cluster/scaladsl"))
  .settings(name := "lagom-scaladsl-cluster")
  .dependsOn(`cluster-core`, `play-json`)
  .settings(runtimeLibCommon: _*)
  .settings(multiJvmTestSettings: _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-testkit" % AkkaVersion % "test",
      "com.typesafe.akka" %% "akka-multi-node-testkit" % AkkaVersion % "test",
      scalaTest % Test,
      "com.novocode" % "junit-interface" % "0.11" % "test"
    )
  ) configs (MultiJvm)

lazy val `pubsub-javadsl` = (project in file("pubsub/javadsl"))
  .settings(name := "lagom-javadsl-pubsub")
  .dependsOn(`cluster-javadsl`)
  .settings(runtimeLibCommon: _*)
  .settings(mimaSettings(since10): _*)
  .settings(multiJvmTestSettings: _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    libraryDependencies ++= Seq(
      "com.google.inject" % "guice" % "4.0",
      "com.typesafe.akka" %% "akka-cluster-tools" % AkkaVersion,
      scalaJava8Compat,
      "com.typesafe.akka" %% "akka-testkit" % AkkaVersion % "test",
      "com.typesafe.akka" %% "akka-multi-node-testkit" % AkkaVersion % "test",
      "com.typesafe.akka" %% "akka-stream-testkit" % AkkaVersion % "test",
      scalaTest % Test,
      "com.novocode" % "junit-interface" % "0.11" % "test"
    )
  ) configs (MultiJvm)

lazy val `pubsub-scaladsl` = (project in file("pubsub/scaladsl"))
  .settings(name := "lagom-scaladsl-pubsub")
  .dependsOn(`cluster-scaladsl`)
  .settings(runtimeLibCommon: _*)
  .settings(multiJvmTestSettings: _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-cluster-tools" % AkkaVersion,
      "com.typesafe.akka" %% "akka-testkit" % AkkaVersion % "test",
      "com.typesafe.akka" %% "akka-multi-node-testkit" % AkkaVersion % "test",
      "com.typesafe.akka" %% "akka-stream-testkit" % AkkaVersion % "test",
      scalaTest % Test
    )
  ) configs (MultiJvm)

lazy val `persistence-core` = (project in file("persistence/core"))
  .settings(name := "lagom-persistence-core")
  .dependsOn(`cluster-core`)
  .settings(runtimeLibCommon: _*)
  .settings(Protobuf.settings)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    libraryDependencies ++= Seq(
      scalaJava8Compat,
      "com.typesafe.akka" %% "akka-persistence" % AkkaVersion,
      "com.typesafe.akka" %% "akka-persistence-query-experimental" % AkkaVersion,
      "com.typesafe.akka" %% "akka-cluster-sharding" % AkkaVersion,
      "com.typesafe.play" %% "play" % PlayVersion,
      "com.typesafe.akka" %% "akka-testkit" % AkkaVersion % "test",
      "com.typesafe.akka" %% "akka-multi-node-testkit" % AkkaVersion % "test",
      "com.typesafe.akka" %% "akka-stream-testkit" % AkkaVersion % "test",
      scalaTest % Test,
      "com.novocode" % "junit-interface" % "0.11" % "test"
    )
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
    )
  )
  .dependsOn(`persistence-core` % "compile;test->test", jackson, `cluster-javadsl`)
  .settings(runtimeLibCommon: _*)
  .settings(mimaSettings(since12): _*)
  .settings(Protobuf.settings)
  .enablePlugins(RuntimeLibPlugins)

lazy val `persistence-scaladsl` = (project in file("persistence/scaladsl"))
  .settings(name := "lagom-scaladsl-persistence")
  .dependsOn(`persistence-core` % "compile;test->test", `play-json`, `cluster-scaladsl`)
  .settings(runtimeLibCommon: _*)
  .settings(Protobuf.settings)
  .enablePlugins(RuntimeLibPlugins)

lazy val `persistence-cassandra-core` = (project in file("persistence-cassandra/core"))
  .settings(name := "lagom-persistence-cassandra-core")
  .dependsOn(`persistence-core` % "compile;test->test")
  .settings(runtimeLibCommon: _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(forkedTests: _*)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-persistence-cassandra" % AkkaPersistenceCassandraVersion,
      "org.apache.cassandra" % "cassandra-all" % CassandraAllVersion % "test" exclude("io.netty", "netty-all"),
      "io.netty" % "netty-codec-http" % NettyVersion % "test",
      "io.netty" % "netty-transport-native-epoll" % NettyVersion % "test" classifier "linux-x86_64"
    )
  )

lazy val `persistence-cassandra-javadsl` = (project in file("persistence-cassandra/javadsl"))
  .settings(
    name := "lagom-javadsl-persistence-cassandra",
    mimaBinaryIssueFilters ++= Seq(
      ProblemFilters.exclude[DirectMissingMethodProblem]("com.lightbend.lagom.javadsl.persistence.cassandra.CassandraSession.this"),
      ProblemFilters.exclude[FinalClassProblem]("com.lightbend.lagom.javadsl.persistence.cassandra.CassandraSession")
    )
  )
  .dependsOn(`persistence-core` % "compile;test->test", `persistence-javadsl` % "compile;test->test",
    `persistence-cassandra-core` % "compile;test->test", `api-javadsl`)
  .settings(runtimeLibCommon: _*)
  .settings(mimaSettings(since12): _*)
  .settings(multiJvmTestSettings: _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(forkedTests: _*)
  .settings() configs (MultiJvm)

lazy val `persistence-cassandra-scaladsl` = (project in file("persistence-cassandra/scaladsl"))
  .settings(name := "lagom-scaladsl-persistence-cassandra")
  .dependsOn(`persistence-core` % "compile;test->test", `persistence-scaladsl` % "compile;test->test",
    `persistence-cassandra-core` % "compile;test->test", `api-scaladsl`)
  .settings(runtimeLibCommon: _*)
  .settings(multiJvmTestSettings: _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(forkedTests: _*)
  .settings() configs (MultiJvm)


lazy val `persistence-jdbc-core` = (project in file("persistence-jdbc/core"))
  .settings(name := "lagom-persistence-jdbc-core")
  .dependsOn(`persistence-core` % "compile;test->test")
  .settings(runtimeLibCommon: _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(forkedTests: _*)
  .settings(
    libraryDependencies ++= Seq(
      "com.github.dnvriend" %% "akka-persistence-jdbc" % "2.6.8",
      "com.typesafe.play" %% "play-jdbc" % PlayVersion
    )
  )

lazy val `persistence-jdbc-javadsl` = (project in file("persistence-jdbc/javadsl"))
  .settings(name := "lagom-javadsl-persistence-jdbc")
  .dependsOn(`persistence-jdbc-core`, `persistence-core` % "compile;test->test", `persistence-javadsl` % "compile;test->test")
  .settings(runtimeLibCommon: _*)
  .settings(mimaSettings(since12): _*)
  .settings(multiJvmTestSettings: _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(forkedTests: _*) configs (MultiJvm)

lazy val `persistence-jdbc-scaladsl` = (project in file("persistence-jdbc/scaladsl"))
  .settings(name := "lagom-scaladsl-persistence-jdbc")
  .dependsOn(`persistence-jdbc-core`, `persistence-core` % "compile;test->test", `persistence-scaladsl` % "compile;test->test")
  .settings(runtimeLibCommon: _*)
  .settings(multiJvmTestSettings: _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(forkedTests: _*) configs (MultiJvm)

lazy val `persistence-jpa-javadsl` = (project in file("persistence-jpa/javadsl"))
  .settings(name := "lagom-javadsl-persistence-jpa")
  .dependsOn(`persistence-jdbc-javadsl` % "compile;test->test")
  .settings(runtimeLibCommon: _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(forkedTests: _*)
  .settings(
    libraryDependencies ++= Seq(
      "org.hibernate.javax.persistence" % "hibernate-jpa-2.1-api" % "1.0.0.Final" % Provided,
      "org.hibernate" % "hibernate-core" % "5.2.5.Final" % Test
    )
  )

lazy val `broker-javadsl` = (project in file("service/javadsl/broker"))
  .enablePlugins(RuntimeLibPlugins)
  .settings(name := "lagom-javadsl-broker")
  .settings(runtimeLibCommon: _*)
  .settings(mimaSettings(since12): _*)
  .dependsOn(`api-javadsl`, `persistence-javadsl`)

lazy val `broker-scaladsl` = (project in file("service/scaladsl/broker"))
  .enablePlugins(RuntimeLibPlugins)
  .settings(name := "lagom-scaladsl-broker")
  .settings(runtimeLibCommon: _*)
  .dependsOn(`api-scaladsl`, `persistence-scaladsl`)

lazy val `kafka-client` = (project in file("service/core/kafka/client"))
  .enablePlugins(RuntimeLibPlugins)
  .settings(name := "lagom-kafka-client")
  .settings(runtimeLibCommon: _*)
  .settings(forkedTests: _*)
  .settings(
    libraryDependencies ++= Seq(
      "org.slf4j" % "log4j-over-slf4j" % "1.7.21",
      "com.typesafe.akka" %% "akka-stream-kafka" % AkkaStreamKafka exclude("org.slf4j","slf4j-log4j12"),
      "org.apache.kafka" %% "kafka" % KafkaVersion exclude("org.slf4j","slf4j-log4j12") exclude("javax.jms", "jms") exclude("com.sun.jdmk", "jmxtools") exclude("com.sun.jmx", "jmxri"),
      scalaTest % Test
    )
  )
  .dependsOn(`api`)

lazy val `kafka-client-javadsl` = (project in file("service/javadsl/kafka/client"))
  .enablePlugins(RuntimeLibPlugins)
  .settings(runtimeLibCommon: _*)
  .settings(mimaSettings(since12): _*)
  .settings(
    name := "lagom-javadsl-kafka-client",
    mimaBinaryIssueFilters += ProblemFilters.exclude[MissingTypesProblem]("com.lightbend.lagom.javadsl.broker.kafka.KafkaTopicFactory")
  )
  .dependsOn(`api-javadsl`, `kafka-client`)

lazy val `kafka-client-scaladsl` = (project in file("service/scaladsl/kafka/client"))
  .enablePlugins(RuntimeLibPlugins)
  .settings(name := "lagom-scaladsl-kafka-client")
  .settings(runtimeLibCommon: _*)
  .dependsOn(`api-scaladsl`, `kafka-client`)

lazy val `kafka-broker` = (project in file("service/core/kafka/server"))
  .enablePlugins(RuntimeLibPlugins)
  .settings(name := "lagom-kafka-broker")
  .settings(runtimeLibCommon: _*)
  .dependsOn(`api`, `persistence-core`, `kafka-client`)

lazy val `kafka-broker-javadsl` = (project in file("service/javadsl/kafka/server"))
  .enablePlugins(RuntimeLibPlugins)
  .settings(name := "lagom-javadsl-kafka-broker")
  .settings(runtimeLibCommon: _*)
  .settings(mimaSettings(since12): _*)
  .settings(forkedTests: _*)
  .settings(
    libraryDependencies ++= Seq(
      scalaTest % Test
    )
  )
  .dependsOn(`broker-javadsl`, `kafka-broker`, `kafka-client-javadsl`, `server-javadsl`, `kafka-server` % Test, logback % Test)

lazy val `kafka-broker-scaladsl` = (project in file("service/scaladsl/kafka/server"))
  .enablePlugins(RuntimeLibPlugins)
  .settings(name := "lagom-scaladsl-kafka-broker")
  .settings(runtimeLibCommon: _*)
  .settings(forkedTests: _*)
  .settings(
    libraryDependencies ++= Seq(
      scalaTest % Test
    )
  )
  .dependsOn(`broker-scaladsl`, `kafka-broker`, `kafka-client-scaladsl`, `server-scaladsl`, `kafka-server` % Test, logback % Test)

lazy val logback = (project in file("logback"))
  .enablePlugins(RuntimeLibPlugins)
  .settings(runtimeLibCommon: _*)
  .settings(
    name := "lagom-logback",
    libraryDependencies ++= Seq(
      // needed only because we use play.utils.Colors
      "com.typesafe.play" %% "play" % PlayVersion
    ) ++ Seq("logback-core", "logback-classic").map("ch.qos.logback" % _ % "1.1.3")
  )

lazy val log4j2 = (project in file("log4j2"))
  .enablePlugins(RuntimeLibPlugins)
  .settings(runtimeLibCommon: _*)
  .settings(
    name := "lagom-log4j2",
    libraryDependencies ++= Seq(
      "log4j-api",
      "log4j-core",
      "log4j-slf4j-impl"
    ).map("org.apache.logging.log4j" % _ % "2.7") ++ Seq(
      "com.lmax" % "disruptor" % "3.3.6",
      "com.typesafe.play" %% "play" % PlayVersion
    )
  )

lazy val `dev-environment` = (project in file("dev"))
  .settings(name := "lagom-dev")
  .settings(common: _*)
  .enablePlugins(AutomateHeaderPlugin)
  .aggregate(`build-link`, `reloadable-server`, `build-tool-support`, `sbt-plugin`, `maven-plugin`, `service-locator`,
    `service-registration-javadsl`, `cassandra-server`, `play-integration-javadsl`, `devmode-scaladsl`,
    `service-registry-client-javadsl`, 
    `maven-java-archetype`, `kafka-server`)
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
    libraryDependencies ++= Seq(
      "com.typesafe.play" % "play-exceptions" % PlayVersion,
      "com.typesafe.play" % "build-link" % PlayVersion
    )
  )

lazy val `reloadable-server` = (project in file("dev") / "reloadable-server")
  .settings(name := "lagom-reloadable-server")
  .settings(runtimeLibCommon: _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.play" %% "play" % PlayVersion,
      "com.typesafe.play" %% "play-server" % PlayVersion
    )
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
    libraryDependencies ++= Seq(
      "com.lightbend.play" %% "play-file-watch" % "1.0.0",
      // This is used in the code to check if the embedded cassandra server is started
      "com.datastax.cassandra" % "cassandra-driver-core" % "3.0.0",
      scalaTest % Test
    )
  ).dependsOn(`build-link`)

lazy val `sbt-plugin` = (project in file("dev") / "sbt-plugin")
  .settings(name := "lagom-sbt-plugin")
  .settings(common: _*)
  .settings(scriptedSettings: _*)
  .enablePlugins(SbtPluginPlugins)
  .settings(
    sbtPlugin := true,
    libraryDependencies ++= Seq(
      // And this is needed to silence the datastax driver logging
      "org.slf4j" % "slf4j-nop" % "1.7.14",
      scalaTest % Test
    ),
    addSbtPlugin(("com.typesafe.play" % "sbt-plugin" % PlayVersion).exclude("org.slf4j","slf4j-simple")),
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
    libraryDependencies ++= Seq(
      "org.apache.maven" % "maven-plugin-api" % MavenVersion,
      "org.apache.maven" % "maven-core" % MavenVersion,
      "org.apache.maven.plugin-testing" % "maven-plugin-testing-harness" % "3.3.0" % Test,
      scalaTest % Test
    ),
    publishMavenStyle := true,
    crossPaths := false,
    mavenClasspath := (externalDependencyClasspath in (`maven-launcher`, Compile)).value.map(_.data),
    mavenTestArgs := Seq(
      "-Xmx768m",
      "-XX:MaxMetaspaceSize=384m",
      s"-Dlagom.version=${version.value}",
      s"-DarchetypeVersion=${version.value}",
      s"-Dplay.version=$PlayVersion",
      s"-Dakka.version=$AkkaVersion",
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
      libraryDependencies := Seq(
        // These dependencies come from https://github.com/apache/maven/blob/master/apache-maven/pom.xml, they are
        // what maven bundles into its own distribution.
        "org.apache.maven" % "maven-embedder" % MavenVersion,
        ("org.apache.maven.wagon" % "wagon-http" % "2.10")
          .classifier("shaded")
          .exclude("org.apache.maven.wagon", "wagon-http-shared4")
          .exclude("org.apache.httpcomponents", "httpclient")
          .exclude("org.apache.httpcomponents", "httpcore"),
        "org.apache.maven.wagon" % "wagon-file" % "2.10",
        "org.eclipse.aether" % "aether-connector-basic" % "1.0.2.v20150114",
        "org.eclipse.aether" % "aether-transport-wagon" % "1.0.2.v20150114",
        "org.slf4j" % "slf4j-simple" % "1.7.21"
      )
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
  "LAGOM-VERSION" -> lagomVersion,
  "PLAY-VERSION" -> PlayVersion,
  "AKKA-VERSION" -> AkkaVersion
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
      }
    ).disablePlugins(EclipsePlugin)

lazy val `maven-java-archetype` = archetypeProject("java")

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
  .settings(name := "lagom-service-locator")
  .settings(runtimeLibCommon: _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    libraryDependencies ++= Seq(
      // Explicit akka dependency because maven chooses the wrong version
      "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
      "com.typesafe.play" %% "play-netty-server" % PlayVersion,
      // Need to upgrade Netty due to encountering this deadlock in the service gateway
      // https://github.com/netty/netty/pull/5110
      "io.netty" % "netty-codec-http" % NettyVersion,
      scalaTest % Test
    )
  )
  .dependsOn(`server-javadsl`, logback, `service-registry-client-javadsl`)

lazy val `service-registry-client-javadsl` = (project in file("dev") / "service-registry" / "client-javadsl")
  .settings(name := "lagom-service-registry-client")
  .settings(runtimeLibCommon: _*)
  .enablePlugins(RuntimeLibPlugins)
  .dependsOn(`client-javadsl`, immutables % "provided")

lazy val `service-registration-javadsl` = (project in file("dev") / "service-registry" / "registration-javadsl")
  .settings(name := "lagom-service-registration")
  .settings(runtimeLibCommon: _*)
  .enablePlugins(RuntimeLibPlugins)
  .dependsOn(`server-javadsl`, `service-registry-client-javadsl`)

lazy val `devmode-scaladsl` = (project in file("dev") / "service-registry" / "devmode-scaladsl")
  .settings(name := "lagom-scaladsl-dev-mode")
  .settings(runtimeLibCommon: _*)
  .enablePlugins(RuntimeLibPlugins)
  .dependsOn(`client-scaladsl`)

lazy val `play-integration-javadsl` = (project in file("dev") / "service-registry" / "play-integration-javadsl")
  .settings(name := "lagom-javadsl-play-integration")
  .settings(runtimeLibCommon: _*)
  .enablePlugins(RuntimeLibPlugins)
  .dependsOn(`service-registry-client-javadsl`)

lazy val `cassandra-server` = (project in file("dev") / "cassandra-server")
  .settings(name := "lagom-cassandra-server")
  .settings(runtimeLibCommon: _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    libraryDependencies ++= Seq(
      // Cassandra goes into 100% CPU spin when starting with netty jars of different versions. Hence, 
      // we are making sure that the only netty dependency comes from cassandra-all, and manually excludes 
      // all netty transitive dependencies of akka-persistence-cassandra. Mind that dependencies are 
      // excluded one-by-one because exclusion rules do not work with maven dependency resolution - see
      // https://github.com/lagom/lagom/issues/26#issuecomment-196718818
      "com.typesafe.akka" %% "akka-persistence-cassandra" % AkkaPersistenceCassandraVersion 
        exclude("io.netty", "netty-all") exclude("io.netty", "netty-handler") exclude("io.netty", "netty-buffer")
        exclude("io.netty", "netty-common") exclude("io.netty", "netty-transport") exclude("io.netty", "netty-codec"),
      "org.apache.cassandra" % "cassandra-all" % CassandraAllVersion
    )
  )

lazy val `kafka-server` = (project in file("dev") / "kafka-server")
  .settings(name := "lagom-kafka-server")
  .settings(runtimeLibCommon: _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    libraryDependencies ++= Seq(
      "org.apache.kafka" %% "kafka" % KafkaVersion,
      // log4j version prior to 1.2.17 required javax.jms, and that artifact could not properly resolved when using maven
      // without adding a resolver. The problem doesn't appear with sbt because the log4j version brought by both zookeeper 
      // and curator dependencies are evicted to version 1.2.17. Unfortunately, because of how maven resolution works, we 
      // have to explicitly add the desired log4j version we want to use here.
      // By the way, log4j 1.2.17 and later resolve the javax.jms dependency issue by using geronimo-jms. See 
      // http://stackoverflow.com/questions/4908651/the-following-artifacts-could-not-be-resolved-javax-jmsjmsjar1-1 
      // for more context. 
      log4J,
      // Note that curator 3.x is only compatible with zookeper 3.5.x. Kafka currently uses zookeeper 3.4, hence we have 
      // to use curator 2.x, which is compatible with zookeeper 3.4 (see the notice in
      // http://curator.apache.org/index.html - make sure to scroll to the bottom)
      "org.apache.curator" % "curator-framework" % "2.10.0",
      "org.apache.curator" % "curator-test" % "2.10.0",
      scalaJava8Compat,
      scalaTest % Test
    )
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
