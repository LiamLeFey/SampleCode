package mudclient.core.commands;

import mudclient.core.events.ConnectionEvent;
import mudclient.core.*;
import java.util.*;
import java.nio.charset.Charset;

public class Connect implements Command{

  public Connect( ){
  }

  public String getName(){
    return "connect";
  }

  public Object doCommand( 
      String[] args,
      WorldConnection ignored ){
    return doCommand( args, null, null );
  }
  public Object doCommand( 
      String[] args,
      VariableScope scope,
      WorldConnection ignored ){
    if( scope == null )
      scope = MudClient.getRootVariableScope();
    if( args == null ) return null;
    if( args.length == 1 )
      args = args[0].split("\\s");
    int argCount = args.length;
    if( argCount < 1 ) return null;
    String arg = args[0];
    World w = (World)scope.getValue( "_World." + arg );
    WorldConnection wc;
    VariableScope root = scope.getRoot();
    try{
      if( w != null ){
        wc = new WorldConnection( w, root, Charset.forName( "UTF-8" ) );
      }else{
        if( argCount > 1 ){
          int port = Integer.parseInt( args[1] );
          wc = new WorldConnection( arg, port, root, Charset.forName( "UTF-8" ) );
        }else{
          wc = new WorldConnection( arg, root, Charset.forName( "UTF-8" ) );
        }
      }
    }catch( Exception e ){
      if( e instanceof java.net.UnknownHostException ){
        ((UserInterface)root.getValue("_UserInterface"))
            .display(e.toString());
        return e;
      }
      System.err.print( "Error while contecting to world with args: " );
      int i;
      for( i = 0; i < args.length; i++ ){
        System.err.print( args[ i ] + " " );
      }
      System.err.println();
      e.printStackTrace( System.err );
      return e;
    }
    String cName = MudClient.addWorldConnection( wc.getWorld().getName(), wc );
    ConnectionEvent ce = new ConnectionEvent( wc );
    ce.fire( new String[]{ cName } );
    return wc;
  }
  public String getHelpString(){
    return "Usage:\n" +
          "/connect <worldName>\n" +
          "/connect <inet.address> [port]\n";
  }
}
