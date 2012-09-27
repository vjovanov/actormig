name := "Actor Migration"

version := "1.0"

organization := "lamp.epfl.ch"

//scalaHome := Some(scalaHomeDir)
scalaVersion := "2.10.0-M7"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"
 
libraryDependencies +=
  "com.typesafe.akka" %% "akka-actor" % "2.1-M2" cross CrossVersion.full

//unmanagedJars in Compile <<= baseDirectory map { base => ((base ** "*.jar") +++ (scalaHomeDir / "lib" / "scala-actors.jar")).classpath }
