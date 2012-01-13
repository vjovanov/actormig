package akka.book.pis

import akka.actor.Actor
import akka.actor.Props
import akka.actor.{
  ActorSystem,
  OneForOneStrategy,
  ActorKilledException,
  ActorInitializationException, 
  Terminated,
  ActorRef
}
import akka.actor.FaultHandlingStrategy._

/*

object Master extends Actor {
  def act() {
    Slave ! 'doWork
    react {
      case 'done =>
      throw new Exception("Master crashed")
    }
  }
}

object Slave extends Actor {
  def act() {
    link(Master)
    loop {
      react {
        case 'doWork =>
          println("Done")
          reply('done)
      }
    }
  }
}

*/

class MasterActor extends Actor {

  var child: ActorRef = null

  override def preStart() { 
    if (child == null)
     child = context.actorOf(Props[SlaveActor], "slaveactor")
   
     context.watch(child) // <-- this is the only call needed for registration
  }
   
  override def postRestart(reason: Throwable) {
    // children should be dead 
    // now should I relink to them
  }

  def receive = {
    case "kill"              ⇒  context.stop(child) 
    case "master death"      => throw new Exception("Is the child alive?")
    case Terminated(x) if x == child ⇒  println("finished")
  }
}

class SlaveActor extends Actor {

  def receive = { 
    case "hello" => 
      throw new Exception("Error!")
  }

}

object L62 extends App {
  val system = ActorSystem("MySystem")
  val myDec: Decider = {
       case _: ActorInitializationException ⇒ Stop
       case _: ActorKilledException         ⇒ Stop
       case _: Exception                    ⇒ println("Restart"); Restart;
       case _                               ⇒ Escalate
   }

  val master = system.actorOf(Props[MasterActor].withFaultHandler(
     OneForOneStrategy(myDec, None, None)), name = "myactor")
  
  master ! "master death"

  
//  master ! "kill" 
//  println("banged kill")
  // wait a bit 
  Thread.sleep(10)

  // bang again 
//  master ! "kill"
//  println("banged kill again!")
  Thread.sleep(10)

  system.shutdown()
}
 


