package scala.reactive
package test.container



import org.scalatest._
import org.scalatest.matchers.ShouldMatchers



class ReactRandomSpec extends FlatSpec with ShouldMatchers {

  "ReactRandom" should "create random events" in {
    val random = new ReactRandom(0L)

    var x: Option[Int] = None
    val changes = random.react.ints onEvent { v =>
      x = Some(v)
    }
    random.emit()

    assert(x.isInstanceOf[Some[_]])
  }

}