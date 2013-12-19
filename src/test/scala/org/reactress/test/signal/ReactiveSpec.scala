package org.reactress
package test.signal



import org.scalatest._
import org.scalatest.matchers.ShouldMatchers



class ReactiveSpec extends FlatSpec with ShouldMatchers {

  def testGC(num: Int)(afterCheck: Reactive.Emitter[Int] => Boolean) {
    var signsOfLife = Array.fill(num)(false)
    val emitter = new Reactive.Emitter[Int]
    for (i <- 0 until num) emitter onTick {
      signsOfLife(i) = true
    }

    sys.runtime.gc()

    emitter += 1

    for (i <- 0 until num) signsOfLife(i) should equal (false)
    afterCheck(emitter) should equal (true)
  }

  "A reactive" should "GC a single dependency" in {
    testGC(1) {
      _.demux == null
    }
  }

  it should "GC several dependencies" in {
    testGC(4) {
      _.demux == null
    }
  }

  it should "GC many dependencies" in {
    testGC(32) {
      _.demux == null
    }
  }

  def testDep(num: Int)(afterCheck: Reactive.Emitter[Int] => Boolean) {
    var signsOfLife = Array.fill(num)(false)
    val emitter = new Reactive.Emitter[Int]
    val subs = for (i <- 0 until num) yield emitter onTick {
      signsOfLife(i) = true
    }

    sys.runtime.gc()

    emitter += 1

    for (i <- 0 until num) signsOfLife(i) should equal (true)
    afterCheck(emitter) should equal (true)
  }

  it should "accurately reflect a single dependency" in {
    testDep(1) {
      _.demux.isInstanceOf[java.lang.ref.WeakReference[_]]
    }
  }

  it should "accurately reflect several dependency" in {
    testDep(6) {
      _.demux.isInstanceOf[WeakBuffer[_]]
    }
  }

  it should "accurately reflect many dependency" in {
    testDep(32) {
      _.demux.isInstanceOf[WeakHashTable[_]]
    }
  }

  def testGChalf(num: Int)(afterCheck: Reactive.Emitter[Int] => Boolean) {
    var signsOfLife = Array.fill(num)(false)
    val emitter = new Reactive.Emitter[Int]
    val kept = for (i <- 0 until num / 2) yield emitter onTick {
      signsOfLife(i) = true
    }
    for (i <- num / 2 until num) emitter onTick {
      signsOfLife(i) = true
    }

    sys.runtime.gc()

    emitter += 1

    for (i <- 0 until num / 2) signsOfLife(i) should equal (true)
    for (i <- num / 2 until num) signsOfLife(i) should equal (false)
    afterCheck(emitter) should equal (true)
  }

  it should "accurately GC some of the several dependencies" in {
    testGChalf(8) {
      _.demux.isInstanceOf[WeakBuffer[_]]
    }
  }

  it should "accurately GC some of the many dependencies" in {
    testGChalf(16) {
      _.demux.isInstanceOf[WeakHashTable[_]]
    }
  }

}
