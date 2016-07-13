package io.reactors
package debugger



import org.json4s._
import org.json4s.JsonDSL._
import scala.concurrent.Future



abstract class Repl {
  /** Unique identifier for the REPL's type.
   */
  def tpe: String

  /** Asynchronously evaluates a command in the REPL and returns the result.
   */
  def eval(cmd: String): Future[Repl.Result]

  /** Future that is completed when the REPL starts.
   */
  def started: Future[Boolean]

  /** Get any pending output.
   */
  def flush(): String

  /** Shuts down the REPL.
   */
  def shutdown(): Unit
}


object Repl {
  case class Result(status: Int, output: String) {
    def asJson: JValue = {
      ("status" -> status) ~
      ("output" -> output)
    }
  }
}
