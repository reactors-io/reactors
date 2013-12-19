package org.reactress
package test.signal



import org.scalatest._
import org.scalatest.matchers.ShouldMatchers



class ReactiveSpec extends FlatSpec with ShouldMatchers {

  "A reactive" should "GC a single dependency" in {
    var signOfLife = false
    val emitter = new Reactive.Emitter[Int]
    emitter onTick {
      signOfLife = true
    }

    sys.runtime.gc()

    emitter += 1

    signOfLife should equal (false)
    emitter.demux should equal (null)
  }

  it should "GC several dependencies" in {
    val several = 4
    var signsOfLife = Array.fill(several)(false)
    val emitter = new Reactive.Emitter[Int]
    for (i <- 0 until several) emitter onTick {
      signsOfLife(i) = true
    }

    sys.runtime.gc()

    emitter += 1

    for (i <- 0 until several) signsOfLife(i) should equal (false)
  }

}
