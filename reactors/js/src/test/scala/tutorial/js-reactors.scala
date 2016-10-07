package tutorial



import io.reactors._
import org.scalatest._
import scala.concurrent._



class JsReactors extends AsyncFunSuite {
  implicit override def executionContext = ExecutionContext.Implicits.global

  test("use JS scheduler") {
    val system = ReactorSystem.default("test")
    /*!begin-include!*/
    /*!begin-code!*/
    val done = Promise[Boolean]()
    system.spawn(Reactor[Unit] { self =>
      self.sysEvents onMatch {
        case ReactorStarted =>
          done.success(true)
          self.main.seal()
      }
    })
    /*!end-code!*/
    /*!end-include(reactors-scala-js-custom-scheduler.html)!*/
    done.future.map(t => assert(t))
  }
}
