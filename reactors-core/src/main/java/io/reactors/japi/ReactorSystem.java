package io.reactors.japi;



import scala.reflect.*;



public class ReactorSystem {
  private io.reactors.ReactorSystem self;

  private ReactorSystem(io.reactors.ReactorSystem self) {
    this.self = self;
  }

  private ReactorSystem(String name) {
    this.self = new io.reactors.ReactorSystem(
      name, io.reactors.ReactorSystem.defaultBundle());
  }

  public <T> Channel<T> spawn(Proto<T> proto) {
    ClassTag<T> tag = ClassTag$.MODULE$.apply(Object.class);
    io.reactors.Arrayable<T> a = io.reactors.Arrayable$.MODULE$.ref(tag);
    io.reactors.Channel<T> ch = self.spawn(proto.getOriginal(), a);
    return new Channel(ch);
  }

  public Bundle bundle() {
    return new Bundle(self.bundle());
  }

  public static ReactorSystem create(String name) {
    return new ReactorSystem(name);
  }

  static ReactorSystem from(io.reactors.ReactorSystem self) {
    return new ReactorSystem(self);
  }

  public static class Bundle {
    private io.reactors.ReactorSystem.Bundle self;

    private Bundle(io.reactors.ReactorSystem.Bundle self) {
      this.self = self;
    }

    public void registerScheduler(String name, Scheduler s) {
      self.registerScheduler(name, s.getSelf());
    }
  }
}
