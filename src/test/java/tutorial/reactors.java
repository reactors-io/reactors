package tutorial;



import io.reactors.japi.*;
import org.junit.Test;



public class reactors {

  /*!begin-include!*/
  /*!begin-code!*/
  ReactorSystem system = ReactorSystem.create("test-system");
  /*!end-code!*/
  /*!end-include(reactors-java-reactors-system.html)!*/

  @Test
  public void runsAnonymousReactor() {
    /*!begin-include!*/
    /*!begin-code!*/
    Proto<String> proto = Reactor.apply(
      (self) -> {}
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
    // ch.send("Hola!");
    /*!end-code!*/
    /*!end-include(reactors-java-reactors-send.html)!*/
  }

  /*!begin-include!*/
  /*!begin-code!*/
  public static class HelloReactor extends Reactor<String> {
  }
  /*!end-code!*/
  /*!end-include(reactors-java-reactors-template.html)!*/

  @Test
  public void runsHelloReactor() {
    /*!begin-include!*/
    /*!begin-code!*/
    Proto<String> proto = Proto.create(HelloReactor.class);
    Channel<String> ch = system.spawn(proto);
    // ch.send("Howdee!");
    /*!end-code!*/
    /*!end-include(reactors-java-reactors-spawn-template.html)!*/
  }
}
