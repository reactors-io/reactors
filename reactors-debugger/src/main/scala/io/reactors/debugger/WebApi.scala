package io.reactors
package debugger






trait WebApi {
  /** Either the full state or the sequence of updates since the specified timestamp.
   *
   *  @return    The latest timestamp, and the state change since the last update
   */
  def state(ts: Long): (Long, WebApi.State)
}


object WebApi {
  abstract class State

  object State {
    class Complete() extends State
    class Delta() extends State
  }
}
