import sbt._
import sbt.Keys._

object Dependencies {

  // Version numbers

  // If you update PlayVersion, you probably need to update the other Play*Version variables.
  // Also be sure to update PlayVersion in docs/build.sbt.
  val PlayVersion = "2.6.7"
  val PlayJsonVersion = "2.6.7"
  val PlayStandaloneWsVersion = "1.1.3"
  val TwirlVersion = "1.3.12"
  val PlayFileWatchVersion = "1.0.0"

  // Also be sure to update AkkaVersion in docs/build.sbt.
  val AkkaVersion = "2.5.6"
  val AkkaHttpVersion = "10.0.10"
  // Also be sure to update ScalaVersion in docs/build.sbt.
  val ScalaVersions = Seq("2.11.11", "2.12.4")
  val SbtScalaVersions = Seq("2.10.6")
  val AkkaPersistenceCassandraVersion = "0.57"
  val AkkaPersistenceJdbcVersion = "3.0.1"
  // Also be sure to update ScalaTestVersion in docs/build.sbt.
  val ScalaTestVersion = "3.0.4"
  val JacksonVersion = "2.8.10"
  val GuavaVersion = "22.0"
  val MavenVersion = "3.3.9"
  val NettyVersion = "4.1.16.Final"
  val NettyReactiveStreamsVersion = "2.0.0"
  val KafkaVersion = "0.11.0.0"
  val AkkaStreamKafkaVersion = "0.17"

  val ScalaJava8CompatVersion = "0.8.0"
  val ScalaXmlVersion = "1.0.6"
  val SlickVersion = "3.2.1"
  // Also be sure to update JUnitVersion in docs/build.sbt.
  val JUnitVersion = "4.11"
  // Also be sure to update JUnitInterfaceVersion in docs/build.sbt.
  val JUnitInterfaceVersion = "0.11"

  val Slf4jVersion = "1.7.25"
  val LogbackVersion = "1.2.3"
  // Also be sure to update Log4jVersion in docs/build.sbt.
  val Log4jVersion = "2.8.2"


  // Some setup before we start creating ModuleID vals
  private val slf4jApi = "org.slf4j" % "slf4j-api" % Slf4jVersion
  private val slf4j: Seq[ModuleID] = Seq("jcl-over-slf4j", "jul-to-slf4j", "log4j-over-slf4j").map {
    "org.slf4j" % _ % Slf4jVersion
  } ++ Seq(slf4jApi)
  private val excludeSlf4j = slf4j.map { moduleId => ExclusionRule(moduleId.organization, moduleId.name) }
  private val log4jModules = Seq(
    "log4j-api",
    "log4j-core",
    "log4j-slf4j-impl"
  ).map("org.apache.logging.log4j" % _ % Log4jVersion excludeAll (excludeSlf4j: _*))


  // Specific libraries that get reused
  private val scalaTest: ModuleID = "org.scalatest" %% "scalatest" % ScalaTestVersion excludeAll (excludeSlf4j: _*)
  private val guava = "com.google.guava" % "guava" % GuavaVersion
  private val log4J = "log4j" % "log4j" % Log4jVersion
  private val scalaJava8Compat = "org.scala-lang.modules" %% "scala-java8-compat" % ScalaJava8CompatVersion
  private val scalaXml = "org.scala-lang.modules" %% "scala-xml" % ScalaXmlVersion
  private val javassist = "org.javassist" % "javassist" % "3.21.0-GA"
  private val scalaParserCombinators = "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.6"
  private val typesafeConfig = "com.typesafe" % "config" % "1.3.1"
  private val sslConfig = "com.typesafe" %% "ssl-config-core" % "0.2.2"
  private val h2 = "com.h2database" % "h2" % "1.4.192"
  private val cassandraDriverCore = "com.datastax.cassandra" % "cassandra-driver-core" % "3.2.0" excludeAll (excludeSlf4j: _*)

  private val akkaActor = "com.typesafe.akka" %% "akka-actor" % AkkaVersion
  private val akkaCluster = "com.typesafe.akka" %% "akka-cluster" % AkkaVersion
  private val akkaClusterSharding = "com.typesafe.akka" %% "akka-cluster-sharding" % AkkaVersion
  private val akkaClusterTools = "com.typesafe.akka" %% "akka-cluster-tools" % AkkaVersion
  private val akkaMultiNodeTestkit = "com.typesafe.akka" %% "akka-multi-node-testkit" % AkkaVersion
  private val akkaPersistence = "com.typesafe.akka" %% "akka-persistence" % AkkaVersion
  private val akkaPersistenceQuery = "com.typesafe.akka" %% "akka-persistence-query" % AkkaVersion
  private val akkaSlf4j = "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion excludeAll (excludeSlf4j: _*)
  private val akkaStream = "com.typesafe.akka" %% "akka-stream" % AkkaVersion
  private val akkaStreamTestkit = "com.typesafe.akka" %% "akka-stream-testkit" % AkkaVersion
  private val akkaTestkit = "com.typesafe.akka" %% "akka-testkit" % AkkaVersion
  private val reactiveStreams = "org.reactivestreams" % "reactive-streams" % "1.0.1"

  private val akkaPersistenceJdbc = "com.github.dnvriend" %% "akka-persistence-jdbc" % AkkaPersistenceJdbcVersion excludeAll (excludeSlf4j: _*)

  // latest version of APC depend on a Cassandra driver core that's not compatible with Lagom (newer netty/guava/etc... under the covers)
  private val akkaPersistenceCassandra = "com.typesafe.akka" %% "akka-persistence-cassandra" % AkkaPersistenceCassandraVersion
  private val akkaPersistenceCassandraLauncher = "com.typesafe.akka" %% "akka-persistence-cassandra-launcher" % AkkaPersistenceCassandraVersion
  private val akkaStreamKafka = "com.typesafe.akka" %% "akka-stream-kafka" % AkkaStreamKafkaVersion

  private val akkaHttpCore = "com.typesafe.akka" %% "akka-http-core" % AkkaHttpVersion
  private val akkaParsing = "com.typesafe.akka" %% "akka-parsing" % AkkaHttpVersion

  private val play = "com.typesafe.play" %% "play" % PlayVersion excludeAll (excludeSlf4j: _*)
  private val playBuildLink = "com.typesafe.play" % "build-link" % PlayVersion excludeAll (excludeSlf4j: _*)
  private val playExceptions = "com.typesafe.play" % "play-exceptions" % PlayVersion excludeAll (excludeSlf4j: _*)
  private val playGuice = "com.typesafe.play" %% "play-guice" % PlayVersion excludeAll (excludeSlf4j: _*)
  private val playJava = "com.typesafe.play" %% "play-java" % PlayVersion excludeAll (excludeSlf4j: _*)
  private val playJdbc = "com.typesafe.play" %% "play-jdbc" % PlayVersion excludeAll (excludeSlf4j: _*)
  private val playNettyServer = "com.typesafe.play" %% "play-netty-server" % PlayVersion excludeAll (excludeSlf4j: _*)
  private val playServer = "com.typesafe.play" %% "play-server" % PlayVersion excludeAll (excludeSlf4j: _*)

  private val playWs = "com.typesafe.play" %% "play-ws" % PlayVersion excludeAll (excludeSlf4j: _*)
  private val playAhcWs = "com.typesafe.play" %% "play-ahc-ws" % PlayVersion excludeAll (excludeSlf4j: _*)
  private val playJson = "com.typesafe.play" %% "play-json" % PlayJsonVersion excludeAll (excludeSlf4j: _*)
  private val playFunctional = "com.typesafe.play" %% "play-functional" % PlayJsonVersion excludeAll (excludeSlf4j: _*)

  private val dropwizardMetricsCore = "io.dropwizard.metrics" % "metrics-core" % "3.2.2" excludeAll (excludeSlf4j: _*)

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
      "com.fasterxml.jackson.module" % "jackson-module-parameter-names" % JacksonVersion,
      akkaPersistenceJdbc,
      "com.github.jnr" % "jffi" % "1.2.16",
      "com.github.jnr" % "jnr-constants" % "0.9.9",
      "com.github.jnr" % "jnr-ffi" % "2.1.6",
      "com.github.jnr" % "jnr-posix" % "3.0.27",
      "com.github.jnr" % "jnr-x86asm" % "1.0.2",
      "com.google.code.findbugs" % "jsr305" % "1.3.9",
      "com.google.errorprone" % "error_prone_annotations" % "2.0.18",
      "com.google.guava" % "guava" % GuavaVersion,
      "com.google.j2objc" % "j2objc-annotations" % "1.1",
      "com.google.inject" % "guice" % "4.1.0",
      "com.google.inject.extensions" % "guice-assistedinject" % "4.1.0",
      "com.googlecode.usc" % "jdbcdslog" % "1.0.6.2",
      h2,
      "com.jolbox" % "bonecp" % "0.8.0.RELEASE",
      "com.lmax" % "disruptor" % "3.3.6",
      "com.novocode" % "junit-interface" % JUnitInterfaceVersion,
      typesafeConfig,
      sslConfig,
      akkaHttpCore,
      akkaStreamKafka,
      akkaParsing,
      akkaPersistenceCassandra,
      akkaPersistenceCassandraLauncher,
      "com.typesafe.netty" % "netty-reactive-streams" % NettyReactiveStreamsVersion,
      "com.typesafe.netty" % "netty-reactive-streams-http" % NettyReactiveStreamsVersion,
      "com.typesafe.play" %% "cachecontrol" % "1.1.2",
      playJson,
      playFunctional,
      // play client libs
      playWs,
      playAhcWs,
      "com.typesafe.play" %% "play-ws-standalone" % PlayStandaloneWsVersion,
      "com.typesafe.play" %% "play-ws-standalone-xml" % PlayStandaloneWsVersion,
      "com.typesafe.play" %% "play-ws-standalone-json" % PlayStandaloneWsVersion,
      "com.typesafe.play" %% "play-ahc-ws-standalone" % PlayStandaloneWsVersion,
      "com.typesafe.play" % "shaded-asynchttpclient" % PlayStandaloneWsVersion,
      "com.typesafe.play" % "shaded-oauth" % PlayStandaloneWsVersion,

      "com.typesafe.play" %% "twirl-api" % TwirlVersion,
      "com.typesafe.slick" %% "slick" % SlickVersion,
      "com.typesafe.slick" %% "slick-hikaricp" % SlickVersion,
      "com.zaxxer" % "HikariCP" % "2.7.2",
      "commons-codec" % "commons-codec" % "1.10",
      "io.aeron" % "aeron-client" % "1.3.0",
      "io.aeron" % "aeron-driver" % "1.3.0",
      dropwizardMetricsCore,
      "io.jsonwebtoken" % "jjwt" % "0.7.0",
      // Netty 3 uses a different package to Netty 4, and a different artifact ID, so can safely coexist
      "io.netty" % "netty" % "3.10.6.Final",
      "javax.cache" % "cache-api" % "1.0.0",
      "javax.inject" % "javax.inject" % "1",
      "javax.transaction" % "jta" % "1.1",
      "joda-time" % "joda-time" % "2.9.9",
      "junit" % "junit" % JUnitVersion,
      "net.jodah" % "typetools" % "0.5.0",
      "net.jpountz.lz4" % "lz4" % "1.3.0",
      "org.agrona" % "agrona" % "0.9.6",
      "org.apache.commons" % "commons-lang3" % "3.6",
      "org.apache.kafka" % "kafka-clients" % KafkaVersion,
      "org.codehaus.mojo" % "animal-sniffer-annotations" % "1.14",
      "org.hibernate" % "hibernate-validator" % "5.2.4.Final",
      "org.hibernate.javax.persistence" % "hibernate-jpa-2.1-api" % "1.0.0.Final",
      "org.immutables" % "value" % "2.3.2",
      javassist,
      "org.joda" % "joda-convert" % "1.7",
      "org.hamcrest" % "hamcrest-core" % "1.3",
      "org.lmdbjava" % "lmdbjava" % "0.6.0",
      "org.pcollections" % "pcollections" % "2.1.2",
      reactiveStreams,
      "org.reflections" % "reflections" % "0.9.11",
      "org.scalactic" %% "scalactic" % ScalaTestVersion,
      "org.scalatest" %% "scalatest" % ScalaTestVersion,
      "org.scala-lang.modules" %% "scala-java8-compat" % ScalaJava8CompatVersion,
      scalaParserCombinators,
      scalaXml,
      "org.scala-sbt" % "test-interface" % "1.0",
      "org.typelevel" %% "macro-compat" % "1.1.1",
      "org.xerial.snappy" % "snappy-java" % "1.1.2.6",
      "tyrex" % "tyrex" % "1.0.1"

    ) ++ jacksonFamily ++ crossLibraryFamily("com.typesafe.akka", AkkaVersion)(
      "akka-actor", "akka-cluster", "akka-cluster-sharding", "akka-cluster-tools", "akka-distributed-data",
      "akka-multi-node-testkit", "akka-persistence", "akka-persistence-query", "akka-protobuf", "akka-remote",
      "akka-slf4j", "akka-stream", "akka-stream-testkit", "akka-testkit"

    ) ++ libraryFamily("com.typesafe.play", PlayVersion)(
      "build-link", "play-exceptions", "play-netty-utils"

    ) ++ crossLibraryFamily("com.typesafe.play", PlayVersion)(
      "play", "play-guice", "play-java", "play-jdbc", "play-jdbc-api",
      "play-netty-server", "play-server", "play-streams", "play-ws", "play-ahc-ws"

    ) ++ libraryFamily("ch.qos.logback", LogbackVersion)(
      "logback-classic", "logback-core"

    ) ++ libraryFamily("io.netty", NettyVersion)(
      "netty-buffer", "netty-codec", "netty-codec-http", "netty-common", "netty-handler", "netty-resolver",
      "netty-transport", "netty-transport-native-epoll", "netty-transport-native-unix-common"

    ) ++ libraryFamily("org.apache.logging.log4j", Log4jVersion)(
      "log4j-api", "log4j-core", "log4j-slf4j-impl"

    ) ++ libraryFamily("org.ow2.asm", "5.0.3")(
      "asm", "asm-analysis", "asm-commons", "asm-tree", "asm-util"

    ) ++ libraryFamily("org.scala-lang", scalaVersion)(
      "scala-library", "scala-reflect"

    ) ++ libraryFamily("org.slf4j", Slf4jVersion)(
      "jcl-over-slf4j", "jul-to-slf4j", "log4j-over-slf4j", "slf4j-api"
    )
  }


  private val jacksonFamily =
    libraryFamily("com.fasterxml.jackson.core", JacksonVersion)(
      "jackson-annotations", "jackson-core", "jackson-databind"
    ) ++ libraryFamily("com.fasterxml.jackson.datatype", JacksonVersion)(
      "jackson-datatype-jdk8", "jackson-datatype-jsr310", "jackson-datatype-guava", "jackson-datatype-pcollections"
    )


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
    "log4j" % "log4j" % "1.2.16",
    "net.sf.jopt-simple" % "jopt-simple" % "5.0.3",
    "org.apache.commons" % "commons-math" % "2.2",
    "org.apache.curator" % "curator-client" % "2.10.0",
    "org.apache.curator" % "curator-framework" % "2.10.0",
    "org.apache.curator" % "curator-test" % "2.10.0",
    "org.apache.kafka" %% "kafka" % KafkaVersion,
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
    "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.4",
    scalaXml,
    akkaActor,
    akkaSlf4j,
    akkaStream,
    play,
    guava,

    // Upgrades needed to match whitelist
    sslConfig
  )

  val `api-javadsl` = libraryDependencies ++= Seq(
    playJava,
    playGuice,
    "org.pcollections" % "pcollections" % "2.1.2",
    scalaTest % Test,
    "com.fasterxml.jackson.module" % "jackson-module-parameter-names" % JacksonVersion % Test
  )

  val `api-scaladsl` = libraryDependencies ++= Seq(
    scalaTest % Test
  )

  val immutables = libraryDependencies += "org.immutables" % "value" % "2.3.2"

  val jackson = libraryDependencies ++= Seq(
    "com.fasterxml.jackson.module" % "jackson-module-parameter-names" % JacksonVersion,
    "com.fasterxml.jackson.datatype" % "jackson-datatype-pcollections" % JacksonVersion,
    "com.fasterxml.jackson.datatype" % "jackson-datatype-guava" % JacksonVersion,
    "com.fasterxml.jackson.datatype" % "jackson-datatype-jdk8" % JacksonVersion,
    "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310" % JacksonVersion,
    akkaTestkit % Test,
    scalaTest % Test,
    "com.novocode" % "junit-interface" % "0.11" % Test
  )

  val `play-json` = libraryDependencies ++= Seq(
    playJson,
    akkaActor,
    akkaTestkit % Test,
    scalaTest % Test,

    // Upgrades needed to match whitelist
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
    akkaSlf4j,
    akkaStream,
    reactiveStreams,
    sslConfig
  )

  val client = libraryDependencies ++= Seq(
    slf4jApi,
    playWs,
    playAhcWs,
    dropwizardMetricsCore,
    "com.typesafe.netty" % "netty-reactive-streams" % NettyReactiveStreamsVersion,
    "io.netty" % "netty-codec-http" % NettyVersion,

    // Upgrades needed to match whitelist versions
    "io.netty" % "netty-handler" % NettyVersion,

    scalaTest % Test
  )

  val `client-javadsl` = libraryDependencies ++= Seq(
    scalaTest % Test
  )

  val `client-scaladsl` = libraryDependencies ++= Seq(
    scalaTest % Test
  )

  val `integration-client-javadsl` = libraryDependencies ++= Seq(
    playWs,
    playAhcWs
  )

  val server = libraryDependencies ++= Nil

  val `server-javadsl` = libraryDependencies ++= Seq(
    slf4jApi
  )

  val `server-scaladsl` = libraryDependencies ++= Seq(
    slf4jApi,
    scalaTest % Test
  )

  val `testkit-core` = libraryDependencies ++= Seq(
    akkaActor,
    akkaStream,

    // Upgrades needed to match whitelist
    scalaJava8Compat,
    scalaParserCombinators,
    sslConfig
  )

  val `testkit-javadsl` = libraryDependencies ++= Seq(
    playNettyServer,
    akkaStreamTestkit,
    akkaPersistenceCassandraLauncher,
    scalaTest % Test,
    "junit" % "junit" % JUnitVersion,


    // Upgrades needed to match whitelist
    "io.netty" % "netty-transport-native-epoll" % NettyVersion
  )

  val `testkit-scaladsl` = libraryDependencies ++= Seq(
    playNettyServer,
    akkaStreamTestkit,
    akkaPersistenceCassandraLauncher,
    scalaTest % Test,
    "junit" % "junit" % JUnitVersion,

    // Upgrades needed to match whitelist
    "io.netty" % "netty-transport-native-epoll" % NettyVersion
  )

  val `integration-tests-javadsl` = libraryDependencies ++= Seq(
    playNettyServer,
    "com.novocode" % "junit-interface" % "0.11" % Test,
    scalaTest,

    // Upgrades needed to match whitelist
    "io.netty" % "netty-transport-native-epoll" % NettyVersion
  )

  val `integration-tests-scaladsl` = libraryDependencies ++= Seq(
    playNettyServer,
    "com.novocode" % "junit-interface" % "0.11" % Test,
    scalaTest,

    // Upgrades needed to match whitelist
    "io.netty" % "netty-transport-native-epoll" % NettyVersion
  )

  val `cluster-core` = libraryDependencies ++= Seq(
    akkaCluster,
    akkaTestkit % Test,
    scalaTest % Test,
    "com.novocode" % "junit-interface" % "0.11" % Test,

    // Upgrades needed to match whitelist
    scalaJava8Compat,
    scalaParserCombinators,
    sslConfig,
    scalaXml % Test
  )

  val `cluster-javadsl` = libraryDependencies ++= Seq(
    akkaTestkit % Test,
    akkaMultiNodeTestkit % Test,
    scalaTest % Test,
    "com.novocode" % "junit-interface" % "0.11" % Test
  )

  val `cluster-scaladsl` = libraryDependencies ++= Seq(
    akkaTestkit % Test,
    akkaMultiNodeTestkit % Test,
    scalaTest % Test,
    "com.novocode" % "junit-interface" % "0.11" % Test,

    // Upgrades needed to match whitelist
    scalaXml % Test
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
    scalaXml % Test
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
    "com.novocode" % "junit-interface" % "0.11" % Test
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
    dropwizardMetricsCore,
    "io.netty" % "netty-handler" % NettyVersion
  )

  val `persistence-cassandra-javadsl` = libraryDependencies ++= Nil

  val `persistence-cassandra-scaladsl` = libraryDependencies ++= Nil

  val `persistence-jdbc-core` = libraryDependencies ++= Seq(
    slf4jApi,
    akkaPersistenceJdbc,
    playJdbc
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
    h2 % Test
  )

  val `broker-javadsl` = libraryDependencies ++= Nil

  val `broker-scaladsl` = libraryDependencies ++= Nil

  val `kafka-client` = libraryDependencies ++= Seq(
    "org.slf4j" % "log4j-over-slf4j" % Slf4jVersion,
    akkaStreamKafka exclude("org.slf4j", "slf4j-log4j12"),
    scalaTest % Test
  )

  val `kafka-client-javadsl` = libraryDependencies ++= Seq(
    scalaTest % Test
  )

  val `kafka-client-scaladsl` = libraryDependencies ++= Seq(
    scalaTest % Test
  )

  val `kafka-broker` = libraryDependencies ++= Nil

  val `kafka-broker-javadsl` = libraryDependencies ++=  Seq(
    slf4jApi,
    scalaTest % Test,
    "junit" % "junit" % JUnitVersion % Test
  )

  val `kafka-broker-scaladsl` = libraryDependencies ++= Seq(
    scalaTest % Test,
    "junit" % "junit" % JUnitVersion % Test
  )

  val logback = libraryDependencies ++= slf4j ++ Seq(
    // needed only because we use play.utils.Colors
    play,

    // Upgrades needed to match whitelist versions
    akkaStream,
    reactiveStreams,
    akkaSlf4j,
    sslConfig
  ) ++ Seq("logback-core", "logback-classic").map("ch.qos.logback" % _ % LogbackVersion)

  val log4j2 = libraryDependencies ++= Seq(slf4jApi) ++
    log4jModules ++
    Seq(
      "com.lmax" % "disruptor" % "3.3.6",
      play,
      // Upgrades needed to match whitelist versions
      akkaStream,
      reactiveStreams,
      akkaSlf4j,
      sslConfig
    )

  val `reloadable-server` = libraryDependencies ++= Seq(
    playServer,

    // Upgrades needed to match whitelist versions
    akkaStream,
    reactiveStreams,
    akkaSlf4j,
    sslConfig
  )

  val `build-tool-support` = libraryDependencies ++= Seq(
    "com.lightbend.play" %% "play-file-watch" % PlayFileWatchVersion,
    playExceptions,
    playBuildLink,
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
    "org.apache.maven" % "maven-plugin-api" % MavenVersion,
    "org.apache.maven" % "maven-core" % MavenVersion,
    "org.apache.maven.plugin-testing" % "maven-plugin-testing-harness" % "3.3.0" % Test,
    slf4jApi,
    scalaTest % Test
  )

  val `maven-launcher` = libraryDependencies := Seq(
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

  val `service-locator` = libraryDependencies ++= Seq(
    playNettyServer,
    akkaHttpCore,
    scalaTest % Test,

    // Upgrades needed to match whitelist
    "io.netty" % "netty-transport-native-epoll" % NettyVersion
  )

  val `service-registry-client-javadsl` = libraryDependencies ++= Nil

  val `service-registration-javadsl` = libraryDependencies ++= Nil

  val `devmode-scaladsl` = libraryDependencies ++= Nil

  val `play-integration-javadsl` = libraryDependencies ++= Nil

  val `cassandra-server` = libraryDependencies ++= Seq(
    akkaPersistenceCassandraLauncher,
    akkaPersistenceCassandra
  )

  val `kafka-server` = libraryDependencies ++=
    // log4j version prior to 1.2.17 required javax.jms, and that artifact could not properly resolved when using maven
    // without adding a resolver. The problem doesn't appear with sbt because the log4j version brought by both zookeeper
    // and curator dependencies are evicted to version 1.2.17. Unfortunately, because of how maven resolution works, we
    // have to explicitly add the desired log4j version we want to use here.
    // By the way, log4j 1.2.17 and later resolve the javax.jms dependency issue by using geronimo-jms. See
    // http://stackoverflow.com/questions/4908651/the-following-artifacts-could-not-be-resolved-javax-jmsjmsjar1-1
    // for more context.
    log4jModules ++
    Seq(
    "org.apache.kafka" %% "kafka" % KafkaVersion exclude("org.slf4j", "slf4j-log4j12"),
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
