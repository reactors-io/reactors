package scala.reactive
package calc



import org.scalatest._
import org.scalatest.matchers.ShouldMatchers



class ReactRandomSpec extends FlatSpec with ShouldMatchers {

  "ReactRandom" should "create random events" in {
    val random = new ReactRandom(0L)

    var x: Option[Int] = None
    val changes = random.react.int() foreach { v =>
      x = Some(v)
    }
    random.generate()

    assert(x.isInstanceOf[Some[_]])
  }

}