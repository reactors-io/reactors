package io.reactors
package debugger



import java.util.concurrent.atomic.AtomicLong
import scala.tools.nsc.Settings
import scala.tools.nsc.interpreter._



abstract class Repl {
  /** Unique identifier for the REPL's type.
   */
  def tpe: String

  /** Evaluates a command in the REPL and returns the result.
   */
  def eval(cmd: String): Repl.Result
}


object Repl {
  case class Result(status: Int, output: String)
}
