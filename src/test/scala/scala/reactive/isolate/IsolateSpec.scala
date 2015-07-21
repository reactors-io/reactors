package scala.reactive
package isolate



import scala.collection._
import scala.concurrent.SyncVar
import org.scalatest._
import org.scalatest.matchers.ShouldMatchers



// object Logging {
//   def log(msg: String) = println(s"${Thread.currentThread.getName}: $msg")
// }


// object Isolates {
//   import Logging._

//   class MasterIso(ask: Channel[Channel[Channel[Int]]]) extends Iso[Channel[Int]] {
//     import implicits.canLeak

//     sysEvents onCase {
//       case IsoStarted => ask << channel
//     }

//     react <<= events foreach { c =>
//       c << 7
//     }
//   }

//   class RegChannelIso(sv: SyncVar[Int]) extends Iso[Null] {
//     val second = system.channels.named("secondChannel").open[Int]

//     react <<= second.events foreach { i =>
//       sv.put(i)
//       second.channel.seal()
//     }
//   }

//   class LookupIso extends Iso[Null] {
//     react <<= system.channels.iget[Int]("reggy#secondChannel").use(_ << 7)
//   }

// }


// trait IsolateSpec extends FlatSpec with ShouldMatchers {
//   import Logging._
//   import Isolates._

//   val isoSystem: IsoSystem

//   "A synced isolate" should "react to an event" in {
//     val sv = new SyncVar[String]

//     val emitter = new Events.Emitter[String]
//     val proto = Proto[OneIso](sv)
//     val c = isoSystem.isolate(proto).attach(emitter).seal()
//     emitter react "test event"
//     emitter.unreact()

//     sv.take() should equal ("test event")
//   }

//   def reactToMany(many: Int) {
//     val sv = new SyncVar[List[Int]]

//     val emitter = new Events.Emitter[Int]
//     val proto = Proto[ManyIso](many, sv)
//     val c = isoSystem.isolate(proto).attach(emitter).seal()
//     for (i <- 0 until many) emitter react i
//     emitter.unreact()

//     val expected = (0 until many).reverse
//     assert(sv.get == expected, "${sv.get} vs $expected")
//   }

//   it should "react to many events" in {
//     for (i <- 2 until 10) reactToMany(i)
//     for (i <- 10 until 200 by 20) reactToMany(i)
//   }

//   it should "see itself as an isolate" in {
//     val sv = new SyncVar[Boolean]

//     val emitter = new Events.Emitter[Int]

//     val proto = Proto[SelfIso](sv)
//     val c = isoSystem.isolate(proto).attach(emitter).seal()

//     emitter react 7
//     emitter.unreact()

//     sv.get should equal (true)
//   }

//   it should "set a custom event queue" in {
//     val sv = new SyncVar[Boolean]

//     val emitter = new Events.Emitter[Int]

//     val proto = Proto[CustomIso](sv).withEventQueue(EventQueue.DevNull.factory)
//     val c = isoSystem.isolate(proto).attach(emitter).seal()

//     emitter react 7
//     emitter.unreact()

//     sv.get should equal (true)
//   }

//   it should "close its reactives when it terminates" in {
//     val sv = new SyncVar[Boolean]

//     val proto = Proto[AutoClosingIso](sv)
//     val c = isoSystem.isolate(proto).seal()

//     sv.get should equal (true)
//   }

//   it should "receive events from all its channels" in {
//     val sv = new SyncVar[Boolean]

//     val emitter = new Events.Emitter[Channel[Int]]

//     val dc = isoSystem.isolate(Proto[DualChannelIso](sv))
//     val mc = isoSystem.isolate(Proto[MasterIso](dc))

//     sv.get should equal (7)

//     dc.seal()
//     mc.seal()
//     Thread.sleep(100)
//   }

//   it should "use channel name resolution" in {
//     val sv = new SyncVar[Boolean]

//     val emitter = new Events.Emitter[Channel[Int]]

//     val rc = isoSystem.isolate(Proto[RegChannelIso](sv).withName("reggy"))
//     val lc = isoSystem.isolate(Proto[LookupIso])

//     sv.get should equal (7)

//     rc.seal()
//     lc.seal()
//     Thread.sleep(100)
//   }

//   it should "use channel name resolution with ivars" in {
//     val sv = new SyncVar[Boolean]

//     val lc = isoSystem.isolate(Proto[LookupIso])
//     Thread.sleep(500)
//     val rc = isoSystem.isolate(Proto[RegChannelIso](sv).withName("reggy"))

//     sv.get should equal (7)

//     rc.seal()
//     lc.seal()
//     Thread.sleep(200)
//   }

// }


// trait LooperIsolateSpec extends FlatSpec with ShouldMatchers {
//   import Logging._
//   import Isolates._

//   val isoSystem: IsoSystem

//   "A LooperIso" should "do 3 loops" in {
//     val sv = new SyncVar[Int]

//     println("looper -----------")

//     val proto = Proto[TestLooper](sv)
//     isoSystem.isolate(proto)

//     sv.get should equal (3)
//   }

// }


// class ExecutorSyncedIsolateSpec extends IsolateSpec with LooperIsolateSpec {

//   val scheduler = Scheduler.default
//   val bundle = IsoSystem.Bundle.default(scheduler)
//   val isoSystem = IsoSystem.default("TestSystem", bundle)

// }


// class NewThreadSyncedIsolateSpec extends IsolateSpec with LooperIsolateSpec {

//   val scheduler = Scheduler.newThread
//   val bundle = IsoSystem.Bundle.default(scheduler)
//   val isoSystem = IsoSystem.default("TestSystem", bundle)

// }


// class PiggybackSyncedIsolateSpec extends LooperIsolateSpec {

//   val scheduler = Scheduler.piggyback
//   val bundle = IsoSystem.Bundle.default(scheduler)
//   val isoSystem = IsoSystem.default("TestSystem", bundle)

// }


// class TimerSyncedIsolateSpec extends LooperIsolateSpec {

//   val scheduler = new Scheduler.Timer(400)
//   val bundle = IsoSystem.Bundle.default(scheduler)
//   val isoSystem = IsoSystem.default("TestSystem", bundle)

// }
