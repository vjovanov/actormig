
import akka.actor.Actor
import akka.actor.Props
import akka.event.Logging
import akka.dispatch.Future
import akka.actor.ActorSystem

class MyActor extends Actor {
  val log = Logging(context.system, this)
  def receive = {
    case "test" ⇒ log.info("received test")
    case _      ⇒ log.info("received unknown message")
  }
}

object SampleApp extends App {
  val system = ActorSystem("MySystem")
  val myActor = system.actorOf(Props[MyActor], name = "myactor")
  myActor ! "test" 
}
