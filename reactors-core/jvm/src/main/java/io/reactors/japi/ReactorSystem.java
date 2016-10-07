package io.reactors.japi;






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
    io.reactors.Arrayable<T> a = Util.arrayable();
    io.reactors.Channel<T> ch = self.spawn(proto.getOriginal(), a);
    return new Channel(ch);
  }

  public Bundle bundle() {
    return new Bundle(self.bundle());
  }

  public ChannelBuilder channels() {
    return new ChannelBuilder(self.channels());
  }

  public <S extends Service> S service(Class<S> cls) {
    try {
      return cls.getConstructor(ReactorSystem.class).newInstance(this);
    } catch (Throwable throwable) {
      throw new RuntimeException(throwable);
    }
  }

  public io.reactors.ReactorSystem rawSystem() {
    return self;
  }

  public void shutdown() {
    self.shutdown();
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
