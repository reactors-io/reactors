package io.reactors
package debugger
package repl



import java.io.PrintWriter
import java.io.StringWriter
import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.tools.nsc.Settings
import scala.tools.nsc.interpreter._



class ScalaRepl extends Repl {
  private val lock = new AnyRef
  private val stringWriter = new StringWriter
  private val repl = new ILoop(None, new PrintWriter(stringWriter)) {
    globalFuture = Future.successful(true)
  }

  def tpe = "Scala"

  def eval(cmd: String) = Future {
    lock.synchronized {
      try repl.processLine(cmd)
      catch {
        case e: Exception =>
          e.printStackTrace()
          throw e
      }
      val output = stringWriter.getBuffer.toString
      stringWriter.getBuffer.setLength(0)
      Repl.Result(0, output)
    }
  }

  def shutdown() {
  }
}
