package akka.book.pis

import akka.actor.Actor
import akka.actor.Props
import akka.actor.ActorSystem

class SillyActor extends Actor {

  override def preStart() {
    for (i <- 1 to 5) {
      println("I'm acting!")
      Thread.sleep(1000)
    }
    context.stop(self)
  }

  def receive = Map.empty
}


class SeriousActor extends Actor {

  override def preStart() {
    for (i <- 1 to 5) {
      println("To be or not to be!")
      Thread.sleep(1000)
    }
    context.stop(self)
  }

  def receive = Map.empty
}

object L1 extends App {
  val system = ActorSystem("MySystem")
  val myActor = system.actorOf(Props[SillyActor], name = "myactor")
  val myActor2 = system.actorOf(Props[SeriousActor], name = "myactor")

  system.shutdown()
}
 


