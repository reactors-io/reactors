package io.reactors
package debugger



import scala.concurrent.Future
import org.json4s._



trait WebApi {
  /** Either the full state or the sequence of updates since the specified timestamp.
   *
   *  @param suid      unique identifier of the session
   *  @param ts        timestamp of the last update
   *  @param repluids  a list of REPL UIDs whose output needs to be collected
   *  @return          the the state change since the last update
   */
  def state(suid: String, ts: Long, repluids: List[String]): JObject

  /** Starts a new REPL.
   *
   *  @param tpe      type of the requested REPL
   *  @return         the (actual) session UID, and REPL UID
   */
  def replGet(tpe: String): Future[JValue]

  /** Evaluates a command in the REPL.
   *
   *  @param repluid  REPL UID
   *  @param command  string with the contents of the command to execute
   *  @return         the status and the output of the command
   */
  def replEval(repluid: String, cmd: String): Future[JValue]

  /** Closes the REPL.
   *
   *  @param repluid  REPL UID
   *  @return         the status and the output of the command
   */
  def replClose(repluid: String): Future[JValue]
}
