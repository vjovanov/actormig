name := "Actor Migration"

version := "1.0"

organization := "lamp.epfl.ch"

//--- Local Scala

scalaHome := Some(scalaHomeDir)

//--- End of Local Scala


unmanagedJars in Compile <<= baseDirectory map { base => ((base ** "*.jar") +++ 
  (scalaHomeDir / "lib" / "scala-actors.jar")).classpath }
