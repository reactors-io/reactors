package scala.reactive
package signal



import org.scalatest._
import org.testx._
import scala.reactive.util._



class ReactiveGCSpec extends FlatSpec with Matchers {

  def testGC(num: Int)(afterCheck: Events.Emitter[Int] => Boolean) = {
    var signsOfLife = Array.fill(num)(false)
    val emitter = new Events.Emitter[Int]
    for (i <- 0 until num) emitter foreach { _ =>
      signsOfLife(i) = true
    }

    // flaky test - GC is not obliged to collect
    sys.runtime.gc()
    sys.runtime.gc()
    sys.runtime.gc()
    sys.runtime.gc()

    emitter react 1

    val allDead =
      (for (i <- 0 until num) yield signsOfLife(i) == false).forall(_ == true)
    val afterCheckOk = afterCheck(emitter)
    assert(allDead && afterCheckOk)
  }

  it should "GC 1 dependency" in {
    val sz = 1
    stackTraced {
      testGC(sz) {
        _.demux == null
      }
    }
  }

  it should "GC 2 dependencies" in {
    val sz = 2
    stackTraced {
      testGC(sz) {
        _.demux == null
      }
    }
  }

  it should "GC 4 dependencies" in {
    val sz = 2
    stackTraced {
      testGC(sz) {
        _.demux == null
      }
    }
  }

  it should "GC 8 dependencies" in {
    val sz = 2
    stackTraced {
      testGC(sz) {
        _.demux == null
      }
    }
  }

  def testOnX(num: Int)(afterCheck: Events.Emitter[Int] => Boolean) = {
    implicit val canLeak = Permission.newCanLeak

    var signsOfLife = Array.fill(num)(false)
    val emitter = new Events.Emitter[Int]
    for (i <- 0 until num) emitter onEvent { _ =>
      signsOfLife(i) = true
    }

    sys.runtime.gc()
    sys.runtime.gc()

    emitter react 1

    val allDead =
      (for (i <- 0 until num) yield signsOfLife(i) == true).forall(_ == true)
    val afterCheckOk = afterCheck(emitter)
    assert(allDead && afterCheckOk)
  }

  it should "not GC onX dependency" in {
    val sz = 5
    stackTraced {
      testOnX(sz) {
        _.demux != null
      }
    }
  }

  def testDep(num: Int)(afterCheck: Events.Emitter[Int] => Boolean) = {
    var signsOfLife = Array.fill(num)(false)
    val emitter = new Events.Emitter[Int]
    val subs = for (i <- 0 until num) yield emitter foreach { _ =>
      signsOfLife(i) = true
    }

    sys.runtime.gc()
    sys.runtime.gc()

    emitter react 1

    val allDead =
      (for (i <- 0 until num) yield signsOfLife(i) == true).forall(_ == true)
    val afterCheckOk = afterCheck(emitter)
    allDead && afterCheckOk
  }

  def testDeps(sz: Int): Boolean = {
    stackTraced {
      testDep(sz) { r =>
        if (sz == 0) {
          r.demux == null
        } else if (sz == 1) {
          r.demux.isInstanceOf[java.lang.ref.WeakReference[_]]
        } else if (sz <= 8) {
          r.demux.isInstanceOf[WeakBuffer[_]]
        } else {
          r.demux.isInstanceOf[WeakHashTable[_]]
        }
      }
    }
  }

  it should "accurately reflect 0 dependencies" in {
    val sz = 0
    assert(testDeps(sz))
  }

  it should "accurately reflect 1 dependency" in {
    val sz = 1
    assert(testDeps(sz))
  }

  it should "accurately reflect 2 dependencies" in {
    val sz = 2
    assert(testDeps(sz))
  }

  it should "accurately reflect 8 dependencies" in {
    val sz = 8
    assert(testDeps(sz))
  }

  def testGChalf(num: Int)(afterCheck: Events.Emitter[Int] => Boolean) {
    var signsOfLife = Array.fill(num)(false)
    val emitter = new Events.Emitter[Int]
    val kept = for (i <- 0 until num / 2) yield emitter foreach { _ =>
      signsOfLife(i) = true
    }
    for (i <- num / 2 until num) emitter foreach { _ =>
      signsOfLife(i) = true
    }

    sys.runtime.gc()
    sys.runtime.gc()

    emitter react 1

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
