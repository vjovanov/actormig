/* sbt -- Simple Build Tool
 * Copyright 2008, 2009 Mark Harrah
 */
package sbt
package classpath

import java.io.File
import java.net.{URI, URL, URLClassLoader}

/** This is a starting point for defining a custom ClassLoader.  Override 'doLoadClass' to define
* loading a class that has not yet been loaded.*/
abstract class LoaderBase(urls: Seq[URL], parent: ClassLoader) extends URLClassLoader(urls.toArray, parent)
{
	require(parent != null) // included because a null parent is legitimate in Java
	@throws(classOf[ClassNotFoundException])
	override final def loadClass(className: String, resolve: Boolean): Class[_] =
	{
		val loaded = findLoadedClass(className)
		val found =
			if(loaded == null)
				doLoadClass(className)
			else
				loaded
			
		if(resolve)
			resolveClass(found)
		found
	}
	/** Provides the implementation of finding a class that has not yet been loaded.*/
	protected def doLoadClass(className: String): Class[_]
	/** Provides access to the default implementation of 'loadClass'.*/
	protected final def defaultLoadClass(className: String): Class[_] = super.loadClass(className, false)
}

/** Searches self first before delegating to the parent.*/
class SelfFirstLoader(classpath: Seq[URL], parent: ClassLoader) extends LoaderBase(classpath, parent)
{
	@throws(classOf[ClassNotFoundException])
	override final def doLoadClass(className: String): Class[_] =
	{
		try { findClass(className) }
		catch { case _: ClassNotFoundException => defaultLoadClass(className) }
	}
}


private class IntermediateLoader(urls: Array[URL], parent: ClassLoader) extends LoaderBase(urls, parent)
{
	def doLoadClass(className: String): Class[_] =
	{
		// if this loader is asked to load an sbt class, it must be because the project we are building is sbt itself,
		 // so we want to load the version of classes on the project classpath, not the parent
		if(className.startsWith(Loaders.SbtPackage))
			findClass(className)
		else
			defaultLoadClass(className)
	}
}
/** Delegates class loading to `parent` for all classes included by `filter`.  An attempt to load classes excluded by `filter`
* results in a `ClassNotFoundException`.*/
class FilteredLoader(parent: ClassLoader, filter: ClassFilter) extends ClassLoader(parent)
{
	require(parent != null) // included because a null parent is legitimate in Java
	def this(parent: ClassLoader, excludePackages: Iterable[String]) = this(parent, new ExcludePackagesFilter(excludePackages))
	
	@throws(classOf[ClassNotFoundException])
	override final def loadClass(className: String, resolve: Boolean): Class[_] =
	{
		if(filter.include(className))
			super.loadClass(className, resolve)
		else
			throw new ClassNotFoundException(className)
	}
}
private class SelectiveLoader(urls: Array[URL], parent: ClassLoader, filter: ClassFilter) extends URLClassLoader(urls, parent)
{
	require(parent != null) // included because a null parent is legitimate in Java
	def this(urls: Array[URL], parent: ClassLoader, includePackages: Iterable[String]) = this(urls, parent, new IncludePackagesFilter(includePackages))
	
	@throws(classOf[ClassNotFoundException])
	override final def loadClass(className: String, resolve: Boolean): Class[_] =
	{
		if(filter.include(className))
			super.loadClass(className, resolve)
		else
		{
			val loaded = parent.loadClass(className)
			if(resolve)
				resolveClass(loaded)
			loaded
		}
	}
}
trait ClassFilter
{
	def include(className: String): Boolean
}
abstract class PackageFilter(packages: Iterable[String]) extends ClassFilter
{
	require(packages.forall(_.endsWith(".")))
	protected final def matches(className: String): Boolean = packages.exists(className.startsWith)
}
class ExcludePackagesFilter(exclude: Iterable[String]) extends PackageFilter(exclude)
{
	def include(className: String): Boolean = !matches(className)
}
class IncludePackagesFilter(include: Iterable[String]) extends PackageFilter(include)
{
	def include(className: String): Boolean = matches(className)
}

private[sbt] class LazyFrameworkLoader(runnerClassName: String, urls: Array[URL], parent: ClassLoader, grandparent: ClassLoader)
	extends LoaderBase(urls, parent)
{
	def doLoadClass(className: String): Class[_] =
	{
		if(Loaders.isNestedOrSelf(className, runnerClassName))
			findClass(className)
		else if(Loaders.isSbtClass(className)) // we circumvent the parent loader because we know that we want the
			grandparent.loadClass(className)              // version of sbt that is currently the builder (not the project being built)
		else
			parent.loadClass(className)
	}
}
private object Loaders
{
	val SbtPackage = "sbt."
	def isNestedOrSelf(className: String, checkAgainst: String) =
		className == checkAgainst || className.startsWith(checkAgainst + "$")
	def isSbtClass(className: String) = className.startsWith(Loaders.SbtPackage)
}

final class NativeCopyConfig(val tempDirectory: File, val explicitLibraries: Seq[File], val searchPaths: Seq[File])
trait NativeCopyLoader extends ClassLoader
{
	protected val config: NativeCopyConfig
	import config._
	
	private[this] val mapped = new collection.mutable.HashMap[String, String]

	override protected def findLibrary(name: String): String =
		synchronized { mapped.getOrElseUpdate(name, findLibrary0(name)) }

	private[this] def findLibrary0(name: String): String =
	{
		val mappedName = System.mapLibraryName(name)
		val explicit = explicitLibraries.filter(_.getName == mappedName).toStream
		val search = searchPaths.toStream flatMap relativeLibrary(mappedName)
		(explicit ++ search).headOption.map(copy).orNull
	}
	private[this] def relativeLibrary(mappedName: String)(base: File): Seq[File] =
	{
		val f = new File(base, mappedName)
		if(f.isFile) f :: Nil else Nil
	}
	private[this] def copy(f: File): String =
	{
		val target = new File(tempDirectory, f.getName)
		IO.copyFile(f, target)
		target.getAbsolutePath
	}
}