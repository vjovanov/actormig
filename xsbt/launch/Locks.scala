/* sbt -- Simple Build Tool
 * Copyright 2009, 2010  Mark Harrah
 */
package xsbt.boot

import java.io.{File, FileOutputStream}
import java.nio.channels.FileChannel
import java.util.concurrent.Callable
import scala.collection.immutable.List

object GetLocks
{
	/** Searches for Locks in parent class loaders before returning Locks from this class loader.
	* Normal class loading doesn't work because the launcher class loader hides xsbt classes.*/
	def find: xsbti.GlobalLock =
		Loaders(getClass.getClassLoader.getParent).flatMap(tryGet).headOption.getOrElse(Locks)
	private[this] def tryGet(loader: ClassLoader): List[xsbti.GlobalLock] =
		try { getLocks0(loader) :: Nil } catch { case e: ClassNotFoundException => Nil }
	private[this] def getLocks0(loader: ClassLoader) =
		Class.forName("xsbt.boot.Locks$", true, loader).getField("MODULE$").get(null).asInstanceOf[xsbti.GlobalLock]
}

// gets a file lock by first getting a JVM-wide lock.
object Locks extends xsbti.GlobalLock
{
	private[this] val locks = new Cache[File, Unit, GlobalLock]( (f, _) => new GlobalLock(f))
	def apply[T](file: File, action: Callable[T]): T = if(file eq null) action.call else apply0(file, action)
	private[this] def apply0[T](file: File, action: Callable[T]): T =
	{
		val lock =
			synchronized
			{
				file.getParentFile.mkdirs()
				file.createNewFile()
				locks(file.getCanonicalFile, ())
			}
		lock.withLock(action)
	}

	private[this] class GlobalLock(file: File)
	{
		private[this] var fileLocked = false
		def withLock[T](run: Callable[T]): T =
			synchronized
			{
				if(fileLocked)
					run.call
				else
				{
					fileLocked = true
					try { withFileLock(run) }
					finally { fileLocked = false }
				}
			}
		private[this] def withFileLock[T](run: Callable[T]): T =
		{
			def withChannelRetries(retries: Int)(channel: FileChannel): T =
				try { withChannel(channel) }
				catch { case i: InternalLockNPE =>
					if(retries > 0) withChannelRetries(retries - 1)(channel) else throw i
				}
				
			def withChannel(channel: FileChannel) =
			{
				val freeLock = try { channel.tryLock } catch { case e: NullPointerException => throw new InternalLockNPE(e) }
				if(freeLock eq null)
				{
					System.out.println("Waiting for lock on " + file + " to be available...");
					val lock = try { channel.lock } catch { case e: NullPointerException => throw new InternalLockNPE(e) }
					try { run.call }
					finally { lock.release() }
				}
				else
				{
					try { run.call }
					finally { freeLock.release() }
				}
			}
			Using(new FileOutputStream(file).getChannel)(withChannelRetries(5))
		}
	}
	private[this] final class InternalLockNPE(cause: Exception) extends RuntimeException(cause)
}
