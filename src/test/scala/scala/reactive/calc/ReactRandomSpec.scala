package scala.reactive
package calc



import org.scalatest._



class ReactRandomSpec extends FlatSpec with Matchers {

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