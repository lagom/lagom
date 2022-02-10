# Using newer versions of Akka and Play Framework

Lagom relies on Akka and Play Framework for some of its implementation.

The versions of Akka and Play that ship with Lagom should usually be sufficient to run a Lagom application. However, in some cases it can be useful to upgrade them while staying on the same version of Lagom. This is usually possible when Akka and Play maintains binary compatibility between releases.

Bear in mind that neither Akka nor Akka HTTP allow [mixed versions](https://doc.akka.io/docs/akka/current/common/binary-compatibility-rules.html#mixed-versioning-is-not-allowed). As a consequence, you must make sure you override the version for all the artifacts on your dependency tree in sync.

## sbt

When you are using sbt, you can force new versions of Akka and Play by adding the following to your `build.sbt`:

```scala
val akkaVersion = "2.6.<newer-version>"
val akkaHttpVersion = "10.2.<newer-version>"
val playVersion = "2.8.<newer-version>"

libraryDependencies in ThisBuild ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-discovery" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-sharding" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-tools" % akkaVersion,
  "com.typesafe.akka" %% "akka-coordination" % akkaVersion,
  "com.typesafe.akka" %% "akka-distributed-data" % akkaVersion,
  "com.typesafe.akka" %% "akka-persistence" % akkaVersion,
  "com.typesafe.akka" %% "akka-persistence-query" % akkaVersion,
  "com.typesafe.akka" %% "akka-protobuf" % akkaVersion,
  "com.typesafe.akka" %% "akka-serialization-jackson" % akkaVersion,
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream-typed" % akkaVersion,

  "com.typesafe.akka" %% "akka-http-core" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http2-support" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,

  "com.typesafe.play" %% "play" % playVersion,
)
```

And also update the version of the play plugin in `project/plugins.sbt`:

```
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % playVersion)
```

## Maven

When you are using Maven, you can force new versions of Akka and Play by adding the explicit dependencies to the `<dependencyManagement>` section in the parent `pom.xml`. Keep in mind that in `dependencyManagement` first occurrence wins, so you list of overwrites must appear before any BOM (e.g. the lagom BOM) to take effect.

Below is an example of how you would overwrite the version for some Akka, Akka HTTP and Play artifacts.

```xml
<project>
    <properties>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <scala.binary.version>2.12</scala.binary.version>
        <akka.version>2.6.newer-version</akka.version>
        <akka.http.version>10.2.newer-version</akka.http.version>
        <play.version>2.8.newer-version</play.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>com.typesafe.akka</groupId>
                <artifactId>akka-discovery_${scala.binary.version}</artifactId>
                <version>${akka.version}</version>
            </dependency>
            <dependency>
                <groupId>com.typesafe.akka</groupId>
                <artifactId>akka-cluster-sharding_${scala.binary.version}</artifactId>
                <version>${akka.version}</version>
            </dependency>
            <dependency>
                <groupId>com.typesafe.akka</groupId>
                <artifactId>akka-cluster-tools_${scala.binary.version}</artifactId>
                <version>${akka.version}</version>
            </dependency>
            <dependency>
                <groupId>com.typesafe.akka</groupId>
                <artifactId>akka-coordination_${scala.binary.version}</artifactId>
                <version>${akka.version}</version>
            </dependency>
            <dependency>
                <groupId>com.typesafe.akka</groupId>
                <artifactId>akka-distributed-data_${scala.binary.version}</artifactId>
                <version>${akka.version}</version>
            </dependency>
            <dependency>
                <groupId>com.typesafe.akka</groupId>
                <artifactId>akka-persistence_${scala.binary.version}</artifactId>
                <version>${akka.version}</version>
            </dependency>
            <dependency>
                <groupId>com.typesafe.akka</groupId>
                <artifactId>akka-persistence-query_${scala.binary.version}</artifactId>
                <version>${akka.version}</version>
            </dependency>
            <dependency>
                <groupId>com.typesafe.akka</groupId>
                <artifactId>akka-protobuf_${scala.binary.version}</artifactId>
                <version>${akka.version}</version>
            </dependency>
            <dependency>
                <groupId>com.typesafe.akka</groupId>
                <artifactId>akka-slf4j_${scala.binary.version}</artifactId>
                <version>${akka.version}</version>
            </dependency>
            <dependency>
                <groupId>com.typesafe.akka</groupId>
                <artifactId>akka-stream_${scala.binary.version}</artifactId>
                <version>${akka.version}</version>
            </dependency>
            <dependency>
                <groupId>com.typesafe.akka</groupId>
                <artifactId>akka-http_${scala.binary.version}</artifactId>
                <version>${akka.http.version}</version>
            </dependency>
            <dependency>
                <groupId>com.typesafe.akka</groupId>
                <artifactId>akka-http-core_${scala.binary.version}</artifactId>
                <version>${akka.http.version}</version>
            </dependency>
            <dependency>
                <groupId>com.typesafe.akka</groupId>
                <artifactId>akka-http-spray-json_${scala.binary.version}</artifactId>
                <version>${akka.http.version}</version>
            </dependency>
            <dependency>
                <groupId>com.typesafe.play</groupId>
                <artifactId>play_${scala.binary.version}</artifactId>
                <version>${play.version}</version>
            </dependency>
          <dependency>
            <groupId>com.lightbend.lagom</groupId>
            <artifactId>lagom-maven-dependencies</artifactId>
            <version>${lagom.version}</version>
            <scope>import</scope>
            <type>pom</type>
          </dependency>
          <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <version>1.18.8</version>
          </dependency>
        </dependencies>
    </dependencyManagement>

</project>
```

### Maven BOMs

In latest versions of Akka you may also use the BOM provided instead of manually listing all the artifacts.  Keep in mind that in `dependencyManagement` first occurrence wins, so any BOM overwriting the Lagom BOM must appear before to take effect.

```xml
<project>
    <properties>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <scala.binary.version>2.12</scala.binary.version>
        <akka.version>2.6.newer-version</akka.version>
        <akka.http.version>10.2.newer-version</akka.http.version>
        <play.version>2.8.newer-version</play.version>
    </properties>
    <dependencyManagement>
      <dependencies>
        <dependency>
          <groupId>com.typesafe.akka</groupId>
          <artifactId>akka-bom_${scala.binary.version}</artifactId>
          <version>${akka.version}</version>
          <type>pom</type>
          <scope>import</scope>
        </dependency>

        <dependency>
          <groupId>com.typesafe.akka</groupId>
          <artifactId>akka-http_${scala.binary.version}</artifactId>
          <version>${akka.http.version}</version>
        </dependency>
        <dependency>
          <groupId>com.typesafe.akka</groupId>
          <artifactId>akka-http-core_${scala.binary.version}</artifactId>
          <version>${akka.http.version}</version>
        </dependency>
        <dependency>
          <groupId>com.typesafe.akka</groupId>
          <artifactId>akka-http-spray-json_${scala.binary.version}</artifactId>
          <version>${akka.http.version}</version>
        </dependency>
        <dependency>
          <groupId>com.typesafe.play</groupId>
          <artifactId>play_${scala.binary.version}</artifactId>
          <version>${play.version}</version>
        </dependency>
      </dependencies>

      <dependency>
        <groupId>com.lightbend.lagom</groupId>
        <artifactId>lagom-maven-dependencies</artifactId>
        <version>${lagom.version}</version>
        <scope>import</scope>
        <type>pom</type>
      </dependency>
      <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <version>1.18.8</version>
      </dependency>

    </dependencyManagement>
</project>
```
