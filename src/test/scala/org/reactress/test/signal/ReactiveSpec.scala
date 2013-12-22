package org.reactress
package test.signal



import org.scalatest._
import org.scalatest.matchers.ShouldMatchers



class ReactiveSpec extends FlatSpec with ShouldMatchers {

  class ReactiveTest {
    val x = ReactCell(0)
    val y = ReactCell(0)
    val z = ReactCell(0)
    val w = ReactCell(0)
  }

  "A reactive" should "be filtered" in {
    val rt = new ReactiveTest
    val s = rt.x.filter {
      _ % 2 == 0
    }
    val a = s onValue { x =>
      assert(x % 2 == 0)
    }

    rt.x := 1
    rt.x := 2
  }

  it should "be mapped" in {
    val e = new Reactive.Emitter[Int]
    val s = e.map {
      _ + 1
    }
    val a = s onValue { x =>
      assert(x == 2)
    }

    e += 1
  }

  it should "be folded past" in {
    val rt = new ReactiveTest
    val s = rt.x.foldPast(List[Int]()) { (acc, x) =>
      x :: acc
    }
    val a = s onValue { xs =>
      assert(xs.reverse == Stream.from(1).take(xs.length))
    }

    rt.x := 1
    rt.x := 2
    rt.x := 3
    rt.x := 4
    rt.x := 5
  }

  it should "be muxed" in {
    val s = ReactCell[Reactive[Int]](Signal.Constant(0))
    val e1 = new Reactive.Emitter[Int]
    val e2 = new Reactive.Emitter[Int]
    val ints = s.mux().signal(0)

    assert(ints() == 0)
    s := e1
    e1 += 10
    assert(ints() == 10)
    e1 += 20
    assert(ints() == 20)
    e2 += 30
    assert(ints() == 20)
    s := e2
    assert(ints() == 20)
    e2 += 40
    assert(ints() == 40)
    e1 += 50
    assert(ints() == 40)
    e2 += 60
    assert(ints() == 60)
  }

  class Cell(var x: Int = 0)

  it should "mutate" in {
    val ms = Signal.Mutable(new Cell)
    val vals = ms.map(_.x).signal(0)
    val e = new ReactCell[Int](0)
    e.mutate(ms) {
      _().x = _
    }

    e := 1
    assert(vals() == 1)
    e := 2
    assert(vals() == 2)
  }

}
