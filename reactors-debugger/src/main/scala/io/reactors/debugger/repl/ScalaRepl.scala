package io.reactors
package debugger
package repl



import scala.concurrent._
import scala.tools.nsc.Settings
import scala.tools.nsc.interpreter._



class ScalaRepl extends Repl {
  val repl = null

  def tpe = "Scala"

  def eval(cmd: String) = {
    Promise.successful(Repl.Result(0, "")).future
  }

  def shutdown() {
  }
}
