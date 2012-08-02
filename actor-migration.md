# Scala Actors Migration Guide

## 1. Introduction

Starting with Scala 2.10.0, the Scala standard library is deprecating Scala actors. In Scala 2.10 the default 
actor library will be [Akka](http://akka.io). We deprecate Scala actors because Akka Actors have better performance,
their programming model prevents accidental memory leaks and guides users to think about fault-handling.
Moreover, Akka provides an uniform interface for accessing both remote and local actors.

To ease the difficulties of migrating from Scala Actors to Akka we have provided the Actor Migration Kit.
The purpose of this document is to guide users through the migration process and explain how to use the provided migration kit.

In Section 2. we will discuss the options that a Scala Actors user has.
In Section "Migration Overview" we will describe the migration process and talk about
what is being changed in the Scala distribution and Scala actors code base in order to support it.
Finally in section "Step by Step Guide for Migration to Akka" we show individual steps, with working examples,
that one needs to take to convert the code to the version compatible with Akka.

## 2. Deciding on Migration

Each user of Scala actors can choose between staying with the current design and migrating to the Akka actors.
In this section we explain which cases will require non trivial code changes for the migration.
By comparing their code base with these rules users can assess weather the migration is necessary.

One of the fundamental differences between Scala and Akka actors is exhaustivity of `react`/`receive` methods. 
In Scala it is possible to write non-exhaustive partial functions for `react`/`receive`, and `react`/`receive` methods 
can be nested to the arbitrary level of depth. Also, linking is working in a bidirectional way while in Akka it is implemented
 unidirectionally - parent child relationship.

In the following list we present use cases that are not straight forward to migrate:

1. Nesting `react`/`reactWithin` calls - the message handling partial function needs to be expanded
 with additional constructs that will bring it closer to the Akka model. Although these changes can be
 confusing, the migration is possible for the arbitrary level of nesting.

2. Using `receive`/`receiveWithn` methods with complex control flow - migration can be confusing since 
the user must reshape the basic blocks of his code. Simple examples are presented in Phase 4.

3. Relying on `link`s bidirectional behavior - actors need to explicitly link to each other in both directions.

4. Usage of the `restart` method - Akka does not provide explicit restart of actors so we can not provide the smooth migration for this use-case. 
The user must reshape the system so there are no usages of the `restart` method.

5. Usage of method `getState` - Akka actors do not have explicit state so this functionality can not be migrated. The user code must not 
have `getState` invocations.

6. Not starting actors after instantiation - Akka actors are automatically started when instantiated. Users will have to 
reshape their system so it starts all the actors right after their instantiation.

7. TODO Philipp (Check the explanation) Migration of `RemoteActor`s is not directly supported - remote actors will need to be changed to the Akka implementation 
in the last step of the guide. This change can not be done in the step by step fashion. If user's system uses may remote actors the migration
can be difficult.


Users should be aware that concurrent code is notorious for bugs that are hard to find and tedious to fix.
Due to differences between actor implementations it is possible that bugs will appear. It is advised
that code is thoroughly tested after the migration is complete.

## 2. Migration Overview

### 2.1 Migration Kit
In Scala 2.10 Scala Actors will be inside the Scala distribution as the separate jar (`scala-actors.jar`), and 
all public actor classes will be deprecated. Together with the distribution we will include Akka Actors in file `akka-actors.jar`.
Future major releases of Scala will not contain Scala actors as the part of the distribution. The Actor Migration Kit resides both in the 
Scala distribution (`scala-actors-migration.jar`) and in the Akka Actors jar.

### 2.2 Migration Phases
Actor Migration Kit should be used in 5 phases. Each phase is designed to introduce minimal changes
to the code base and, allows the user run all system tests. In the first four phases of the migration 
the code will use Scala actors implementation, but the methods used and class signatures will be transformed
to the form that closely resembles Akka actors. The migration kit on the Scala side introduces new actor type 
(`StashingActor`) and enforces access to actors through `ActorRef` interface. It also enforces creation of actors
through the special methods on the `MigrationSystem` object. In these phases it will also be possible to migrate one
actor at a time. This will reduce the possibility of complex errors that are caused by several bugs introduced at the same time.

After the migration on the Scala side is complete the user should change import statements and change 
the library used to Akka. Akka will provide a new actor type that allows migration of Scala actors.
This step migrates all actors to the Akka back-end and it could create bugs in the system. On the Akka side we
introduce the `MigrationSystem` and the `Actor with Statsh` which allow modeling of Scala's actors `react`
and the default actor life-cycle. Once code is migrated to Akka users will be able to use all the features 
of Akka.

## 3. Step by Step Guide for Migration to Akka

In this chapter we will go through 5 phases of the actor migration. After each phase you can test your
code, to check if everything is still OK in the system. In the first 4 phases one can migrate one actor at a time and test the 
functionality. However, the last phase migrates all actors to Akka and it can be tested only as a whole. After this phase your system
should have the same functionality as before, but on Akka code base.

### Phase 1 - Everything as an Actor
The Scala Actors library provides public access to multiple types of actors. They are organized in the class hierarchy and each subclass
provides slightly richer functionality. To make migration easier first phase will be to change each actor in your system to be of type `Actor`.
This migration step is straight forward since the `Actor` class is located at the bottom of the hierarchy and 
provides the broadest functionality. 

The Scala Actors library provides following actors in its hierarchy:
 
1. `Reactor` - migrate by changing your class definition from `class MyService extends Reactor[T]` to `class MyReactor extends Actor`. 
Compared to the `Actor`, `Reactor` provides additional type parameter which marks the type of the messages received. If your code uses
that information then you might need to: _i)_ apply pattern matching with explicit type or, _ii)_ do the downcast of your message from
`Any` to the type `T`.

2. `ReplyReactor` - migrate by changing your class definition from `class MyService extends ReplyReactor` to `class MyService extends Actor`

3. `Actor` - stays the same as before

4. `DaemonActor` - migrate by changing your class definition from `class MyService extends DaemonActor` to `class MyService extends Actor`. To pair the 
functionality of the `DaemonActor` add the following line to the class definition.

        override def scheduler: IScheduler = DaemonScheduler

### Phase 2 - Instantiations

In Akka, actors can be accessed only through the narrow interface named `ActorRef`. Instances of `ActorRef` are acquired by
instantiating actors within the by name block that is passed to the `actorOf` method of the `MigrationSystem` object.
To ease the migration we have added the subset of the Akka `ActorRef` and the `MigrationSystem`. Migration to `ActorRef`s will be the next step in the migration.
We will present how to migrate most common patterns of Scala `Actor` instantiation and the we will show how to overcome issues when method signatures
of `ActorRef` and `Actor` do not align. Since this phase requires two steps we recommend to do it one actor at a time.

1. Constructor Call Instantiation

        val myActor = new MyActor(arg1, arg2)

    Should be replaced with:

        val myActor = MigrationSystem.actorOf(new MyActor(arg1, arg2))

    In case constructor is called with no arguments the special version of method `actorOf` can be used.

        val myActor = MigrationSystem.actorOf[MigrationActorNoArgs]

2. DSL for Creating Actors

        val myActor = actor { 
           // actor definition
        }

    Should be replaced with:

        val myActor = MigrationSystem.actorOf(new Actor {
           def act() {
             // actor definition
           }
        })

3. Object Extended from Actor

        object MyActor extends Actor { 
           // MyActor definition 
        }

    Should be replaced with:

        object MyActor {
          val ref = MigrationSystem.actorOf[MyActor]
        }

        class MyActor extends Actor {
          // Same MyActor definition
        }

All accesses to the object `MyActor` should be replaced with accesses to the `MyActor.ref`.


TODO cleanup this paragraph and move to apropriate location.
Note that Akka actors are started automatically on instantiation and that the above translation will start all the actors when instantiated.
In case actors in your system are created and started at different locations you will need to find a workaround and make actors start when instantiated.

#### Different Method Signatures

At this point we have changed all the actor instantiations to return `ActorRef`s, however, we are not done yet.
There are differences in the usage of `ActorRef`s and `Actor`s and we need to change the methods invoked of each migrated instance.
Unfortunately, some of the methods that Scala `Actor`s provide can not be migrated. For the following methods you will have to find a workaround:

1. `getState()` - actors in Akka are managed by their supervising actors and are restarted by default.
In that scenario state of an actor is not relevant.
 
2. `restart()` - standard Akka actors are restarted by default after failure. This can not be paired on the Scala side.

For other methods we provide the simple translation scheme:

1. `!!(msg: Any): Future[Any]` - should be replaced with invocation of method 
`?(message: Any)(implicit timeout: Timeout): Future[Any]`. For example, `actor !! message` should be
replaced with `val fut = actor ? message; fut()`.

2. `!![A] (msg: Any, handler: PartialFunction[Any, A]): Future[A]` - should be replaced with invocation
of method `?(message: Any)(implicit timeout: Timeout): Future[Any]`. The handler can be extracted as a separate
function and then applied to the generated future result. The result of handle should yield another future like
in the following example:

        val handler: PartialFunction[Any, T] =  ... // code of the handler
        val fut = (respActor ? msg)
        Futures.future{ handler.apply(fut2()) }

3. `!? (msg: Any): Any` - should be replaced with `?(message: Any)(implicit timeout: Timeout): Future[Any]`
and explicit blocking on the future. For example,`actor !? message` should be replaced with
`Some((respActor.?(msg2)(Duration.Inf))())`.

4. `!? (msec: Long, msg: Any): Option[Any]` - should be replaced with
`?(message: Any)(implicit timeout: Timeout): Future[Any]` and explicit blocking on the future.
For example,`actor !? (timeout, message)` should be replaced with `Some((actor.?(message)(timeout))())`.  

Public methods that are not mentioned here are declared public for purposes of the actors DSL. They can be used only
inside the actor definition so their migration is not relevant.

### Phase 3 - `Actor`s become `StashingActor`s

At this point we instantiate actors through special factory methods, all actors to use the same `Actor` interface
and all actors are accessed through `ActorRef` interface.
Now we need to change all actors to the`StashingActor` class. This class behaves exactly the same like Scala `Actor`
but provides methods that allow easy, step by step, migration to the Akka behavior.
To change your code base to the new type of actor all your actors should extend `StashingActor`. 
Each, `class MyActor extends Actor` should become `class MyActor extends StashingActor`.

The `StashingActor` does not support `receive`/`receiveWithin` methods. These methods need to be replaced with usage of `react`/`reactWithin`. 
We will present the transformation only for two simplest scenarios: _i)_ single receive with code before and after _ii)_ receive within a loop.

TODO

To make the code compile you will have to add `override` before the `act` method and to create
the empty `receive` method in the code like in the following example. 

    def AActor extends StashingActor {

       // dummy receive method (not used for now)
       def receive = {case x => x}

       override def act() {
         // old code with methods receive changed to react.
       }
    }

Method `act` needs to be overriden since its implementation in `StashingActor` contains the implementation that mimics Akka actors.

After this point you can run your test suite (assuming that you have one) and the whole system should behave as before.
This is due to the fact that methods in `StashingActor` and `Actor` use the same architecture. 

### Phase 4 - Removing the `act` Method

`StashingActor` contains methods from both Akka and Scala. Moreover, its default `act` implementation processes 
messages the same way as Akka processes methods. In this section we describe how to remove the `act` method and how to
change the methods used in `StashingActor` to resemble Akka. It is recommended to do this change one actor at a time. 
In `scala.actors`, behavior is defined by implementing the act method. Logically, an actor is a concurrent process
which simply executes the body of its act method, and then terminates.
On the other side in Akka, the behavior is defined by using a global message handler which processes the messages in the
actors mailbox one by one. The message handler is a partial function which gets applied to each message.

Since all Akka behavior depends on the removal of the `act` method we will first do that step and then we will change
the individual methods. In removal of `act` we will describe the most common usage patterns. For more complex examples
users will have to figure out how to rewrite code in terms of Akka abstractions.

#### Removal of Act

First we will explain how to migrate most often patterns used in `act` method and then we will explain how to change
individual method names.

1. If there is any code in the `act` method that is being executed before the first `loop` with `react` that code
should be moved to the `preStart` method.

    def act() {
       // some code
       loop {
         react { ... }
       }
    }

Should be replaced with

    def preStart() {
       // some code
    }

    def act() {
      loop {
        react{ ... }
      }
    }

This rule applies for all following patterns.

2. When `act` is in the form of the simple `loop` with a nested react use the following pattern.
TODO loop-react

3. When `act` contains a `loopWhile` construct use the following translation.
    def act() = {
      loopWhile(c) {
        react {
          case x: Int =>
            // do task
            if (x == 42) {
              c = false
            }
        }
      }
    }

Should be replaced with
  def receive = {
     case x: Int =>
       // do task
       if (x == 42) {
         context.stop(self)
       }
  }

4. When `act` contains nested reacts use the following rule.
  def act() = {
      var c = true
      loopWhile(c) {
        react {
          case x: Int =>
            // do task
            if (x == 42) {
              c = false
            } else {
              react {
                case y: String =>
                  // do nested task
              }
            }
            // after react
        }
      }
    }

Should be replaced with
  def receive = {
    case x: Int =>
      // do task
      if (x == 42) {
        context.stop(self)
      } else {
        context.become(({
          case y: String =>
            // do nested task
        }: Receive).andThen(x => {
          unstashAll()
          context.unbecome()
        }).orElse { case x => stash() })
  }

5. reactWithin
  reactWithin(500) {
    case TIMEOUT =>
    case Msg =>
  }

  // set the timeout for the Akka actor 

6. Exception handling TODO

7. Linking of actors TODO

### Phase 5 - Moving to the Akka Back-end

At this point your code is ready to operate on Akka actors. Now we can switch the actual jar from Scala Actors to
Akka actors. After this change the compilation will fail due to different package names. We will have to change each
imported actor from scala to Akka. Other than packages all class names completely match. If there are any
special cases, text search and replace needs to be used. The table of conversions is presented below:


TODO Philipp: Paragraph about remoting. Here you replace Scala remote actors with akka ones.
  alive(port: Int): Unit - starts the remote service -> this done by configuration in Akka
  register(name, actor) - passing the name to the actorOf
TODO point to the test cases.

Written and implemented by: Vojin Jovanovic and Philipp Haller

Report bugs at the [Scala Bugtracker](https://issues.scala-lang.org/ "Scala issue reporting tool").
During the RC release phase bugs will be fixed within several working days thus that would be the 
best time to try the migration on your application. 
