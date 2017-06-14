import sbt._
import sbt.Keys._

object Dependencies {

  // Version numbers
  val PlayVersion = "2.5.13"
  val AkkaVersion = "2.4.19"
  val ScalaVersion = "2.11.11"
  val AkkaPersistenceCassandraVersion = "0.29"
  val ScalaTestVersion = "3.0.1"
  val JacksonVersion = "2.7.8"
  val CassandraAllVersion = "3.0.9"
  val CassandraDriverVersion = "3.1.4"
  val GuavaVersion = "19.0"
  val MavenVersion = "3.3.9"
  val NettyVersion = "4.0.42.Final"
  val KafkaVersion = "0.10.0.1"
  val AkkaStreamKafkaVersion = "0.13"
  val Log4jVersion = "1.2.17"
  val ScalaJava8CompatVersion = "0.7.0"
  val ScalaXmlVersion = "1.0.5"
  val SlickVersion = "3.2.0"

  // Specific libraries that get reused
  private val scalaTest = "org.scalatest" %% "scalatest" % ScalaTestVersion
  private val guava = "com.google.guava" % "guava" % GuavaVersion
  private val log4J = "log4j" % "log4j" % Log4jVersion
  private val scalaJava8Compat = "org.scala-lang.modules" %% "scala-java8-compat" % ScalaJava8CompatVersion
  private val scalaXml = "org.scala-lang.modules" %% "scala-xml" % ScalaXmlVersion
  private val jbossLogging = "org.jboss.logging" % "jboss-logging" % "3.3.0.Final"
  private val typesafeConfig = "com.typesafe" % "config" % "1.3.1"

  private val akkaActor = "com.typesafe.akka" %% "akka-actor" % AkkaVersion
  private val akkaCluster = "com.typesafe.akka" %% "akka-cluster" % AkkaVersion
  private val akkaClusterSharding = "com.typesafe.akka" %% "akka-cluster-sharding" % AkkaVersion
  private val akkaClusterTools = "com.typesafe.akka" %% "akka-cluster-tools" % AkkaVersion
  private val akkaMultiNodeTestkit = "com.typesafe.akka" %% "akka-multi-node-testkit" % AkkaVersion
  private val akkaPersistence = "com.typesafe.akka" %% "akka-persistence" % AkkaVersion
  private val akkaPersistenceQuery = "com.typesafe.akka" %% "akka-persistence-query-experimental" % AkkaVersion
  private val akkaSlf4j = "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion
  private val akkaStream = "com.typesafe.akka" %% "akka-stream" % AkkaVersion
  private val akkaStreamTestkit = "com.typesafe.akka" %% "akka-stream-testkit" % AkkaVersion
  private val akkaTestkit = "com.typesafe.akka" %% "akka-testkit" % AkkaVersion

  // latest version of APC depend on a Cassandra driver core that's not compatible with Lagom (newer netty/guava/etc... under the covers)
  private val akkaPersistenceCassandra = "com.typesafe.akka" %% "akka-persistence-cassandra" % AkkaPersistenceCassandraVersion exclude ("com.datastax.cassandra" , "cassandra-driver-core")
  private val cassandraDriverCore = "com.datastax.cassandra" % "cassandra-driver-core" % CassandraDriverVersion

  private val akkaStreamKafka = "com.typesafe.akka" %% "akka-stream-kafka" % AkkaStreamKafkaVersion

  private val play = "com.typesafe.play" %% "play" % PlayVersion
  private val playBuildLink = "com.typesafe.play" % "build-link" % PlayVersion
  private val playExceptions =  "com.typesafe.play" % "play-exceptions" % PlayVersion
  private val playJava = "com.typesafe.play" %% "play-java" % PlayVersion
  private val playJdbc = "com.typesafe.play" %% "play-jdbc" % PlayVersion
  private val playJson = "com.typesafe.play" %% "play-json" % PlayVersion
  private val playNettyServer = "com.typesafe.play" %% "play-netty-server" % PlayVersion
  private val playServer = "com.typesafe.play" %% "play-server" % PlayVersion
  private val playWs = "com.typesafe.play" %% "play-ws" % PlayVersion

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
      "com.addthis.metrics" % "reporter-config-base" % "3.0.0",
      "com.addthis.metrics" % "reporter-config3" % "3.0.0",
      "com.boundary" % "high-scale-lib" % "1.0.6",
      "com.clearspring.analytics" % "stream" % "2.5.2",
      cassandraDriverCore,
      "com.fasterxml" % "classmate" % "1.3.0",
      "com.fasterxml.jackson.module" % "jackson-module-parameter-names" % JacksonVersion,
      "com.github.dnvriend" %% "akka-persistence-jdbc" % "2.4.17.1",
      "com.github.jbellis" % "jamm" % "0.3.0",
      "com.github.jnr" % "jffi" % "1.2.10",
      "com.github.jnr" % "jffi" % "1.2.10",
      "com.github.jnr" % "jnr-constants" % "0.9.0",
      "com.github.jnr" % "jnr-ffi" % "2.0.7",
      "com.github.jnr" % "jnr-posix" % "3.0.27",
      "com.github.jnr" % "jnr-x86asm" % "1.0.2",
      "com.google.code.findbugs" % "jsr305" % "3.0.1",
      "com.google.guava" % "guava" % GuavaVersion,
      "com.google.inject" % "guice" % "4.0",
      "com.google.inject.extensions" % "guice-assistedinject" % "4.0",
      "com.googlecode.concurrentlinkedhashmap" % "concurrentlinkedhashmap-lru" % "1.4",
      "com.googlecode.json-simple" % "json-simple" % "1.1",
      "com.googlecode.usc" % "jdbcdslog" % "1.0.6.2",
      "com.h2database" % "h2" % "1.4.192",
      "com.jolbox" % "bonecp" % "0.8.0.RELEASE",
      "com.lmax" % "disruptor" % "3.3.6",
      "com.ning" % "compress-lzf" % "0.8.4",
      "com.novocode" % "junit-interface" % "0.11",
      "com.thinkaurelius.thrift" % "thrift-server" % "0.3.7",
      typesafeConfig,
      "com.typesafe" %% "ssl-config-core" % "0.2.1",
      akkaStreamKafka,
      akkaPersistenceCassandra,
      "com.typesafe.netty" % "netty-reactive-streams" % "1.0.8",
      "com.typesafe.netty" % "netty-reactive-streams-http" % "1.0.8",
      "com.typesafe.play" %% "twirl-api" % "1.1.1",
      "com.typesafe.slick" %% "slick" % SlickVersion,
      "com.typesafe.slick" %% "slick-hikaricp" % SlickVersion,
      "com.zaxxer" % "HikariCP" % "2.5.1",
      "commons-cli" % "commons-cli" % "1.1",
      "commons-codec" % "commons-codec" % "1.10",
      "commons-logging" % "commons-logging" % "1.2",
      "io.aeron" % "aeron-client" % "1.2.5",
      "io.aeron" % "aeron-driver" % "1.2.5",
      "io.dropwizard.metrics" % "metrics-core" % "3.1.2",
      "io.dropwizard.metrics" % "metrics-jvm" % "3.1.0",
      // Netty 3 uses a different package to Netty 4, and a different artifact ID, so can safely coexist
      "io.netty" % "netty" % "3.10.6.Final",
      "it.unimi.dsi" % "fastutil" % "6.5.7",
      "javax.el" % "javax.el-api" % "3.0.0",
      "javax.inject" % "javax.inject" % "1",
      "javax.transaction" % "jta" % "1.1",
      "javax.validation" % "validation-api" % "1.1.0.Final",
      "joda-time" % "joda-time" % "2.9.6",
      "junit" % "junit" % "4.11",
      "net.java.dev.jna" % "jna" % "4.0.0",
      "net.jodah" % "typetools" % "0.4.4",
      "net.jpountz.lz4" % "lz4" % "1.3.0",
      "oauth.signpost" % "signpost-commonshttp4" % "1.2.1.2",
      "oauth.signpost" % "signpost-core" % "1.2.1.2",
      "org.agrona" % "agrona" % "0.9.5",
      "org.antlr" % "ST4" % "4.0.8",
      "org.antlr" % "antlr" % "3.5.2",
      "org.antlr" % "antlr-runtime" % "3.5.2",
      "org.apache.cassandra" % "cassandra-all" % CassandraAllVersion,
      "org.apache.cassandra" % "cassandra-thrift" % CassandraAllVersion,
      "org.apache.commons" % "commons-lang3" % "3.4",
      "org.apache.commons" % "commons-math3" % "3.2",
      "org.apache.httpcomponents" % "httpclient" % "4.5.2",
      "org.apache.httpcomponents" % "httpcore" % "4.4.4",
      "org.apache.kafka" % "kafka-clients" % "0.10.0.1",
      "org.apache.thrift" % "libthrift" % "0.9.2",
      "org.apache.tomcat" % "tomcat-servlet-api" % "8.0.33",
      "org.caffinitas.ohc" % "ohc-core" % "0.4.3",
      "org.codehaus.jackson" % "jackson-core-asl" % "1.9.2",
      "org.codehaus.jackson" % "jackson-mapper-asl" % "1.9.2",
      "org.eclipse.jdt.core.compiler" % "ecj" % "4.4.2",
      "org.fusesource" % "sigar" % "1.6.4",
      "org.hibernate" % "hibernate-validator" % "5.2.4.Final",
      "org.hibernate.javax.persistence" % "hibernate-jpa-2.1-api" % "1.0.0.Final",
      "org.immutables" % "value" % "2.3.2",
      "org.javassist" % "javassist" % "3.21.0-GA",
      jbossLogging,
      "org.joda" % "joda-convert" % "1.8.1",
      "org.hamcrest" % "hamcrest-core" % "1.3",
      "org.mindrot" % "jbcrypt" % "0.3m",
      "org.pcollections" % "pcollections" % "2.1.2",
      "org.reactivestreams" % "reactive-streams" % "1.0.0",
      "org.reflections" % "reflections" % "0.9.10",
      "org.scalactic" %% "scalactic" % ScalaTestVersion,
      "org.scalatest" %% "scalatest" % ScalaTestVersion,
      "org.scala-lang.modules" %% "scala-java8-compat" % ScalaJava8CompatVersion,
      "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.4",
      scalaXml,
      "org.scala-sbt" % "test-interface" % "1.0",
      "org.scala-stm" %% "scala-stm" % "0.7",
      "org.springframework" % "spring-beans" % "4.2.7.RELEASE",
      "org.springframework" % "spring-context" % "4.2.7.RELEASE",
      "org.springframework" % "spring-core" % "4.2.7.RELEASE",
      "org.uncommons.maths" % "uncommons-maths" % "1.2.2a",
      "org.xerial.snappy" % "snappy-java" % "1.1.2.6",
      "org.yaml" % "snakeyaml" % "1.16",
      "tyrex" % "tyrex" % "1.0.1",
      "xerces" % "xercesImpl" % "2.11.0",
      "xml-apis" % "xml-apis" % "1.4.01"

    ) ++ libraryFamily("com.fasterxml.jackson.core", JacksonVersion)(
      "jackson-annotations", "jackson-core", "jackson-databind"

    ) ++ libraryFamily("com.fasterxml.jackson.datatype", JacksonVersion)(
      "jackson-datatype-jdk8", "jackson-datatype-jsr310", "jackson-datatype-guava", "jackson-datatype-pcollections"

    ) ++ crossLibraryFamily("com.typesafe.akka", AkkaVersion)(
      "akka-actor", "akka-cluster", "akka-cluster-sharding", "akka-cluster-tools", "akka-multi-node-testkit",
      "akka-persistence", "akka-persistence-query-experimental", "akka-protobuf", "akka-remote", "akka-slf4j",
      "akka-stream", "akka-stream-testkit", "akka-testkit"

    ) ++ libraryFamily("com.typesafe.play", PlayVersion)(
      "build-link", "play-exceptions", "play-netty-utils"

    ) ++ crossLibraryFamily("com.typesafe.play", PlayVersion)(
      "play", "play-datacommons", "play-functional", "play-iteratees", "play-java", "play-jdbc", "play-jdbc-api",
      "play-json", "play-netty-server", "play-server", "play-streams", "play-ws"

    ) ++ libraryFamily("ch.qos.logback", "1.1.3")(
      "logback-classic", "logback-core"

    ) ++ libraryFamily("io.netty", NettyVersion)(
      "netty-buffer", "netty-codec", "netty-codec-http", "netty-common", "netty-handler", "netty-transport",
      "netty-transport-native-epoll"

    ) ++ libraryFamily("org.apache.logging.log4j", "2.7")(
      "log4j-api", "log4j-core", "log4j-slf4j-impl"

    ) ++ libraryFamily("org.asynchttpclient", "2.0.24")(
      "async-http-client", "async-http-client-netty-utils", "netty-codec-dns", "netty-resolver", "netty-resolver-dns"

    ) ++ libraryFamily("org.ow2.asm", "5.0.3")(
      "asm", "asm-analysis", "asm-commons", "asm-tree", "asm-util"

    ) ++ libraryFamily("org.scala-lang", scalaVersion)(
      "scala-library", "scala-reflect"

    ) ++ libraryFamily("org.slf4j", "1.7.21")(
      "jcl-over-slf4j", "jul-to-slf4j", "log4j-over-slf4j", "slf4j-api"
    )
  }

  // These dependencies are used by JPA to test, but we don't want to export them as part of our regular whitelist,
  // so we maintain it separately.
  val JpaTestWhitelist = Seq(
    "antlr" % "antlr" % "2.7.7",
    "dom4j" % "dom4j" % "1.6.1",
    "javax.annotation" % "jsr250-api" % "1.0",
    "javax.el" % "el-api" % "2.2",
    "javax.enterprise" % "cdi-api" % "1.1",
    "org.apache.geronimo.specs" % "geronimo-jta_1.1_spec" % "1.1.1",
    "org.hibernate" % "hibernate-core" % "5.2.5.Final",
    "org.hibernate.common" % "hibernate-commons-annotations" % "5.0.1.Final",
    "org.jboss" % "jandex" % "2.0.3.Final",
    "org.jboss.spec.javax.interceptor" % "jboss-interceptors-api_1.1_spec" % "1.0.0.Beta1"
  )

  // These dependencies are used by the Kafka tests, but we don't want to export them as part of our regular
  // whitelist, so we maintain it separately.
  val KafkaTestWhitelist = Seq(
    "com.101tec" % "zkclient" % "0.8",
    "com.yammer.metrics" % "metrics-core" % "2.2.0",
    "jline" % "jline" % "0.9.94",
    "log4j" % "log4j" % "1.2.17",
    "net.sf.jopt-simple" % "jopt-simple" % "4.9",
    "org.apache.commons" % "commons-math" % "2.2",
    "org.apache.curator" % "curator-client" % "2.10.0",
    "org.apache.curator" % "curator-framework" % "2.10.0",
    "org.apache.curator" % "curator-test" % "2.10.0",
    "org.apache.kafka" %% "kafka" % "0.10.0.1",
    "org.apache.zookeeper" % "zookeeper" % "3.4.6"
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
    guava
  )

  val `api-javadsl` = libraryDependencies ++= Seq(
    playJava,
    "org.pcollections" % "pcollections" % "2.1.2",
    // Needed to upgrade from 3.18 to ensure everything is on 3.20
    "org.javassist" % "javassist" % "3.21.0-GA",
    "com.fasterxml" % "classmate" % "1.3.0",
    jbossLogging,
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
    scalaTest % Test
  )

  val `api-tools` = libraryDependencies ++= Seq(
    play,
    scalaTest % Test,

    // Upgrades for Play dependencies
    akkaActor,
    akkaSlf4j,
    akkaStream,
    scalaXml,
    guava,
    jbossLogging
  )

  val client = libraryDependencies ++= Seq(
    playWs,
    "io.dropwizard.metrics" % "metrics-core" % "3.1.2",

    // Needed to match whitelist versions
    "io.netty" % "netty-codec-http" % NettyVersion,
    "io.netty" % "netty-transport-native-epoll" % NettyVersion,
    "com.typesafe.netty" % "netty-reactive-streams" % "1.0.8"
  )

  val `client-javadsl` = libraryDependencies ++= Seq(
    scalaTest % Test
  )

  val `client-scaladsl` = libraryDependencies ++= Seq(
    scalaTest % Test
  )

  val `integration-client-javadsl` = libraryDependencies ++= Nil

  val server = libraryDependencies ++= Nil

  val `server-javadsl` = libraryDependencies ++= Nil

  val `server-scaladsl` = libraryDependencies ++= Seq(
    scalaTest % Test
  )

  val `testkit-core` = libraryDependencies ++= Seq(
    akkaActor,
    akkaStream,
    typesafeConfig
  )

  val `testkit-javadsl` = libraryDependencies ++= Seq(
    playNettyServer,
    "org.apache.cassandra" % "cassandra-all" % CassandraAllVersion exclude("io.netty", "netty-all"),
    akkaStreamTestkit,
    akkaPersistenceCassandra,
    scalaTest % Test,
    scalaJava8Compat,
    "junit" % "junit" % "4.11",

    // These deps are depended on by cassandra-all, and need to be upgraded in order to be consistent with transitive
    // dependencies from our other libraries
    "com.lmax" % "disruptor" % "3.3.6",
    "javax.validation" % "validation-api" % "1.1.0.Final",
    "org.hibernate" % "hibernate-validator" % "5.2.4.Final",
    "org.slf4j" % "log4j-over-slf4j" % "1.7.21",
    "org.xerial.snappy" % "snappy-java" % "1.1.2.6",
    "org.yaml" % "snakeyaml" % "1.16"
  )

  val `testkit-scaladsl` = libraryDependencies ++= Seq(
    playNettyServer,
    "org.apache.cassandra" % "cassandra-all" % CassandraAllVersion exclude("io.netty", "netty-all"),
    akkaStreamTestkit,
    akkaPersistenceCassandra,
    scalaTest % Test,
    "junit" % "junit" % "4.11",

    // These deps are depended on by cassandra-all, and need to be upgraded in order to be consistent with transitive
    // dependencies from our other libraries
    "com.lmax" % "disruptor" % "3.3.6",
    "javax.validation" % "validation-api" % "1.1.0.Final",
    "org.hibernate" % "hibernate-validator" % "5.2.4.Final",
    jbossLogging,
    "org.slf4j" % "log4j-over-slf4j" % "1.7.21",
    "org.xerial.snappy" % "snappy-java" % "1.1.2.6",
    "org.yaml" % "snakeyaml" % "1.16",
    "com.fasterxml" % "classmate" % "1.3.0"
  )

  val `integration-tests-javadsl` = libraryDependencies ++= Seq(
    playNettyServer,
    "com.novocode" % "junit-interface" % "0.11" % Test,
    scalaTest
  )

  val `integration-tests-scaladsl` = libraryDependencies ++= Seq(
    playNettyServer,
    "com.novocode" % "junit-interface" % "0.11" % Test,
    scalaTest
  )

  val `cluster-core` = libraryDependencies ++= Seq(
    akkaCluster,
    typesafeConfig,
    akkaTestkit % Test,
    scalaTest % Test,
    "com.novocode" % "junit-interface" % "0.11" % Test
  )

  val `cluster-javadsl` = libraryDependencies ++= Seq(
    akkaTestkit % Test,
    akkaMultiNodeTestkit % Test,
    scalaJava8Compat,
    scalaTest % Test,
    "com.novocode" % "junit-interface" % "0.11" % Test,
    "com.google.inject" % "guice" % "4.0"
  )

  val `cluster-scaladsl` = libraryDependencies ++= Seq(
    akkaTestkit % Test,
    akkaMultiNodeTestkit % Test,
    scalaTest % Test,
    "com.novocode" % "junit-interface" % "0.11" % Test
  )

  val `pubsub-javadsl` = libraryDependencies ++= Seq(
    "com.google.inject" % "guice" % "4.0",
    akkaClusterTools,
    scalaJava8Compat,
    akkaTestkit % Test,
    akkaMultiNodeTestkit % Test,
    akkaStreamTestkit % Test,
    scalaTest % Test,
    "com.novocode" % "junit-interface" % "0.11" % Test
  )

  val `pubsub-scaladsl` = libraryDependencies ++= Seq(
    akkaClusterTools,
    akkaTestkit % Test,
    akkaMultiNodeTestkit % Test,
    akkaStreamTestkit % Test,
    scalaTest % Test
  )

  val `persistence-core` = libraryDependencies ++= Seq(
    scalaJava8Compat,
    scalaXml,
    guava,
    akkaPersistence,
    akkaPersistenceQuery,
    akkaClusterSharding,
    akkaSlf4j,
    play,
    akkaTestkit % Test,
    akkaMultiNodeTestkit % Test,
    akkaStreamTestkit % Test,
    scalaTest % Test,
    "com.novocode" % "junit-interface" % "0.11" % Test,

    // These dependencies get upgraded in Test so that we can ensure that our tests are using the same dependencies
    // as the users will have in their tests
    "com.lmax" % "disruptor" % "3.3.6" % Test,
    "javax.validation" % "validation-api" % "1.1.0.Final" % Test,
    "org.hibernate" % "hibernate-validator" % "5.2.4.Final" % Test,
    jbossLogging % Test,
    "org.slf4j" % "log4j-over-slf4j" % "1.7.21" % Test,
    "org.xerial.snappy" % "snappy-java" % "1.1.2.6" % Test,
    "org.yaml" % "snakeyaml" % "1.16" % Test,
    "com.fasterxml" % "classmate" % "1.3.0" % Test
  )

  val `persistence-javadsl` = libraryDependencies ++= Seq(
    // this mean we have production code depending on testkit
    akkaTestkit
  )

  val `persistence-scaladsl` = libraryDependencies ++= Seq(
    // this mean we have production code depending on testkit
    akkaTestkit
  )
  val `persistence-cassandra-core` = libraryDependencies ++= Seq(
    akkaPersistenceCassandra,
    cassandraDriverCore,
    // cassandra-driver-core pulls in an older version of all these
    "io.netty" % "netty-buffer" % NettyVersion,
    "io.netty" % "netty-codec" % NettyVersion,
    "io.netty" % "netty-common" % NettyVersion,
    "io.netty" % "netty-handler" % NettyVersion,
    "io.netty" % "netty-transport" % NettyVersion,

    "org.apache.cassandra" % "cassandra-all" % CassandraAllVersion % Test exclude("io.netty", "netty-all"),
    "io.netty" % "netty-codec-http" % NettyVersion % Test,
    "io.netty" % "netty-transport-native-epoll" % NettyVersion % Test classifier "linux-x86_64",
    "org.apache.httpcomponents" % "httpclient" % "4.5.2" % Test
  )

  val `persistence-cassandra-javadsl` = libraryDependencies ++= Seq(
    cassandraDriverCore
  )

  val `persistence-cassandra-scaladsl` = libraryDependencies ++= Seq(
    cassandraDriverCore
  )

  val `persistence-jdbc-core` = libraryDependencies ++= Seq(
    "com.github.dnvriend" %% "akka-persistence-jdbc" % "2.4.17.1",
    playJdbc
  )

  val `persistence-jdbc-javadsl` = libraryDependencies ++= Nil

  val `persistence-jdbc-scaladsl` = libraryDependencies ++= Nil

  val `persistence-jpa-javadsl` = libraryDependencies ++= Seq(
    "org.hibernate.javax.persistence" % "hibernate-jpa-2.1-api" % "1.0.0.Final" % Provided,
    "org.hibernate" % "hibernate-core" % "5.2.5.Final" % Test
  )

  val `broker-javadsl` = libraryDependencies ++= Nil

  val `broker-scaladsl` = libraryDependencies ++= Nil

  val `kafka-client` = libraryDependencies ++= Seq(
    "org.slf4j" % "log4j-over-slf4j" % "1.7.21",
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

  val `kafka-broker-javadsl` = libraryDependencies ++= Seq(
    scalaTest % Test,
    "junit" % "junit" % "4.11" % Test
  )

  val `kafka-broker-scaladsl` = libraryDependencies ++= Seq(
    scalaTest % Test,
    "junit" % "junit" % "4.11" % Test
  )

  val logback = libraryDependencies ++= Seq(
    // needed only because we use play.utils.Colors
    play,

    // Upgrades for Play libraries
    akkaActor,
    akkaSlf4j,
    akkaStream,
    scalaXml,
    guava
  ) ++ Seq("logback-core", "logback-classic").map("ch.qos.logback" % _ % "1.1.3")

  val log4j2 = libraryDependencies ++= Seq(
    "log4j-api",
    "log4j-core",
    "log4j-slf4j-impl"
  ).map("org.apache.logging.log4j" % _ % "2.7") ++ Seq(
    "com.lmax" % "disruptor" % "3.3.6",
    play,

    // Upgrades for Play dependencies
    akkaActor,
    akkaSlf4j,
    akkaStream,
    scalaXml,
    guava
  )

  val `build-link` = libraryDependencies ++= Seq(
    playExceptions,
    playBuildLink
  )

  val `reloadable-server` = libraryDependencies ++= Seq(
    playServer,

    // Upgrades for Play dependencies
    akkaActor,
    akkaSlf4j,
    akkaStream,
    guava,
    scalaXml
  )

  val `build-tool-support` = libraryDependencies ++= Seq(
    "com.lightbend.play" %% "play-file-watch" % "1.0.0",
    // This is used in the code to check if the embedded cassandra server is started
    "com.datastax.cassandra" % "cassandra-driver-core" % "3.0.0",
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
    // Need to upgrade Netty due to encountering this deadlock in the service gateway
    // https://github.com/netty/netty/pull/5110
    "io.netty" % "netty-codec-http" % NettyVersion,
    scalaTest % Test
  )

  val `service-registry-client-javadsl` = libraryDependencies ++= Nil

  val `service-registration-javadsl` = libraryDependencies ++= Nil

  val `devmode-scaladsl` = libraryDependencies ++= Nil

  val `play-integration-javadsl` = libraryDependencies ++= Nil

  val `cassandra-server` = libraryDependencies ++= Seq(
    // Cassandra goes into 100% CPU spin when starting with netty jars of different versions. Hence,
    // we are making sure that the only netty dependency comes from cassandra-all, and manually excludes
    // all netty transitive dependencies of akka-persistence-cassandra. Mind that dependencies are
    // excluded one-by-one because exclusion rules do not work with maven dependency resolution - see
    // https://github.com/lagom/lagom/issues/26#issuecomment-196718818
    akkaPersistenceCassandra
      exclude("io.netty", "netty-all") exclude("io.netty", "netty-handler") exclude("io.netty", "netty-buffer")
      exclude("io.netty", "netty-common") exclude("io.netty", "netty-transport") exclude("io.netty", "netty-codec"),
    // Explicitly override the jboss-logging transitive dependency from cassandra-all.
    // By default, it uses jboss-logging 3.1.0.CR2, which is under LGPL.
    // This forces it to a newer version that is licensed under Apache v2.
    jbossLogging,
    "org.apache.cassandra" % "cassandra-all" % CassandraAllVersion
  )

  val `kafka-server` = libraryDependencies ++= Seq(
    "org.apache.kafka" %% "kafka" % KafkaVersion exclude("org.slf4j", "slf4j-log4j12"),
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

  val validateDependencies = taskKey[Unit]("Validate Lagom dependencies to ensure they are whitelisted")
  val dependencyWhitelist = settingKey[Seq[ModuleID]]("The whitelist of dependencies")

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
            Some(moduleId -> s"$scope dependency not in whitelist: $moduleId")
          case Some(unmatched) if moduleId.revision != unmatched =>
            Some(moduleId -> s"$scope dependency ${moduleId.organization}:${moduleId.name} version ${moduleId.revision} doesn't match whitelist version $unmatched")
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

  val validateDependenciesSetting = validateDependencies := validateDependenciesTask.value
  val dependencyWhitelistSetting = dependencyWhitelist := DependencyWhitelist.value

  private class DependencyWhitelistValidationFailed extends RuntimeException with FeedbackProvidedException {
    override def toString = "Dependency whitelist validation failed!"
  }
}
