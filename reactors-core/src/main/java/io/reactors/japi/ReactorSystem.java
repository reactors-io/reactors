package io.reactors.japi;



import scala.reflect.*;



public class ReactorSystem {
  public static ReactorSystem create(String name) {
    return new ReactorSystem(name);
  }

  private io.reactors.ReactorSystem proxy;

  private ReactorSystem(String name) {
    this.proxy = new io.reactors.ReactorSystem(
      name, io.reactors.ReactorSystem.defaultBundle());
  }

  public <T> Channel<T> spawn(Proto<T> proto) {
    ClassTag<T> tag = ClassTag$.MODULE$.apply(proto.getProxy().clazz());
    io.reactors.Arrayable<T> a = io.reactors.Arrayable$.MODULE$.ref(tag);
    io.reactors.Channel<T> ch = proxy.spawn(proto.getProxy(), a);
    return new Channel(ch);
  }
}
