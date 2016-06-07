package tutorial;



import io.reactors.japi.*;
import org.junit.Test;



public class reactors {

  private ReactorSystem system = ReactorSystem.create("test-system");

  public static class HelloReactor extends Reactor<String> {
  }

  @Test
  public void runsReactor() {
    /*!begin-include!*/
    /*!begin-code!*/
    system.spawn(Proto.create(HelloReactor.class));
    /*!end-code!*/
    /*!end-include(reactors-java-reactors-spawn.html)!*/
  }

}
