/* sbt -- Simple Build Tool
 * Copyright 2009  Mark Harrah, Vesa Vilhonen
 */
package sbt

import java.io.{File,OutputStream}

trait ForkJava
{
	def javaHome: Option[File]
	def outputStrategy: Option[OutputStrategy]
	def connectInput: Boolean
}
trait ForkScala extends ForkJava
{
	def scalaJars: Iterable[File]
}
trait ForkScalaRun extends ForkScala
{
	def workingDirectory: Option[File]
	def runJVMOptions: Seq[String]
}
final case class ForkOptions(javaHome: Option[File] = None, outputStrategy: Option[OutputStrategy] = None, scalaJars: Iterable[File] = Nil, workingDirectory: Option[File] = None, runJVMOptions: Seq[String] = Nil, connectInput: Boolean = false) extends ForkScalaRun

sealed abstract class OutputStrategy extends NotNull
case object StdoutOutput extends OutputStrategy
case class BufferedOutput(logger: Logger) extends OutputStrategy
case class LoggedOutput(logger: Logger) extends OutputStrategy
case class CustomOutput(output: OutputStream) extends OutputStrategy

import java.lang.{ProcessBuilder => JProcessBuilder}
object Fork
{
	private val ScalacMainClass = "scala.tools.nsc.Main"
	private val ScalaMainClass = "scala.tools.nsc.MainGenericRunner"

	val java = new ForkJava("java")
	val javac = new ForkJava("javac")
	val scala = new ForkScala(ScalaMainClass)
	val scalac = new ForkScala(ScalacMainClass)

	private def javaCommand(javaHome: Option[File], name: String): File =
	{
		val home = javaHome.getOrElse(new File(System.getProperty("java.home")))
		new File(new File(home, "bin"), name)
	}

	final class ForkJava(commandName: String) extends NotNull
	{
		def apply(javaHome: Option[File], options: Seq[String], log: Logger): Int =
			apply(javaHome, options, BufferedOutput(log))
		def apply(javaHome: Option[File], options: Seq[String], outputStrategy: OutputStrategy): Int =
			apply(javaHome, options, None, outputStrategy)
		def apply(javaHome: Option[File], options: Seq[String], workingDirectory: Option[File], log: Logger): Int =
			apply(javaHome, options, workingDirectory, BufferedOutput(log))
		def apply(javaHome: Option[File], options: Seq[String], workingDirectory: Option[File], outputStrategy: OutputStrategy): Int =
			apply(javaHome, options, workingDirectory, Map.empty, outputStrategy)
		def apply(javaHome: Option[File], options: Seq[String], workingDirectory: Option[File], env: Map[String, String], outputStrategy: OutputStrategy): Int =
			fork(javaHome, options, workingDirectory, env, false, outputStrategy).exitValue
		def fork(javaHome: Option[File], options: Seq[String], workingDirectory: Option[File], env: Map[String, String], connectInput: Boolean, outputStrategy: OutputStrategy): Process =
		{
			val executable = javaCommand(javaHome, commandName).getAbsolutePath
			val command = (executable :: options.toList).toArray
			val builder = new JProcessBuilder(command : _*)
			workingDirectory.foreach(wd => builder.directory(wd))
			val environment = builder.environment
			for( (key, value) <- env )
				environment.put(key, value)
			outputStrategy match {
				case StdoutOutput => Process(builder).run(connectInput)
				case BufferedOutput(logger) => Process(builder).runBuffered(logger, connectInput)
				case LoggedOutput(logger) => Process(builder).run(logger, connectInput)
				case CustomOutput(output) => (Process(builder) #> output).run(connectInput)
			}
		}
	}

	final class ForkScala(mainClassName: String) extends NotNull
	{
		def apply(javaHome: Option[File], jvmOptions: Seq[String], scalaJars: Iterable[File], arguments: Seq[String], log: Logger): Int =
			apply(javaHome, jvmOptions, scalaJars, arguments, None, BufferedOutput(log))
		def apply(javaHome: Option[File], jvmOptions: Seq[String], scalaJars: Iterable[File], arguments: Seq[String], workingDirectory: Option[File], log: Logger): Int =
			apply(javaHome, jvmOptions, scalaJars, arguments, workingDirectory, BufferedOutput(log))
		def apply(javaHome: Option[File], jvmOptions: Seq[String], scalaJars: Iterable[File], arguments: Seq[String], workingDirectory: Option[File], outputStrategy: OutputStrategy): Int =
			fork(javaHome, jvmOptions, scalaJars, arguments, workingDirectory, false, outputStrategy).exitValue()
		def fork(javaHome: Option[File], jvmOptions: Seq[String], scalaJars: Iterable[File], arguments: Seq[String], workingDirectory: Option[File], connectInput: Boolean, outputStrategy: OutputStrategy): Process =
		{
			if(scalaJars.isEmpty) error("Scala jars not specified")
			val scalaClasspathString = "-Xbootclasspath/a:" + scalaJars.map(_.getAbsolutePath).mkString(File.pathSeparator)
			val mainClass = if(mainClassName.isEmpty) Nil else mainClassName :: Nil
			val options = jvmOptions ++ (scalaClasspathString :: mainClass ::: arguments.toList)
			Fork.java.fork(javaHome, options, workingDirectory, Map.empty, connectInput, outputStrategy)
		}
	}
}
