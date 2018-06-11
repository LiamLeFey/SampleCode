package mudclient.core;

import java.util.regex.*;

public class TriggerManager{
  // single line triggers
  private ArrayList<Trigger> slTriggers;
  // multi-line triggers
  private ArrayList<Trigger> mlTriggers;

  // this will feed the input through the various triggers, in order,
  // transforming it for each before it is passed on to the next, and
  // then finally pass the input on to the UI with cName as the connection
  public void processData( Segment s, UserInterface ui, String cName ){
    for( int i = 0; i < slTriggers.size(); i++ ){
      s = slTriggers.get( i ).
    }
  }
}
