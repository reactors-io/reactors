package io.reactors
package debugger



import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._



trait WebApi {
  /** Either the full state or the sequence of updates since the specified timestamp.
   *
   *  @param suid   unique identifier of the session
   *  @param ts     timestamp of the last update
   *  @return       the the state change since the last update
   */
  def state(suid: String, ts: Long): WebApi.Update
}


object WebApi {
  class Update(
    val timestamp: Long,
    val deltas: Seq[DeltaDebugger.Delta]
  ) {
    def asJson = compact(render(
      ("timestamp" -> timestamp) ~
      ("deltas" -> deltas.map(_.asJson))
    ))
  }
}
