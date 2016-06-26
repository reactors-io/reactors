package tutorial;



/*!begin-include!*/
/*!begin-code!*/
import io.reactors.japi.*;
/*!end-code!*/
/*!end-include(reactors-java-event-streams-import.html)!*/
import org.junit.Assert;
import org.junit.Test;



public class event_streams {
  private Events<String> createEventStream() {
    return Events.never();
  }

  static FakeSystem System = new FakeSystem();

  @Test
  public void eventsOnEvent() {
    /*!begin-include!*/
    /*!begin-code!*/
    Events<String> myEvents = createEventStream();
    /*!end-code!*/
    /*!end-include(reactors-java-event-streams-create.html)!*/

    /*!begin-include!*/
    /*!begin-code!*/
    myEvents.onEvent(x -> System.out.println(x));
    /*!end-code!*/
    /*!end-include(reactors-java-event-streams-on-event.html)!*/
  }

  /*!begin-include!*/
  /*!begin-code!*/
  public <T> void trace(Events<T> events) {
    events.onEvent(x -> System.out.println(x));
  }
  /*!end-code!*/
  /*!end-include(reactors-java-event-streams-trace.html)!*/

  
}
