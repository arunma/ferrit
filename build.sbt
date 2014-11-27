name := "Ferrit"

version := "1.0"

scalaVersion := "2.10.3"

testOptions in Test += Tests.Setup( () => System.setProperty("logback.configurationFile", "../../../logback.xml"))

scalacOptions ++= Seq("-feature", "-deprecation")

exportJars := true

retrieveManaged := true

parallelExecution in Test := false


// ---------------- Dependency Settings ----------------

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

resolvers += "Typesafe Akka" at "http://repo.akka.io/snapshots"

resolvers += "Spray Repostory" at "http://repo.spray.io"

libraryDependencies ++= {
  val akkaVersion = "2.3.3"
  val sprayVersion = "1.3.1"
  Seq(
    "com.typesafe.akka" %% "akka-actor" % akkaVersion,
    "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
    "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
    "ch.qos.logback" % "logback-classic" % "1.0.9",
    "org.scalatest" %% "scalatest" % "1.9.1" % "test",
    "org.scalamock" %% "scalamock-scalatest-support" % "3.0.1" % "test",
    "org.mockito" % "mockito-all" % "1.9.0" % "test",
    "io.spray" % "spray-testkit" % sprayVersion % "test",
    "io.spray" % "spray-can" % sprayVersion,
    "io.spray" % "spray-client" % sprayVersion,
    "io.spray" % "spray-routing" % sprayVersion,
    "com.typesafe.play" %% "play-json" % "2.2.0",
    "com.ning" % "async-http-client" % "1.7.20",
    "org.jsoup" % "jsoup" % "1.7.3",
    "joda-time" % "joda-time" % "2.2",
    "org.joda" % "joda-convert" % "1.2",
    "com.datastax.cassandra" % "cassandra-driver-core" % "2.1.2",
    "net.jpountz.lz4" % "lz4" % "1.2.0"
  )
}


// ---------------- Revolver Settings ----------------

Seq(Revolver.settings: _*)

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
