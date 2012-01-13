/* sbt -- Simple Build Tool
 * Copyright 2008 Mark Harrah, David MacIver
 */
package sbt

import java.io.File
import scala.collection.mutable.{HashSet, Set}

trait Format[T]
{
	def toString(t: T): String
	def fromString(s: String): T
}
abstract class SimpleFormat[T] extends Format[T]
{
	def toString(t: T) = t.toString
}
object Format
{
	implicit val file: Format[File] = new Format[File]
	{
		def toString(file: File) = file.getAbsolutePath
		def fromString(s: String) = (new File(s)).getAbsoluteFile
	}
	implicit val hash: Format[Array[Byte]] = new Format[Array[Byte]]
	{
		def toString(hash: Array[Byte]) = Hash.toHex(hash)
		def fromString(hash: String) = Hash.fromHex(hash)
	}
	def set[T](implicit format: Format[T]): Format[Set[T]] = new Format[Set[T]]
	{
		def toString(set: Set[T]) = set.toList.map(format.toString).mkString(File.pathSeparator)
		def fromString(s: String) = (new HashSet[T]) ++ IO.pathSplit(s).map(_.trim).filter(!_.isEmpty).map(format.fromString)
	}
	implicit val string: Format[String] = new SimpleFormat[String] { def fromString(s: String) = s }
	/*implicit val test: Format[Discovered] = new SimpleFormat[Discovered]
	{
		def fromString(s: String) = DiscoveredParser.parse(s).fold(error, x => x)
	}*/
}