package mudclient.core.commands;

import mudclient.core.*;
import java.util.*;

public class AddWorld implements Command{

  public AddWorld( ){
  }

  public String getName(){
    return "addworld";
  }

  public Object doCommand(
      String[] args,
      VariableScope ignore,
      WorldConnection ignored ){

    if( args == null ) return null;
    if( args.length == 1 )
      args = args[0].split("\\s");
    if( args.length < 2 ) return null;
    String name = args[0];
    String host = args[1];
    // default to port 23 (default telnet port)
    int port = 23;
    if( args.length > 2 ){
      try{
        port = Integer.parseInt( args[2] );
      } catch( NumberFormatException e ){}
    }
    World world = new World( name, host, port );
    MudClient.getRootVariableScope().setValue( "_World." + name, world );
    return world;
  }

  public Object doCommand( 
      String[] args,
      WorldConnection ignored ){
    return doCommand( args, null, null );
  }
  public String getHelpString(){
    return "Usage: /addworld <name> <inet.address> [port]\n";
  }
}
