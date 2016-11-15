
val PlayVersion = "2.5.0"
val AkkaVersion = "2.4.11"

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
    scalaVersion := "2.11.7",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-stream-testkit" % AkkaVersion % "test",
      "org.apache.cassandra" % "cassandra-all" % "3.0.2" % "test",
      "junit" % "junit" % "4.12" % "test",
      "com.novocode" % "junit-interface" % "0.11" % "test",
      "org.scalatest" %% "scalatest" % "2.2.4" % Test,
      "com.typesafe.play" %% "play-netty-server" % PlayVersion % Test,
      "com.typesafe.play" %% "play-logback" % PlayVersion % Test
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
        ), "Home.html", "Java Home", Map("api/index.html" -> "Java")),
        Documentation("scala", Seq(
          DocPath(baseDirectory.value / "manual" / "common", "."),
          DocPath(baseDirectory.value / "manual" / "scala", "."),
          DocPath(unidocTarget, "api")
        ), "Home.html", "Scala Home", Map("api/index.html" -> "Scala"))
      )
    },
    // Disable scala validation while docs are incomplete
    markdownDocumentation in markdownGenerateRefReports := {
      markdownDocumentation.value.filter { doc =>
        sys.props.get("validate.scala.docs").exists(_ == "true") || doc.name == "java"
      }
    },
    markdownUseBuiltinTheme := false,
    markdownTheme := Some("lagom.LagomMarkdownTheme"),
    markdownGenerateTheme := Some("bare"),
    markdownGenerateIndex := true,
    markdownStageIncludeWebJars := false,
    markdownSourceUrl := Some(url(s"https://github.com/lagom/lagom/edit/$branch/docs/manual/"))

  ).dependsOn(serviceIntegrationTests, persistenceJdbcJavadsl, persistenceJdbcScaladsl, testkitScaladsl,
      kafkaBroker, immutables % "test->compile", theme % "run-markdown")

lazy val parentDir = Path.fileProperty("user.dir").getParentFile

// Depend on the integration tests, they should bring everything else in
lazy val serviceIntegrationTests = ProjectRef(parentDir, "integration-tests-javadsl")
lazy val persistenceJdbcJavadsl = ProjectRef(parentDir, "persistence-jdbc-javadsl")
lazy val persistenceJdbcScaladsl = ProjectRef(parentDir, "persistence-jdbc-scaladsl")
lazy val testkitScaladsl = ProjectRef(parentDir, "testkit-scaladsl")
lazy val kafkaBroker = ProjectRef(parentDir, "kafka-broker")

// Needed to compile test classes using immutables annotation
lazy val immutables = ProjectRef(parentDir, "immutables")

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

lazy val theme = project
  .in(file("theme"))
  .enablePlugins(SbtWeb, SbtTwirl)
  .settings(
    name := "lagom-docs-theme",
    scalaVersion := "2.11.7",
    resolvers += Resolver.typesafeIvyRepo("releases"),
    libraryDependencies ++= Seq(
      "com.lightbend.markdown" %% "lightbend-markdown-server" % LightbendMarkdownVersion
    )
  )
