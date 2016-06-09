package tutorial;



import io.reactors.japi.*;
import org.junit.Test;



public class reactors {

  /*!begin-include!*/
  /*!begin-code!*/
  ReactorSystem system = ReactorSystem.create("test-system");
  /*!end-code!*/
  /*!end-include(reactors-java-reactors-system.html)!*/

  public static class HelloReactor extends Reactor<String> {
  }

  @Test
  public void runsReactor() {
    /*!begin-include!*/
    /*!begin-code!*/
    Proto<String> proto = Proto.create(HelloReactor.class);
    /*!end-code!*/
    /*!end-include(reactors-java-reactors-anonymous.html)!*/

    /*!begin-include!*/
    /*!begin-code!*/
    Channel<String> ch = system.spawn(proto);
    /*!end-code!*/
    /*!end-include(reactors-java-reactors-spawn.html)!*/
  }

}
