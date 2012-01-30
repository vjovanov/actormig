# Scala Actors Migration Guide 

## Introduction

Starting from Scala version 2.10.0 standard library is switching to Akka Actors as their default implementation. Reason for this change is that Akka Actors have much better performance, their API design prevents accidental memory leak and guides users to think about fault-handling. Moreover, it provides uniform interface for accessing both remote and local actors. 

To ease the difficulties of migration from Scala to Akka Actors we have provided migration code on both Akka and Scala side. Purpose of this document is to guide users through the migration process and explain how to use the provided migration kit.   

With release 2.10 Scala Actors will be shipped together with the Scala distribution but separated from the `scala-library.jar`. Scala Actors will be contained in the `scala-actors.jar` in the deprecated form. Together with the distribution we will include Akka Actors in file `akka-actors.jar`. The old Scala actors will be preserved in the original form as the part of the Scalax community library.    

Future major releases of Scala will not contain Scala actors as the part of the distribution. 
 
In section "Staying With Scala Actors" we will discuss the option of staying with the existing implementation of actors. Then in section "Migration Overview" we will describe the migration process and talk about what is being changed in the Scala distribution and Scala actors code base in order to support it . 
Then in section "Step by Step Guide for Migration to Akka" we show individual steps, with working examples, that one needs to take to convert the code to the version compatible with Akka. 

## Staying With Scala Actors

TODO Move section to end.
In this section we discuss the possibility of staying with current implementation of Scala Actors. Here we will explain which actor usage patterns will be hard to migrate and which patterns should not be complicated. 

One of fundamental differences between Scala and Akka actors is exhaustivity of `react`/`receive` methods. In Scala it is possible to write non-exhaustive partial funtctions. Also it is possbile to nest `react` methods to the arbitrary level of depth for the purpose of serving just one message and then returning to the previous behavior. Also in Scala Actors linking working in bidirectional way while in Akka it is implemented unidirectionally. 

Although we provide a migration solution for almost every use case there are still cases that will require more change. In the following list there are cases that are not straight forward to migrate with brief explanation. Compare the list with your code so it is easier to decide wheather to stay with Scala Actors or migrate to Akka.

1. Nested `react` without the loop.

2. Relying on bidirectional linking

3. Usage of `restart` method on actors

4. Usage of methods `getState`
 
In case you decide to stay with Scala actors the following changes need to be done to your build scripts and code base. 
1. If you are using SBT as your build tool add the following dependency to your project TODO. In case you are using ant or custom build tools download the appropriate Scala jar from Scalax and modify your build to include it in the class path. 
2. To enable coexistence of Scalax actors and Scala we have changed package names to TODO. You will have to go through your code and replace all import packages from `scala.actors` to scalax.actors`. 

Remaining of this document is dedicated to migration from Scala Actors to Akka. In case you decided to stay with Scala Actors there is no need to read further.

TODO Talk about linking/fault handling
#### Linking and Unlinking 
* Conversion to death watch and DeathPact from Akka

#### Fault Handling 
In Scala Actors actor local fault handling was done using the method.

## Migration Overview

* Scala actors introduce new actor type that resembles the actors in akka.
* Scala actors get all transformed into a new scala actor. Then the system is tested. 
* All actors are then moved to Akka actor by simply changing the import statements.
* All actors are accessed through ActorRef interfaces.
* Scala actors are evicted from the standard library but preserved in the distribution???? Is this true? 
* Are Akka actors placed in the distribution or they need to be imported separately??? 

### Inheritance of Actor, Reactor and ReplyReactor 

## Step by Step Guide for Migration to Akka

In this chapter we will go through 5 phases of actor migration. Each phase is designed in a such way that you can test your code to check if everything is still OK in your system. After completion of the last phase you should be able to have the same functionality but on Akka code base. 

### 1. Everything as an Actor
Scala Actors provide public access to multiple types of actors. They are organized in the hierarchy and each subclass provides slight richer functionality. To make migration easier first step will be to change each type of actor that is used in the project to actual class `Actor`. This migration step should not be complicated since the `Actor` class is located at the bottom of the hierarchy and provides broadest functionality. 

Scala Actors provide following actors in their hierarchy:
 
1. `Reactor` - migrate by changing your class definition from `class xyz extends Reactor[T]` to `class xyz extends Actor`. Compared to the `Actor`, `Reactor` provides additional type parameter which marks the type of the messages received. If your code uses that information than you might need to *i)* apply pattern matching with explicit type or *ii)* do the downcast from `Any` to the type of the message.  

2. `ReplyReactor` - migrate by changing your class definition from `class xyz extends ReplyReactor` to `class xyz extends Actor`

3. `Actor` - stays the same

4. `DaemonActor` - TODO daemon dispatcher.  


### 2. Instantiations

In Akka actors can be accessed only through the narrow interface named `ActorRef`. Instances of `ActorRef` are acquired by instantiating actors only within the special block that is passed to the `actorOf` method of `MigrationSystem` object. To ease the migration we have added the subset of Akka `ActorRef` and `MigrationSystem`.
Migration to `ActorRef`s will be the next step in migration. We will present how to migrate most common patterns of Scala `Actor` instantiation and the we will show how to overcome issues when method signatures of `ActorRef` and `Actor` do not align.  

1. Constructor Call Instantiation
 
        val migActor = new MigratingActor(arg1, arg2) 

    Should be replaced with: 
 
        val migActor = MigrationSystem.actorOf(new MigrationActor(arg1, arg2))
 
    In case constructor is called with no arguments the special version of method `actorOf` can be used.
 
        val migActor = MigrationSystem.actorOf[MigrationActorNoArgs]
 
2. DSL for Creating Actors

        val migActor = actor { 
           // actor definition
        }

    Should be replace with: 

        val migActor = ActorSystem.actorOf(new Actor {
           def act() {
             // actor definition
           }
        })
        migActor.start()

3. Object Extended from Actor

        object MigrationActor extends Actor { 
           // MigrationActor definition 
        }
    
    Should be replaced with:

        object MigrationActor {
          val ref = MigrationSystem.actorOf[MigrationActor]
        }
        
        class MigrationActor extends Actor {
          // Same MigrationActor definition
        }
     
#### Different Method Signatures

At this point we have changed all the instantiations of actors to return `ActorRef`s, however, we are not done yet. There are differences in usage of `ActorRef`s and `Actor`s and we need to change the usage of each instance. Unfortunately, some of the methods that Scala `Actor` provides can not be migrated. For the following methods you will have to find a way around them in your code:

1. `getState()` - actors in Akka are managed by their supervising actors and are restarted by default. In that scenario state of an actor is not relevant.
 
2. `restart()` - standard Akka actors are restarted by default after failure. TODO. Explain how to migrate this functionality.

For other methods we provide simple translation scheme:

1. `!!(msg: Any): Future[Any]` - should be replaced with invocation of method `?(message: Any)(implicit timeout: Timeout): Future[Any]`. For example, `actor !! message` should be replaced with `val fut = actor ? message; fut()`.
  
2. `!![A] (msg: Any, handler: PartialFunction[Any, A]): Future[A]` - should be replaced with invocation of method `?(message: Any)(implicit timeout: Timeout): Future[Any]`. The handler can be extracted as a separate function and then applied to the generated future result. The result of handle should yield another future like in the following example:
         
        val handler: PartialFunction[Any, T] =  ... // code of the handler             
        val fut = (respActor ? msg)        
        Futures.future{handler.apply(fut2())}
 
3. `!? (msg: Any): Any` - should be replaced with `?(message: Any)(implicit timeout: Timeout): Future[Any]` and explicit blocking on the future. For example,`actor !? message` should be replaced with `Some((respActor.?(msg2)(Duration.Inf))())`.
 
4. `!? (msec: Long, msg: Any): Option[Any]` - should be replaced with `?(message: Any)(implicit timeout: Timeout): Future[Any]` and explicit blocking on the future. For example,`actor !? (timeout, message)` should be replaced with `Some((actor.?(message)(timeout))())`.  
    
Other public methods are public just for purposes of actors DSL and can be used inside the actor definition. Therefore there is no need to migrate those methods in this phase.
    
After migrating these methods you can run your test suite and the behavior of the system should remain the same. 

### 3. Changing to RichActor

At this point we have changed all actors to use the same Actor interface and made them be created through special factory methods and accessed through `ActorRef` interface. Now we need to change all actors to the `RichActor` class. This class behaves exactly the same like Scala `Actor` but provides methods that allow easy, step by step, migration to Akka behavior.
To change your code base to the new type of actor all your actors should extend `RichActor`. Each, `class xyz extends Actor` should become `class xyz extends RichActor`.

After this point you can run your test suite (assuming that you have one) and everything should work as before. 

### 4. Removing the `act` Method

* In scala.actors, behavior is defined by implementing the act method. Logically, an actor is a concurrent process
  which simply executes the body of its act method, and then terminates.

* In Akka, the behavior of an actor is defined using a global message handler which processes the messages in the
  actors mailbox one by one. The message handler is a partial function which gets applied to each message.

* Patterns: code-react, loop-react, code-loop-react, code-loop-react-react, same with reactWithin

reactWithin(500) {
  case TIMEOUT =>
  case Msg =>
}

### 5. Changing the Imports and the build to Akka
At this point your code is ready to operate on Akka actors. Now we can switch the actual jar from Scala Actors to
Akka actors. After this change the compilation will fail due to different package names. We will have to change each
imported actor from scala to Akka. Other than packages all class names completely match. If there are any
special cases, text search and replace needs to be used. The table of conversions is presented below:

### 6.(Optional) Move as many actors as possible to standard Akka implementation
Now that you have migrated your code base to Akka actors your actors should run one order of magnitude faster. Also, you can start exploring available functionality of Akka. To explore all the great features of Akka acotors visit their documentation site TODO link. Ideally all the changes we have made should be ironed out to function by Akka original design (without `RichActor`). Try changing your functionality to work with standard Akka actor.    

Written and implemented by: Vojin Jovanovic and Philipp Haller

Report bugs at the [Scala Bugtracker](https://issues.scala-lang.org/ "Scala issue reporting tool"). During the RC release phase bugs will be fixed within several working days thus that would be the best time to try the migration on your application. 
