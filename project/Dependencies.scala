/**
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */
package lagom.build

import lagom.build.Dependencies.akkaActorTyped
import lagom.build.Dependencies.junit
import sbt.Keys._
import sbt._

object Dependencies {
  object Versions {
    // Version numbers

    val Scala212 = "2.12.10"
    val Scala213 = "2.13.1"
    val Scala    = Seq(Scala212, Scala213)
    val SbtScala = Seq(Scala212)

    // This is not the sbt version used by Lagom build itself, but
    // instead the version used to build Lagom sbt plugin.
    val TargetSbt1 = "1.3.13"

    // If you update the version of Play, you probably need to update the other Play* variables.
    val Play             = "2.8.4" // sync with docs/build.sbt
    val PlayJson         = "2.9.1"
    val PlayStandaloneWs = "2.1.2"
    val Twirl            = "1.5.0" // sync with docs/project/plugins.sbt
    val PlayFileWatch    = "1.1.14"

    val Akka: String = sys.props.getOrElse("lagom.build.akka.version", "2.6.8") // sync with docs/build.sbt
    val AkkaHttp     = "10.1.12"

    val AkkaPersistenceCassandra = "0.104"
    // this is the version used in AkkaPersistenceCassandra, we stick with it
    val CassandraDriver     = "3.7.2"
    val AkkaPersistenceJdbc = "3.5.3"
    val AkkaManagement      = "1.0.9"

    val Disruptor = "3.4.2"

    // Also be sure to update ScalaTestVersion in docs/build.sbt.
    val ScalaTest            = "3.1.4"
    val Jackson              = "2.10.5"
    val JacksonCore          = Jackson
    val JacksonDatatype      = Jackson
    val JacksonDatabind      = Jackson
    val Guava                = "30.1-jre"
    val Maven                = "3.6.3"
    val MavenWagon           = "3.3.3"
    val MavenResolver        = "1.4.1"
    val Netty                = "4.1.50.Final"
    val NettyReactiveStreams = "2.0.5"
    // adapt links in (java/scala)/KafkaClient.md for minor version changes
    val AlpakkaKafka = "2.0.4"
    // Keep this version consistent with Alpakka Kafka Connector
    val Kafka = "2.4.1"

    val Curator       = "2.12.0"
    val Immutables    = "2.8.8"
    val HibernateCore = "5.4.23.Final"
    val PCollections  = "3.1.4"

    val ScalaJava8Compat = "0.9.1"
    val ScalaXml         = "1.3.0"
    val Slick            = "3.3.2"
    // JUnit[Interface] should be sync with:
    //   lagomJUnitDeps in dev/sbt-plugin/src/main/scala/com/lightbend/lagom/sbt/LagomImport.scala
    //   JUnitVersion in docs/build.sbt
    val JUnit          = "4.13.1"
    val JUnitInterface = "0.11"

    val Slf4j   = "1.7.30"
    val Logback = "1.2.3"
    val Log4j   = "2.13.3"

    val jetty = "9.4.20.v20190813"

    val Selenium  = "3.141.59"
    val ByteBuddy = "1.10.18"
  }

  // Some setup before we start creating ModuleID vals
  private val slf4jApi = "org.slf4j" % "slf4j-api" % Versions.Slf4j
  private val slf4j: Seq[ModuleID] = Seq("jcl-over-slf4j", "jul-to-slf4j", "log4j-over-slf4j").map {
    "org.slf4j" % _ % Versions.Slf4j
  } ++ Seq(slf4jApi)
  private val excludeSlf4j = slf4j.map { moduleId =>
    ExclusionRule(moduleId.organization, moduleId.name)
  }
  private val log4jModules = Seq(
    "log4j-api",
    "log4j-core",
    "log4j-slf4j-impl"
  ).map(artifactId => ("org.apache.logging.log4j" % artifactId % Versions.Log4j).excludeAll(excludeSlf4j: _*))

  // Specific libraries that get reused
  private val scalaTest: ModuleID    = ("org.scalatest" %% "scalatest" % Versions.ScalaTest).excludeAll(excludeSlf4j: _*)
  private val guava                  = "com.google.guava" % "guava" % Versions.Guava
  private val scalaJava8Compat       = "org.scala-lang.modules" %% "scala-java8-compat" % Versions.ScalaJava8Compat
  private val scalaCollectionCompat  = "org.scala-lang.modules" %% "scala-collection-compat" % "2.2.0"
  private val scalaXml               = "org.scala-lang.modules" %% "scala-xml" % Versions.ScalaXml
  private val javassist              = "org.javassist" % "javassist" % "3.24.0-GA"
  private val byteBuddy              = "net.bytebuddy" % "byte-buddy" % Versions.ByteBuddy
  private val scalaParserCombinators = "org.scala-lang.modules" %% "scala-parser-combinators" % "1.1.2"
  private val typesafeConfig         = "com.typesafe" % "config" % "1.4.1"
  private val sslConfig              = "com.typesafe" %% "ssl-config-core" % "0.4.2"
  private val h2                     = "com.h2database" % "h2" % "1.4.200"
  private val cassandraDriverCore =
    ("com.datastax.cassandra" % "cassandra-driver-core" % Versions.CassandraDriver).excludeAll(excludeSlf4j: _*)

  private val akkaActor              = "com.typesafe.akka" %% "akka-actor" % Versions.Akka
  private val akkaRemote             = "com.typesafe.akka" %% "akka-remote" % Versions.Akka
  private val akkaCluster            = "com.typesafe.akka" %% "akka-cluster" % Versions.Akka
  private val akkaClusterSharding    = "com.typesafe.akka" %% "akka-cluster-sharding" % Versions.Akka
  private val akkaClusterTools       = "com.typesafe.akka" %% "akka-cluster-tools" % Versions.Akka
  private val akkaDistributedData    = "com.typesafe.akka" %% "akka-distributed-data" % Versions.Akka
  private val akkaJackson            = "com.typesafe.akka" %% "akka-serialization-jackson" % Versions.Akka
  private val akkaMultiNodeTestkit   = "com.typesafe.akka" %% "akka-multi-node-testkit" % Versions.Akka
  private val akkaPersistence        = "com.typesafe.akka" %% "akka-persistence" % Versions.Akka
  private val akkaPersistenceQuery   = "com.typesafe.akka" %% "akka-persistence-query" % Versions.Akka
  private val akkaPersistenceTestkit = "com.typesafe.akka" %% "akka-persistence-testkit" % Versions.Akka
  private val akkaSlf4j              = ("com.typesafe.akka" %% "akka-slf4j" % Versions.Akka).excludeAll(excludeSlf4j: _*)
  private val akkaStream             = "com.typesafe.akka" %% "akka-stream" % Versions.Akka
  private val akkaProtobuf_v3        = "com.typesafe.akka" %% "akka-protobuf-v3" % Versions.Akka

  // akka typed dependencies
  private val akkaActorTyped           = "com.typesafe.akka" %% "akka-actor-typed"            % Versions.Akka
  private val akkaClusterTyped         = "com.typesafe.akka" %% "akka-cluster-typed"          % Versions.Akka
  private val akkaPersistenceTyped     = "com.typesafe.akka" %% "akka-persistence-typed"      % Versions.Akka
  private val akkaClusterShardingTyped = "com.typesafe.akka" %% "akka-cluster-sharding-typed" % Versions.Akka

  private val akkaManagement                 = "com.lightbend.akka.management" %% "akka-management"                   % Versions.AkkaManagement
  private val akkaManagementClusterHttp      = "com.lightbend.akka.management" %% "akka-management-cluster-http"      % Versions.AkkaManagement
  private val akkaManagementClusterBootstrap = "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap" % Versions.AkkaManagement

  private val akkaTestkit       = "com.typesafe.akka"   %% "akka-testkit"             % Versions.Akka
  private val akkaStreamTestkit = "com.typesafe.akka"   %% "akka-stream-testkit"      % Versions.Akka
  private val akkaTestkitTyped  = "com.typesafe.akka"   %% "akka-actor-testkit-typed" % Versions.Akka
  private val reactiveStreams   = "org.reactivestreams" % "reactive-streams"          % "1.0.3"

  private val akkaDiscovery = "com.typesafe.akka" %% "akka-discovery" % Versions.Akka

  private val akkaPersistenceJdbc =
    ("com.github.dnvriend" %% "akka-persistence-jdbc" % Versions.AkkaPersistenceJdbc).excludeAll(excludeSlf4j: _*)

  // latest version of APC depend on a Cassandra driver core that's not compatible with Lagom (newer netty/guava/etc... under the covers)
  private val akkaPersistenceCassandra         = "com.typesafe.akka" %% "akka-persistence-cassandra"          % Versions.AkkaPersistenceCassandra
  private val akkaPersistenceCassandraLauncher = "com.typesafe.akka" %% "akka-persistence-cassandra-launcher" % Versions.AkkaPersistenceCassandra
  private val akkaStreamKafka                  = "com.typesafe.akka" %% "akka-stream-kafka"                   % Versions.AlpakkaKafka
  private val kafkaClients                     = "org.apache.kafka"  % "kafka-clients"                        % Versions.Kafka

  private val akkaHttpCore      = "com.typesafe.akka" %% "akka-http-core"       % Versions.AkkaHttp
  private val akkaHttpRouteDsl  = "com.typesafe.akka" %% "akka-http"            % Versions.AkkaHttp
  private val akkaHttpSprayJson = "com.typesafe.akka" %% "akka-http-spray-json" % Versions.AkkaHttp
  private val akkaParsing       = "com.typesafe.akka" %% "akka-parsing"         % Versions.AkkaHttp

  private val sprayJson = "io.spray" %% "spray-json" % "1.3.6"

  private val play           = ("com.typesafe.play" %% "play"           % Versions.Play).excludeAll(excludeSlf4j: _*)
  private val playBuildLink  = ("com.typesafe.play" % "build-link"      % Versions.Play).excludeAll(excludeSlf4j: _*)
  private val playExceptions = ("com.typesafe.play" % "play-exceptions" % Versions.Play).excludeAll(excludeSlf4j: _*)
  private val playGuice      = ("com.typesafe.play" %% "play-guice"     % Versions.Play).excludeAll(excludeSlf4j: _*)
  private val playJava       = ("com.typesafe.play" %% "play-java"      % Versions.Play).excludeAll(excludeSlf4j: _*)
  private val playJdbc       = ("com.typesafe.play" %% "play-jdbc"      % Versions.Play).excludeAll(excludeSlf4j: _*)
  private val playNettyServer =
    ("com.typesafe.play" %% "play-netty-server" % Versions.Play).excludeAll(excludeSlf4j: _*)
  private val playAkkaHttpServer =
    ("com.typesafe.play" %% "play-akka-http-server" % Versions.Play).excludeAll(excludeSlf4j: _*)
  private val playServer = ("com.typesafe.play" %% "play-server" % Versions.Play).excludeAll(excludeSlf4j: _*)
  private val playTest   = ("com.typesafe.play" %% "play-test"   % Versions.Play).excludeAll(excludeSlf4j: _*)

  private val playWs    = ("com.typesafe.play" %% "play-ws"     % Versions.Play).excludeAll(excludeSlf4j: _*)
  private val playAhcWs = ("com.typesafe.play" %% "play-ahc-ws" % Versions.Play).excludeAll(excludeSlf4j: _*)
  private val playJson  = ("com.typesafe.play" %% "play-json"   % Versions.PlayJson).excludeAll(excludeSlf4j: _*)
  private val playFunctional =
    ("com.typesafe.play" %% "play-functional" % Versions.PlayJson).excludeAll(excludeSlf4j: _*)
  private val playFileWatch =
    ("com.lightbend.play" %% "play-file-watch" % Versions.PlayFileWatch).excludeAll(excludeSlf4j: _*)

  private val pcollections          = "org.pcollections" % "pcollections" % Versions.PCollections
  private val jsr250                = "javax.annotation" % "jsr250-api" % "1.0"
  private val junit                 = "junit" % "junit" % Versions.JUnit
  private val commonsLang           = "org.apache.commons" % "commons-lang3" % "3.11"
  private val javaxAnnotationApi    = "javax.annotation" % "javax.annotation-api" % "1.3.2"
  private val dropwizardMetricsCore = ("io.dropwizard.metrics" % "metrics-core" % "3.2.6").excludeAll(excludeSlf4j: _*)

  private val checkerQual = "org.checkerframework" % "checker-qual" % "2.11.1"

  private val okhttp3 = "com.squareup.okhttp3" % "okhttp" % "3.11.0"
  private val okio    = "com.squareup.okio"    % "okio"   % "2.7.0"
  private val kotlinDeps = Seq(
    "org.jetbrains.kotlin" % "kotlin-stdlib"        % "1.3.70",
    "org.jetbrains.kotlin" % "kotlin-stdlib-common" % "1.3.70",
    "org.jetbrains"        % "annotations"          % "13.0"
  )

  val ow2asmDeps = libraryFamily("org.ow2.asm", "8.0.1")(
    "asm",
    "asm-analysis",
    "asm-commons",
    "asm-tree",
    "asm-util"
  )

  private val jffi         = "com.github.jnr" % "jffi"          % "1.2.23"
  private val jnrConstants = "com.github.jnr" % "jnr-constants" % "0.9.15"
  private val jnrFfi       = "com.github.jnr" % "jnr-ffi"       % "2.1.15"
  private val jnrPosix     = "com.github.jnr" % "jnr-posix"     % "3.0.61"
  private val jnra64asm    = "com.github.jnr" % "jnr-a64asm"    % "1.0.0"
  private val jnrx86asm    = "com.github.jnr" % "jnr-x86asm"    % "1.0.2"

  private val mockitoCore = "org.mockito" % "mockito-core" % "3.4.6"

  private val jacksonFamily =
    libraryFamily("com.fasterxml.jackson.core", Versions.JacksonCore)(
      "jackson-annotations",
      "jackson-core"
    ) ++ libraryFamily("com.fasterxml.jackson.core", Versions.JacksonDatabind)(
      "jackson-databind"
    ) ++ libraryFamily("com.fasterxml.jackson.datatype", Versions.JacksonDatatype)(
      "jackson-datatype-jdk8",
      "jackson-datatype-jsr310",
      "jackson-datatype-guava",
      "jackson-datatype-pcollections",
    ) ++ libraryFamily("com.fasterxml.jackson.module", Versions.JacksonDatatype)(
      "jackson-module-parameter-names",
      "jackson-module-paranamer"
    ) ++ Seq("com.fasterxml.jackson.module" %% "jackson-module-scala" % Versions.JacksonDatatype) ++ libraryFamily(
      "com.fasterxml.jackson.dataformat",
      Versions.JacksonDatatype
    )(
      "jackson-dataformat-cbor",
    )

  val scalaParserCombinatorOverrides = Seq(scalaParserCombinators)

  // Allowed dependencies that Lagom is allowed to depend on, either directly or transitively.
  // This list is used to validate all of Lagom's dependencies.
  // By maintaining this allowed list, we can be absolutely sure of what we depend on, that we consistently depend on the
  // same versions of libraries across our modules, we can ensure also that we have no partial upgrades of families of
  // libraries (such as Play or Akka), and we will also be alerted when a transitive dependency is upgraded (because
  // the validation task will fail) which means we can manually check that it is safe to upgrade that dependency.
  val AllowedDependencies: Def.Initialize[Seq[ModuleID]] = Def.setting {
    val scalaVersion = Keys.scalaVersion.value

    Seq(
      "aopalliance" % "aopalliance" % "1.0",
      cassandraDriverCore,
      akkaPersistenceJdbc,
      jffi,
      jnrConstants,
      jnrFfi,
      jnrPosix,
      jnra64asm,
      jnrx86asm,
      "com.google.code.findbugs" % "jsr305"                  % "3.0.2",
      "com.google.errorprone"    % "error_prone_annotations" % "2.3.4",
      guava,
      "com.google.j2objc"            % "j2objc-annotations"   % "1.3",
      "com.google.inject"            % "guice"                % "4.2.3",
      "com.google.inject.extensions" % "guice-assistedinject" % "4.2.3",
      "com.googlecode.usc"           % "jdbcdslog"            % "1.0.6.2",
      checkerQual,
      "javax.xml.bind"   % "jaxb-api"             % "2.3.1",
      "jakarta.xml.bind" % "jakarta.xml.bind-api" % "2.3.3",
      h2,
      "com.jolbox"   % "bonecp"          % "0.8.0.RELEASE",
      "com.lmax"     % "disruptor"       % Versions.Disruptor,
      "com.novocode" % "junit-interface" % Versions.JUnitInterface,
      typesafeConfig,
      sslConfig,
      akkaDiscovery,
      akkaHttpCore,
      akkaHttpRouteDsl,
      akkaHttpSprayJson,
      akkaStreamKafka,
      akkaJackson,
      akkaParsing,
      akkaManagement,
      akkaManagementClusterHttp,
      akkaManagementClusterBootstrap,
      akkaPersistenceCassandra,
      akkaPersistenceCassandraLauncher,
      akkaPersistenceTestkit,
      sprayJson,
      "com.typesafe.netty" % "netty-reactive-streams"      % Versions.NettyReactiveStreams,
      "com.typesafe.netty" % "netty-reactive-streams-http" % Versions.NettyReactiveStreams,
      "com.typesafe.play"  %% "cachecontrol"               % "2.0.0",
      playJson,
      playFunctional,
      // play client libs
      playWs,
      playAhcWs,
      "com.typesafe.play" %% "play-ws-standalone"      % Versions.PlayStandaloneWs,
      "com.typesafe.play" %% "play-ws-standalone-xml"  % Versions.PlayStandaloneWs,
      "com.typesafe.play" %% "play-ws-standalone-json" % Versions.PlayStandaloneWs,
      "com.typesafe.play" %% "play-ahc-ws-standalone"  % Versions.PlayStandaloneWs,
      "com.typesafe.play" % "shaded-asynchttpclient"   % Versions.PlayStandaloneWs,
      "com.typesafe.play" % "shaded-oauth"             % Versions.PlayStandaloneWs,
      playTest,
      // dependencies added by play-test
      "org.fluentlenium"        % "fluentlenium-core"       % "3.7.1",
      "org.seleniumhq.selenium" % "selenium-support"        % Versions.Selenium,
      "org.seleniumhq.selenium" % "selenium-api"            % Versions.Selenium,
      "org.seleniumhq.selenium" % "selenium-remote-driver"  % Versions.Selenium,
      "org.seleniumhq.selenium" % "selenium-firefox-driver" % Versions.Selenium,
      byteBuddy,
      "org.apache.commons"   % "commons-exec"    % "1.3",
      "commons-logging"      % "commons-logging" % "1.2",
      "com.google.code.gson" % "gson"            % "2.8.4",
      okhttp3,
      okio,
      "org.atteo.classindex"        % "classindex"         % "3.4",
      "org.seleniumhq.selenium"     % "htmlunit-driver"    % "2.36.0",
      "xalan"                       % "xalan"              % "2.7.2",
      "xalan"                       % "serializer"         % "2.7.2",
      "org.apache.commons"          % "commons-text"       % "1.7",
      "org.apache.httpcomponents"   % "httpmime"           % "4.5.9",
      "org.apache.httpcomponents"   % "httpclient"         % "4.5.9",
      "org.apache.httpcomponents"   % "httpcore"           % "4.4.11",
      "net.sourceforge.htmlunit"    % "htmlunit"           % "2.36.0",
      "net.sourceforge.htmlunit"    % "htmlunit-core-js"   % "2.36.0",
      "net.sourceforge.htmlunit"    % "neko-htmlunit"      % "2.36.0",
      "org.brotli"                  % "dec"                % "0.1.2",
      "xerces"                      % "xercesImpl"         % "2.12.0",
      "xml-apis"                    % "xml-apis"           % "1.4.01",
      "net.sourceforge.htmlunit"    % "htmlunit-cssparser" % "1.5.0",
      "commons-io"                  % "commons-io"         % "2.6",
      "commons-net"                 % "commons-net"        % "3.6",
      "org.eclipse.jetty.websocket" % "websocket-client"   % Versions.jetty,
      "org.eclipse.jetty"           % "jetty-client"       % Versions.jetty,
      "org.eclipse.jetty"           % "jetty-http"         % Versions.jetty,
      "org.eclipse.jetty"           % "jetty-util"         % Versions.jetty,
      "org.eclipse.jetty"           % "jetty-io"           % Versions.jetty,
      "org.eclipse.jetty"           % "jetty-xml"          % Versions.jetty,
      "org.eclipse.jetty.websocket" % "websocket-common"   % Versions.jetty,
      "org.eclipse.jetty.websocket" % "websocket-api"      % Versions.jetty,
      jsr250,
      "com.typesafe.play"  %% "twirl-api"      % Versions.Twirl,
      "com.typesafe.slick" %% "slick"          % Versions.Slick,
      "com.typesafe.slick" %% "slick-hikaricp" % Versions.Slick,
      "com.zaxxer"         % "HikariCP"        % "3.4.5",
      "commons-codec"      % "commons-codec"   % "1.11",
      dropwizardMetricsCore,
      "io.jsonwebtoken" % "jjwt" % "0.9.1",
      // Netty 3 uses a different package to Netty 4, and a different artifact ID, so can safely coexist
      "io.netty"            % "netty"                   % "3.10.6.Final",
      "javax.cache"         % "cache-api"               % "1.1.1",
      "javax.inject"        % "javax.inject"            % "1",
      "javax.transaction"   % "jta"                     % "1.1",
      "jakarta.transaction" % "jakarta.transaction-api" % "1.3.3",
      "joda-time"           % "joda-time"               % "2.10.5",
      "junit"               % "junit"                   % Versions.JUnit,
      "net.jodah"           % "typetools"               % "0.5.0",
      "org.lz4"             % "lz4-java"                % "1.7.1",
      "com.github.luben"    % "zstd-jni"                % "1.4.3-1",
      "org.agrona"          % "agrona"                  % "1.4.1",
      commonsLang,
      kafkaClients,
      "org.codehaus.mojo"               % "animal-sniffer-annotations" % "1.18",
      "org.hibernate"                   % "hibernate-validator"        % "5.2.4.Final",
      "org.hibernate.javax.persistence" % "hibernate-jpa-2.1-api"      % "1.0.2",
      "org.immutables"                  % "value"                      % Versions.Immutables,
      javassist,
      "org.joda"       % "joda-convert"  % "1.9.2",
      "org.hamcrest"   % "hamcrest-core" % "1.3",
      "org.lmdbjava"   % "lmdbjava"      % "0.7.0",
      "com.hierynomus" % "asn-one"       % "0.4.0",
      pcollections,
      reactiveStreams,
      "org.scalactic" %% "scalactic" % Versions.ScalaTest,
      scalaTest,
      "org.scala-lang.modules" %% "scala-java8-compat" % Versions.ScalaJava8Compat,
      scalaParserCombinators,
      scalaXml,
      "org.scala-sbt"     % "test-interface" % "1.0",
      "org.typelevel"     %% "macro-compat"  % "1.1.1",
      "org.xerial.snappy" % "snappy-java"    % "1.1.7.3",
      "tyrex"             % "tyrex"          % "1.0.1",
      javaxAnnotationApi,
      scalaCollectionCompat,
      "com.google.guava"           % "failureaccess"          % "1.0.1",
      "com.google.guava"           % "listenablefuture"       % "9999.0-empty-to-avoid-conflict-with-guava",
      "com.google.protobuf"        % "protobuf-java"          % "3.10.0",
      "javax.activation"           % "activation"             % "1.1",
      "javax.activation"           % "javax.activation-api"   % "1.2.0",
      "jakarta.activation"         % "jakarta.activation-api" % "1.2.2",
      "com.thoughtworks.paranamer" % "paranamer"              % "2.8",
      mockitoCore,
      "org.objenesis" % "objenesis"        % "2.6",
      "net.bytebuddy" % "byte-buddy-agent" % Versions.ByteBuddy
    ) ++ jacksonFamily ++ crossLibraryFamily("com.typesafe.akka", Versions.Akka)(
      "akka-actor",
      "akka-actor-typed",
      "akka-cluster",
      "akka-cluster-typed",
      "akka-cluster-sharding",
      "akka-cluster-sharding-typed",
      "akka-cluster-tools",
      "akka-distributed-data",
      "akka-multi-node-testkit",
      "akka-persistence",
      "akka-persistence-typed",
      "akka-actor-testkit-typed", // remove this when https://github.com/akka/akka/pull/27830 is fixed
      "akka-persistence-query",
      "akka-protobuf-v3",
      "akka-remote",
      "akka-slf4j",
      "akka-stream",
      "akka-stream-testkit",
      "akka-testkit",
      "akka-coordination",
      "akka-pki"
    ) ++ libraryFamily("com.typesafe.play", Versions.Play)(
      "build-link",
      "play-exceptions",
      "play-netty-utils"
    ) ++ crossLibraryFamily("com.typesafe.play", Versions.Play)(
      "play",
      "play-guice",
      "play-java",
      "play-jdbc",
      "play-jdbc-api",
      "play-netty-server",
      "play-akka-http-server",
      "play-server",
      "play-streams",
    ) ++ libraryFamily("ch.qos.logback", Versions.Logback)(
      "logback-classic",
      "logback-core"
    ) ++ libraryFamily("io.netty", Versions.Netty)(
      "netty-buffer",
      "netty-codec",
      "netty-codec-http",
      "netty-common",
      "netty-handler",
      "netty-resolver",
      "netty-transport",
      "netty-transport-native-epoll",
      "netty-transport-native-unix-common"
    ) ++ libraryFamily("org.apache.logging.log4j", Versions.Log4j)(
      "log4j-api",
      "log4j-core",
      "log4j-slf4j-impl"
    ) ++ libraryFamily("org.scala-lang", scalaVersion)(
      "scala-library",
      "scala-reflect"
    ) ++ libraryFamily("org.slf4j", Versions.Slf4j)(
      "jcl-over-slf4j",
      "jul-to-slf4j",
      "log4j-over-slf4j",
      "slf4j-api",
      "slf4j-nop",
      "slf4j-log4j12"
    ) ++ ow2asmDeps ++ kotlinDeps.map(_ % Test)
  }

  // These dependencies are used by JPA to test, but we don't want to export them as part of our regular allowedlist,
  // so we maintain it separately.
  val JpaTestWhitelist = Seq(
    "antlr"                            % "antlr"                           % "2.7.7",
    "com.fasterxml"                    % "classmate"                       % "1.5.1",
    "org.dom4j"                        % "dom4j"                           % "2.1.3",
    "javax.el"                         % "el-api"                          % "2.2",
    "javax.enterprise"                 % "cdi-api"                         % "1.1",
    "org.apache.geronimo.specs"        % "geronimo-jta_1.1_spec"           % "1.1.1",
    "org.hibernate"                    % "hibernate-core"                  % Versions.HibernateCore,
    "org.hibernate.common"             % "hibernate-commons-annotations"   % "5.1.0.Final",
    "org.jboss"                        % "jandex"                          % "2.1.3.Final",
    "org.jboss.logging"                % "jboss-logging"                   % "3.3.2.Final",
    "org.jboss.spec.javax.interceptor" % "jboss-interceptors-api_1.1_spec" % "1.0.0.Beta1",
    "javax.persistence"                % "javax.persistence-api"           % "2.2",
    "org.jboss.spec.javax.transaction" % "jboss-transaction-api_1.2_spec"  % "1.1.1.Final",
    "org.glassfish.jaxb"               % "jaxb-runtime"                    % "2.3.1",
    "org.glassfish.jaxb"               % "txw2"                            % "2.3.1",
    "com.sun.istack"                   % "istack-commons-runtime"          % "3.0.7",
    "org.jvnet.staxex"                 % "stax-ex"                         % "1.8",
    "com.sun.xml.fastinfoset"          % "FastInfoset"                     % "1.2.15"
  )

  // These dependencies are used by the Kafka tests, but we don't want to export them as part of our regular
  // allowList, so we maintain it separately.
  val KafkaTestWhitelist = Seq(
    "com.101tec"                 % "zkclient"             % "0.11",
    "com.yammer.metrics"         % "metrics-core"         % "2.2.0",
    "jline"                      % "jline"                % "0.9.94",
    "log4j"                      % "log4j"                % "1.2.17",
    "com.typesafe.scala-logging" %% "scala-logging"       % "3.9.0",
    "net.sf.jopt-simple"         % "jopt-simple"          % "5.0.4",
    "org.apache.commons"         % "commons-math"         % "2.2",
    "org.apache.curator"         % "curator-client"       % Versions.Curator,
    "org.apache.curator"         % "curator-framework"    % Versions.Curator,
    "org.apache.curator"         % "curator-test"         % Versions.Curator,
    "org.apache.kafka"           %% "kafka"               % Versions.Kafka,
    "org.apache.zookeeper"       % "zookeeper"            % "3.4.13",
    "org.apache.yetus"           % "audience-annotations" % "0.5.0"
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
    akkaActorTyped,
    akkaJackson,
    play,
    guava,
    // Upgrades needed to match allowed versions
    sslConfig,
    pcollections,
    playJson,
    jnrFfi,
    jffi,
    jnra64asm,
    jnrConstants,
    slf4jApi,
  ) ++ jacksonFamily ++ ow2asmDeps // to match allowed versions

  val `api-javadsl` = libraryDependencies ++= Seq(
    playJava,
    playGuice,
    pcollections,
    // Upgrades needed to match allowed versions
    sslConfig,
    scalaTest                      % Test,
    "com.fasterxml.jackson.module" % "jackson-module-parameter-names" % Versions.Jackson % Test
  )

  val `api-scaladsl` = libraryDependencies ++= Seq(
    scalaCollectionCompat,
    // Upgrades needed to match allowed versions
    sslConfig,
    scalaTest % Test
  )

  val immutables = libraryDependencies += "org.immutables" % "value" % Versions.Immutables

  val jackson = libraryDependencies ++= jacksonFamily ++ Seq(
    // Upgrades needed to match allowed versions
    sslConfig,
    pcollections,
    akkaJackson,
    akkaTestkit    % Test,
    akkaActorTyped % Test,
    scalaTest      % Test,
    junit          % Test,
    slf4jApi       % Test,
    "com.novocode" % "junit-interface" % "0.11" % Test,
  )

  val `play-json` = libraryDependencies ++= Seq(
    playJson,
    akkaActor,
    akkaTestkit % Test,
    scalaTest   % Test,
    // Upgrades needed to match allowed versions
    scalaOrganization.value % "scala-reflect" % scalaVersion.value,
    scalaJava8Compat,
    pcollections,
    scalaXml               % Test,
    scalaParserCombinators % Test
    // explicitly depend on particular versions of jackson
  ) ++ jacksonFamily ++ Seq(
    // explicitly depend on particular versions of guava
    guava
  )

  val `api-tools` = libraryDependencies ++= Seq(
    akkaActorTyped,
    akkaJackson,
    play,
    scalaTest % Test,
    // Upgrades needed to match allowed versions
    reactiveStreams,
    sslConfig,
    playJson,
    scalaParserCombinators,
    scalaXml,
    akkaStream,
    akkaActor,
    akkaActorTyped,
    akkaJackson,
    akkaSlf4j,
    akkaProtobuf_v3,
    guava,
    jnrFfi,
    jffi,
    jnra64asm,
    jnrConstants,
    pcollections,
  ) ++ jacksonFamily ++ ow2asmDeps // to match allowed versions

  val client = libraryDependencies ++= Seq(
    slf4jApi,
    playWs,
    playAhcWs,
    dropwizardMetricsCore,
    "com.typesafe.netty" % "netty-reactive-streams" % Versions.NettyReactiveStreams,
    "io.netty"           % "netty-codec-http" % Versions.Netty,
    scalaTest            % Test,
    // Upgrades needed to match allowed versions
    sslConfig,
    "io.netty" % "netty-handler" % Versions.Netty,
    // update to enforce using snapshots in nightly jobs
    akkaActorTyped % Test,
  )

  val `client-javadsl` = libraryDependencies ++= Seq(
    akkaTestkit % Test,
    scalaTest   % Test,
    junit       % Test,
    mockitoCore % Test,
    // Upgrades needed to match allowed versions
    byteBuddy % Test
  )

  val `client-scaladsl` = libraryDependencies ++= Seq(
    scalaTest % Test
  )

  val `integration-client-javadsl` = libraryDependencies ++= Seq(
    akkaJackson,
    playWs,
    playAhcWs,
    // we need to explicitly add akka-remote in test scope
    // because the test for LagomClientFactory needs it
    akkaRemote % Test,
    scalaTest  % Test,
    // Upgrades needed to match allowed versions
    scalaCollectionCompat,
    // update to enforce using snapshots in nightly jobs
    akkaActorTyped % Test,
    akkaJackson
  )

  val server = libraryDependencies ++= Nil

  val `server-javadsl` = libraryDependencies ++= Seq(
    akkaManagement,
    slf4jApi,
    commonsLang,
    javaxAnnotationApi
  )

  val `server-scaladsl` = libraryDependencies ++= Seq(
    akkaManagement,
    slf4jApi,
    scalaTest % Test,
    // update to enforce using snapshots in nightly jobs
    akkaActorTyped % Test,
  )

  val `testkit-core` = libraryDependencies ++= Seq(
    akkaActor,
    akkaActorTyped,
    akkaJackson,
    akkaStream,
    play,
    akkaPersistenceCassandraLauncher,
    // Upgrades needed to match allowed versions
    sslConfig,
    playJson,
    akkaSlf4j,
    scalaXml,
    jffi,
    jnrFfi,
    jnrConstants,
    jnrPosix,
    byteBuddy,
    // update to enforce using snapshots in nightly jobs
    akkaActorTyped,
    akkaJackson
  ) ++ ow2asmDeps // to match allowed versions

  val `testkit-javadsl` = libraryDependencies ++= Seq(
    akkaStreamTestkit,
    playAkkaHttpServer % Test,
    scalaTest          % Test,
    "junit"            % "junit" % Versions.JUnit,
    h2                 % Test,
    // Without any binding, slf4j will print warnings when running tests
    "org.slf4j" % "slf4j-nop" % Versions.Slf4j % Test
  )

  val `testkit-scaladsl` = libraryDependencies ++= Seq(
    akkaStreamTestkit,
    playAkkaHttpServer % Test,
    scalaTest          % Test,
    "junit"            % "junit" % Versions.JUnit,
    h2                 % Test,
    // Without any binding, slf4j will print warnings when running tests
    "org.slf4j" % "slf4j-nop" % Versions.Slf4j % Test
  )

  val `integration-tests-javadsl` = libraryDependencies ++= Seq(
    playAkkaHttpServer % Test,
    playNettyServer    % Test,
    playTest           % Test,
    junit              % Test,
    slf4jApi           % Test,
    "com.novocode"     % "junit-interface" % "0.11" % Test,
    scalaTest,
    // Upgrades needed to match allowed versions
    okio        % Test,
    byteBuddy   % Test,
    commonsLang % Test,
    "io.netty"  % "netty-transport-native-epoll" % Versions.Netty,
    "io.netty"  % "netty-transport-native-unix-common" % Versions.Netty,
    // update to enforce using snapshots in nightly jobs
    akkaActorTyped % Test,
  )

  val `integration-tests-scaladsl` = libraryDependencies ++= Seq(
    playAkkaHttpServer % Test,
    playNettyServer    % Test,
    playTest           % Test,
    junit              % Test,
    slf4jApi           % Test,
    "com.novocode"     % "junit-interface" % "0.11" % Test,
    scalaTest,
    okio        % Test,
    byteBuddy   % Test,
    commonsLang % Test,
    "io.netty"  % "netty-transport-native-epoll" % Versions.Netty,
    "io.netty"  % "netty-transport-native-unix-common" % Versions.Netty
  )

  val `lagom-akka-discovery-service-locator-core` = libraryDependencies ++= Seq(
    akkaDiscovery,
    slf4jApi,
    scalaTest % Test,
    // Upgrades needed to match allowed versions
    scalaJava8Compat,
    scalaXml
  )

  val `lagom-akka-discovery-service-locator-scaladsl` = libraryDependencies ++= Seq(
    scalaTest          % Test,
    playAkkaHttpServer % Test,
    // update to enforce using snapshots in nightly jobs
    akkaStream
  )

  val `akka-management-core` = libraryDependencies ++= Seq(
    play,
    akkaManagement,
    // Upgrades needed to match allowed versions
    akkaStream,
    akkaActor,
    akkaActorTyped,
    akkaJackson,
    akkaProtobuf_v3,
    akkaSlf4j,
    scalaXml,
    akkaHttpCore,
    akkaHttpRouteDsl,
    akkaHttpSprayJson,
    akkaParsing,
    guava,
    jnrFfi,
    jffi,
    jnra64asm,
    jnrConstants,
    pcollections,
    slf4jApi,
    playJson,
    playFunctional
  ) ++ jacksonFamily ++ ow2asmDeps // to match allowed versions

  val `akka-management-javadsl`  = libraryDependencies ++= Seq.empty[ModuleID]
  val `akka-management-scaladsl` = libraryDependencies ++= Seq.empty[ModuleID]

  val `cluster-core` = libraryDependencies ++= Seq(
    akkaCluster,
    akkaClusterTyped,
    akkaManagementClusterBootstrap,
    akkaManagementClusterHttp,
    akkaProtobuf_v3,
    akkaTestkit          % Test,
    akkaMultiNodeTestkit % Test,
    scalaTest            % Test,
    junit                % Test,
    slf4jApi             % Test,
    "com.novocode"       % "junit-interface" % "0.11" % Test,
    // Upgrades needed to match allowed versions
    sslConfig,
    scalaJava8Compat,
    scalaParserCombinators,
    scalaXml,
    akkaSlf4j,
    playJson,
    sprayJson,
    jffi,
    jnrFfi,
    jnrConstants,
    // transitive dependencies from Akka Management
    // may not match the Akka version in use so
    // must be explicitly bumped
    akkaDiscovery,
    akkaClusterSharding,
    akkaClusterShardingTyped,
    akkaDistributedData,
    akkaPersistence,
    akkaClusterTools
  ) ++ Seq("logback-core", "logback-classic").map(
    "ch.qos.logback" % _ % Versions.Logback % Test
  )

  val `cluster-javadsl` = libraryDependencies ++= Seq(
    akkaTestkit          % Test,
    akkaMultiNodeTestkit % Test,
    scalaTest            % Test,
    junit                % Test,
    "com.novocode"       % "junit-interface" % "0.11" % Test,
    // Upgrades needed to match allowed versions
    slf4jApi,
  )

  val `cluster-scaladsl` = libraryDependencies ++= Seq(
    akkaTestkit          % Test,
    akkaMultiNodeTestkit % Test,
    scalaTest            % Test,
    junit                % Test,
    "com.novocode"       % "junit-interface" % "0.11" % Test,
    // Upgrades needed to match allowed versions
    sslConfig,
    scalaXml,
    akkaSlf4j
    // explicitly depend on particular versions of jackson
  ) ++ jacksonFamily ++ Seq(
    // explicitly depend on particular versions of guava
    guava,
    // Upgrades needed to match allowed versions
    slf4jApi,
  )

  val `pubsub-javadsl` = libraryDependencies ++= Seq(
    akkaClusterTools,
    akkaTestkit          % Test,
    akkaMultiNodeTestkit % Test,
    akkaStreamTestkit    % Test,
    scalaTest            % Test,
    junit                % Test,
    slf4jApi             % Test,
    "com.novocode"       % "junit-interface" % "0.11" % Test,
    slf4jApi             % Test,
    // Upgrades needed to match allowed versions
    jnrConstants,
    // explicitly depend on particular versions of jackson
  ) ++ jacksonFamily ++ Seq(
    // explicitly depend on particular versions of guava
    guava
  )

  val `pubsub-scaladsl` = libraryDependencies ++= Seq(
    akkaClusterTools,
    akkaTestkit          % Test,
    akkaMultiNodeTestkit % Test,
    akkaStreamTestkit    % Test,
    scalaTest            % Test,
    slf4jApi             % Test,
    // Upgrades needed to match allowed versions
    sslConfig,
    scalaXml % Test,
    akkaSlf4j,
    jnrConstants
  )

  val `projection-core` = libraryDependencies ++= Seq(
    akkaClusterSharding,
    akkaSlf4j,
    akkaTestkit          % Test,
    akkaMultiNodeTestkit % Test,
    akkaStreamTestkit    % Test,
    scalaTest            % Test,
    junit                % Test,
    "com.novocode"       % "junit-interface" % "0.11" % Test,
    // Upgrades needed to match allowed versions
    jnrConstants,
  )

  val `projection-scaladsl` = libraryDependencies ++= Seq.empty[ModuleID]

  val `projection-javadsl` = libraryDependencies ++= Seq.empty[ModuleID]

  val `persistence-core` = libraryDependencies ++= Seq(
    akkaActor, // explicit dependency for CRON builds using Akka snapshot versions
    akkaActorTyped,
    akkaPersistence,
    akkaPersistenceQuery,
    akkaClusterSharding,
    akkaPersistenceTyped,
    akkaClusterShardingTyped,
    akkaJackson,
    akkaSlf4j,
    play,
    akkaTestkit          % Test,
    akkaMultiNodeTestkit % Test,
    akkaStreamTestkit    % Test,
    scalaTest            % Test,
    junit                % Test,
    "com.novocode"       % "junit-interface" % "0.11" % Test,
    // Upgrades needed to match allowed versions
    sslConfig,
    playJson,
    scalaXml
  ) ++ jacksonFamily // akka typed brings in jackson deps, but we Lagom has a more recent one

  val `persistence-testkit` = libraryDependencies ++= Seq(
    akkaPersistence,
    akkaTestkit,
    akkaTestkitTyped,
    slf4jApi,
    scalaJava8Compat,
    // Upgrades needed to match allowed versions
    sslConfig
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
    // Upgrades needed to match allowed versions
    sslConfig,
    dropwizardMetricsCore,
    scalaXml,
    jnrConstants,
    jnrPosix,
    "io.netty" % "netty-handler" % Versions.Netty
  )

  val `persistence-cassandra-javadsl` = libraryDependencies ++= Seq(
    junit % Test,
    jsr250,
  )

  val `persistence-cassandra-scaladsl` = libraryDependencies ++= Seq(
    )

  val `persistence-jdbc-core` = libraryDependencies ++= Seq(
    slf4jApi,
    akkaPersistenceJdbc,
    playJdbc,
    junit % Test,
    h2    % Test,
    // Upgrades needed to match allowed versions
    scalaCollectionCompat,
    scalaXml,
    jnrConstants,
    pcollections,
  )

  val `persistence-jdbc-javadsl` = libraryDependencies ++= Seq(
    h2 % Test
  )

  val `persistence-jdbc-scaladsl` = libraryDependencies ++= Seq(
    h2 % Test
  )

  val `persistence-jpa-javadsl` = libraryDependencies ++= Seq(
    "org.hibernate.javax.persistence" % "hibernate-jpa-2.1-api" % "1.0.2" % Provided,
    "org.hibernate"                   % "hibernate-core" % Versions.HibernateCore % Test,
    h2                                % Test,
    javassist                         % Test,
    // Upgrades needed to match allowed versions
    byteBuddy,
    jnrConstants
  )

  val `broker-javadsl` = libraryDependencies ++= Nil

  val `broker-scaladsl` = libraryDependencies ++= Nil

  val `kafka-client` = libraryDependencies ++= Seq(
    "org.slf4j" % "log4j-over-slf4j" % Versions.Slf4j,
    akkaStreamKafka.exclude("org.slf4j", "slf4j-log4j12"),
    scalaTest % Test,
    // Upgrades needed to match allowed versions
    sslConfig,
    kafkaClients,
    scalaCollectionCompat,
  )

  val `kafka-client-javadsl` = libraryDependencies ++= Seq(
    scalaTest % Test,
    // Upgrades needed to match allowed versions
    sslConfig,
    kafkaClients,
    jnrConstants
  )

  val `kafka-client-scaladsl` = libraryDependencies ++= Seq(
    scalaTest % Test,
    // Upgrades needed to match allowed versions
    sslConfig,
    kafkaClients,
    jnrConstants
  )

  val `kafka-broker` = libraryDependencies ++= Seq(
    kafkaClients,
    // Upgrades needed to match allowed versions
    jnrConstants,
    pcollections,
    scalaCollectionCompat,
  )

  val `kafka-broker-javadsl` = libraryDependencies ++= Seq(
    slf4jApi,
    "log4j"   % "log4j" % "1.2.17",
    scalaTest % Test,
    junit     % Test,
    // Upgrades needed to match allowed versions
    jnrPosix
  )

  val `kafka-broker-scaladsl` = libraryDependencies ++= Seq(
    "log4j"   % "log4j" % "1.2.17",
    scalaTest % Test,
    junit     % Test,
    // Upgrades needed to match allowed versions
    jnrPosix
  )

  val logback = libraryDependencies ++= slf4j ++ Seq(
    // needed only because we use play.utils.Colors
    play,
    // Upgrades needed to match allowed versions
    reactiveStreams,
    sslConfig,
    playJson,
    scalaParserCombinators,
    akkaStream,
    akkaActor,
    akkaActorTyped,
    akkaJackson,
    akkaSlf4j,
    akkaProtobuf_v3,
    scalaXml,
    guava,
    jnrFfi,
    jffi,
    jnra64asm,
    jnrConstants,
    pcollections,
  ) ++ jacksonFamily ++ ow2asmDeps ++ Seq("logback-core", "logback-classic").map(
    "ch.qos.logback" % _ % Versions.Logback
  )

  val log4j2 = libraryDependencies ++= Seq(slf4jApi) ++
    log4jModules ++
    ow2asmDeps ++ // to match allowed versions
    jacksonFamily ++
    Seq(
      "com.lmax" % "disruptor" % Versions.Disruptor,
      play,
      // Upgrades needed to match allowed versions
      reactiveStreams,
      sslConfig,
      playJson,
      scalaXml,
      scalaParserCombinators,
      akkaStream,
      akkaActor,
      akkaActorTyped,
      akkaJackson,
      akkaSlf4j,
      akkaProtobuf_v3,
      guava,
      jnrFfi,
      jffi,
      jnra64asm,
      jnrConstants,
      pcollections,
    )

  val `reloadable-server` = libraryDependencies ++= Seq(
    playServer,
    // Upgrades needed to match allowed versions
    reactiveStreams,
    playJson,
    scalaParserCombinators,
    scalaXml,
    akkaStream,
    akkaActor,
    akkaActorTyped,
    slf4jApi,
    akkaSlf4j,
    akkaProtobuf_v3,
    jnrFfi,
    jffi,
    jnra64asm,
    jnrConstants,
    guava,
    checkerQual
  ) ++ ow2asmDeps // to match allowed versions

  val `server-containers` = libraryDependencies ++= Seq(
    // This is used in the code to check if the embedded cassandra server is started
    cassandraDriverCore,
  )

  val `build-tool-support` = libraryDependencies ++= Seq(
    playExceptions,
    playBuildLink,
    playFileWatch,
    // explicitly depend on particular versions of guava
    guava,
    scalaTest % Test
  )

  val `sbt-plugin` = libraryDependencies ++= Seq(
    // And this is needed to silence the datastax driver logging
    "org.slf4j" % "slf4j-nop" % Versions.Slf4j,
    scalaTest   % Test
  )

  val `maven-plugin` = libraryDependencies ++= Seq(
    "org.apache.maven"                % "maven-plugin-api"             % Versions.Maven,
    "org.apache.maven"                % "maven-core"                   % Versions.Maven,
    "org.apache.maven.plugin-testing" % "maven-plugin-testing-harness" % "3.3.0" % Test,
    slf4jApi,
    scalaTest % Test
  )

  val `maven-launcher` = libraryDependencies := Seq(
    "org.apache.maven" % "apache-maven" % Versions.Maven
  )

  val `service-locator` = libraryDependencies ++= Seq(
    playAkkaHttpServer,
    akkaHttpCore,
    scalaTest % Test,
    // update to enforce using snapshots in nightly jobs
    akkaActorTyped,
  )

  val `dev-mode-ssl-support` = libraryDependencies ++= Seq(
    playServer,
    akkaHttpCore,
    // updates to match allowed List
    akkaActor,
    akkaActorTyped,
    akkaJackson,
    akkaStream,
    akkaProtobuf_v3,
    akkaSlf4j,
    slf4jApi,
    pcollections,
    typesafeConfig,
    sslConfig,
    scalaXml,
    playJson,
    guava,
    checkerQual
  ) ++ jacksonFamily

  val `service-registry-client-core` =
    libraryDependencies ++= Seq(
      akkaDiscovery,
      slf4jApi,
      akkaTestkit % Test,
      scalaTest   % Test,
      // updates to match whitelist
      akkaActor,
      akkaActorTyped,
      scalaJava8Compat,
      // update to enforce using snapshots in nightly jobs
      akkaActorTyped % Test,
    )

  val `service-registry-client-javadsl` =
    libraryDependencies ++= Seq(
      akkaDiscovery,
      akkaTestkit    % Test,
      junit          % Test,
      "com.novocode" % "junit-interface" % "0.11" % Test,
      // update to enforce using snapshots in nightly jobs
      akkaActorTyped % Test,
    )

  val `service-registration-javadsl` = libraryDependencies ++= Nil

  val `devmode-scaladsl` =
    libraryDependencies ++= Seq(
      akkaDiscovery
    )

  val `play-integration-javadsl` = libraryDependencies ++= Nil

  val `cassandra-server` = libraryDependencies ++= Seq(
    akkaPersistenceCassandraLauncher,
    akkaPersistenceCassandra,
    // explicitly depend on particular versions of guava
    guava
  )

  val `kafka-server` = libraryDependencies ++= Seq(
    "org.apache.kafka" %% "kafka" % Versions.Kafka,
    // Note that curator 3.x is only compatible with zookeeper 3.5.x. Kafka currently uses zookeeper 3.4, hence we have
    // to use curator 2.x, which is compatible with zookeeper 3.4 (see the notice in
    // http://curator.apache.org/index.html - make sure to scroll to the bottom)
    "org.apache.curator" % "curator-framework" % Versions.Curator,
    "org.apache.curator" % "curator-test"      % Versions.Curator,
    javassist,
    scalaJava8Compat,
    scalaTest % Test,
    // explicitly depend on particular versions of guava
    guava
  )

  val validateDependencies = taskKey[Unit]("Validate Lagom dependencies to ensure they are allowed")
  val allowedDependencies  = settingKey[Seq[ModuleID]]("The allowed dependencies")
  val allowedPrune         = taskKey[Unit]("List items that can be pruned from the allowed ")

  val validateDependenciesTask: Def.Initialize[Task[Unit]] = Def.task {
    // We validate compile dependencies to ensure that whatever we are exporting, we are exporting the right
    // versions. We validate test dependencies to ensure that our tests run against the same versions that we are
    // exporting
    val compileClasspath = (managedClasspath in Compile).value
    val testClasspath    = (managedClasspath in Test).value
    val cross            = CrossVersion(scalaVersion.value, scalaBinaryVersion.value)
    val log              = streams.value.log
    val svb              = scalaBinaryVersion.value

    val allowList = (allowedDependencies.value ++ KafkaTestWhitelist).iterator
      .map(cross)
      .toTraversable
      .groupBy(crossModuleId => (crossModuleId.organization, crossModuleId.name))
      .iterator
      .map { case (key, crossModuleIds) => key -> crossModuleIds.map(_.revision) }
      .toMap

    val dupes = allowList.collect { case (key, versions) if versions.size > 1 => (key, versions) }
    if (dupes.nonEmpty) {
      dupes.foreach {
        case ((org, id), revs) =>
          val revsS = revs.mkString("[", ", ", "]")
          log.error(s"[${name.value}] dependency $org:$id in allowList with multiple versions: $revsS")
      }
      throw new AllowDependenciesValidationFailed
    }

    def collectProblems(scope: String, classpath: Classpath) = {
      classpath.collect(Function.unlift { dep =>
        val moduleId = dep.get(moduleID.key).getOrElse {
          sys.error(s"Managed classpath dependency without moduleID: $dep")
        }

        allowList.get((moduleId.organization, moduleId.name)) match {
          case None =>
            Some(moduleId -> s"[${name.value}] $scope dependency not in allowList: $moduleId")
          case Some(revs) if revs.forall(moduleId.revision != _) =>
            val unmatched = revs.mkString("[", ", ", "]")
            Some(
              moduleId -> s"[${name.value}] $scope dependency ${moduleId.organization}:${moduleId.name} version ${moduleId.revision} doesn't match allowList versions $unmatched"
            )
          case _ => None
        }
      })
    }

    val problems = collectProblems("Compile", compileClasspath) ++ collectProblems("Test", testClasspath)

    if (problems.nonEmpty) {
      problems.foreach(p => log.error(p._2))
      log.error(s"Found ${problems.length} errors.")

      log.debug {
        // This makes it very easy to fix the problem, by outputting a formatted list of dependencies to add.
        problems
          .map { problem =>
            val mid   = problem._1
            val cross = mid.name.endsWith("_" + svb)
            val m     = if (cross) "%%" else "%"
            val name  = if (cross) mid.name.dropRight(svb.length + 1) else mid.name
            s""""${mid.organization}" $m "$name" % "${mid.revision}""""
          }
          .sorted
          .mkString(
            "The following dependencies need to be added to the allowList:\n",
            ",\n",
            ""
          )
      }
      if (sys.env.get("TRAVIS_EVENT_TYPE") != Some("cron"))
        throw new AllowDependenciesValidationFailed
    }
  }

  val allowedPruneTask: Def.Initialize[Task[Unit]] = Def.task {
    val compileClasspath = (managedClasspath in Compile).value
    val testClasspath    = (managedClasspath in Test).value
    val cross            = CrossVersion(scalaVersion.value, scalaBinaryVersion.value)
    val log              = streams.value.log
    val svb              = scalaBinaryVersion.value

    val allowList: Map[(String, String), String] = allowedDependencies.value.map { moduleId =>
      val crossModuleId = cross(moduleId)
      (crossModuleId.organization, crossModuleId.name) -> crossModuleId.revision
    }.toMap

    def collectProblems(scope: String, classpath: Classpath): Set[(String, String)] = {
      val modules: Set[(String, String)] =
        classpath.toSet[Attributed[File]].flatMap(_.get(moduleID.key)).map(mod => (mod.organization, mod.name))
      allowList.keySet -- modules
    }

    val problems = collectProblems("Compile", compileClasspath) ++ collectProblems("Test", testClasspath)

    if (problems.nonEmpty) {
      problems.foreach(p => log.error(s"${name.value} - Found unnecessary allowList item: ${p._1}:${p._2}"))
    } else {
      log.error(s"${name.value} needs a complete allowList.")
    }
  }
  val allowedPruneSetting         = allowedPrune := allowedPruneTask.value
  val validateDependenciesSetting = validateDependencies := validateDependenciesTask.value
  val allowDependenciesSetting    = allowedDependencies := AllowedDependencies.value

  private class AllowDependenciesValidationFailed extends RuntimeException with FeedbackProvidedException {
    override def toString = "Allow Dependencies validation failed!"
  }
}
