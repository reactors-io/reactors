package io.reactors
package debugger



import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._



trait WebApi {
  /** Either the full state or the sequence of updates since the specified timestamp.
   *
   *  @return    The the state change since the last update
   */
  def state(ts: Long): WebApi.State
}


object WebApi {
  abstract class State {
    def timestamp: Long
    def asJson: String
  }

  object State {
    class Complete(val timestamp: Long) extends State {
      def asJson = compact(render(
        ("timestamp" -> timestamp)
      ))
    }
    class Delta(val timestamp: Long) extends State {
      def asJson = compact(render(
        ("timestamp" -> timestamp)
      ))
    }
  }
}
