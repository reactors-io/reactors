package org



import scala.annotation.implicitNotFound



package object reactors {

  type spec = specialized

  case class IsolateError(msg: String, cause: Throwable) extends Error(msg, cause)

  /** Determines if the throwable is lethal, i.e. should the program immediately stop.
   */
  def isLethal(t: Throwable): Boolean = t match {
    case e: VirtualMachineError => true
    case e: LinkageError => true
    case e: IsolateError => true
    case _ => false
  }

  /** Matches lethal throwables.
   */
  object Lethal {
    def unapply(t: Throwable): Option[Throwable] = t match {
      case e: VirtualMachineError => Some(e)
      case e: LinkageError => Some(e)
      case e: IsolateError => Some(e)
      case _ => None
    }
  }

  /** Determines if the throwable is not lethal.
   */
  def isNonLethal(t: Throwable): Boolean = !isLethal(t)

  /** Matches non-lethal throwables.
   */
  object NonLethal {
    def unapply(t: Throwable): Option[Throwable] = t match {
      case e: VirtualMachineError => None
      case e: LinkageError => None
      case e: IsolateError => None
      case _ => Some(t)
    }
  }

  /** Partial function ignores non-lethal throwables.
   *
   *  This is useful in composition with other exception handlers.
   */
  val ignoreNonLethal: PartialFunction[Throwable, Unit] = {
    case t if isNonLethal(t) => // ignore
  }

}
