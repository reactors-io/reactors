package io.reactors
package debugger



import org.apache.commons.io.IOUtils
import org.rapidoid.net.Server
import org.rapidoid.setup._



class WebDebugger(val system: ReactorSystem)
extends DebugApi with Protocol.Service {
  private val server: Server = WebDebugger.createServer(system)

  def isEnabled = false

  def eventSent[@spec(Int, Long, Double) T](c: Channel[T], x: T) {
  }

  def eventDelivered[@spec(Int, Long, Double) T](c: Channel[T], x: T) {
  }

  def reactorStarted(r: Reactor[_]) {
  }

  def reactorScheduled(r: Reactor[_]) {
  }

  def reactorPreempted(r: Reactor[_]) {
  }

  def reactorDied(r: Reactor[_]) {
  }

  def reactorTerminated(r: Reactor[_]) {
  }

  def connectorCreated[T](c: Connector[T]) {
  }

  def connectorSealed[T](c: Connector[T]) {
  }

  def shutdown() {
    server.shutdown()
  }
}


object WebDebugger {
  private val debuggerPage = {
    val stream = getClass.getResourceAsStream("/io/reactors/debugger/index.html")
    IOUtils.toString(stream, "UTF-8")
  }

  private[debugger] def createServer(system: ReactorSystem): Server = {
    val s = Setup.create(system.name)

    // attributes
    s.port(system.bundle.config.getInt("debug-api.port"))

    //routes
    s.get("/").html(debuggerPage)

    s.listen()
  }

  def main(args: Array[String]) {
    val config = ReactorSystem.customConfig("""
      debug-api = {
        name = "io.reactors.debugger.WebDebugger"
      }
    """)
    val bundle = new ReactorSystem.Bundle(Scheduler.default, config)
    val system = new ReactorSystem("web-debugger", bundle)
  }
}
