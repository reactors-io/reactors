package io.reactors
package protocol



import scala.util.Random



package object algo {
  private[reactors] class EventsAlgorithmOps[@spec(Int, Long, Double) T](
    val events: Events[T]
  ) {
    /** Returns `k` uniformly sampled elements after this event stream unreacts.
     *
     *  This method takes `O(k)` space and reacting to each event takes `O(1)` time.
     *  If the remainder of the stream has less than `k` elements, then all those
     *  elements are returned in the sample.
     *
     *  See "Random sampling with a reservoir", by Vitter.
     */
    def reservoirSample(k: Int)(implicit a: Arrayable[T]): IVar[Array[T]] = {
      assert(k > 0)
      val random = new Random
      val array = a.newRawArray(k)
      var i = 0
      val updates = events.map { x =>
        if (i < k) {
          array(i) = x
        } else {
          val j = random.nextInt(i + 1)
          if (j < k) array(j) = x
        }
        i += 1
        array
      }
      (Events.single(array) union updates).last
        .map(a => if (i < k) a.take(i) else a).toIVar
    }
  }

  implicit def eventsToAlgorithmsOps[@spec(Int, Long, Double) T](events: Events[T]) =
    new EventsAlgorithmOps(events)
}
