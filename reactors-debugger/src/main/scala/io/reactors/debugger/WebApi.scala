package io.reactors
package debugger



import org.json4s._



trait WebApi {
  /** Either the full state or the sequence of updates since the specified timestamp.
   *
   *  @param suid   unique identifier of the session
   *  @param ts     timestamp of the last update
   *  @return       the the state change since the last update
   */
  def state(suid: String, ts: Long): JValue
}
