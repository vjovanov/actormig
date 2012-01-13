/* sbt -- Simple Build Tool
 * Copyright 2011  Mark Harrah
 */
package sbt

	import java.io.File
	import compiler.AnalyzingCompiler

object ConsoleProject
{
	def apply(state: State, extra: String, cleanupCommands: String = "", options: Seq[String] = Nil)(implicit log: Logger)
	{
		val extracted = Project extract state
		val bindings = ("currentState" -> state) :: ("extracted" -> extracted ) :: Nil
		val unit = extracted.currentUnit
		val compiler = Compiler.compilers(ClasspathOptions.repl)(state.configuration, log).scalac
		val imports = Load.getImports(unit.unit) ++ Load.importAll(bindings.map(_._1))
		val importString = imports.mkString("", ";\n", ";\n\n")
		val initCommands = importString + extra
		(new Console(compiler))(unit.classpath, options, initCommands, cleanupCommands)(Some(unit.loader), bindings)
	}
}
