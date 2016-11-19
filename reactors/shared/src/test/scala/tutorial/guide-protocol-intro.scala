/*!md
---
layout: tutorial
title: Introduction to Protocols
topic: reactors
logoname: reactress-mini-logo-flat.png
projectname: Reactors.IO
homepage: http://reactors.io
permalink: /reactors/protocol-intro/index.html
pagenum: 1
pagetot: 40
section: guide-protocol
---
!*/
package tutorial

import org.scalatest._
import scala.collection._
import scala.concurrent.ExecutionContext
import scala.concurrent.Promise



/*!md
## Introduction to Protocols

// TODO
!*/
class GuideServerProtocol extends AsyncFunSuite {

  implicit override def executionContext = ExecutionContext.Implicits.global

  test("router master-workers") {
    val done = Promise[String]()
    def println(x: String) = done.success(x)

    /*!md
    Before we start, we import the contents of the `io.reactors` and the
    `io.reactors.protocol` packages.
    We then create a default reactor system:
    !*/

    /*!begin-code!*/
    import io.reactors._
    import io.reactors.protocol._

    val system = ReactorSystem.default("test-system")
    /*!end-code!*/

    //val server =

    done.future.map(t => assert(t == "HELLO"))
  }
}
