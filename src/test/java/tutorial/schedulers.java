package tutorial;



/*!begin-include!*/
/*!begin-code!*/
import io.reactors.japi.*;
/*!end-code!*/
/*!end-include(reactors-java-schedulers-import.html)!*/
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.Assert;
import org.junit.Test;



public class schedulers {
  static FakeSystem System = new FakeSystem();

  /*!begin-include!*/
  /*!begin-code!*/
  public static class Logger extends Reactor<String> {
    private int count = 3;
    public Logger() {
      sysEvents().onEvent(x -> {
        if (x.isReactorScheduled()) {
          System.out.println("scheduled");
        } else if (x.isReactorPreempted()) {
          count -= 1;
          if (count == 0) {
            main().seal();
            System.out.println("terminating");
          }
        }
      });
      main().events().onEvent(
        x -> System.out.println(x)
      );
    }
  }
  /*!end-code!*/
  /*!end-include(reactors-java-schedulers-logger.html)!*/

  /*!begin-include!*/
  /*!begin-code!*/
  ReactorSystem system = ReactorSystem.create("test-system");
  /*!end-code!*/
  /*!end-include(reactors-java-schedulers-system.html)!*/

  @Test
  public void globalExecutionContext() throws InterruptedException {
    /*!begin-include!*/
    /*!begin-code!*/
    Proto<String> proto = Proto.create(Logger.class).withScheduler(
      Scheduler.GLOBAL_EXECUTION_CONTEXT);
    Channel<String> ch = system.spawn(proto);
    /*!end-code!*/
    /*!end-include(reactors-java-schedulers-global-ec.html)!*/

    Assert.assertEquals("scheduled", System.out.queue.take());

    Thread.sleep(1000);

    /*!begin-include!*/
    /*!begin-code!*/
    ch.send("event 1");
    /*!end-code!*/
    /*!end-include(reactors-java-schedulers-global-ec-send.html)!*/

    Assert.assertEquals("scheduled", System.out.queue.take());
    Assert.assertEquals("event 1", System.out.queue.take());

    Thread.sleep(1000);

    /*!begin-include!*/
    /*!begin-code!*/
    ch.send("event 2");
    /*!end-code!*/
    /*!end-include(reactors-java-schedulers-global-ec-send-again.html)!*/

    Assert.assertEquals("scheduled", System.out.queue.take());
    Assert.assertEquals("event 2", System.out.queue.take());
    Assert.assertEquals("terminating", System.out.queue.take());
  }
}
