/*!md
---
layout: tutorial
title: What are Reactors?
topic: reactors
logoname: reactress-mini-logo-flat.png
projectname: Reactors.IO
homepage: http://reactors.io
permalink: /reactors/why-reactors/index.html
pagenum: 1
pagetot: 40
section: guide-intro
---
!*/
package tutorial



import io.reactors._
import org.scalatest._
import scala.concurrent.Promise
import scala.concurrent.ExecutionContext



/*!md
## What are Reactors?

**Reactors** is a foundational framework for concurrent and distributed systems.
It allows creating concurrent and distributed applications more easily,
by providing correct, robust and composable programming abstractions.
Based on the *reactor model* for distributed programming,
Reactors provide means to write location-transparent programs,
that can be subdivided into modular components.
At the same time, Reactors 

You can download Reactors from
[http://reactors.io/download/](http://reactors.io/download/).


## Why Reactors?

Writing concurrent and distributed programs is hard.
Ensuring correctness, scalability and fault-tolerance is even harder.
There are many reasons why this is the case,
and below we list some of them:

- First of all, most concurrent and distributed computations are by nature
  non-deterministic. This non-determinism is not a consequence of poor programming
  abstractions, but is inherent in systems that need to react to external events.
- Data races are a characteristic of shared-memory multicore systems.
  Combined with inherent non-determinism, these lead to subtle bugs that are hard to
  detect or reproduce.
- Random faults, network outages, or interruptions present in distributed programming
  compromise correctness and robustness of distributed systems.
- Shared-memory programs do not work in distributed environments,
  and existing shared-memory programs are not easily ported to a distributed setup.
- It is extremely hard to correctly compose concurrent and distributed programs.
  Correctness of specific components is no guarantee for global program correctness
  when those components are used together.

The consequence of all this is that concurrent and distributed programming are
costly and hard to get right.
It is even an established practice in many companies
to avoid multi-threaded code whenever possible.

There are frameworks out there that try to address the aforementioned problems
with concurrent and distributed programming.
While in some cases these issues are partially addressed by some existing frameworks,
Reactors go a step further.
In particular, the Reactors framework is based on the following:

- Location-transparent reactors, lightweight entities that execute concurrently with
  each other, but are internally always single-threaded,
  and can be ported from a single machine to a distributed setting.
- Asynchronous first-class event streams that can be reasoned about
  in a declarative, functional manner, and are the basis for composing components.
- Channels that can be shared between reactors, and are used to asynchronously
  send events.

These three unique abstractions are the core prerequisite
for building powerful distributed computing abstractions.
Most other utilities in the Reactors framework are built in terms of reactors,
channels and event streams.
!*/
class WhyReactors extends AsyncFunSuite {
  implicit override def executionContext = ExecutionContext.Implicits.global
}
