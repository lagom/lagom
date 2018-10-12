/**
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */
import sbt.Keys._
import sbt._

object Dependencies {

  object Versions {
    // Version numbers

    // If you update the version of Play, you probably need to update the other Play* variables.
    // Also be sure to update PlayVersion in docs/build.sbt.
    val Play = "2.7.0-M4"
    val PlayJson = "2.6.10"
    val PlayStandaloneWs = "2.0.0-M6"
    val Twirl = "1.4.0-M2"
    val PlayFileWatch = "1.1.7"

    // Also be sure to update AkkaVersion in docs/build.sbt.
    val Akka: String = sys.props.getOrElse("akka.version", "2.5.17")
    val AkkaHttp = "10.1.5"
    val AkkaManagement = "0.17.0"
    // Also be sure to update ScalaVersion in docs/build.sbt.
    val Scala = Seq("2.12.6", "2.11.12")
    val SbtScala = Seq("2.10.6", "2.12.6")
    val AkkaPersistenceCassandra = "0.61"
    val AkkaPersistenceJdbc = "3.4.0"
    // Also be sure to update ScalaTestVersion in docs/build.sbt.
    val ScalaTest = "3.0.4"
    val Jackson = "2.9.6"
    val JacksonCore = Jackson
    val JacksonDatatype = Jackson
    val JacksonDatabind = Jackson
    val Guava = "26.0-jre"
    val Maven = "3.3.9"
    val Netty = "4.1.29.Final"
    val NettyReactiveStreams = "2.0.0"
    val Kafka = "1.0.1"
    val AlpakkaKafka = "0.22"

    val ScalaJava8Compat = "0.8.0"
    val ScalaXml = "1.1.0"
    val Slick = "3.2.3"
    // Also be sure to update JUnitVersion in docs/build.sbt.
    val JUnit = "4.12"
    // Also be sure to update JUnitInterfaceVersion in docs/build.sbt.
    val JUnitInterface = "0.11"

    val Slf4j = "1.7.25"
    val Logback = "1.2.3"
    // Also be sure to update Log4jVersion in docs/build.sbt.
    val Log4j = "2.10.0"

  }

  // Some setup before we start creating ModuleID vals
  private val slf4jApi = "org.slf4j" % "slf4j-api" % Versions.Slf4j
  private val slf4j: Seq[ModuleID] = Seq("jcl-over-slf4j", "jul-to-slf4j", "log4j-over-slf4j").map {
    "org.slf4j" % _ % Versions.Slf4j
  } ++ Seq(slf4jApi)
  private val excludeSlf4j = slf4j.map { moduleId => ExclusionRule(moduleId.organization, moduleId.name) }
  private val log4jModules = Seq(
    "log4j-api",
    "log4j-core",
    "log4j-slf4j-impl"
  ).map("org.apache.logging.log4j" % _ % Versions.Log4j excludeAll (excludeSlf4j: _*))


  // Specific libraries that get reused
  private val scalaTest: ModuleID = "org.scalatest" %% "scalatest" % Versions.ScalaTest excludeAll (excludeSlf4j: _*)
  private val guava = "com.google.guava" % "guava" % Versions.Guava
  private val scalaJava8Compat = "org.scala-lang.modules" %% "scala-java8-compat" % Versions.ScalaJava8Compat
  private val scalaXml = "org.scala-lang.modules" %% "scala-xml" % Versions.ScalaXml
  private val javassist = "org.javassist" % "javassist" % "3.21.0-GA"
  private val scalaParserCombinators = "org.scala-lang.modules" %% "scala-parser-combinators" % "1.1.1"
  private val typesafeConfig = "com.typesafe" % "config" % "1.3.3"
  private val sslConfig = "com.typesafe" %% "ssl-config-core" % "0.3.6"
  private val h2 = "com.h2database" % "h2" % "1.4.192"
  private val cassandraDriverCore = "com.datastax.cassandra" % "cassandra-driver-core" % "3.6.0" excludeAll (excludeSlf4j: _*)

  private val akkaActor = "com.typesafe.akka" %% "akka-actor" % Versions.Akka
  private val akkaRemote = "com.typesafe.akka" %% "akka-remote" % Versions.Akka
  private val akkaCluster = "com.typesafe.akka" %% "akka-cluster" % Versions.Akka
  private val akkaClusterSharding = "com.typesafe.akka" %% "akka-cluster-sharding" % Versions.Akka
  private val akkaClusterTools = "com.typesafe.akka" %% "akka-cluster-tools" % Versions.Akka
  private val akkaMultiNodeTestkit = "com.typesafe.akka" %% "akka-multi-node-testkit" % Versions.Akka
  private val akkaPersistence = "com.typesafe.akka" %% "akka-persistence" % Versions.Akka
  private val akkaPersistenceQuery = "com.typesafe.akka" %% "akka-persistence-query" % Versions.Akka
  private val akkaSlf4j = "com.typesafe.akka" %% "akka-slf4j" % Versions.Akka excludeAll (excludeSlf4j: _*)
  private val akkaStream = "com.typesafe.akka" %% "akka-stream" % Versions.Akka
  private val akkaProfobuf = "com.typesafe.akka" %% "akka-protobuf" % Versions.Akka

  private val akkaStreamTestkit = "com.typesafe.akka" %% "akka-stream-testkit" % Versions.Akka
  private val akkaTestkit = "com.typesafe.akka" %% "akka-testkit" % Versions.Akka
  private val reactiveStreams = "org.reactivestreams" % "reactive-streams" % "1.0.2"

  private val akkaDiscovery = "com.lightbend.akka.discovery" %% "akka-discovery" % Versions.AkkaManagement

  private val akkaPersistenceJdbc = "com.github.dnvriend" %% "akka-persistence-jdbc" % Versions.AkkaPersistenceJdbc excludeAll (excludeSlf4j: _*)

  // latest version of APC depend on a Cassandra driver core that's not compatible with Lagom (newer netty/guava/etc... under the covers)
  private val akkaPersistenceCassandra = "com.typesafe.akka" %% "akka-persistence-cassandra" % Versions.AkkaPersistenceCassandra
  private val akkaPersistenceCassandraLauncher = "com.typesafe.akka" %% "akka-persistence-cassandra-launcher" % Versions.AkkaPersistenceCassandra
  private val akkaStreamKafka = "com.typesafe.akka" %% "akka-stream-kafka" % Versions.AlpakkaKafka

  private val akkaHttpCore = "com.typesafe.akka" %% "akka-http-core" % Versions.AkkaHttp
  private val akkaParsing = "com.typesafe.akka" %% "akka-parsing" % Versions.AkkaHttp

  private val play = "com.typesafe.play" %% "play" % Versions.Play excludeAll (excludeSlf4j: _*)
  private val playBuildLink = "com.typesafe.play" % "build-link" % Versions.Play excludeAll (excludeSlf4j: _*)
  private val playExceptions = "com.typesafe.play" % "play-exceptions" % Versions.Play excludeAll (excludeSlf4j: _*)
  private val playGuice = "com.typesafe.play" %% "play-guice" % Versions.Play excludeAll (excludeSlf4j: _*)
  private val playJava = "com.typesafe.play" %% "play-java" % Versions.Play excludeAll (excludeSlf4j: _*)
  private val playJdbc = "com.typesafe.play" %% "play-jdbc" % Versions.Play excludeAll (excludeSlf4j: _*)
  private val playNettyServer = "com.typesafe.play" %% "play-netty-server" % Versions.Play excludeAll (excludeSlf4j: _*)
  private val playAkkaHttpServer = "com.typesafe.play" %% "play-akka-http-server" % Versions.Play excludeAll (excludeSlf4j: _*)
  private val playServer = "com.typesafe.play" %% "play-server" % Versions.Play excludeAll (excludeSlf4j: _*)
  private val playTest = "com.typesafe.play" %% "play-test" % Versions.Play excludeAll (excludeSlf4j: _*)

  private val playWs = "com.typesafe.play" %% "play-ws" % Versions.Play excludeAll (excludeSlf4j: _*)
  private val playAhcWs = "com.typesafe.play" %% "play-ahc-ws" % Versions.Play excludeAll (excludeSlf4j: _*)
  private val playJson = "com.typesafe.play" %% "play-json" % Versions.PlayJson excludeAll (excludeSlf4j: _*)
  private val playFunctional = "com.typesafe.play" %% "play-functional" % Versions.PlayJson excludeAll (excludeSlf4j: _*)
  private val playFileWatch = "com.lightbend.play" %% "play-file-watch" % Versions.PlayFileWatch excludeAll (excludeSlf4j: _*)

  private val junit = "junit" % "junit" % Versions.JUnit
  private val commonsLang = "org.apache.commons" % "commons-lang3" % "3.7"

  private val dropwizardMetricsCore = "io.dropwizard.metrics" % "metrics-core" % "3.2.2" excludeAll (excludeSlf4j: _*)

  private val jacksonFamily =
    libraryFamily("com.fasterxml.jackson.core", Versions.JacksonCore)(
      "jackson-annotations", "jackson-core"
    ) ++ libraryFamily("com.fasterxml.jackson.core", Versions.JacksonDatabind)(
      "jackson-databind"
    ) ++ libraryFamily("com.fasterxml.jackson.datatype", Versions.JacksonDatatype)(
      "jackson-datatype-jdk8", "jackson-datatype-jsr310", "jackson-datatype-guava", "jackson-datatype-pcollections"
    )

  val scalaParserCombinatorOverrides = Set(scalaParserCombinators)

  // A whitelist of dependencies that Lagom is allowed to depend on, either directly or transitively.
  // This list is used to validate all of Lagom's dependencies.
  // By maintaining this whitelist, we can be absolutely sure of what we depend on, that we consistently depend on the
  // same versions of libraries across our modules, we can ensure also that we have no partial upgrades of families of
  // libraries (such as Play or Akka), and we will also be alerted when a transitive dependency is upgraded (because
  // the validation task will fail) which means we can manually check that it is safe to upgrade that dependency.
  val DependencyWhitelist: Def.Initialize[Seq[ModuleID]] = Def.setting {
    val scalaVersion = Keys.scalaVersion.value

    Seq(
      "aopalliance" % "aopalliance" % "1.0",
      cassandraDriverCore,
      "com.fasterxml.jackson.module" % "jackson-module-parameter-names" % Versions.Jackson,
      akkaPersistenceJdbc,
      "com.github.jnr" % "jffi" % "1.2.16",
      "com.github.jnr" % "jnr-constants" % "0.9.9",
      "com.github.jnr" % "jnr-ffi" % "2.1.7",
      "com.github.jnr" % "jnr-posix" % "3.0.44",
      "com.github.jnr" % "jnr-x86asm" % "1.0.2",
      "com.google.code.findbugs" % "jsr305" % "3.0.2",
      "com.google.errorprone" % "error_prone_annotations" % "2.1.3",
      guava,
      "com.google.j2objc" % "j2objc-annotations" % "1.1",
      "com.google.inject" % "guice" % "4.2.1",
      "com.google.inject.extensions" % "guice-assistedinject" % "4.2.1",
      "com.googlecode.usc" % "jdbcdslog" % "1.0.6.2",
      "org.checkerframework" % "checker-qual" % "2.5.2",
      "javax.xml.bind" % "jaxb-api" % "2.3.0",
      h2,
      "com.jolbox" % "bonecp" % "0.8.0.RELEASE",
      "com.lmax" % "disruptor" % "3.3.6",
      "com.novocode" % "junit-interface" % Versions.JUnitInterface,
      typesafeConfig,
      sslConfig,
      akkaDiscovery,
      akkaHttpCore,
      akkaStreamKafka,
      akkaParsing,
      akkaPersistenceCassandra,
      akkaPersistenceCassandraLauncher,
      "com.typesafe.netty" % "netty-reactive-streams" % Versions.NettyReactiveStreams,
      "com.typesafe.netty" % "netty-reactive-streams-http" % Versions.NettyReactiveStreams,
      "com.typesafe.play" %% "cachecontrol" % "1.1.3",
      playJson,
      playFunctional,
      // play client libs
      playWs,
      playAhcWs,
      "com.typesafe.play" %% "play-ws-standalone" % Versions.PlayStandaloneWs,
      "com.typesafe.play" %% "play-ws-standalone-xml" % Versions.PlayStandaloneWs,
      "com.typesafe.play" %% "play-ws-standalone-json" % Versions.PlayStandaloneWs,
      "com.typesafe.play" %% "play-ahc-ws-standalone" % Versions.PlayStandaloneWs,
      "com.typesafe.play" % "shaded-asynchttpclient" % Versions.PlayStandaloneWs,
      "com.typesafe.play" % "shaded-oauth" % Versions.PlayStandaloneWs,
      playTest,
      // dependencies added by play-test
      "org.fluentlenium" % "fluentlenium-core" % "3.6.1",
      "org.seleniumhq.selenium" % "selenium-support" % "3.13.0",
      "org.seleniumhq.selenium" % "selenium-api" % "3.13.0",
      "org.seleniumhq.selenium" % "selenium-remote-driver" % "3.13.0",
      "org.seleniumhq.selenium" % "selenium-firefox-driver" % "3.13.0",
      "net.bytebuddy" % "byte-buddy" % "1.8.3",
      "org.apache.commons" % "commons-exec" % "1.3",
      "commons-logging" % "commons-logging" % "1.2",
      "com.google.code.gson" % "gson" % "2.8.4",
      "com.squareup.okhttp3" % "okhttp" % "3.10.0",
      "com.squareup.okio" % "okio" % "1.14.1",
      "org.atteo.classindex" % "classindex" % "3.4",
      "org.seleniumhq.selenium" % "htmlunit-driver" % "2.31.0",
      "net.sourceforge.htmlunit" % "htmlunit" % "2.31",
      "xalan" % "xalan" % "2.7.2",
      "xalan" % "serializer" % "2.7.2",
      "org.apache.commons" % "commons-text" % "1.3",
      "org.apache.httpcomponents" % "httpmime" % "4.5.5",
      "org.apache.httpcomponents" % "httpclient" % "4.5.5",
      "org.apache.httpcomponents" % "httpcore" % "4.4.9",
      "net.sourceforge.htmlunit" % "htmlunit-core-js" % "2.31",
      "net.sourceforge.htmlunit" % "neko-htmlunit" % "2.31",
      "xerces" % "xercesImpl" % "2.11.0",
      "xml-apis" % "xml-apis" % "1.4.01",
      "net.sourceforge.htmlunit" % "htmlunit-cssparser" % "1.0.0",
      "commons-io" % "commons-io" % "2.6",
      "commons-net" % "commons-net" % "3.6",
      "org.eclipse.jetty.websocket" % "websocket-client" % "9.4.10.v20180503",
      "org.eclipse.jetty" % "jetty-client" % "9.4.10.v20180503",
      "org.eclipse.jetty" % "jetty-http" % "9.4.10.v20180503",
      "org.eclipse.jetty" % "jetty-util" % "9.4.10.v20180503",
      "org.eclipse.jetty" % "jetty-io" % "9.4.10.v20180503",
      "org.eclipse.jetty" % "jetty-xml" % "9.4.10.v20180503",
      "org.eclipse.jetty.websocket" % "websocket-common" % "9.4.10.v20180503",
      "org.eclipse.jetty.websocket" % "websocket-api" % "9.4.10.v20180503",

      "com.typesafe.play" %% "twirl-api" % Versions.Twirl,
      "com.typesafe.slick" %% "slick" % Versions.Slick,
      "com.typesafe.slick" %% "slick-hikaricp" % Versions.Slick,
      "com.zaxxer" % "HikariCP" % "3.2.0",
      "commons-codec" % "commons-codec" % "1.10",
      "io.aeron" % "aeron-client" % "1.9.3",
      "io.aeron" % "aeron-driver" % "1.9.3",
      dropwizardMetricsCore,
      "io.jsonwebtoken" % "jjwt" % "0.9.0",
      // Netty 3 uses a different package to Netty 4, and a different artifact ID, so can safely coexist
      "io.netty" % "netty" % "3.10.6.Final",
      "javax.cache" % "cache-api" % "1.0.0",
      "javax.inject" % "javax.inject" % "1",
      "javax.transaction" % "jta" % "1.1",
      "joda-time" % "joda-time" % "2.9.9",
      "junit" % "junit" % Versions.JUnit,
      "net.jodah" % "typetools" % "0.5.0",
      "org.lz4" % "lz4-java" % "1.4",
      "org.agrona" % "agrona" % "0.9.18",
      commonsLang,
      "org.apache.kafka" % "kafka-clients" % Versions.Kafka,
      "org.codehaus.mojo" % "animal-sniffer-annotations" % "1.14",
      "org.hibernate" % "hibernate-validator" % "5.2.4.Final",
      "org.hibernate.javax.persistence" % "hibernate-jpa-2.1-api" % "1.0.0.Final",
      "org.immutables" % "value" % "2.3.2",
      javassist,
      "org.joda" % "joda-convert" % "1.9.2",
      "org.hamcrest" % "hamcrest-core" % "1.3",
      "org.lmdbjava" % "lmdbjava" % "0.6.1",
      "org.pcollections" % "pcollections" % "2.1.2",
      reactiveStreams,
      "org.scalactic" %% "scalactic" % Versions.ScalaTest,
      scalaTest,
      "org.scala-lang.modules" %% "scala-java8-compat" % Versions.ScalaJava8Compat,
      scalaParserCombinators,
      scalaXml,
      "org.scala-sbt" % "test-interface" % "1.0",
      "org.typelevel" %% "macro-compat" % "1.1.1",
      "org.xerial.snappy" % "snappy-java" % "1.1.4",
      "tyrex" % "tyrex" % "1.0.1"

    ) ++ jacksonFamily ++ crossLibraryFamily("com.typesafe.akka", Versions.Akka)(
      "akka-actor", "akka-cluster", "akka-cluster-sharding", "akka-cluster-tools", "akka-distributed-data",
      "akka-multi-node-testkit", "akka-persistence", "akka-persistence-query", "akka-protobuf", "akka-remote",
      "akka-slf4j", "akka-stream", "akka-stream-testkit", "akka-testkit"

    ) ++ libraryFamily("com.typesafe.play", Versions.Play)(
      "build-link", "play-exceptions", "play-netty-utils"

    ) ++ crossLibraryFamily("com.typesafe.play", Versions.Play)(
      "play", "play-guice", "play-java", "play-jdbc", "play-jdbc-api",
      "play-netty-server", "play-akka-http-server", "play-server", "play-streams", "play-ws", "play-ahc-ws"

    ) ++ libraryFamily("ch.qos.logback", Versions.Logback)(
      "logback-classic", "logback-core"

    ) ++ libraryFamily("io.netty", Versions.Netty)(
      "netty-buffer", "netty-codec", "netty-codec-http", "netty-common", "netty-handler", "netty-resolver",
      "netty-transport", "netty-transport-native-epoll", "netty-transport-native-unix-common"

    ) ++ libraryFamily("org.apache.logging.log4j", Versions.Log4j)(
      "log4j-api", "log4j-core", "log4j-slf4j-impl"

    ) ++ libraryFamily("org.ow2.asm", "5.0.3")(
      "asm", "asm-analysis", "asm-commons", "asm-tree", "asm-util"

    ) ++ libraryFamily("org.scala-lang", scalaVersion)(
      "scala-library", "scala-reflect"

    ) ++ libraryFamily("org.slf4j", Versions.Slf4j)(
      "jcl-over-slf4j", "jul-to-slf4j", "log4j-over-slf4j", "slf4j-api", "slf4j-nop", "slf4j-log4j12"
    )
  }


  // These dependencies are used by JPA to test, but we don't want to export them as part of our regular whitelist,
  // so we maintain it separately.
  val JpaTestWhitelist = Seq(
    "antlr" % "antlr" % "2.7.7",
    "com.fasterxml" % "classmate" % "1.3.0",
    "dom4j" % "dom4j" % "1.6.1",
    "javax.annotation" % "jsr250-api" % "1.0",
    "javax.el" % "el-api" % "2.2",
    "javax.enterprise" % "cdi-api" % "1.1",
    "org.apache.geronimo.specs" % "geronimo-jta_1.1_spec" % "1.1.1",
    "org.hibernate" % "hibernate-core" % "5.2.5.Final",
    "org.hibernate.common" % "hibernate-commons-annotations" % "5.0.1.Final",
    "org.jboss" % "jandex" % "2.0.3.Final",
    "org.jboss.logging" % "jboss-logging" % "3.3.0.Final",
    "org.jboss.spec.javax.interceptor" % "jboss-interceptors-api_1.1_spec" % "1.0.0.Beta1"
  )

  // These dependencies are used by the Kafka tests, but we don't want to export them as part of our regular
  // whitelist, so we maintain it separately.
  val KafkaTestWhitelist = Seq(
    "com.101tec" % "zkclient" % "0.10",
    "com.yammer.metrics" % "metrics-core" % "2.2.0",
    "jline" % "jline" % "0.9.94",
    "log4j" % "log4j" % "1.2.17",
    "net.sf.jopt-simple" % "jopt-simple" % "5.0.4",
    "org.apache.commons" % "commons-math" % "2.2",
    "org.apache.curator" % "curator-client" % "2.10.0",
    "org.apache.curator" % "curator-framework" % "2.10.0",
    "org.apache.curator" % "curator-test" % "2.10.0",
    "org.apache.kafka" %% "kafka" % Versions.Kafka,
    "org.apache.zookeeper" % "zookeeper" % "3.4.10"
  )

  private def crossLibraryFamily(groupId: String, version: String)(artifactIds: String*) = {
    artifactIds.map(aid => groupId %% aid % version)
  }

  private def libraryFamily(groupId: String, version: String)(artifactIds: String*) = {
    artifactIds.map(aid => groupId % aid % version)
  }

  // Dependencies for each module
  val api = libraryDependencies ++= Seq(
    scalaParserCombinators,
    scalaXml,
    akkaActor,
    akkaSlf4j,
    akkaStream,
    play,
    guava,

    // Upgrades needed to match whitelist
    sslConfig,
    playJson
  )

  val `api-javadsl` = libraryDependencies ++= Seq(
    playJava,
    playGuice,
    "org.pcollections" % "pcollections" % "2.1.2",

    // Upgrades needed to match whitelist
    sslConfig,

    scalaTest % Test,
    "com.fasterxml.jackson.module" % "jackson-module-parameter-names" % Versions.Jackson % Test
  )

  val `api-scaladsl` = libraryDependencies ++= Seq(
    // Upgrades needed to match whitelist
    sslConfig,

    scalaTest % Test
  )

  val immutables = libraryDependencies += "org.immutables" % "value" % "2.3.2"

  val jackson = libraryDependencies ++= Seq(
    "com.fasterxml.jackson.module" % "jackson-module-parameter-names" % Versions.Jackson,
    "com.fasterxml.jackson.datatype" % "jackson-datatype-pcollections" % Versions.JacksonDatatype,
    "com.fasterxml.jackson.datatype" % "jackson-datatype-guava" % Versions.JacksonDatatype,
    "com.fasterxml.jackson.datatype" % "jackson-datatype-jdk8" % Versions.JacksonDatatype,
    "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310" % Versions.JacksonDatatype,

    // Upgrades needed to match whitelist
    sslConfig,

    akkaTestkit % Test,
    scalaTest % Test,
    junit % Test,
    "com.novocode" % "junit-interface" % "0.11" % Test
  )

  val `play-json` = libraryDependencies ++= Seq(
    playJson,
    akkaActor,
    akkaTestkit % Test,
    scalaTest % Test,

    // Upgrades needed to match whitelist
    scalaOrganization.value % "scala-reflect" % scalaVersion.value,
    scalaJava8Compat,
    scalaXml % Test,
    scalaParserCombinators % Test
    // explicitly depend on particular versions of jackson
  ) ++ jacksonFamily ++ Seq(
    // explicitly depend on particular versions of guava
    guava
  )

  val `api-tools` = libraryDependencies ++= Seq(
    play,
    scalaTest % Test,

    // Upgrades needed to match whitelist
    reactiveStreams,
    sslConfig,
    playJson,
    scalaParserCombinators,
    akkaStream,
    akkaActor,
    akkaSlf4j,
    akkaProfobuf
  )

  val client = libraryDependencies ++= Seq(
    slf4jApi,
    playWs,
    playAhcWs,
    dropwizardMetricsCore,
    "com.typesafe.netty" % "netty-reactive-streams" % Versions.NettyReactiveStreams,
    "io.netty" % "netty-codec-http" % Versions.Netty,
    scalaTest % Test,

    // Upgrades needed to match whitelist versions
    sslConfig,
    "io.netty" % "netty-handler" % Versions.Netty
  )

  val `client-javadsl` = libraryDependencies ++= Seq(
    scalaTest % Test
  )

  val `client-scaladsl` = libraryDependencies ++= Seq(
    scalaTest % Test
  )

  val `integration-client-javadsl` = libraryDependencies ++= Seq(
    playWs,
    playAhcWs,
    // we need to explicitly add akka-remote in test scope
    // because the test for LagomClientFactory needs it
    akkaRemote % Test,
    scalaTest % Test
  )

  val server = libraryDependencies ++= Nil

  val `server-javadsl` = libraryDependencies ++= Seq(
    slf4jApi,
    commonsLang
  )

  val `server-scaladsl` = libraryDependencies ++= Seq(
    slf4jApi,
    scalaTest % Test
  )

  val `testkit-core` = libraryDependencies ++= Seq(
    akkaActor,
    akkaStream,
    play,
    akkaPersistenceCassandraLauncher,

    // Upgrades needed to match whitelist
    sslConfig,
    playJson,
    akkaSlf4j
  )

  val `testkit-javadsl` = libraryDependencies ++= Seq(
    playAkkaHttpServer,
    akkaStreamTestkit,
    scalaTest % Test,
    "junit" % "junit" % Versions.JUnit,
    h2 % Test,

    // Without any binding, slf4j will print warnings when running tests
    "org.slf4j" % "slf4j-nop" % Versions.Slf4j % Test
  )

  val `testkit-scaladsl` = libraryDependencies ++= Seq(
    playAkkaHttpServer,
    akkaStreamTestkit,
    scalaTest % Test,
    "junit" % "junit" % Versions.JUnit,
    h2 % Test,

    // Without any binding, slf4j will print warnings when running tests
    "org.slf4j" % "slf4j-nop" % Versions.Slf4j % Test
  )

  val `integration-tests-javadsl` = libraryDependencies ++= Seq(
    playNettyServer,
    playAkkaHttpServer,
    playTest % Test,
    junit % Test,
    "com.novocode" % "junit-interface" % "0.11" % Test,
    scalaTest
  )

  val `integration-tests-scaladsl` = libraryDependencies ++= Seq(
    playAkkaHttpServer,
    playTest % Test,
    junit % Test,
    "com.novocode" % "junit-interface" % "0.11" % Test,
    scalaTest
  )

  val `cluster-core` = libraryDependencies ++= Seq(
    akkaCluster,
    akkaTestkit % Test,
    scalaTest % Test,
    play,
    junit % Test,
    "com.novocode" % "junit-interface" % "0.11" % Test,

    // Upgrades needed to match whitelist
    sslConfig,
    scalaJava8Compat,
    scalaParserCombinators,
    scalaXml % Test,
    akkaSlf4j
  )

  val `cluster-javadsl` = libraryDependencies ++= Seq(
    akkaTestkit % Test,
    akkaMultiNodeTestkit % Test,
    scalaTest % Test,
    junit % Test,
    "com.novocode" % "junit-interface" % "0.11" % Test
  )

  val `cluster-scaladsl` = libraryDependencies ++= Seq(
    akkaTestkit % Test,
    akkaMultiNodeTestkit % Test,
    scalaTest % Test,
    junit % Test,
    "com.novocode" % "junit-interface" % "0.11" % Test,

    // Upgrades needed to match whitelist
    sslConfig,
    scalaXml % Test,
    akkaSlf4j
    // explicitly depend on particular versions of jackson
  ) ++ jacksonFamily ++ Seq(
    // explicitly depend on particular versions of guava
    guava
  )

  val `pubsub-javadsl` = libraryDependencies ++= Seq(
    akkaClusterTools,
    akkaTestkit % Test,
    akkaMultiNodeTestkit % Test,
    akkaStreamTestkit % Test,
    scalaTest % Test,
    junit % Test,
    "com.novocode" % "junit-interface" % "0.11" % Test
    // explicitly depend on particular versions of jackson
  ) ++ jacksonFamily ++ Seq(
    // explicitly depend on particular versions of guava
    guava
  )

  val `pubsub-scaladsl` = libraryDependencies ++= Seq(
    akkaClusterTools,
    akkaTestkit % Test,
    akkaMultiNodeTestkit % Test,
    akkaStreamTestkit % Test,
    scalaTest % Test,

    // Upgrades needed to match whitelist
    sslConfig,
    scalaXml % Test,
    akkaSlf4j
  )

  val `persistence-core` = libraryDependencies ++= Seq(
    akkaPersistence,
    akkaPersistenceQuery,
    akkaClusterSharding,
    akkaSlf4j,
    play,
    akkaTestkit % Test,
    akkaMultiNodeTestkit % Test,
    akkaStreamTestkit % Test,
    scalaTest % Test,
    junit % Test,
    "com.novocode" % "junit-interface" % "0.11" % Test,

    // Upgrades needed to match whitelist
    sslConfig,
    playJson
  )

  val `persistence-testkit` = libraryDependencies ++= Seq(
    akkaPersistence,
    akkaTestkit,
    slf4jApi
  )

  val `persistence-javadsl` = libraryDependencies ++= Seq(
    slf4jApi,
    // this mean we have production code depending on testkit
    akkaTestkit
  )

  val `persistence-scaladsl` = libraryDependencies ++= Seq(
    slf4jApi,
    // this mean we have production code depending on testkit
    akkaTestkit
  )
  val `persistence-cassandra-core` = libraryDependencies ++= Seq(
    slf4jApi,
    akkaPersistenceCassandra,
    akkaPersistenceCassandraLauncher % Test,

    // Upgrades needed to match whitelist
    sslConfig,
    dropwizardMetricsCore,
    cassandraDriverCore,
    "io.netty" % "netty-handler" % Versions.Netty
  )

  val `persistence-cassandra-javadsl` = libraryDependencies ++= Seq(
    junit % Test,
    // Upgrades needed to match whitelist
    cassandraDriverCore
  )

  val `persistence-cassandra-scaladsl` = libraryDependencies ++= Seq(
    // Upgrades needed to match whitelist
    cassandraDriverCore
  )

  val `persistence-jdbc-core` = libraryDependencies ++= Seq(
    slf4jApi,
    akkaPersistenceJdbc,
    playJdbc,

    junit % Test,
    h2 % Test
  )

  val `persistence-jdbc-javadsl` = libraryDependencies ++= Seq(
    h2 % Test
  )

  val `persistence-jdbc-scaladsl` = libraryDependencies ++= Seq(
    h2 % Test
  )

  val `persistence-jpa-javadsl` = libraryDependencies ++= Seq(
    "org.hibernate.javax.persistence" % "hibernate-jpa-2.1-api" % "1.0.0.Final" % Provided,
    "org.hibernate" % "hibernate-core" % "5.2.5.Final" % Test,
    h2 % Test,
    javassist % Test
  )

  val `broker-javadsl` = libraryDependencies ++= Nil

  val `broker-scaladsl` = libraryDependencies ++= Nil

  val `kafka-client` = libraryDependencies ++= Seq(
    "org.slf4j" % "log4j-over-slf4j" % Versions.Slf4j,
    akkaStreamKafka exclude("org.slf4j", "slf4j-log4j12"),
    scalaTest % Test,

    // Upgrades needed to match whitelist$
    sslConfig
  )

  val `kafka-client-javadsl` = libraryDependencies ++= Seq(
    scalaTest % Test,

    // Upgrades needed to match whitelist$
    sslConfig
  )

  val `kafka-client-scaladsl` = libraryDependencies ++= Seq(
    scalaTest % Test,

    // Upgrades needed to match whitelist$
    sslConfig
  )

  val `kafka-broker` = libraryDependencies ++= Nil

  val `kafka-broker-javadsl` = libraryDependencies ++=  Seq(
    slf4jApi,
    "log4j" % "log4j" % "1.2.17",
    scalaTest % Test,
    junit % Test
  )

  val `kafka-broker-scaladsl` = libraryDependencies ++= Seq(
    "log4j" % "log4j" % "1.2.17",
    scalaTest % Test,
    junit % Test
  )

  val logback = libraryDependencies ++= slf4j ++ Seq(
    // needed only because we use play.utils.Colors
    play,

    // Upgrades needed to match whitelist versions
    reactiveStreams,
    sslConfig,
    playJson,
    scalaParserCombinators,
    akkaStream,
    akkaActor,
    akkaSlf4j,
    akkaProfobuf
  ) ++ Seq("logback-core", "logback-classic").map("ch.qos.logback" % _ % Versions.Logback)

  val log4j2 = libraryDependencies ++= Seq(slf4jApi) ++
    log4jModules ++
    Seq(
      "com.lmax" % "disruptor" % "3.3.6",
      play,

      // Upgrades needed to match whitelist versions
      reactiveStreams,
      sslConfig,
      playJson,
      scalaParserCombinators,
      akkaStream,
      akkaActor,
      akkaSlf4j,
      akkaProfobuf

    )

  val `reloadable-server` = libraryDependencies ++= Seq(
    playServer,

    // Upgrades needed to match whitelist versions
    reactiveStreams,
    playJson,
    scalaParserCombinators,
    akkaStream,
    akkaActor,
    akkaSlf4j,
    akkaProfobuf
  )

  val `build-tool-support` = libraryDependencies ++= Seq(
    playExceptions,
    playBuildLink,
    playFileWatch,
    // This is used in the code to check if the embedded cassandra server is started
    cassandraDriverCore,
    scalaTest % Test
  )

  val `sbt-plugin` = libraryDependencies ++= Seq(
    // And this is needed to silence the datastax driver logging
    "org.slf4j" % "slf4j-nop" % "1.7.14",
    scalaTest % Test
  )

  val `maven-plugin` = libraryDependencies ++= Seq(
    "org.apache.maven" % "maven-plugin-api" % Versions.Maven,
    "org.apache.maven" % "maven-core" % Versions.Maven,
    "org.apache.maven.plugin-testing" % "maven-plugin-testing-harness" % "3.3.0" % Test,
    slf4jApi,
    scalaTest % Test
  )

  val `maven-launcher` = libraryDependencies := Seq(
    // These dependencies come from https://github.com/apache/maven/blob/master/apache-maven/pom.xml, they are
    // what maven bundles into its own distribution.
    "org.apache.maven" % "maven-embedder" % Versions.Maven,
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

  val `service-locator` = libraryDependencies ++= Seq(
    playAkkaHttpServer,
    akkaHttpCore,
    scalaTest % Test
  )

  val `dev-mode-ssl-support` = libraryDependencies ++= Seq(
    playAkkaHttpServer,
    akkaHttpCore,

    // updates to match whitelist
    akkaActor,
    akkaStream,
    akkaProfobuf,
    akkaSlf4j,
    typesafeConfig,
    sslConfig
  )

  val `service-registry-client-core` =
    libraryDependencies ++= Seq(
      akkaDiscovery,
      slf4jApi,

      akkaTestkit % Test,
      scalaTest % Test,
      // updates to match whitelist
      akkaActor
    )

  val `service-registry-client-javadsl` =
    libraryDependencies ++= Seq(
      akkaDiscovery,

      akkaTestkit % Test,
      junit % Test,
      "com.novocode" % "junit-interface" % "0.11" % Test
    )

  val `service-registration-javadsl` = libraryDependencies ++= Nil

  val `devmode-scaladsl` =
    libraryDependencies ++= Seq(
      akkaDiscovery
    )

  val `play-integration-javadsl` = libraryDependencies ++= Nil

  val `cassandra-server` = libraryDependencies ++= Seq(
    akkaPersistenceCassandraLauncher,
    akkaPersistenceCassandra
  )

  val `kafka-server` = libraryDependencies ++= Seq(
    "org.apache.kafka" %% "kafka" % Versions.Kafka,
    // Note that curator 3.x is only compatible with zookeeper 3.5.x. Kafka currently uses zookeeper 3.4, hence we have
    // to use curator 2.x, which is compatible with zookeeper 3.4 (see the notice in
    // http://curator.apache.org/index.html - make sure to scroll to the bottom)
    "org.apache.curator" % "curator-framework" % "2.10.0",
    "org.apache.curator" % "curator-test" % "2.10.0",
    javassist,
    scalaJava8Compat,
    scalaTest % Test
  )

  val validateDependencies = taskKey[Unit]("Validate Lagom dependencies to ensure they are whitelisted")
  val dependencyWhitelist = settingKey[Seq[ModuleID]]("The whitelist of dependencies")
  val pruneWhitelist = taskKey[Unit]("List items that can be pruned from the whitelist ")

  val validateDependenciesTask: Def.Initialize[Task[Unit]] = Def.task {
    // We validate compile dependencies to ensure that whatever we are exporting, we are exporting the right
    // versions. We validate test dependencies to ensure that our tests run against the same versions that we are
    // exporting
    val compileClasspath = (managedClasspath in Compile).value
    val testClasspath = (managedClasspath in Test).value
    val cross = CrossVersion(scalaVersion.value, scalaBinaryVersion.value)
    val log = streams.value.log
    val svb = scalaBinaryVersion.value

    val whitelist = dependencyWhitelist.value.map { moduleId =>
      val crossModuleId = cross(moduleId)
      (crossModuleId.organization, crossModuleId.name) -> crossModuleId.revision
    }.toMap

    def collectProblems(scope: String, classpath: Classpath) = {
      classpath.collect(Function.unlift { dep =>
        val moduleId = dep.get(moduleID.key).getOrElse {
          sys.error(s"Managed classpath dependency without moduleID: $dep")
        }

        whitelist.get((moduleId.organization, moduleId.name)) match {
          case None =>
            Some(moduleId -> s"[${name.value}] $scope dependency not in whitelist: $moduleId")
          case Some(unmatched) if moduleId.revision != unmatched =>
            Some(moduleId -> s"[${name.value}] $scope dependency ${moduleId.organization}:${moduleId.name} version ${moduleId.revision} doesn't match whitelist version $unmatched")
          case _ => None
        }
      })
    }

    val problems = collectProblems("Compile", compileClasspath) ++ collectProblems("Test", testClasspath)

    if (problems.nonEmpty) {
      problems.foreach(p => log.error(p._2))

      log.debug {
        // This makes it very easy to fix the problem, by outputting a formatted list of dependencies to add.
        problems.map { problem =>
          val mid = problem._1
          val cross = mid.name.endsWith("_" + svb)
          val m = if (cross) "%%" else "%"
          val name = if (cross) mid.name.dropRight(svb.length + 1) else mid.name
          s""""${mid.organization}" $m "$name" % "${mid.revision}""""
        }.sorted.mkString(
          "The following dependencies need to be added to the whitelist:\n",
          ",\n",
          ""
        )
      }
      throw new DependencyWhitelistValidationFailed
    }
  }

  val pruneWhitelistTask: Def.Initialize[Task[Unit]] = Def.task {
    val compileClasspath = (managedClasspath in Compile).value
    val testClasspath = (managedClasspath in Test).value
    val cross = CrossVersion(scalaVersion.value, scalaBinaryVersion.value)
    val log = streams.value.log
    val svb = scalaBinaryVersion.value

    val whitelist: Map[(String, String), String] = dependencyWhitelist.value.map { moduleId =>
      val crossModuleId = cross(moduleId)
      (crossModuleId.organization, crossModuleId.name) -> crossModuleId.revision
    }.toMap

    def collectProblems(scope: String, classpath: Classpath): Set[(String, String)] = {
      val modules: Set[(String, String)] = classpath.toSet[Attributed[File]].flatMap(_.get(moduleID.key)).map(mod => (mod.organization, mod.name))
      whitelist.keySet -- modules
    }

    val problems = collectProblems("Compile", compileClasspath) ++ collectProblems("Test", testClasspath)

    if (problems.nonEmpty) {
      problems.foreach(p => log.error(s"${name.value} - Found unnecessary whitelisted item: ${p._1}:${p._2}"))
    } else {
      log.error(s"${name.value} needs a complete whitelist.")
    }

  }
  val pruneWhitelistSetting = pruneWhitelist := pruneWhitelistTask.value


  val validateDependenciesSetting = validateDependencies := validateDependenciesTask.value
  val dependencyWhitelistSetting = dependencyWhitelist := DependencyWhitelist.value

  private class DependencyWhitelistValidationFailed extends RuntimeException with FeedbackProvidedException {
    override def toString = "Dependency whitelist validation failed!"
  }

}
