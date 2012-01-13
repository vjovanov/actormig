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

### Instantiation

* First change all actors (as they are) to be instantiated with actorOf
* Then fix the code to work with ActorRefs. Each public method on the actor trait needs to be addressed. 

### Changing to RichActor
All actors, reactors, channels and reply reactors should be changed to RichActor

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
