package mudclient.core;

public interface Command {

  // must return the name of the command (the thing the user types)
  // note that this may be null (i.e. UserDefined trigger)
  public String getName();

  // returns the result of the command
  public Object doCommand( 
      String[] args, 
      VariableScope localScope,
      WorldConnection foreground );

  // returns the result of the command
  public Object doCommand( 
      String[] args, 
      WorldConnection foreground );

  // returns a string with info on usage of the command.
  public String getHelpString();
}
