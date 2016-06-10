package io.reactors.japi;



import java.util.function.Consumer;



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

  private static class AnonymousReactor<T> extends Reactor<T> {
    public AnonymousReactor(Consumer<Reactor<T>> c) {
      c.accept(this);
    }
  }

  public static <T> Proto<T> apply(Consumer<Reactor<T>> c) {
    return Proto.create(AnonymousReactor.class, c);
  }
}
