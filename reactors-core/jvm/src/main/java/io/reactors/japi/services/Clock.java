package io.reactors.japi.services;



import io.reactors.japi.Events;
import io.reactors.japi.ReactorSystem;
import io.reactors.japi.Service;
import java.util.concurrent.TimeUnit;
import scala.concurrent.duration.Duration;




public class Clock extends Service {
  private io.reactors.services.Clock rawClock;

  public Clock(ReactorSystem system) {
    this.rawClock = getRawService(system, io.reactors.services.Clock.class);
  }

  public Events<Void> timeout(long millis) {
    Duration delay = Duration.apply(millis, TimeUnit.MILLISECONDS);
    return (new Events(rawClock.timeout(delay))).map(u -> null);
  }
}
