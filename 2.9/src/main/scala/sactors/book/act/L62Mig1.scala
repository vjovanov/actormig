package sactors.book.act.migi

import scala.actors.RichActor
import scala.actors.ActorSystem

class Master extends RichActor {
  def handle = Map.empty
  override def act() {
    loop {
      react {
        case 'alive =>
          println("yes")
      }
    }
  }
}

class Slave extends RichActor {
  def handle = Map.empty
  override def act() {
    loop {
      react {
        case 'doWork =>
        println("Done")
        reply('done)
      }
    }
  }
}

class MasterKillingSlave extends RichActor {
  def handle = Map.empty
  override def act() {
    loop {
      react {
        case 'doWork =>
          println("I have no master.")
          throw new Exception("Suicide")
      }
    }
  }
}


object L62 extends App {

 println("Starting")
 val master =  ActorSystem.actorOf[Master].start()
 val slave =  ActorSystem.actorOf[Slave].start()
 val masterKillingSlave = ActorSystem.actorOf[MasterKillingSlave].start()
 slave ! 'doWork
 masterKillingSlave ! 'doWork
 master ! 'alive
 
 Thread.sleep(200)  

 Thread.sleep(200)
}


