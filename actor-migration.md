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
* All actors are then moved to akka actor by simply changing the import statements.
* All actors are accessed through ActorRef interfaces.
* Scala actors are evicted from the standard library but preserved in the distribution???? Is this true? 
* Are Akka actors placed in the distribution or they need to be imported separately??? 

### Inheritance of Actor, Reactor and ReplyReactor 

### Introduction of CPS React

### Replacement explanation for each method



## Step by Step Guide for Migration to Akka

### Everything as an Actor
 Scala Actors have the whole hierarchy of actor classes that can be extended. TODO List them
 they all need to be replaced with an Actor. This will be straight forward since actor is subtype of all of them and provides only additional functionality. 


### Instantiation

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

We have changed all the instantiations of actors to return `ActorRef`s but we are not done yet. Since there are differences in usage of `ActorRefs` and `Actors` we will show how to migrate method usages. Unfortunately, some of the methods that exist on `Actor` can not be migrated. For the following methods you will have to find a way around them in your code:

1. `getState()` - actors in Akka are managed by their supervising actors and are restarted by default. In that scenario state of an actor is not relevant.
 
2. `restart()` - standard Akka actors are restarted by default after failure. TODO. Explain how to migrate this functionality.

For other methods we provide simple translation scheme:

1. `!!(msg: Any): Future[Any]` - should be replaced with method `?(message: Any)(implicit timeout: Timeout): Future[Any]`. For example, `actor !! message` should be replaced with `actor ? message'.
  
2. `!![A] (msg: Any, handler: PartialFunction[Any, A]): Future[A]` - TODO
 
3. `!? (msg: Any): Any` - should be replaced with `?(message: Any)(implicit timeout: Timeout): Future[Any]` and explicit blocking on the future. For example,`actor !? message` should be replaced with `Some((actor ? message)())'.
 
4. `!? (msec: Long, msg: Any): Option[Any]` - should be replaced with `?(message: Any)(implicit timeout: Timeout): Future[Any]` and explicit blocking on the future. For example,`actor !? message` should be replaced with `Some((actor ?(message)(msec))())'.  
    
Other public methods are public just for purposes of actors DSL and can actually be used inside the actor definition.
    
#### ActorRefs and Patern Matching

### Changing to RichActor
* All actors should be changed to `RichActor`

### Act Method
* Changing receive to cps based react
* Warning about the continuation of react
* Changing loop combinators to standard loops. 

### Linking and Unlinking 
* Conversion to death watch and DeathPact from Akka

### Fault Handling 
* Changing to fault handling in actor hierarchies

### Changing the Imports and the build to Akka 

### Move as many actors as possible to standard Akka implementation 
