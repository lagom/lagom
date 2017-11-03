val ScalaVersion = "2.11.11"

val AkkaVersion = "2.5.6"
val JUnitVersion = "4.11"
val JUnitInterfaceVersion = "0.11"
val ScalaTestVersion = "3.0.4"
val PlayVersion = "2.6.7"
val Log4jVersion = "2.8.2"
val MacWireVersion = "2.3.0"
val LombokVersion = "1.16.10"
val HibernateVersion = "5.2.5.Final"

val branch = {
  val rev = "git rev-parse --abbrev-ref HEAD".!!.trim
  if (rev == "HEAD") {
    // not on a branch, get the hash
    "git rev-parse HEAD".!!.trim
  } else rev
}


lazy val docs = project
  .in(file("."))
  .enablePlugins(LightbendMarkdown)
  .settings(forkedTests: _*)
  .settings(
    resolvers += Resolver.typesafeIvyRepo("releases"),
    scalaVersion := ScalaVersion,
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-stream-testkit" % AkkaVersion % "test",
      "junit" % "junit" % JUnitVersion % "test",
      "com.novocode" % "junit-interface" % JUnitInterfaceVersion % "test",
      "org.scalatest" %% "scalatest" % ScalaTestVersion % Test,
      "com.typesafe.play" %% "play-netty-server" % PlayVersion % Test,
      "com.typesafe.play" %% "play-logback" % PlayVersion % Test,
      "org.apache.logging.log4j" % "log4j-api" % Log4jVersion % "test",
      "com.softwaremill.macwire" %% "macros" % MacWireVersion % "provided",
      "org.projectlombok" % "lombok" % LombokVersion,
      "org.hibernate" % "hibernate-core" % HibernateVersion,
      "org.hibernate" % "hibernate-validator" % HibernateVersion
    ),
    javacOptions ++= Seq("-encoding", "UTF-8", "-source", "1.8", "-target", "1.8", "-parameters", "-Xlint:unchecked", "-Xlint:deprecation"),
    testOptions in Test += Tests.Argument("-oDF"),
    testOptions += Tests.Argument(TestFrameworks.JUnit, "-v", "-a"),
    // This is needed so that Java APIs that use immutables will typecheck by the Scala compiler
    compileOrder in Test := CompileOrder.JavaThenScala,

    markdownDocumentation := {
      val javaUnidocTarget = parentDir / "target" / "javaunidoc"
      val unidocTarget = parentDir / "target" / "unidoc"
      streams.value.log.info(s"Serving javadocs from $javaUnidocTarget and scaladocs from $unidocTarget. Rerun unidoc in root project to refresh")
      Seq(
        Documentation("java", Seq(
          DocPath(baseDirectory.value / "manual" / "common", "."),
          DocPath(baseDirectory.value / "manual" / "java", "."),
          DocPath(javaUnidocTarget, "api")
        ), "Home.html", "Java Home", Map("api/index.html" -> "API Documentation")),
        Documentation("scala", Seq(
          DocPath(baseDirectory.value / "manual" / "common", "."),
          DocPath(baseDirectory.value / "manual" / "scala", "."),
          DocPath(unidocTarget, "api")
        ), "Home.html", "Scala Home", Map("api/index.html" -> "API Documentation"))
      )
    },
    markdownUseBuiltinTheme := false,
    markdownTheme := Some("lagom.LagomMarkdownTheme"),
    markdownGenerateTheme := Some("bare"),
    markdownGenerateIndex := true,
    markdownStageIncludeWebJars := false,
    markdownSourceUrl := Some(url(s"https://github.com/lagom/lagom/edit/$branch/docs/manual/"))

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
    immutables % "test->compile",
    theme % "run-markdown",
    devmodeScaladsl
  )

lazy val parentDir = Path.fileProperty("user.dir").getParentFile

// Depend on the integration tests, they should bring everything else in
lazy val serviceIntegrationTestsJavadsl = ProjectRef(parentDir, "integration-tests-javadsl")
lazy val serviceIntegrationTestsScaladsl = ProjectRef(parentDir, "integration-tests-scaladsl")
lazy val persistenceJdbcJavadsl = ProjectRef(parentDir, "persistence-jdbc-javadsl")
lazy val persistenceJdbcScaladsl = ProjectRef(parentDir, "persistence-jdbc-scaladsl")
lazy val persistenceJpaJavadsl = ProjectRef(parentDir, "persistence-jpa-javadsl")
lazy val persistenceCassandraScaladsl = ProjectRef(parentDir, "persistence-cassandra-scaladsl")
lazy val testkitJavadsl = ProjectRef(parentDir, "testkit-javadsl")
lazy val testkitScaladsl = ProjectRef(parentDir, "testkit-scaladsl")
lazy val playJson = ProjectRef(parentDir, "play-json")
lazy val kafkaBrokerScaladsl = ProjectRef(parentDir, "kafka-broker-scaladsl")
lazy val devmodeScaladsl = ProjectRef(parentDir, "devmode-scaladsl")
lazy val pubsubScaladsl = ProjectRef(parentDir, "pubsub-scaladsl")

// Needed to compile test classes using immutables annotation
lazy val immutables = ProjectRef(parentDir, "immutables")

// for forked tests, necessary for Cassandra
def forkedTests: Seq[Setting[_]] = Seq(
  fork in Test := true,
  concurrentRestrictions in Global += Tags.limit(Tags.Test, 1),
  javaOptions in Test ++= Seq("-Xms256M", "-Xmx512M"),
  testGrouping in Test := (definedTests in Test map singleTestsGrouping).value
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
