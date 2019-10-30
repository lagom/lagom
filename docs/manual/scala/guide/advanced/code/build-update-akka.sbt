// #akka-update
// The newer Akka version you want to use.
val akkaVersion = "2.6.0-RC2"

// Akka dependencies used by Lagom
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor"                  % akkaVersion,
  "com.typesafe.akka" %% "akka-actor-testkit-typed"    % akkaVersion,
  "com.typesafe.akka" %% "akka-actor-typed"            % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster"                % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-sharding"       % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-sharding-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-tools"          % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-typed"          % akkaVersion,
  "com.typesafe.akka" %% "akka-coordination"           % akkaVersion,
  "com.typesafe.akka" %% "akka-discovery"              % akkaVersion,
  "com.typesafe.akka" %% "akka-distributed-data"       % akkaVersion,
  "com.typesafe.akka" %% "akka-multi-node-testkit"     % akkaVersion,
  "com.typesafe.akka" %% "akka-persistence"            % akkaVersion,
  "com.typesafe.akka" %% "akka-persistence-query"      % akkaVersion,
  "com.typesafe.akka" %% "akka-persistence-typed"      % akkaVersion,
  "com.typesafe.akka" %% "akka-protobuf"               % akkaVersion,
  "com.typesafe.akka" %% "akka-protobuf-v3"            % akkaVersion,
  "com.typesafe.akka" %% "akka-remote"                 % akkaVersion,
  "com.typesafe.akka" %% "akka-slf4j"                  % akkaVersion,
  "com.typesafe.akka" %% "akka-stream"                 % akkaVersion,
  "com.typesafe.akka" %% "akka-stream-testkit"         % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit"                % akkaVersion,
)
// #akka-update

// #akka-other-artifacts
import com.lightbend.lagom.core.LagomVersion

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-stream-typed" % LagomVersion.akka
)
// #akka-other-artifacts
