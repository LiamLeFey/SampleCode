package mudclient.core.commands;

import mudclient.core.*;

public class Quit implements Command{

  public Quit( ){
  }

  public String getName(){
    return "quit";
  }

  public Object doCommand( 
      String[] args,
      WorldConnection ignored ){
    return doCommand( null, null, null );
  }
  public Object doCommand( 
      String[] args,
      VariableScope ignore,
      WorldConnection ignored ){
    if( tools.Utils.arrayToString( args, " " ).trim().length() != 0 ){
      MudClient.display( getHelpString() );
      return null;
    }
    System.exit( 0 );
    return null;
  }
  public String getHelpString(){
    return "Usage: /quit\n";
  }
}
