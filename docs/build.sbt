import akka.JavaVersion
import akka.CrossJava

val ScalaVersion = "2.12.9"

val AkkaVersion: String   = sys.props.getOrElse("lagom.build.akka.version", "2.6.0-M5")
val JUnitVersion          = "4.12"
val JUnitInterfaceVersion = "0.11"
val ScalaTestVersion      = "3.0.8"
val PlayVersion           = "2.8.0-M4"
val Log4jVersion          = "2.12.1"
val MacWireVersion        = "2.3.2"
val LombokVersion         = "1.18.8"
val HibernateVersion      = "5.4.4.Final"
val ValidationApiVersion  = "2.0.1.Final"

val branch = {
  import scala.sys.process._
  val rev = "git rev-parse --abbrev-ref HEAD".!!.trim
  if (rev == "HEAD") {
    // not on a branch, get the hash
    "git rev-parse HEAD".!!.trim
  } else rev
}

def evictionSettings: Seq[Setting[_]] = Seq(
  // This avoids a lot of dependency resolution warnings to be showed.
  // No need to show them here since it is the docs project.
  evictionWarningOptions in update := EvictionWarningOptions.default
    .withWarnTransitiveEvictions(false)
    .withWarnDirectEvictions(false)
)

lazy val docs = project
  .in(file("."))
  .enablePlugins(LightbendMarkdown, AutomateHeaderPlugin)
  .settings(forkedTests: _*)
  .settings(evictionSettings: _*)
  .settings(
    resolvers += Resolver.typesafeIvyRepo("releases"),
    scalaVersion := ScalaVersion,
    libraryDependencies ++= Seq(
      "com.typesafe.akka"        %% "akka-stream-testkit"   % AkkaVersion % "test",
      "junit"                    % "junit"                  % JUnitVersion % "test",
      "com.novocode"             % "junit-interface"        % JUnitInterfaceVersion % "test",
      "org.scalatest"            %% "scalatest"             % ScalaTestVersion % Test,
      "com.typesafe.play"        %% "play-akka-http-server" % PlayVersion % Test,
      "com.typesafe.play"        %% "play-logback"          % PlayVersion % Test,
      "org.apache.logging.log4j" % "log4j-api"              % Log4jVersion % "test",
      "com.softwaremill.macwire" %% "macros"                % MacWireVersion % "provided",
      "org.projectlombok"        % "lombok"                 % LombokVersion,
      "org.hibernate"            % "hibernate-core"         % HibernateVersion,
      "javax.validation"         % "validation-api"         % ValidationApiVersion
    ),
    scalacOptions ++= Seq("-deprecation", "-Xfatal-warnings"),
    javacOptions ++= Seq(
      "-encoding",
      "UTF-8",
      "-parameters",
      "-Xlint:unchecked",
      "-Xlint:deprecation",
      "-Werror"
    ) ++ JavaVersion.sourceAndTarget(CrossJava.Keys.fullJavaHomes.value("8")),
    testOptions in Test += Tests.Argument("-oDF"),
    testOptions += Tests.Argument(TestFrameworks.JUnit, "-v", "-a"),
    // This is needed so that Java APIs that use immutables will typecheck by the Scala compiler
    compileOrder in Test := CompileOrder.JavaThenScala,
    sourceDirectories in javafmt in Test ++= (unmanagedSourceDirectories in Test).value,
    sourceDirectories in javafmt in Test ++= (unmanagedResourceDirectories in Test).value,
    markdownDocumentation := {
      val javaUnidocTarget = parentDir / "target" / "javaunidoc"
      val unidocTarget     = parentDir / "target" / "unidoc"
      streams.value.log.info(
        s"Serving javadocs from $javaUnidocTarget and scaladocs from $unidocTarget. Rerun unidoc in root project to refresh"
      )
      Seq(
        Documentation(
          "java",
          Seq(
            DocPath(baseDirectory.value / "manual" / "common", "."),
            DocPath(baseDirectory.value / "manual" / "java", "."),
            DocPath(javaUnidocTarget, "api")
          ),
          "Home.html",
          "Java Home",
          Map("api/index.html" -> "API Documentation")
        ),
        Documentation(
          "scala",
          Seq(
            DocPath(baseDirectory.value / "manual" / "common", "."),
            DocPath(baseDirectory.value / "manual" / "scala", "."),
            DocPath(unidocTarget, "api")
          ),
          "Home.html",
          "Scala Home",
          Map("api/index.html" -> "API Documentation")
        )
      )
    },
    markdownUseBuiltinTheme := false,
    markdownTheme := Some("lagom.LagomMarkdownTheme"),
    markdownGenerateTheme := Some("bare"),
    markdownGenerateIndex := true,
    markdownStageIncludeWebJars := false,
    markdownSourceUrl := Some(url(s"https://github.com/lagom/lagom/edit/$branch/docs/manual/")),
    headerLicense := Some(
      HeaderLicense.Custom(
        "Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>"
      )
    ),
  )
  .dependsOn(
    serviceIntegrationTestsJavadsl,
    persistenceJdbcJavadsl,
    persistenceJpaJavadsl,
    serviceIntegrationTestsScaladsl,
    persistenceCassandraScaladsl,
    persistenceJdbcScaladsl,
    testkitJavadsl,
    testkitScaladsl,
    kafkaBrokerScaladsl,
    playJson,
    pubsubScaladsl,
    akkaDiscoveryJavadsl,
    akkaDiscoveryScaladsl,
    immutables % "test->compile",
    theme      % "run-markdown",
    devmodeScaladsl
  )

lazy val parentDir = Path.fileProperty("user.dir").getParentFile

// Depend on the integration tests, they should bring everything else in
lazy val serviceIntegrationTestsJavadsl  = ProjectRef(parentDir, "integration-tests-javadsl")
lazy val serviceIntegrationTestsScaladsl = ProjectRef(parentDir, "integration-tests-scaladsl")
lazy val persistenceJdbcJavadsl          = ProjectRef(parentDir, "persistence-jdbc-javadsl")
lazy val persistenceJdbcScaladsl         = ProjectRef(parentDir, "persistence-jdbc-scaladsl")
lazy val persistenceJpaJavadsl           = ProjectRef(parentDir, "persistence-jpa-javadsl")
lazy val persistenceCassandraScaladsl    = ProjectRef(parentDir, "persistence-cassandra-scaladsl")
lazy val testkitJavadsl                  = ProjectRef(parentDir, "testkit-javadsl")
lazy val testkitScaladsl                 = ProjectRef(parentDir, "testkit-scaladsl")
lazy val playJson                        = ProjectRef(parentDir, "play-json")
lazy val kafkaBrokerScaladsl             = ProjectRef(parentDir, "kafka-broker-scaladsl")
lazy val devmodeScaladsl                 = ProjectRef(parentDir, "devmode-scaladsl")
lazy val pubsubScaladsl                  = ProjectRef(parentDir, "pubsub-scaladsl")
lazy val akkaDiscoveryJavadsl            = ProjectRef(parentDir, "akka-discovery-service-locator-javadsl")
lazy val akkaDiscoveryScaladsl           = ProjectRef(parentDir, "akka-discovery-service-locator-scaladsl")

// Needed to compile test classes using immutables annotation
lazy val immutables = ProjectRef(parentDir, "immutables")

// Pass through system properties starting with "akka"
// Used to set -Dakka.test.timefactor in CI
val defaultJavaOptions = Vector("-Xms256M", "-Xmx512M") ++ sys.props.collect {
  case (key, value) if key.startsWith("akka") => s"-D$key=$value"
}

// for forked tests, necessary for Cassandra
def forkedTests: Seq[Setting[_]] = Seq(
  fork in Test := true,
  concurrentRestrictions in Global += Tags.limit(Tags.Test, 1),
  javaOptions in Test ++= defaultJavaOptions,
  testGrouping in Test := (definedTests in Test).map(singleTestsGrouping).value
)

// group tests, a single test per group
def singleTestsGrouping(tests: Seq[TestDefinition]) = {
  // We could group non Cassandra tests into another group
  // to avoid new JVM for each test, see https://www.scala-sbt.org/release/docs/Testing.html
  tests.map { test =>
    Tests.Group(
      name = test.name,
      tests = Seq(test),
      runPolicy = Tests.SubProcess(ForkOptions().withRunJVMOptions(defaultJavaOptions)),
    )
  }
}

lazy val theme = project
  .in(file("theme"))
  .enablePlugins(SbtWeb, SbtTwirl)
  .settings(
    name := "lagom-docs-theme",
    scalaVersion := ScalaVersion,
    resolvers += Resolver.typesafeIvyRepo("releases"),
    libraryDependencies ++= Seq(
      "com.lightbend.markdown" %% "lightbend-markdown-server" % LightbendMarkdownVersion
    )
  )
