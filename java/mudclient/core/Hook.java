package mudclient.core;

public interface Hook {

  // returns the event name
  public String getMCEventName();

  // returns the result
  public Object fireHook( MCEvent event, VariableScope scope );
}
