package io.reactors



import io.reactors.concurrent.BaselineBench
import io.reactors.concurrent.BigBench
import io.reactors.concurrent.CountingActorBench
import io.reactors.concurrent.FibonacciBench
import io.reactors.concurrent.ForkJoinCreationBench
import io.reactors.concurrent.ForkJoinThroughputBench
import io.reactors.concurrent.PingPongBench
import io.reactors.concurrent.StreamingPingPongBench
import io.reactors.concurrent.ThreadRingBench
import io.reactors.container.MatrixBench
import io.reactors.remote.RuntimeMarshalerBench
import org.scalameter.Bench



class Benchmarks extends Bench.Group {
  include(new BaselineBench)
  include(new PingPongBench)
  include(new StreamingPingPongBench)
  include(new ThreadRingBench)
  include(new CountingActorBench)
  include(new ForkJoinCreationBench)
  include(new ForkJoinThroughputBench)
  include(new FibonacciBench)
  include(new BigBench)
  include(new MatrixBench {})
  include(new RuntimeMarshalerBench)
}
