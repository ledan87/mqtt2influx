name := "mqtt2influx"

version := "0.1"
organization := "de.daniel"

scalaVersion := "2.12.6"


resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.5.9"
libraryDependencies += "org.scalactic" %% "scalactic" % "3.0.4"
libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.4" % "test"
libraryDependencies += "net.straylightlabs" % "hola" % "0.2.1"
libraryDependencies += "org.mockito" % "mockito-all" % "2.0.2-beta"
libraryDependencies += "com.typesafe.akka" %% "akka-testkit" % "2.5.9" % Test
libraryDependencies += "org.junit.jupiter" % "junit-jupiter-engine" % "5.1.0" % Test
libraryDependencies += "com.lightbend.akka" %% "akka-stream-alpakka-mqtt" % "0.17"
libraryDependencies += "com.typesafe.akka" %% "akka-http" % "10.1.0-RC2"
libraryDependencies += "com.typesafe.akka" %% "akka-stream" % "2.5.9"