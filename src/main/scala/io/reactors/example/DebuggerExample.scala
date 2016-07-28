package io.reactors
package example



import io.reactors.debugger._



object DebuggerExample {
  def main(args: Array[String]) {
    val config = ReactorSystem.customConfig("""
      debug-api = {
        name = "io.reactors.debugger.WebDebugger"
      }
    """)
    val bundle = new ReactorSystem.Bundle(Scheduler.default, config)
    val system = new ReactorSystem("web-debugger", bundle)
    system.names.resolve
  }
}
