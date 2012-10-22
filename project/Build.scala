import sbt._

object ScalaHome extends Build {
/* val scalaHomeDir = {  
   val props = new java.util.Properties()
   IO.load(props, file(".") / "local.properties")
   val x = props.getProperty("scala.home")
   if (x == null)
     sys.error("Please set the scala.home property in the local.properties file. This property should point to the local distribution of www.github.com/phaller/scala.git branch actors-migration.")
   else
     file(x)
 }*/


 lazy val root = Project(id = "actormig",
                            base = file("."),
                            settings = Project.defaultSettings)
}

