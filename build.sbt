import org.allenai.plugins.CoreDependencies._

name := "AI2-Ferrit"

version := "1.0"

enablePlugins(WebServicePlugin)

libraryDependencies ++= Seq(
  "org.mockito" % "mockito-all" % "1.9.0" % "test",
  sprayTestkit,
  allenAiTestkit % "it,test",
  "com.typesafe.play" %% "play-json" % "2.2.0",
  "com.ning" % "async-http-client" % "1.7.20",
  "org.jsoup" % "jsoup" % "1.7.3",
  "com.datastax.cassandra" % "cassandra-driver-core" % "2.1.2",
  "net.jpountz.lz4" % "lz4" % "1.2.0"
)

dependencyOverrides ++= Set(
  akkaActor,
  akkaTestkit,
  typesafeConfig,
  "io.netty" % "netty" % "3.9.0.Final",
  "org.scala-lang" % "scala-reflect" % "2.10.4"
)

// ---------------- Revolver Settings ----------------
javaOptions in Revolver.reStart += "-Xmx1g"

Revolver.reColors := Seq("blue", "green", "magenta")

mainClass in Revolver.reStart := Some("org.ferrit.server.Ferrit")

Revolver.reLogTag := ""

// ---------------- SBT Scoverage ----------------

resolvers += Classpaths.sbtPluginReleases

instrumentSettings

parallelExecution in ScoverageTest := false


// ---------------- SBT Dependency Graph Settings ----------------

net.virtualvoid.sbt.graph.Plugin.graphSettings
