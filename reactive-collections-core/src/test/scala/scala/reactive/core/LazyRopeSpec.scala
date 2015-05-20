package scala.reactive.core



import org.scalatest._
import org.scalatest.matchers.ShouldMatchers



class LazyRopeSpec extends FlatSpec with ShouldMatchers {
  import LazyRope._

  "LazyRope" should "complete the snippet" in {
    var w = Wrapper[Int](Conc.Empty, Nil)

    for (i <- 0 until 27) {
      w = appendAndPay(w, new Conc.Single(i))
      println(w)
    }
  }

}
