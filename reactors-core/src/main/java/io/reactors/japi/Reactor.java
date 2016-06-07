package io.reactors.japi;






public abstract class Reactor<T> {
  private io.reactors.concurrent.Frame frame;
  private ReactorSystem system;

  public Reactor() {
    frame = io.reactors.Reactor$.MODULE$.selfFrame().get();
    system = ReactorSystem.from(frame.reactorSystem());
  }

  public ReactorSystem system() {
    return system;
  }

  public long uid() {
    return frame.uid();
  }
}
