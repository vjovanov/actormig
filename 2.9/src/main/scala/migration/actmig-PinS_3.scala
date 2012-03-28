import akka.actor.{ MigrationSystem, Actor, Stash, Terminated, Props, ActorRef }

object SillyActor {
  val ref = MigrationSystem.actorOf(Props(() => new SillyActor, "akka.actor.default-stash-dispatcher"))
}

/* PinS, Listing 32.1: A simple actor
 */

class SillyActor extends Actor with Stash {
  def receive = { case _ => println("Why are you not dead"); context.stop(self) }

  override def preStart() {
    for (i <- 1 to 5) {
      println("I'm acting!")
      Thread.sleep(10)
    }
    context.stop(self)
  }

  override def postStop() {
    println("Post stop")
  }
}

object SeriousActor {
  val ref = MigrationSystem.actorOf(Props(() => new SeriousActor, "akka.actor.default-stash-dispatcher"))
}

class SeriousActor extends Actor with Stash {
  def receive = { case _ => println("Nop") }
  override def preStart() {
    for (i <- 1 to 5) {
      println("To be or not to be.")
      //Thread.sleep(1000)
      Thread.sleep(10)
    }
    context.stop(self)
  }
}

/* PinS, Listing 32.3: An actor that calls react
 */
object NameResolver {
  val ref = MigrationSystem.actorOf(Props(() => new NameResolver, "akka.actor.default-stash-dispatcher"))
}

// TODO Mention recursion
class NameResolver extends Actor with Stash {
  import java.net.{ InetAddress, UnknownHostException }

  def receive = {
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
  def makeEchoActor(): ActorRef = MigrationSystem.actorOf(Props(() => new Actor with Stash {

    def receive = { // how to handle receive
      case 'stop =>
        context.stop(self)
      case msg =>
        println("received message: " + msg)
    }
  }, "akka.actor.default-stash-dispatcher"))

  /* PinS, page 696
   */
  def makeIntActor(): ActorRef = MigrationSystem.actorOf(Props(() => new Actor with Stash {

    def receive = {
      case x: Int => // I only want Ints
        unstashAll()
        println("Got an Int: " + x)
        context.stop(self)
      case m => stash()
    }
  }, "akka.actor.default-stash-dispatcher"))

  // TODO test the name resolver with pattern matching
  MigrationSystem.actorOf(Props(() => new Actor with Stash {
    val serious = SeriousActor.ref
    val silly = SillyActor.ref

    // TODO remove trap exit totally 
    override def preStart() {
      context.watch(SillyActor.ref)
    }

    def receive = {
      case Terminated(`silly`) =>
        unstashAll()
        context.watch(SeriousActor.ref)
        context.become {
          case Terminated(`serious`) =>
            // PinS, page 694
            val seriousActor2 = MigrationSystem.actorOf(Props(() => {
              new Actor with Stash {

                def receive = { case _ => context.stop(self) }

                override def preStart() = {
                  for (i <- 1 to 5) {
                    println("That is the question.")
                    //Thread.sleep(1000)
                    Thread.sleep(10)
                  }
                  context.stop(self)
                }
              }
            }, "akka.actor.default-stash-dispatcher"))

            Thread.sleep(80)
            val echoActor = makeEchoActor()
            context.watch(echoActor)
            echoActor ! "hi there"
            echoActor ! 15
            echoActor ! 'stop
            // TODO write a wrapper for automatic unstashing and exception handling
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
                context.stop(self)
              case m =>
                println("Stash 1 " + m)
                stash()
            }
          case m =>
            println("Stash 2 " + m)
            stash()
        }
      case m =>
        println("Stash 3 " + m)
        stash()
    }
  }, "akka.actor.default-stash-dispatcher"))
}

