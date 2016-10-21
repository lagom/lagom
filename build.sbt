import java.net.InetSocketAddress
import java.nio.channels.ServerSocketChannel
import java.util.{Timer, TimerTask}
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}

import sbt.ScriptedPlugin
import Tests._
import com.typesafe.sbt.SbtMultiJvm
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm
import lagom.Protobuf
import com.typesafe.sbt.SbtScalariform
import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import de.heikoseeberger.sbtheader.HeaderPattern

val PlayVersion = "2.5.4"
val AkkaVersion = "2.4.11"
val AkkaPersistenceCassandraVersion = "0.17"
val ScalaTestVersion = "2.2.4"
val JacksonVersion = "2.7.8"
val CassandraAllVersion = "3.0.2"
val GuavaVersion = "19.0"
val MavenVersion = "3.3.9"
val NettyVersion = "4.0.40.Final"
val KafkaVersion = "0.10.0.1"
val AkkaStreamKafka = "0.12"
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
          | * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
          | */
          |""".stripMargin
     ),
     "java" -> (
       HeaderPattern.cStyleBlockComment,
       """|/*
          | * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
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
        // todo - put this in a parent pom rather than in each project
        Seq("buffer", "codec", "codec-http", "common", "handler", "transport", "transport-native-epoll").map { nettyDep =>
          <dependency>
            <groupId>io.netty</groupId>
            <artifactId>netty-{nettyDep}</artifactId>
            <version>{NettyVersion}</version>
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

def runtimeLibCommon: Seq[Setting[_]] = common ++ SbtScalariform.scalariformSettings ++ Seq(
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
  executeTests in MultiJvm <<= (executeTests in MultiJvm) tag(Tags.Test),
  // make sure that MultiJvm tests are executed by the default test target, 
  // and combine the results from ordinary test and multi-jvm tests
  executeTests in Test <<= (executeTests in Test, executeTests in MultiJvm) map {
    case (testResults, multiNodeResults)  =>
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

val javadslProjects = Seq[ProjectReference](
  `api-javadsl`,
  `server-javadsl`,
  `client-javadsl`,
  `broker-javadsl`
)

val coreProjects = Seq[ProjectReference](
  `api-tools`,
  spi,
  jackson,
  `play-json`,
  core,
  cluster,
  pubsub,
  `kafka-client`,
  `kafka-broker`,
  persistence,
  `persistence-javadsl`,
  `persistence-scaladsl`,
  `persistence-cassandra`,
  `persistence-jdbc`,
  `testkit-javadsl`,
  `testkit-scaladsl`,
  logback,
  immutables,
  `integration-client-javadsl`
)

val otherProjects = Seq[ProjectReference](
  `dev-environment`,
  `integration-tests-javadsl`
)

lazy val root = (project in file("."))
  .settings(name := "lagom")
  .settings(common: _*)
  .settings(
    PgpKeys.publishSigned := {},
    publishLocal := {},
    publishArtifact in Compile := false,
    publish := {}
  )
  .enablePlugins(lagom.UnidocRoot)
  .settings(UnidocRoot.settings(Nil, otherProjects ++
    Seq[ProjectReference](`sbt-scripted-library`, `sbt-scripted-tools`)): _*)
  .aggregate(javadslProjects: _*)
  .aggregate(coreProjects: _*)
  .aggregate(otherProjects: _*)

def RuntimeLibPlugins = AutomateHeaderPlugin && Sonatype && PluginsAccessor.exclude(BintrayPlugin) 
def SbtPluginPlugins = AutomateHeaderPlugin && BintrayPlugin && PluginsAccessor.exclude(Sonatype) 

lazy val `api-javadsl` = (project in file("service/javadsl/api"))
  .settings(name := "lagom-javadsl-api")
  .settings(runtimeLibCommon: _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.play" %% "play-java" % PlayVersion,
      "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
      "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion,
      "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
      // An explicit depnedency is added on Guava because mavens resolution rule is stupid - it doesn't use the most
      // recent version in the tree, it uses the version that's closest to the root of the tree. So this puts the
      // version we need closer to the root of the tree.
      guava,
      "org.pcollections" % "pcollections" % "2.1.2",
      "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.4",
      scalaTest % Test,
      "com.fasterxml.jackson.module" % "jackson-module-parameter-names" % JacksonVersion % Test
    )
  ).dependsOn(spi)

lazy val immutables = (project in file("immutables"))
  .settings(name := "lagom-javadsl-immutables")
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
      "com.typesafe.play" %% "play-json" % PlayVersion,
      scalaTest % Test
    )
  )
  .dependsOn(
    `api-javadsl`,
    `server-javadsl` % "compile->test"
  )

lazy val core = (project in file("core"))
  .settings(name := "lagom-core")
  .settings(runtimeLibCommon: _*)
  .enablePlugins(RuntimeLibPlugins)
  .dependsOn(`api-javadsl`, jackson)

lazy val `client-javadsl` = (project in file("service/javadsl/client"))
  .settings(name := "lagom-javadsl-client")
  .settings(runtimeLibCommon: _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.play" %% "play-ws" % PlayVersion,
      "io.dropwizard.metrics" % "metrics-core" % "3.1.2"
    )
  )
  .dependsOn(core)

lazy val `integration-client-javadsl` = (project in file("service/javadsl/integration-client"))
  .settings(name := "lagom-javadsl-integration-client")
  .settings(runtimeLibCommon: _*)
  .enablePlugins(RuntimeLibPlugins)
  .dependsOn(`client-javadsl`, `service-registry-client`, `kafka-client`)

lazy val `server-javadsl` = (project in file("service/javadsl/server"))
  .settings(
    name := "lagom-javadsl-server",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
      guava
    )
  )
  .enablePlugins(RuntimeLibPlugins)
  .settings(runtimeLibCommon: _*)
  .dependsOn(core, `client-javadsl`, immutables % "provided")

lazy val `testkit-javadsl` = (project in file("testkit/javadsl"))
  .settings(name := "lagom-javadsl-testkit")
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
  .dependsOn(`server-javadsl`, pubsub, `broker-javadsl`, persistence % "compile;test->test", `persistence-cassandra` % "test->test")

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
  .dependsOn(persistence % "compile;test->test", `persistence-scaladsl` % "compile;test->test")  

lazy val `integration-tests-javadsl` = (project in file("service/javadsl/integration-tests"))
  .settings(name := "lagom-service-integration-tests")
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
  .dependsOn(`server-javadsl`, `persistence-cassandra`, pubsub, `testkit-javadsl`, logback,`integration-client-javadsl`)

// for forked tests, necessary for Cassandra
def forkedTests: Seq[Setting[_]] = Seq(
  fork in Test := true,
  concurrentRestrictions in Global += Tags.limit(Tags.Test, 1),
  javaOptions in Test ++= Seq("-Xms256M", "-Xmx512M"),
  testGrouping in Test <<= definedTests in Test map singleTestsGrouping
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
      runPolicy = Tests.SubProcess(javaOptions))
  }
}

lazy val cluster = (project in file("cluster"))
  .settings(name := "lagom-javadsl-cluster")
  .dependsOn(`api-javadsl`, jackson)
  .settings(runtimeLibCommon: _*)
  .settings(multiJvmTestSettings: _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-cluster" % AkkaVersion,
      "com.typesafe.akka" %% "akka-testkit" % AkkaVersion % "test",
      "com.typesafe.akka" %% "akka-multi-node-testkit" % AkkaVersion % "test",
      scalaJava8Compat,
      scalaTest % Test,
      "com.novocode" % "junit-interface" % "0.11" % "test",
      "com.google.inject" % "guice" % "4.0"
    )
  ) configs (MultiJvm)

lazy val pubsub = (project in file("pubsub"))
  .settings(name := "lagom-javadsl-pubsub")
  .dependsOn(cluster)
  .settings(runtimeLibCommon: _*)
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

lazy val persistence = (project in file("persistence/core"))
  .settings(name := "lagom-persistence")
  .dependsOn(cluster)
  .settings(runtimeLibCommon: _*)
  .settings(Protobuf.settings)
  .enablePlugins(RuntimeLibPlugins)
  .settings(
    libraryDependencies ++= Seq(
      "com.google.inject" % "guice" % "4.0",
      scalaJava8Compat,
      "com.typesafe.akka" %% "akka-persistence" % AkkaVersion,
      "com.typesafe.akka" %% "akka-persistence-query-experimental" % AkkaVersion,
      "com.typesafe.akka" %% "akka-cluster-sharding" % AkkaVersion,
      "com.typesafe.akka" %% "akka-testkit" % AkkaVersion % "test",
      "com.typesafe.akka" %% "akka-multi-node-testkit" % AkkaVersion % "test",
      "com.typesafe.akka" %% "akka-stream-testkit" % AkkaVersion % "test",
      scalaTest % Test,
      "com.novocode" % "junit-interface" % "0.11" % "test"
    )
  )

lazy val `persistence-javadsl` = (project in file("persistence/javadsl"))
  .settings(name := "lagom-javadsl-persistence")
  .dependsOn(persistence % "compile;test->test")
  .settings(runtimeLibCommon: _*)
  .settings(Protobuf.settings)
  .enablePlugins(RuntimeLibPlugins)

lazy val `persistence-scaladsl` = (project in file("persistence/scaladsl"))
  .settings(name := "lagom-scaladsl-persistence")
  .dependsOn(persistence % "compile;test->test")
  .settings(runtimeLibCommon: _*)
  .settings(Protobuf.settings)
  .enablePlugins(RuntimeLibPlugins)

lazy val `persistence-cassandra` = (project in file("persistence-cassandra"))
  .settings(name := "lagom-javadsl-persistence-cassandra")
  .dependsOn(persistence % "compile;test->test", `persistence-javadsl` % "compile;test->test")
  .settings(runtimeLibCommon: _*)
  .settings(multiJvmTestSettings: _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(forkedTests: _*)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-persistence-cassandra" % AkkaPersistenceCassandraVersion,
      "org.apache.cassandra" % "cassandra-all" % CassandraAllVersion % "test" exclude("io.netty", "netty-all"),
      "io.netty" % "netty-codec-http" % NettyVersion % "test",
      "io.netty" % "netty-transport-native-epoll" % NettyVersion % "test" classifier "linux-x86_64"
    )
  ) configs (MultiJvm)

lazy val `persistence-jdbc` = (project in file("persistence-jdbc"))
  .settings(name := "lagom-javadsl-persistence-jdbc")
  .dependsOn(persistence % "compile;test->test", `persistence-javadsl` % "compile;test->test")
  .settings(runtimeLibCommon: _*)
  .settings(multiJvmTestSettings: _*)
  .enablePlugins(RuntimeLibPlugins)
  .settings(forkedTests: _*)
  .settings(
    libraryDependencies ++= Seq(
      "com.github.dnvriend" %% "akka-persistence-jdbc" % "2.6.7",
      "com.typesafe.play" %% "play-jdbc" % PlayVersion
    )
  ) configs (MultiJvm)

lazy val `kafka-client` = (project in file("kafka-client"))
  .enablePlugins(RuntimeLibPlugins)
  .settings(name := "lagom-javadsl-kafka-client")
  .settings(runtimeLibCommon: _*)
  .settings(
    libraryDependencies ++= Seq(
      "org.slf4j" % "log4j-over-slf4j" % "1.7.21",
      "com.typesafe.akka" %% "akka-stream-kafka" % AkkaStreamKafka exclude("org.slf4j","slf4j-log4j12"),
      "org.apache.kafka" %% "kafka" % KafkaVersion exclude("org.slf4j","slf4j-log4j12") exclude("javax.jms", "jms") exclude("com.sun.jdmk", "jmxtools") exclude("com.sun.jmx", "jmxri"),
      scalaTest % Test
    )
  )
  .dependsOn(`api-javadsl`)

lazy val `broker-javadsl` = (project in file("service/javadsl/broker"))
  .enablePlugins(RuntimeLibPlugins)
  .settings(name := "lagom-javadsl-broker")
  .settings(runtimeLibCommon: _*)
  .dependsOn(`api-javadsl`, `persistence-javadsl`)

lazy val `kafka-broker` = (project in file("kafka-broker"))
  .enablePlugins(RuntimeLibPlugins)
  .settings(name := "lagom-javadsl-kafka-broker")
  .settings(runtimeLibCommon: _*)
  .settings(forkedTests: _*)
  .settings(
    libraryDependencies ++= Seq(
      scalaTest % Test
    )
  )
  .dependsOn(`kafka-client`, `broker-javadsl`, `client-javadsl` % "optional", `kafka-server` % Test, logback % Test, `server-javadsl`)

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

lazy val `dev-environment` = (project in file("dev"))
  .settings(name := "lagom-dev")
  .settings(common: _*)
  .enablePlugins(AutomateHeaderPlugin)
  .aggregate(`build-link`, `reloadable-server`, `build-tool-support`, `sbt-plugin`, `maven-plugin`, `service-locator`,
    `service-registration`, `cassandra-server`, `cassandra-registration`,  `play-integration`, `service-registry-client`,
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
    sourceGenerators in Compile <+= (version, sourceManaged in Compile) map Generators.version,
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
      s"-Dscala.binary.version=${(scalaBinaryVersion in `api-javadsl`).value}",
      "-Dorg.slf4j.simpleLogger.showLogName=false",
      "-Dorg.slf4j.simpleLogger.showThreadName=false"
    )
  ).dependsOn(`build-tool-support`)

lazy val `maven-launcher` = (project in file("dev") / "maven-launcher")
    .settings(
      name := "lagom-maven-launcher",
      description := "Dummy project, exists only to resolve the maven launcher classpath",
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
  Seq(scriptedLaunchOpts <+= version apply { v => s"-Dproject.version=$v" }) ++
  Seq(
    scriptedDependencies := {
      startTick()
      scriptedDependencies.value
    },
    scripted := {
      // this actually get executed *after* scripted is evaluated, since the macro rewrites the below
      // to be a dependency.
      stopTick()
      scripted.evaluated
    },
    scripted <<= scripted.tag(Tags.Test),
    scriptedLaunchOpts ++= Seq(
      "-Xmx768m",
      "-XX:MaxMetaspaceSize=384m",
      "-Dscala.version=" + sys.props.get("scripted.scala.version").getOrElse((scalaVersion in `reloadable-server`).value)
    )
  )

// This outputs a tick tock every minute to ensure travis doesn't decide that the build is frozen
// during scripted tests
val timer = new Timer("scripted-tick-timer", true)
val task = new AtomicReference[TimerTask]()
val ticks = new AtomicInteger()

def startTick(): Unit = synchronized {
  if (ticks.getAndIncrement() == 0) {
    val t = new TimerTask {
      var tick = true
      override def run(): Unit = {
        if (tick) {
          println("tick")
        } else {
          println("tock")
        }
        tick = !tick
      }
    }
    task.set(t)
    timer.schedule(t, 60000, 60000)
  }
}

def stopTick(): Unit = synchronized {
  if (ticks.decrementAndGet() == 0) {
    task.get().cancel()
  }
}

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
          if (pomXml.contains("%LAGOM-VERSION%")) {
            IO.write(pomFile, pomXml.replaceAll("%LAGOM-VERSION%", version.value))
          }
        }
        (copyResources in Compile).value
      }
    )

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

lazy val `service-locator` = (project in file("dev") / "service-locator")
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
  .dependsOn(`server-javadsl`, logback, `service-registry-client`)

lazy val `service-registry-client` = (project in file("dev") / "service-registry-client")
  .settings(name := "lagom-service-registry-client")
  .settings(runtimeLibCommon: _*)
  .enablePlugins(RuntimeLibPlugins)
  .dependsOn(`client-javadsl`, immutables % "provided")

lazy val `service-registration` = (project in file("dev") / "service-registration")
  .settings(name := "lagom-service-registration")
  .settings(runtimeLibCommon: _*)
  .enablePlugins(RuntimeLibPlugins)
  .dependsOn(`server-javadsl`, `service-registry-client`)

lazy val `cassandra-registration` = (project in file("dev") / "cassandra-registration")
  .settings(name := "lagom-cassandra-registration")
  .settings(runtimeLibCommon: _*)
  .enablePlugins(RuntimeLibPlugins)
  .dependsOn(`api-javadsl`, `persistence-cassandra`, `service-registry-client`)

lazy val `play-integration` = (project in file("dev") / "play-integration")
  .settings(name := "lagom-play-integration")
  .settings(runtimeLibCommon: _*)
  .enablePlugins(RuntimeLibPlugins)
  .dependsOn(`service-registry-client`)

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
