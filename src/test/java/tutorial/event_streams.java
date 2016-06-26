package tutorial;



/*!begin-include!*/
/*!begin-code!*/
import io.reactors.japi.*;
/*!end-code!*/
/*!end-include(reactors-java-event-streams-import.html)!*/



public class event_streams {
  private Events<String> createEventStream() {
    return Events.never();
  }

  /*!begin-include!*/
  /*!begin-code!*/
  Events<String> myEvents = createEventStream();
  /*!end-code!*/
  /*!end-include(reactors-java-event-streams-create.html)!*/
}
