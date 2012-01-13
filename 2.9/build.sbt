name := "Actor Migration"

version := "1.0"

organization := "lamp.epfl.ch"

scalaVersion := "2.9.1"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies += "org.scala-tools.testing" %% "scalacheck" % "1.9" % "test"

libraryDependencies += "com.typesafe.akka" % "akka-actor" % "2.0-SNAPSHOT"

