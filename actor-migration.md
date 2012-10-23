# The Scala Actors Migration Guide

## 1. Introduction

Starting with the Scala 2.10.0, the Scala actors library is being deprecated. In Scala 2.10.0 the default
actor library will be [Akka](http://akka.io) actors. The Scala actors are being deprecated because Akka actors have better performance,
their programming model prevents accidental memory leaks, provides an uniform interface for accessing both remote and local actors, 
and guides users to think about fault-handling.

To ease the difficulties of migrating from Scala actors to Akka we have provided the Actor Migration Kit (AMK). AMK consists of the code
 in the [Scala distribution](http://www.scala-lang.org/downloads), in Akka, and this document. The purpose of the document is to guide users through the migration process and explain
how to use the AMK.

The document has the following structure. In Section "Deciding on Migration" we will discuss the possibilities that a user of Scala actors has,
 and we will point out which cases can be hard to migrate. In Section "Migration Overview" we will describe the migration process and talk about
changes in the [Scala distribution](http://www.scala-lang.org/downloads) that make the migration possible. Finally, in section "Step by Step Guide for Migration to Akka" we show individual steps,
 with working examples, that user should take to migrate to Akka.

## 2. Deciding on Migration

A user of Scala actors can choose between staying with the Scala actors, and migrating to Akka. The decision depends on the code base that needs to be migrated.
 Some use cases are harder to migrate than the others. Also, some projects might not require high performance of Akka and do not have a need to move away from
 Scala actors.

In the following list we present use cases that are harder to migrate and describe what are the difficulties. Users should compare their code base with these cases
and assess weather it is better to migrate, or to stay with the Scala actors.

1. Nesting `react`/`reactWithin` calls - the message handling partial function needs to be expanded
 with additional constructs that will bring it closer to the Akka model. Although these changes can be
 complicated, the migration is possible for the arbitrary level of nesting. See Step 4 for the examples.

2. Using `receive`/`receiveWithn` methods with complex control flow - migration can be complicated since
the users need to reshape code of the `act` method. The `receive` is modeled with `react` and `andThen` on the 
message processing partial function. Simple examples are presented in Step 4.

3. Relying on termination reason and bidirectional behavior with `link` method - Scala and Akka actors have different fault-handling and actor monitoring models.
In Scala linked actors terminate if one of the linked parties terminates abnormally. If termination is tracked explicitly (by `self.trapExit`) the actor receives
the termination reason from the failed actor. This functionality can not be migrated to Akka with the AMK. The AMK allows migration only for the 
[Akka monitoring](http://doc.akka.io/docs/akka/2.0.3/general/supervision.html#What_Lifecycle_Monitoring_Means)
mechanism. Monitoring is different than linking because it is unidirectional and the termination reason is now known. If monitoring support is not enough to migrate
 the user code there are two possible workarounds:

    * Postpone the migration of linking to the last possible moment (Step 4). Then when moving to Akka create an actor hierarchy that will handle faults.

    * Make all actor failures explicit and send user defined messages for each type of failure in the actor. For example, in the master-slave configuration, 
    slave catches errors explicitly and notifies the master about the failure by sending a message containing the type of failure.

4. Usage of the `restart` method - Akka does not provide explicit restart of actors so we can not provide the smooth migration for this use-case. 
The user must change the system so there are no usages of the `restart` method.

5. Usage of method `getState` - Akka actors do not have explicit state so this functionality can not be migrated. The user code must not 
have `getState` invocations.

6. Not starting actors right after instantiation - Akka actors are automatically started when instantiated. Users will have to 
reshape their system so it starts all the actors right after their instantiation.

7. TODO Philipp (Check the explanation) Migration of `RemoteActor`s is not directly supported - remote actors will need to be changed to the Akka implementation 
in the final step of the guide.

8. Method `mailboxSize` does not exist in Akka and therefore can not be migrated. This method is seldom used and can easily be removed.

Additionally, users should be aware that concurrent code is notorious for bugs that are hard to debug and fix.
Due to differences between actor implementations it is possible that errors will appear. It is recommended
to thoroughly test the code after the migration is complete.

In case of staying with Scala actors, the users need to make a fork of the Scala actors in which the deprecation is removed and library will be maintained.
Forking and maintaining the fork of Scala actors can require significant effort in the long run. However it is less error prone than migration to Akka.

## 3. Migration Overview

### 3.1 Migration Kit
Starting with version 2.10.0 Scala actors reside inside the [Scala distribution](http://www.scala-lang.org/downloads) as a separate jar (*scala-actors.jar*), and 
the their interface is deprecated. The distribution also includes Akka actors in the *akka-actor.jar*. 
The AMK resides both in the Scala actors and in the *akka-actor.jar*. Future major releases of Scala will not contain Scala actors and the AMK.

To start the migration user needs to add the *scala-actors.jar* and the *scala-actors-migration.jar* to the build of their projects. 
Addition of *scala-actors.jar* and *scala-actors-migration.jar* enables the usage of the AMK described below.
 These artifacts reside in the [Scala Tools repository](https://oss.sonatype.org/content/groups/scala-tools/org/scala-lang/) and in the [Scala distribution](http://www.scala-lang.org/downloads).

### 3.2 Step by Step Migration
Actor Migration Kit should be used in 5 steps. Each step is designed to introduce minimal changes
to the code base and allows users to run all system tests after it. In the first four steps of the migration 
the code will use the Scala actors implementation. However, the methods and class signatures will be transformed to closely resemble Akka.
The migration kit on the Scala side introduces a new actor type (`StashingActor`) and enforces access to actors through the `ActorRef` interface.
Additionally, it enforces creation of actors through the special methods on the `ActorDSL` object. In these steps it will also be possible to migrate one
actor at a time. This will reduce the possibility of complex errors that are caused by several bugs introduced at the same time.

After the migration on the Scala side is complete the user should change import statements and change 
the library used to Akka. On the Akka side we introduce the `ActorDSL` and the `ActWithStash` which allow
 modeling of the Scala actors' `react` and life-cycle. This step migrates all actors to the Akka back-end 
 and could introduce bugs in the system. Once code is migrated to Akka,
 users will be able to use all the features of Akka.

## 3. Step by Step Guide for Migration to Akka

In this chapter we will go through 5 steps of the actor migration. After each step the code can be tested for possible errors. In the first 4
 steps one can migrate one actor at a time and test the functionality. However, the last step migrates all actors to Akka and it can be tested
only as a whole. After this step the system should have the same functionality as before, however it will use the Akka actor library.

### Step 1 - Everything as an Actor
The Scala actors library provides public access to multiple types of actors. They are organized in the class hierarchy and each subclass
provides slightly richer functionality. To make further steps of the migration easier we will first change each actor in the system to be of type `Actor`.
This migration step is straightforward since the `Actor` class is located at the bottom of the hierarchy and provides the broadest functionality. 

The Actors from the Scala library should be migrated by the the following rules:

1. `class MyServ extends Reactor[T]` -> `class MyServ extends Actor`

    Note that `Reactor` provides an additional type parameter which represents the type of the messages received. If user code uses
that information then one needs to: _i)_ apply pattern matching with explicit type, or _ii)_ do the downcast of a message from
`Any` to the type `T`.

2. `class MyServ extends ReplyReactor` -> `class MyServ extends Actor`

3. `class MyServ extends DaemonActor` -> `class MyServ extends Actor`

    To pair the functionality of the `DaemonActor` add the following line to the class definition.

        override def scheduler: IScheduler = DaemonScheduler

### Step 2 - Instantiations

In Akka, actors can be accessed only through the narrow interface named `ActorRef`. Instances of `ActorRef` can be acquired either 
by invoking an `actor` method on the `ActorDSL` object or through the `actorOf` method on an `ActorSystem` instance.
In the Scala side of AMK we provide a subset of the Akka `ActorRef` and the `ActorDSL` that enables us mimic the Akka functionality.

This step of the migration makes all accesses to actors through `ActorRef`s. First, we present how to migrate common patterns for instantiating 
Scala `Actor`s. Then we show how to overcome issues with different interfaces of `ActorRef` and `Actor`.

#### Actor Instantiation

The translation rules for actor instantiation:

1. Constructor Call Instantiation

        val myActor = new MyActor(arg1, arg2)
        myActor.start

    Should be replaced with

        ActorDSL.actor(new MyActor(arg1, arg2))

2. DSL for Creating Actors

        val myActor = actor {
           // actor definition
        }

    Should be replaced with

        val myActor = ActorDSL.actor(new Actor {
           def act() {
             // actor definition
           }
        })

3. Object extended from the `Actor` trait

        object MyActor extends Actor {
           // MyActor definition
        }
        MyActor.start

    Should be replaced with

        class MyActor extends Actor {
          // MyActor definition
        }

        object MyActor {
          val ref = ActorDSL.actor(new MyActor)
        }

   All accesses to the object `MyActor` should be replaced with accesses to the `MyActor.ref`.

Note that Akka actors are always started on instantiation. Therefore, the above translation will change the migrated system so all the actors start
 right after construction. In case actors in the migrated system are created and started at different locations and changing this can affect the behavior of the system, 
users need to change the code so actors are started right after instantiation.

#### Different Method Signatures

At this point we have changed all the actor instantiations to return `ActorRef`s, however, we are not done yet.
There are differences in the interface of `ActorRef`s and `Actor`s so we need to change the methods invoked on each migrated instance.
Unfortunately, some of the methods that Scala `Actor`s provide can not be migrated. For the following methods users need to find a workaround:

1. `getState()` - actors in Akka are managed by their supervising actors and are restarted by default.
In that scenario state of an actor is not relevant.

2. `restart()` - standard Akka actors are restarted by default after failure. This can not be paired on the Scala side.

All other `Actor` methods need to be translated to two methods that exist on the ActorRef. The translation is achieved by the rules described below. 
Note that all the rules require the following imports:

    import scala.concurrent.duration._
    import scala.actors.migration.pattern.ask
    import scala.actors.migration._
    import scala.concurrent._

1. `!!(msg: Any): Future[Any]` gets replaced with `?`.

        actor !! message ->
          val fut = respActor.?(message)(Timeout((100 * 365) days))
          Await.result(fut, Duration.Inf)

2. `!![A] (msg: Any, handler: PartialFunction[Any, A]): Future[A]` gets replaced with `?`. The handler can be extracted as a separate
function and then applied to the generated future result. The result of a handle should yield another future like
in the following example:

        val handler: PartialFunction[Any, T] =  ... // handler
        actor !! (message, handler) ->
          val fut = (respActor.?(message)(Timeout((100 * 365) days)))
          Await.result(fut.map(handler), Duration.Inf)

3. `!? (msg: Any): Any` gets replaced with `?` and explicit blocking on the returned future.

        actor !? message ->
          val res = respActor.?(message)(Timeout((100 * 365) days))
          Await.result(res, Duration.Inf)

4. `!? (msec: Long, msg: Any): Option[Any]` gets replaced with `?` and explicit blocking on the future.

        actor !? (timeout, message) ->
          val res = respActor.?(message)(Timeout(timeout milliseconds))
          val promise = Promise[Option[Any]]()
          res.onComplete(v => promise.success(v.toOption))
          Await.result(promise.future, Duration.Inf)

Public methods that are not mentioned here are declared public for purposes of the actors DSL. They can be used only
inside the actor definition so their migration is not relevant in this step.

### Step 3 - `Actor`s become `StashingActor`s

At this point all actors inherit the `Actor` trait, we instantiate actors through special factory methods,
and all actors are accessed through the `ActorRef` interface.
Now we need to change all actors to the `StashingActor` class from the AMK. This class behaves exactly the same like Scala `Actor`
but provides methods that pair the Akka behavior as well. This allows easy, step by step, migration to the Akka behavior.

To change user base to the new type of actor all actors should extend the `StashingActor` instead of the `Actor`. Apply the 
following rule:

    class MyActor extends Actor -> class MyActor extends StashingActor

After this change code will not compile. The `StashingActor` trait does not support `receive`/`receiveWithin` methods. These methods need to be replaced with usage of `react`/`reactWithin`. 
We present the transformation for two simplest scenarios: series of receives, and receive within a loop. For other scenarions users should 
devise a translation based on these two.

1. Series of `receive` methods with code before and after

        def act() = {
          // do before
          receive {
            case msg =>
              // process 1
          }
          // in between
          receive {
            case msg =>
              // process 2
          }
          // after
        }

   should be replaced with the following code

        def act() = {
          // do before
          react (({
            case msg =>
              // process 1
          }: PartialFunction[Any, Unit]).andThen { x =>
            // in between
            react (({
              case msg =>
                // process 2
            }: PartialFunction[Any, Unit]).andThen { x =>
              // after
            })
          })
        }

2. Receive inside a loop that terminates based on a condition.

        def act() = {
          var c = true
          while (c) {
            // before body
            receive {
              case msg =>
                // process
              case "exit" => 
                c = false
            }
            // after receive
          }
          // after loop
        }

   should be replaced with

        def act() = {
          var c = true
          loopWhile(c) {
            // before body
            react (({
              case msg =>
                // process
              case "exit" => 
                c = false
            }: PartialFunction[Any, Unit]).andThen { x =>
              // after receive
              if (c == false) {
                // after loop
              }
            })
          }
        }


Additionally, to make the code compile, users must add the `override` keyword before the `act` method, and to create
the empty `receive` method in the code. Method `act` needs to be overriden since its implementation in `StashingActor` 
mimics the message processing loop of Akka. The changes are shown in the following example:

    class MyActor extends StashingActor {

       // dummy receive method (not used for now)
       def receive = {case x => x}

       override def act() {
         // old code with methods receive changed to react.
       }
    }

After this point user can run the test suite and the whole system should behave as before. The `StashingActor` and `Actor` use the same infrastructure so the system 
should behave exactly the same.

### Step 4 - Removing the `act` Method

In this section we describe how to remove the `act` method from `StashingActor`s and how to
change the methods used in the `StashingActor` to resemble Akka. Since this step can be complex, it is recommended 
to do changes one actor at a time. In Scala, actor's behavior is defined by implementing the act method. Logically, an actor is a concurrent process
which executes the body of its `act` method, and then terminates. In Akka, the behavior is defined by using a global message
handler which processes the messages in the actors mailbox one by one. The message handler is a partial function, returned by the `receive` method, 
which gets applied to each message.

Since the behavior of Akka methods in the `StashingActor` depends on the removal of the `act` we have to do that first. Then we will give the translation 
rules for translating individual methods of the Scala `Actor`.

#### Removal of `act`

In the following list we present the translation rules for common message processing patterns. This list is not 
exhausive and it covers only the common patterns. However, users can migrate more complex `act` methods to Akka by looking
 at existing translation rules and extending them for more complex situations.

1. If there is any code in the `act` method that is being executed before the first `loop` with `react` that code
should be moved to the `preStart` method.

        def act() {
          // some code
          loop {
            react { ... }
          }
        }

   should be replaced with

        def preStart() {
          // some code
        }

        def act() {
          loop {
            react{ ... }
          }
        }

   This rule should be used in other patterns as well if there is code before the first react.

2. When `act` is in the form of the simple `loop` with a nested react use the following pattern.

        def act() = {
          loop {
            react {
              // body
            }
          }
        }

   should be replaced with

        def receive = {
          // body
        }

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

   should be replaced with

        def receive = {
          case x: Int =>
            // do task
            if (x == 42) {
              context.stop(self)
            }
        }

4. When `act` contains nested reacts use the following rule:

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

   should be replaced with

        def receive = {
          case x: Int =>
            // do task
            if (x == 42) {
              // after react
              context.stop(self)
            } else {
              context.become(({
                case y: String =>
                // do nested task
              }: Receive).andThen(x => {
                unstashAll()
                context.unbecome()
             }).orElse { case x => stash(x) })
            }
        }

5. In case `reactWithin` method is used use the following translation rule.

        loop {
          reactWithin(t) {
            case TIMEOUT => // timeout processing code
            case msg => // message processing code
          }
        }

   should be replaced with
      import scala.concurrent.duration._

      context.setReceiveTimeout(t millisecond)
      def receive = {
        case ReceiveTimeout => // timeout processing code
        case msg => // message processing code
      }

6. Exception handling is done in a different way in Akka. To mimic Scala actors behavior apply the following rule

        def act() = {
          loop {
            react {
              case msg =>
              // work that can fail
            }
          }
        }

        override def exceptionHandler = {
          case x: Exception => println("got exception")
        }

   should be replaced with

        def receive = PFCatch({
          case msg =>
            // work that can fail
        }, { case x: Exception => println("got exception") })

   where `PFCatch` is defined as

        class PFCatch(f: PartialFunction[Any, Unit],
          handler: PartialFunction[Exception, Unit])
          extends PartialFunction[Any, Unit] {

          def apply(x: Any) = {
            try {
              f(x)
            } catch {
              case e: Exception if handler.isDefinedAt(e) => 
                handler(e)
            }
          }

          def isDefinedAt(x: Any) = f.isDefinedAt(x)
        }

        object PFCatch {
          def apply(f: PartialFunction[Any, Unit],
            handler: PartialFunction[Exception, Unit]) = 
              new PFCatch(f, handler)
        }

   `PFCatch` is not included in the AMK as it can stay as the permanent feature in the migrated code
   and the AMK will be removed with the next major release. Once the whole migration is complete fault-handling
    can also be converted to the Akka [supervision](http://doc.akka.io/docs/akka/2.1.0/general/supervision.html#What_Supervision_Means).



#### Changing `Actor` Methods

After we have removed the `act` method we should rename the methods that do not exist in Akka but have similar functionality. In the following list we present 
the list of differences and their translation:

1. `exit()`/`exit(reason)` - should be replaced with `context.stop(self)`

2. `receiver()` - should be replaced with `self`

3. `reply(msg)` - should be replaced with `sender ! msg`

4. `link(actor)` - In Akka, linking of actors is done partially by [supervision](http://doc.akka.io/docs/akka/2.1.0/general/supervision.html#What_Supervision_Means)
and partially by [actor monitoring](http://doc.akka.io/docs/akka/2.1.0/general/supervision.html#What_Lifecycle_Monitoring_Means). In the AMK we support
only the monitoring method so the complete Scala functionality can not be migrated.

   The difference between linking and watching is that watching actors always receive the termination notification.
However, instead of matching on the Scala `Exit` message that contains the reason of termination the Akka watching 
returns the `Terminated(a: ActorRef)` message that contains only the `ActorRef`. The functionality of getting the reason
 for termination is not supported by the migration. It can be done in Akka, after the Step 4, by organizing the actors in a supervision hierarchy.

   If the actor that is watching does not match the `Terminated` message, and this message arrives, it will be terminated with the `DeathPactException`.
Note that this will happen even when the watched actor terminated normally. In Scala linked actors terminate, with the same termination reason, only if
one of the actors terminates abnormally.

   If the system can not be migrated solely with watching the user has the two alternatives described in "Deciding on Migration".

   NOTE: There is another subtle difference between Scala and Akka actors. In Scala, `link`/`watch` to the already dead actor will not have affect.
In Akka, watching the already dead actor will result in sending the `Terminated` message. This can give unexpected behavior in the Step 5 of the migration guide.

### Step 5 - Moving to the Akka Back-end

At this point user code is ready to operate on Akka actors. Now we can switch the actors library from Scala to
Akka actors. In order to do this configure the build to exclude the `scala-actors.jar` and the `scala-actors-migration.jar`
 and add the *akka-actor.jar*. The AMK is built to work only with Akka actors version 2.1 which are included in the [Scala distribution](http://www.scala-lang.org/downloads)
  and can be configured by these [instructions](http://doc.akka.io/docs/akka/current/intro/getting-started.html#Using_a_build_tool). During
  the RC phase the Akka RC number should match the Scala one (e.g. Scala 2.10.0-RC2 runs with Akka 2.1-RC2).

After this change the compilation will fail due to different package names and some . We will have to change each imported actor 
from scala to Akka. Following is the non-exhaustive list of package names that need to be changed:
    * `scala.actors._` -> `akka.actor._`
    * `scala.actors.migration.StashingActor` -> `akka.actor.ActorDSL.ActWithStash`
    * `scala.actors.migration.pattern.ask` -> `akka.pattern.ask`
    * `scala.actors.migration.Timeout` -> `akka.util.Timeout`

Then there is a slight difference in the declaration of the `StashingActor` in Scala and Akka. All declarations of
the `StashingActor` should be replaced with `ActWithStash`. This transformation can be achieved by simple text search and replace. 
Also, method declarations `def receive =` in `ActWithStash` should be prepended with override.

In the Scala actors the `stash` method needs a message as a parameter. For example:

    def receive = {
      ...
      case x => stash(x)
    }

In Akka only the currently processed message can be stashed. Therefore replace the above example with:

    def receive = {
      ...
      case x => stash
    }

#### Adding Actor Systems

The Akka actors are organized in [Actor systems](http://doc.akka.io/docs/akka/2.1/general/actor-systems.html). Each actor that is instantiated
must belong to one `ActorSystem`. To achieve this add an `ActorSystem` instance to each actor instatiation call as a first argument. The following example 
shows the transformation.

To achieve this transformation you need to have an actor system instantiated. For example:

    val system = ActorSystem("migration-system")

Then apply the following transformation:

    ActorDSL.actor(...) -> ActorDSL.actor(system)(...)

Finally, Scala programs are terminating when all the non-daemon threads and actors finish. With Akka the program ends when all the non-daemon threads finish and all actor systems are shut down.
 Actor systems need to be explicitly terminated before the program can exit. This is achieved by invoking the `shutdown` method on an Actor system.

#### Remote Actors
TODO Philipp: Paragraph about remoting.
  alive(port: Int): Unit - starts the remote service -> this done by configuration in Akka
  register(name, actor) - passing the name to the actorOf

All of the code snippets presented in this document can be found in the [Scala test suite](http://github.com/scala/scala/tree/master/test/files/jvm) as test files with the prefix `actmig`.

This document and the Actor Migration Kit were designed and implemented by: Vojin Jovanovic and Philipp Haller

If you find any issues or rough edges please report them at the [Scala Bugtracker](https://issues.scala-lang.org/ "Scala issue reporting tool").
During the RC release cycles bugs will be fixed within several working days thus that would be the best time to try the AMK on an application.
