package mudclient.core;

import java.io.IOException;

public class UserDefinition 
implements Command, Trigger, Hook {

  private boolean isAlias;
  private String name;
  private String regex;
  private MCEvent event;

  private ActionSequence body;

  private boolean active;

  public UserDefinition(){}

  public Object executeBody( 
      String[] args,
      VariableScope scope,
      WorldConnection foreground ){
    // here we need to process the args.

    try{
      return body.perform( scope, foreground );
    }catch( IOException e ){
      return e;
    }
  }
  public String getName(){
    return name;
  }
  public Object doCommand( 
      String[] args, 
      VariableScope scope, 
      WorldConnection foreground ){
    return executeBody( args, scope, foreground );
  }
  public String getTriggerText(){
    return regex;
  }
  public Object fireTrigger(
      String[] args, 
      VariableScope scope, 
      WorldConnection foreground ){
    return executeBody( args, scope, foreground );
  }
  public Object fireHook(
      MCEvent event,
      VariableScope scope ){
    WorldConnection connection;
    if( event.hasProperty( "WorldConnection" ) )
      connection = (WorldConnection)event.getProperty( "WorldConnection" );
    else
      connection = null;
    String[] args = event.getArgs();
    return executeBody( args, scope, connection );
  }
  public String getMCEventName(){
    if( event != null )
      return event.getName();
    return null;
  }
  public boolean isHook(){
    return event != null;
  }
  public boolean isTrigger(){
    return regex != null && ! regex.equals( "" );
  }
  public boolean isCommand(){
    return name != null && ! name.equals( "" );
  }
  public boolean isAlias(){
    return isAlias;
  }
  public void setAlias( boolean b ){
    isAlias = isCommand() && b;
  }
}
