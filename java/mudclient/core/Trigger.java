package mudclient.core;

public interface Trigger {

  // multiline triggers are not allowed to change any output text, to
  // avoid delaying the output
  public boolean isMultiLine();

  // returns the name
  public String getName();

  // returns the regex text
  public String getTriggerText();

  // returns the result
  public Object fireTrigger( 
      String[] args, 
      VariableScope scope,
      WorldConnection connection );
}
