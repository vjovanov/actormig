package akka.book.pis

import akka.actor.Actor
import akka.actor.Props
import akka.actor.{ActorSystem, OneForOneStrategy, ActorKilledException, ActorInitializationException}
import akka.actor.FaultHandlingStrategy._
/*
object A extends Actor {
  def act() {
    react {
    case 'hello =>
    throw new Exception("Error!")
    }
  }
  override def exceptionHandler = {
   case e: Exception =>
   println(e.getMessage())
  }
} 
*/

// TODO (VJ) 'hello fails
class FailingActor extends Actor {

  def receive = { 
    case "hello" => 
      println("Created with: " + context.props)
      println("Parent: " + context.parent)
      context.parent ! "something"
      println("just banged")
      throw new Exception("Error!")
  }

}

object L61 extends App {
  val system = ActorSystem("MySystem")
  val myDec: Decider = { 
       case _: ActorInitializationException ⇒ Stop
       case _: ActorKilledException         ⇒ Stop
       case _: Exception                    ⇒ Stop
       case _                               ⇒ Escalate
     }
  val parent = system.actorOf(Props[FailingActor])
  val myActor = system.actorOf(Props[FailingActor].withFaultHandler(
     OneForOneStrategy(myDec, None, None)) , name = "myactor")  
   
  myActor ! "hello"   
  println("banged hello")
  // wait a bit 
  Thread.sleep(1000)

  // bang again 
  myActor ! "hello"
  myActor ? "hello" Duraiton.Inf
  println("banged hello again!")

  system.shutdown()
}
 


