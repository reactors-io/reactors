package tutorial;



import io.reactors.japi.*;
import java.util.concurrent.*;
import org.junit.Assert;
import org.junit.Test;



public class reactors {

  /*!begin-include!*/
  /*!begin-code!*/
  ReactorSystem system = ReactorSystem.create("test-system");
  /*!end-code!*/
  /*!end-include(reactors-java-reactors-system.html)!*/

  static class FakeSystem {
    public class Out {
      public LinkedTransferQueue<Object> queue = new LinkedTransferQueue<>();
      public void println(Object x) {
        queue.add(x);
      }
    }
    public Out out = new Out();
  }

  static FakeSystem System = new FakeSystem();

  @Test
  public void runsAnonymousReactor() {
    /*!begin-include!*/
    /*!begin-code!*/
    Proto<String> proto = Reactor.apply(
      self -> self.main().events().onEvent(x -> System.out.println(x))
    );
    /*!end-code!*/
    /*!end-include(reactors-java-reactors-anonymous.html)!*/

    /*!begin-include!*/
    /*!begin-code!*/
    Channel<String> ch = system.spawn(proto);
    /*!end-code!*/
    /*!end-include(reactors-java-reactors-spawn.html)!*/

    /*!begin-include!*/
    /*!begin-code!*/
    ch.send("Hola!");
    /*!end-code!*/
    /*!end-include(reactors-java-reactors-send.html)!*/

    try {
      Assert.assertEquals("Hola!", System.out.queue.take());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /*!begin-include!*/
  /*!begin-code!*/
  public static class HelloReactor extends Reactor<String> {
    public HelloReactor() {
      main().events().onEvent(
        x -> System.out.println(x)
      );
    }
  }
  /*!end-code!*/
  /*!end-include(reactors-java-reactors-template.html)!*/

  @Test
  public void runsHelloReactor() {
    ReactorSystem system = ReactorSystem.create("test-system");

    /*!begin-include!*/
    /*!begin-code!*/
    Proto<String> proto = Proto.create(HelloReactor.class);
    Channel<String> ch = system.spawn(proto);
    ch.send("Howdee!");
    /*!end-code!*/
    /*!end-include(reactors-java-reactors-spawn-template.html)!*/

    try {
      Assert.assertEquals("Howdee!", System.out.queue.take());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    /*!begin-include!*/
    /*!begin-code!*/
    system.spawn(Proto.create(HelloReactor.class)
      .withScheduler(Scheduler.NEW_THREAD));
    /*!end-code!*/
    /*!end-include(reactors-java-reactors-with-scheduler.html)!*/

    /*!begin-include!*/
    /*!begin-code!*/
    system.bundle().registerScheduler("customTimer", Scheduler.timer(1000));
    Proto<String> protoWithTimer =
      Proto.create(HelloReactor.class).withScheduler("customTimer");
    Channel<String> periodic = system.spawn(protoWithTimer);
    periodic.send("Ohayo");
    /*!end-code!*/
    /*!end-include(reactors-java-reactors-custom-scheduler.html)!*/

    try {
      Assert.assertEquals("Ohayo", System.out.queue.take());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
