mainClass in assembly := Some("org.ferrit.server.Ferrit")

jarName in assembly := "ferrit-server.jar"

outputPath in assembly := new java.io.File("./bin/ferrit-server.jar")

// To skip tests during assembly:

test in assembly := {}
