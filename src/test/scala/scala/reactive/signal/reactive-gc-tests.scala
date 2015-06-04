package scala.reactive
package signal



import org.scalacheck._
import org.scalacheck.Gen._
import org.scalacheck.Prop._
import org.scalatest._
import org.scalatest.matchers.ShouldMatchers
import org.testx._
import scala.reactive.util._



class ReactiveGCCheck extends Properties("ReactiveGC") with ExtendedProperties {

  val sizes = detChoose(1, 100)

  def testGC(num: Int)(afterCheck: Events.Emitter[Int] => Boolean): Prop = {
    var signsOfLife = Array.fill(num)(false)
    val emitter = new Events.Emitter[Int]
    for (i <- 0 until num) emitter foreach { _ =>
      signsOfLife(i) = true
    }

    // flaky test - GC is not obliged to collect
    sys.runtime.gc()
    sys.runtime.gc()

    emitter react 1

    val allDead = s"all dead: ${signsOfLife.mkString(", ")}" |:
      (for (i <- 0 until num) yield signsOfLife(i) == false).forall(_ == true)
    val afterCheckOk = s"after check passes" |: afterCheck(emitter)
    allDead && afterCheckOk
  }

  property("GC a single dependency") = forAllNoShrink(sizes) { sz =>
    stackTraced {
      testGC(sz) {
        _.demux == null
      }
    }
  }

  def testOnX(num: Int)(afterCheck: Events.Emitter[Int] => Boolean): Prop = {
    implicit val canLeak = Permission.newCanLeak

    var signsOfLife = Array.fill(num)(false)
    val emitter = new Events.Emitter[Int]
    for (i <- 0 until num) emitter onEvent { _ =>
      signsOfLife(i) = true
    }

    sys.runtime.gc()
    sys.runtime.gc()

    emitter react 1

    val allDead = s"all live: ${signsOfLife.mkString(", ")}" |:
      (for (i <- 0 until num) yield signsOfLife(i) == true).forall(_ == true)
    val afterCheckOk = s"after check passes" |: afterCheck(emitter)
    allDead && afterCheckOk
  }

  property("not GC onX dependency") = forAllNoShrink(sizes) { sz =>
    stackTraced {
      testOnX(sz) {
        _.demux != null
      }
    }
  }

  def testDep(num: Int)(afterCheck: Events.Emitter[Int] => Boolean): Prop = {
    var signsOfLife = Array.fill(num)(false)
    val emitter = new Events.Emitter[Int]
    val subs = for (i <- 0 until num) yield emitter foreach { _ =>
      signsOfLife(i) = true
    }

    sys.runtime.gc()
    sys.runtime.gc()

    emitter react 1

    val allDead = s"all live: ${signsOfLife.mkString(", ")}" |:
      (for (i <- 0 until num) yield signsOfLife(i) == true).forall(_ == true)
    val afterCheckOk = s"after check passes" |: afterCheck(emitter)
    allDead && afterCheckOk
  }

  property("accurately reflect dependencies") = forAllNoShrink(sizes) { sz =>
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
}


class ReactiveGCSpec extends FlatSpec with ShouldMatchers {

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
