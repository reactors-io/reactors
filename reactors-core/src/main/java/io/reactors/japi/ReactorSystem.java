package io.reactors.japi;



import scala.reflect.*;



public class ReactorSystem {
  public static ReactorSystem create(String name) {
    return new ReactorSystem(name);
  }

  static ReactorSystem from(io.reactors.ReactorSystem self) {
    return new ReactorSystem(self);
  }

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
}
