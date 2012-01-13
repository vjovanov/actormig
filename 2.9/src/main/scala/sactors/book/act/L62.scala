package sactors.book.act

import scala.actors.Actor

object Master extends Actor {
  def act() {
    loop {
      react {
        case 'alive =>
          println("yes")
      }
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

object MasterKillingSlave extends Actor {
  def act() {
    link(Master)
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
 Master.start()
 Slave.start()
 MasterKillingSlave.start()
 Slave ! 'doWork
 MasterKillingSlave ! 'doWork
 Master ! 'alive
 
 Thread.sleep(200)  

 println(MasterKillingSlave.getState)
 println(Slave.getState)
 println(Master.getState)

 Thread.sleep(200)
}
