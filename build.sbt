organization in ThisBuild := "com.example"
version in ThisBuild := "1.0-SNAPSHOT"

// the Scala version that will be used for cross-compiled libraries
scalaVersion in ThisBuild := "2.13.0"

val akkaVersion = "2.6.9"
val akkaProjectionVersion = "1.0.0"

val macwire = "com.softwaremill.macwire" %% "macros" % "2.3.3" % "provided"
val scalaTest = "org.scalatest" %% "scalatest" % "3.1.1" % Test
val playJsonDerivedCodecs = "org.julienrf" %% "play-json-derived-codecs" % "7.0.0"
val akkaProjection = "com.lightbend.akka" %% "akka-projection-core" % akkaProjectionVersion
val akkaProjectionEventSourced = "com.lightbend.akka" %% "akka-projection-eventsourced" % akkaProjectionVersion
val akkaProjectionCassandra = "com.lightbend.akka" %% "akka-projection-cassandra" % akkaProjectionVersion

val akkaOverride = Seq(
  "com.typesafe.akka" %% "akka-actor"                  % akkaVersion,
  "com.typesafe.akka" %% "akka-remote"                 % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster"                % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-sharding"       % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-sharding-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-tools"          % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-typed"          % akkaVersion,
  "com.typesafe.akka" %% "akka-coordination"           % akkaVersion,
  "com.typesafe.akka" %% "akka-discovery"              % akkaVersion,
  "com.typesafe.akka" %% "akka-distributed-data"       % akkaVersion,
  "com.typesafe.akka" %% "akka-serialization-jackson"  % akkaVersion,
  "com.typesafe.akka" %% "akka-persistence"            % akkaVersion,
  "com.typesafe.akka" %% "akka-persistence-query"      % akkaVersion,
  "com.typesafe.akka" %% "akka-slf4j"                  % akkaVersion,
  "com.typesafe.akka" %% "akka-stream"                 % akkaVersion,
  "com.typesafe.akka" %% "akka-protobuf-v3"            % akkaVersion,
  "com.typesafe.akka" %% "akka-actor-typed"            % akkaVersion,
  "com.typesafe.akka" %% "akka-persistence-typed"      % akkaVersion,
  "com.typesafe.akka" %% "akka-multi-node-testkit"     % akkaVersion % Test,
  "com.typesafe.akka" %% "akka-testkit"                % akkaVersion % Test,
  "com.typesafe.akka" %% "akka-stream-testkit"         % akkaVersion % Test,
  "com.typesafe.akka" %% "akka-actor-testkit-typed"    % akkaVersion % Test,
  // Use "sbt-dependency-graph" or any other dependency report generator to
  // make sure you add all the necessary dependencies on this list
)


lazy val `hello-world` = (project in file("."))
  .aggregate(`common`,`hello-world-api`, `hello-world-impl`)

lazy val `common` = (project in file("common"))
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslApi,
      scalaTest
    )
  )

lazy val `hello-world-api` = (project in file("hello-world-api"))
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslApi,
      playJsonDerivedCodecs
    ),
    dependencyOverrides ++= akkaOverride
  ).dependsOn(`common`)

lazy val `hello-world-impl` = (project in file("hello-world-impl"))
  .enablePlugins(LagomScala)
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslPersistenceCassandra,
      lagomScaladslKafkaBroker,
      lagomScaladslTestKit,
      akkaProjection,
      akkaProjectionEventSourced,
      akkaProjectionCassandra,
      macwire,
      scalaTest
    ),
    dependencyOverrides ++= akkaOverride :+ "io.dropwizard.metrics" % "metrics-core" % "3.2.6",
  )
  .settings(lagomForkedTestSettings)
  .dependsOn(`common`,`hello-world-api`)



