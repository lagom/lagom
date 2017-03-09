import sbt._
import sbt.Keys._

object Dependencies {

  // Version numbers
  val PlayVersion = "2.5.10"
  val AkkaVersion = "2.4.17"
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

  // Specific libraries that get reused
  val scalaTest = "org.scalatest" %% "scalatest" % ScalaTestVersion
  val guava = "com.google.guava" % "guava" % GuavaVersion
  val log4J = "log4j" % "log4j" % Log4j
  val scalaJava8Compat = "org.scala-lang.modules" %% "scala-java8-compat" % ScalaJava8CompatVersion


  // Dependencies for each module
  val api = libraryDependencies ++= Seq(
    "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.4",
    "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
    "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion,
    "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
    "com.typesafe.play" %% "play" % PlayVersion
  )

  val `api-javadsl` = libraryDependencies ++= Seq(
    "com.typesafe.play" %% "play-java" % PlayVersion,
    // todo Remove as part of #530
    guava,
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
    // todo Many of these won't be needed when #530 is done
    "com.fasterxml.jackson.core" % "jackson-core" % JacksonVersion,
    "com.fasterxml.jackson.core" % "jackson-annotations" % JacksonVersion,
    "com.fasterxml.jackson.core" % "jackson-databind" % JacksonVersion,
    "com.fasterxml.jackson.datatype" % "jackson-datatype-pcollections" % JacksonVersion,
    "com.fasterxml.jackson.datatype" % "jackson-datatype-guava" % JacksonVersion,
    "com.fasterxml.jackson.datatype" % "jackson-datatype-jdk8" % JacksonVersion,
    "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310" % JacksonVersion,
    "com.typesafe.akka" %% "akka-testkit" % AkkaVersion % "test",
    scalaTest % Test,
    "com.novocode" % "junit-interface" % "0.11" % "test"
  )

  val `play-json` = libraryDependencies ++= Seq(
    "com.typesafe.play" %% "play-json" % PlayVersion,
    "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
    "com.typesafe.akka" %% "akka-testkit" % AkkaVersion,
    scalaTest % Test
  )

  val `api-tools` = libraryDependencies ++= Seq(
    "com.typesafe.play" %% "play" % PlayVersion,
    scalaTest % Test
  )

  val client = libraryDependencies ++= Seq(
    "com.typesafe.play" %% "play-ws" % PlayVersion,
    "io.dropwizard.metrics" % "metrics-core" % "3.1.2"
  )

  val `client-javadsl` = libraryDependencies ++= Nil

  val `client-scaladsl` = libraryDependencies ++= Seq(
    scalaTest % Test
  )

  val `integration-client-javadsl` = libraryDependencies ++= Nil

  val server = libraryDependencies ++= Seq(
    // todo probably not needed when #530 is done
    "com.typesafe.akka" %% "akka-actor" % AkkaVersion
  )

  val `server-javadsl` = libraryDependencies ++= Seq(
    // todo probably not needed when #530 is done
    "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
    guava
  )

  val `server-scaladsl` = libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
    scalaTest % Test
  )

  val `testkit-core` = libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
    "com.typesafe.akka" %% "akka-stream" % AkkaVersion
  )

  val `testkit-javadsl` = libraryDependencies ++= Seq(
    "com.typesafe.play" %% "play-netty-server" % PlayVersion,
    "org.apache.cassandra" % "cassandra-all" % CassandraAllVersion exclude("io.netty", "netty-all"),
    "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
    "com.typesafe.akka" %% "akka-stream-testkit" % AkkaVersion,
    "com.typesafe.akka" %% "akka-persistence-cassandra" % AkkaPersistenceCassandraVersion,
    scalaTest % Test,
    scalaJava8Compat
  )

  val `testkit-scaladsl` = libraryDependencies ++= Seq(
    "com.typesafe.play" %% "play-netty-server" % PlayVersion,
    "org.apache.cassandra" % "cassandra-all" % CassandraAllVersion exclude("io.netty", "netty-all"),
    "com.typesafe.akka" %% "akka-stream-testkit" % AkkaVersion,
    "com.typesafe.akka" %% "akka-persistence-cassandra" % AkkaPersistenceCassandraVersion,
    scalaTest % Test
  )

  val `integration-tests-javadsl` = libraryDependencies ++= Seq(
    "com.typesafe.play" %% "play-netty-server" % PlayVersion,
    "com.novocode" % "junit-interface" % "0.11" % "test",
    scalaTest
  )

  val `integration-tests-scaladsl` = libraryDependencies ++= Seq(
    "com.typesafe.play" %% "play-netty-server" % PlayVersion,
    "com.novocode" % "junit-interface" % "0.11" % "test",
    scalaTest
  )

  val `cluster-core` = libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-cluster" % AkkaVersion,
    "com.typesafe.akka" %% "akka-testkit" % AkkaVersion % "test",
    scalaTest % Test,
    "com.novocode" % "junit-interface" % "0.11" % "test"
  )

  val `cluster-javadsl` = libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-testkit" % AkkaVersion % "test",
    "com.typesafe.akka" %% "akka-multi-node-testkit" % AkkaVersion % "test",
    scalaJava8Compat,
    scalaTest % Test,
    "com.novocode" % "junit-interface" % "0.11" % "test",
    "com.google.inject" % "guice" % "4.0"
  )

  val `cluster-scaladsl` = libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-testkit" % AkkaVersion % "test",
    "com.typesafe.akka" %% "akka-multi-node-testkit" % AkkaVersion % "test",
    scalaTest % Test,
    "com.novocode" % "junit-interface" % "0.11" % "test"
  )

  val `pubsub-javadsl` = libraryDependencies ++= Seq(
    "com.google.inject" % "guice" % "4.0",
    "com.typesafe.akka" %% "akka-cluster-tools" % AkkaVersion,
    scalaJava8Compat,
    "com.typesafe.akka" %% "akka-testkit" % AkkaVersion % "test",
    "com.typesafe.akka" %% "akka-multi-node-testkit" % AkkaVersion % "test",
    "com.typesafe.akka" %% "akka-stream-testkit" % AkkaVersion % "test",
    scalaTest % Test,
    "com.novocode" % "junit-interface" % "0.11" % "test"
  )

  val `pubsub-scaladsl` = libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-cluster-tools" % AkkaVersion,
    "com.typesafe.akka" %% "akka-testkit" % AkkaVersion % "test",
    "com.typesafe.akka" %% "akka-multi-node-testkit" % AkkaVersion % "test",
    "com.typesafe.akka" %% "akka-stream-testkit" % AkkaVersion % "test",
    scalaTest % Test
  )

  val `persistence-core` = libraryDependencies ++= Seq(
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

  val `persistence-javadsl` = libraryDependencies ++= Nil

  val `persistence-scaladsl` = libraryDependencies ++= Nil

  val `persistence-cassandra-core` = libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-persistence-cassandra" % AkkaPersistenceCassandraVersion,
    "org.apache.cassandra" % "cassandra-all" % CassandraAllVersion % "test" exclude("io.netty", "netty-all"),
    "io.netty" % "netty-codec-http" % NettyVersion % "test",
    "io.netty" % "netty-transport-native-epoll" % NettyVersion % "test" classifier "linux-x86_64"
  )

  val `persistence-cassandra-javadsl` = libraryDependencies ++= Nil

  val `persistence-cassandra-scaladsl` = libraryDependencies ++= Nil

  val `persistence-jdbc-core` = libraryDependencies ++= Seq(
    "com.github.dnvriend" %% "akka-persistence-jdbc" % "2.6.8",
    "com.typesafe.play" %% "play-jdbc" % PlayVersion
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
    "com.typesafe.akka" %% "akka-stream-kafka" % AkkaStreamKafka exclude("org.slf4j","slf4j-log4j12"),
    "org.apache.kafka" %% "kafka" % KafkaVersion exclude("org.slf4j","slf4j-log4j12") exclude("javax.jms", "jms") exclude("com.sun.jdmk", "jmxtools") exclude("com.sun.jmx", "jmxri"),
    scalaTest % Test
  )

  val `kafka-client-javadsl` = libraryDependencies ++= Nil

  val `kafka-client-scaladsl` = libraryDependencies ++= Nil

  val `kafka-broker` = libraryDependencies ++= Nil

  val `kafka-broker-javadsl` = libraryDependencies ++= Seq(
    scalaTest % Test
  )

  val `kafka-broker-scaladsl` = libraryDependencies ++= Seq(
    scalaTest % Test
  )

  val logback = libraryDependencies ++= Seq(
    // needed only because we use play.utils.Colors
    "com.typesafe.play" %% "play" % PlayVersion
  ) ++ Seq("logback-core", "logback-classic").map("ch.qos.logback" % _ % "1.1.3")

  val log4j2 = libraryDependencies ++= Seq(
    "log4j-api",
    "log4j-core",
    "log4j-slf4j-impl"
  ).map("org.apache.logging.log4j" % _ % "2.7") ++ Seq(
    "com.lmax" % "disruptor" % "3.3.6",
    "com.typesafe.play" %% "play" % PlayVersion
  )

  val `build-link` = libraryDependencies ++= Seq(
    "com.typesafe.play" % "play-exceptions" % PlayVersion,
    "com.typesafe.play" % "build-link" % PlayVersion
  )

  val `reloadable-server` = libraryDependencies ++= Seq(
    "com.typesafe.play" %% "play" % PlayVersion,
    "com.typesafe.play" %% "play-server" % PlayVersion
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
    // Explicit akka dependency because maven chooses the wrong version
    "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
    "com.typesafe.play" %% "play-netty-server" % PlayVersion,
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
    "com.typesafe.akka" %% "akka-persistence-cassandra" % AkkaPersistenceCassandraVersion
      exclude("io.netty", "netty-all") exclude("io.netty", "netty-handler") exclude("io.netty", "netty-buffer")
      exclude("io.netty", "netty-common") exclude("io.netty", "netty-transport") exclude("io.netty", "netty-codec"),
    "org.apache.cassandra" % "cassandra-all" % CassandraAllVersion
  )

  val `kafka-server` = libraryDependencies ++= Seq(
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
}