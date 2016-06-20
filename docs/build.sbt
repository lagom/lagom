
val PlayVersion = "2.5.0"
val AkkaVersion = "2.4.4"

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
    javacOptions in compile ++= Seq("-encoding", "UTF-8", "-source", "1.8", "-target", "1.8", "-parameters", "-Xlint:unchecked", "-Xlint:deprecation"),
    testOptions in Test += Tests.Argument("-oDF"),
    testOptions += Tests.Argument(TestFrameworks.JUnit, "-v", "-a"),
    // This is needed so that Java APIs that use immutables will typecheck by the Scala compiler
    compileOrder in Test := CompileOrder.JavaThenScala,

    markdownDocsTitle := "Lagom",
    markdownDocPaths += {
      // What I'd really like to do here is trigger the unidoc task in the root project externally,
      // however I tried that and for some reason it doesn't work.  So instead we'll just depend on
      // it being run manually.
      val javaUnidocTarget = parentDir / "target" / "javaunidoc"
      streams.value.log.info(s"Serving javadocs from $javaUnidocTarget. Rerun unidoc in root project to refresh")
      javaUnidocTarget -> "api"
    },
    markdownApiDocs := Seq(
        "api/index.html" -> "Java"
    ),
    markdownUseBuiltinTheme := false,
    markdownTheme := Some("lagom.LagomMarkdownTheme"),
    markdownGenerateTheme := Some("bare"),
    markdownGenerateIndex := true,
    markdownSourceUrl := Some(url(s"https://github.com/lagom/lagom/tree/$branch/docs/manual/")),

    markdownS3CredentialsHost := "downloads.typesafe.com.s3.amazonaws.com",
    markdownS3Bucket := Some("downloads.typesafe.com"),
    markdownS3Prefix := "rp/lagom/",
    markdownS3Region := awscala.Region0.US_EAST_1,
    excludeFilter in markdownS3PublishDocs ~= {
      _ || "*.scala" || "*.java" || "*.sbt" || "*.conf" || "*.md" || "*.toc"
    }

  ).dependsOn(serviceIntegrationTests, immutables % "test->compile", theme % "run-markdown")

lazy val parentDir = Path.fileProperty("user.dir").getParentFile

// Depend on the integration tests, they should bring everything else in
lazy val serviceIntegrationTests = ProjectRef(parentDir, "service-integration-tests")
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
      "com.lightbend.markdown" %% "lightbend-markdown-server" % LightbendMarkdownVersion,
      "org.webjars" % "jquery" % "1.9.0",
      "org.webjars" % "prettify" % "4-Mar-2013"
    ),
    pipelineStages in Assets := Seq(uglify),
    LessKeys.compress := true
  )
