name := "Ferrit"

version := "1.0"

scalaVersion := "2.11.8"

testOptions in Test += Tests.Setup( () => System.setProperty("logback.configurationFile", "../../../logback.xml"))

scalacOptions ++= Seq("-feature", "-deprecation")

exportJars := true

parallelExecution in Test := false


// ---------------- Dependency Settings ----------------

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

resolvers += "Typesafe Akka" at "http://repo.akka.io/snapshots"

resolvers += "Spray Repostory" at "http://repo.spray.io"

libraryDependencies ++= {
  val akkaVersion = "2.4.1"
  val sprayVersion = "1.3.3"
  Seq(
    "com.typesafe.akka" %% "akka-actor" % akkaVersion,
    "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
    "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
    "ch.qos.logback" % "logback-classic" % "1.1.6",
    "org.scalatest" %% "scalatest" % "2.2.6",
    "org.scalamock" %% "scalamock-scalatest-support" % "3.2.2",
    "org.mockito" % "mockito-all" % "1.10.19",
    "io.spray" %% "spray-testkit" % sprayVersion,
    "io.spray" %% "spray-can" % sprayVersion,
    "io.spray" %% "spray-client" % sprayVersion,
    "io.spray" %% "spray-routing" % sprayVersion,
    "com.typesafe.play" %% "play-json" % "2.5.0",
    "com.ning" % "async-http-client" % "1.9.33",
    "org.jsoup" % "jsoup" % "1.8.3",
    "joda-time" % "joda-time" % "2.9.2",
    "org.joda" % "joda-convert" % "1.8.1",
    "com.datastax.cassandra" % "cassandra-driver-core" % "3.0.0",
    "net.jpountz.lz4" % "lz4" % "1.2.0"
  )
}


// ---------------- Revolver Settings ----------------

javaOptions in reStart += "-Xmx1g"

reColors := Seq("blue", "green", "magenta")

mainClass in reStart := Some("org.ferrit.server.Ferrit")

reLogTag := ""

