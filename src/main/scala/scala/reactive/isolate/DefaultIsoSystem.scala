package scala.reactive
package isolate



import java.util.concurrent.atomic._
import scala.collection._



/** Default isolate system implementation.
 *
 *  @param name      the name of this isolate system
 *  @param bundle    the scheduler bundle used by the isolate system
 */
class DefaultIsoSystem(
  val name: String,
  val bundle: IsoSystem.Bundle = IsoSystem.defaultBundle
) extends IsoSystem


object DefaultIsoSystem {

}

