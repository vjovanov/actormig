# Scala Actors Migration Guide 

## Introduction
* Akka actors have better performance, API and support for distributed programming than scala actors. 
* Scala library is officially switching to Akka Actors.  
* In release 2.10 scala actors will still be in the standard library but in deprecated form. 
* As of 2.11 standard library will be shipped with akka actors as the default implementation of actors
* This document is supposed to ease the code migration from scala actors to akka actors

In section "Staying With Scala Actors" we will discuss the option of staying with the existing implementation of actors. Then in section "Migration Overview" we will describe the migration process and talk about what is being changed in the Scala distribution and Scala actors code base in order to support it . 
Then in section "Step by Step Guide for Migration to Akka" we show individual steps, with working examples, that one needs to take to convert the code to the version compatible with Akka. 

## Staying With Scala Actors
* Scala actors will be moved to scalax. Every project that wants to keep using them can switch to that implementation by following guidelines in this section.
* Explanation for SBT, ANT and manual builds.
* Check the policy of scalax !!

## Migration Overview

* Scala actors introduce new actor type that resembles the actors in akka.
* Scala actors get all transformed into a new scala actor. Then the system is tested. 
* All actors are then moved to Akka actor by simply changing the import statements.
* All actors are accessed through ActorRef interfaces.
* Scala actors are evicted from the standard library but preserved in the distribution???? Is this true? 
* Are Akka actors placed in the distribution or they need to be imported separately??? 

### Inheritance of Actor, Reactor and ReplyReactor 

## Step by Step Guide for Migration to Akka

In this chapter we will go through 5 phases of actor migration. After each phase you should be able to run and test your code. After completion of the last phase you should be able to have exactly the same functionality on Akka code base. 

### 1. Everything as an Actor
Scala Actors provide public access to multiple types of actors. They are organized in the hierarchy and each subclass provides slight richer functionality. To make migration easier first step will be to change each type of actor that is used in the project to actual class `Actor`. This migration step should not be complicated since the `Actor` class is located at the bottom of the hierarchy and provides broadest functionality. 

Scala Actors provide following actors in their hierarchy:
 
1. `Reactor` - migrate by changing your class definition from `class xyz extends Reactor[T]` to `class xyz extends Actor`. Compared to the `Actor`, `Reactor` provides additional type parameter which marks the type of the messages received. If your code uses that information than you might need to *i)* apply pattern matching with explicit type or *ii)* do the downcast from `Any` to the type of the message.  

2. `ReplyReactor` - migrate by changing your class definition from `class xyz extends ReplyReactor` to `class xyz extends Actor`

3. `Actor` - stays the same

4. `DaemonActor` - TODO daemon dispatcher.  


### 2. Instantiation

In Akka actors can be accessed only through the narrow interface named `ActorRef`. Instances of `ActorRef` are acquired by instantiating actors only within the special block that is passed to the `actorOf` method of `ActorSystem` object. To ease the migration we have added the subset of Akka `ActorRef` and `ActorSystem`.
Migration to `ActorRef`s will be the next step in migration. We will present how to migrate most common patterns of Scala `Actor` instantiation and the we will show how to overcome issues when method signatures of `ActorRef` and `Actor` do not align.  

1. Constructor Call Instantiation
 
        val migActor = new MigratingActor(arg1, arg2) 

    Should be replaced with: 
 
        val migActor = ActorSystem.actorOf(new MigrationActor(arg1, arg2))
 
    In case constructor is called with no arguments the special version of method `actorOf` can be used.
 
        val migActor = ActorSystem.actorOf[MigrationActorNoArgs]
 
2. DSL for Creating Actors

        val migActor = actor { 
           // actor definition
        }

    Should be replace with: 

        val migActor = ActorSystem.actorOf(actor {
           // actor definition
        })

3. Object Extended from Actor

        object MigrationActor extends Actor { 
           // MigrationActor definition 
        }
    
    Should be replaced with:

        object MigrationActor {
          val ref = ActorSystem.actorOf[MigrationActor]
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

#### ActorRefs and Pattern Matching

If your actors were defined as an object or a case class and you were pattern matching against them you should not convert pattern matching to `ActorRef`s. In the following steps we will change abstractions where pattern matching was needed.

### 3. Changing to RichActor

At this point we have changed all actors to use the same Actor interface and made them be created through special factory methods and accessed through `ActorRef` interface. Now we need to change all actors to the `RichActor` class. This class behaves exactly the same like Scala `Actor` but provides methods that allow easy, step by step, migration to Akka behavior.
To change your code base to the new type of actor all your actors should extend `RichActor`. Each, `class xyz extends Actor` should become `class xyz extends RichActor`.

After this point you can run your test suite (assuming that you have one) and everything should work as before. 

### 4. Act Method
* Changing receive to cps based react
* Warning about the continuation of react
* Changing loop combinators to standard loops. 

#### Linking and Unlinking 
* Conversion to death watch and DeathPact from Akka

#### Fault Handling 
In Scala Actors actor local fault handling was done using the method.

### 5. Changing the Imports and the build to Akka 
At this point your code is ready to operate on Akka actors. Now we can switch the actual jar from Scala Actors to Akka actors. After this change the compilation will fail due to different package names. We will have to change each imported actor from scala to Akka. Other than packages all class names completely match. To ease he complexity we provide the simple `bash` script (TODO link) that will change simple instances of import statements. If there are any special cases, text search and replace needs to be used. The table of conversions is presented below:

1. TODO
 
TODO write the script for changing imports if there are no corner cases.
 
### 6.(Optional) Move as many actors as possible to standard Akka implementation
Now that you have migrated your code base to Akka actors your actors should run one order of magnitude faster. Also, you can start exploring available functionality of Akka. To explore all the great features of Akka acotors visit their documentation site TODO link. Ideally all the changes we have made should be ironed out to function by Akka original design (without `RichActor`). Try changing your functionality to work with standard Akka actor.    

Written and implemented by: Vojin Jovanovic and Philipp Haller

Report bugs at [Scala Bugtracker](https://issues.scala-lang.org/ "Scala issue reporting tool"). During the RC release phase bugs will be fixed within several working days thus that would be the best time to try the migration on your application. 