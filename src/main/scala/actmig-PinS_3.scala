/**
 * NOTE: Code snippets from this test are included in the Actor Migration Guide. In case you change
 * code in these tests prior to the 2.10.0 release please send the notification to @vjovanov.
 */
import akka.actor._
import akka.actor.ActorDSL.ActWithStash
import scala.concurrent.duration._
import scala.concurrent.{ Promise, Await }
import scala.concurrent.ExecutionContext.Implicits.global

object AS {
 val s = ActorSystem("migration-system")
}

object SillyActor {
  val startPromise = Promise[Boolean]()
  val ref = ActorDSL.actor(AS.s)(new SillyActor)
}

/* PinS, Listing 32.1: A simple actor
 */
class SillyActor extends ActWithStash {
  override def receive: PartialFunction[Any, Unit] = { case _ => println("Why are you not dead"); context.stop(self) }

  override def preStart() {
    Await.ready(SillyActor.startPromise.future, 5 seconds)
    for (i <- 1 to 5)
      println("I'm acting!")
    context.stop(self)
  }

  override def postStop() {
    println("Post stop")
  }
}

object SeriousActor {
  val startPromise = Promise[Boolean]()
  val ref = ActorDSL.actor(AS.s)(new SeriousActor)
}

class SeriousActor extends ActWithStash {
  override def receive: PartialFunction[Any, Unit] = { case _ => println("Nop") }
  override def preStart() {
    Await.ready(SeriousActor.startPromise.future, 5 seconds)
    for (i <- 1 to 5)
      println("To be or not to be.")
    context.stop(self)
  }
}

/* PinS, Listing 32.3: An actor that calls react
 */
object NameResolver {
  val ref = ActorDSL.actor(AS.s)(new NameResolver)
}

class NameResolver extends ActWithStash {
  import java.net.{ InetAddress, UnknownHostException }

  override def receive: PartialFunction[Any, Unit] = {
    case (name: String, actor: ActorRef) =>
      actor ! getIp(name)
    case "EXIT" =>
      println("Name resolver exiting.")
      context.stop(self) // quit
    case msg =>
      println("Unhandled message: " + msg)
  }

  def getIp(name: String): Option[InetAddress] = {
    try {
      Some(InetAddress.getByName(name))
    } catch {
      case _: UnknownHostException => None
    }
  }

}

object Test extends App {

  /* PinS, Listing 32.2: An actor that calls receive
   */
  def makeEchoActor(): ActorRef = ActorDSL.actor(AS.s)(new ActWithStash {

    override def receive: PartialFunction[Any, Unit] = { // how to handle receive
      case 'stop =>
        context.stop(self)
      case msg =>
        println("received message: " + msg)
    }
  })

  /* PinS, page 696
   */
  def makeIntActor(): ActorRef = ActorDSL.actor(AS.s)(new ActWithStash {

    override def receive: PartialFunction[Any, Unit] = {
      case x: Int => // I only want Ints
        unstashAll()
        println("Got an Int: " + x)
        context.stop(self)
      case _ => stash
    }
  })

  val mainPromise = Promise[Unit]

  ActorDSL.actor(AS.s)(new ActWithStash {
    val silly = SillyActor.ref

    override def preStart() {
      context.watch(SillyActor.ref)
      SillyActor.startPromise.success(true)
    }

    override def receive: PartialFunction[Any, Unit] = {
      case Terminated(`silly`) =>
        unstashAll()
        val serious = SeriousActor.ref
        context.watch(SeriousActor.ref)
        SeriousActor.startPromise.success(true)
        context.become {
          case Terminated(`serious`) =>
            val seriousPromise2 = Promise[Boolean]()
            // PinS, page 694
            val seriousActor2 = ActorDSL.actor(AS.s)(
              new ActWithStash {

                override def receive = { case _ => context.stop(self) }

                override def preStart() = {
                  for (i <- 1 to 5)
                    println("That is the question.")
                  seriousPromise2.success(true)
                  context.stop(self)
                }
              })

            Await.ready(seriousPromise2.future, 5 seconds)
            val echoActor = makeEchoActor()
            context.watch(echoActor)
            echoActor ! "hi there"
            echoActor ! 15
            echoActor ! 'stop
            context.become {
              case Terminated(_) =>
                unstashAll()
                val intActor = makeIntActor()
                intActor ! "hello"
                intActor ! math.Pi
                // only the following send leads to output
                intActor ! 12
                context.unbecome()
                context.unbecome()
                // report termination
                mainPromise.success(())
                context.stop(self)
              case m =>
                println("Stash 1 " + m)
                stash
            }
          case m =>
            println("Stash 2 " + m)
            stash
        }
      case m =>
        println("Stash 3 " + m)
        stash
    }
  })

  mainPromise.future.onComplete(x => AS.s.shutdown)
}
