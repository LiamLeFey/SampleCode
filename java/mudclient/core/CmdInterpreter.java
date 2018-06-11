package mudclient.core;

import java.io.IOException;
import java.util.Hashtable;

public class CmdInterpreter{
  private static Hashtable<KObj, StringBuffer> partialCmds =
        new Hashtable<KObj, StringBuffer>();
  public static Object interpret( 
      String cmd, 
      WorldConnection connection ) throws IOException {
    return interpret( cmd, null, connection );
  }
  public static Object interpret( 
      String cmd, 
      VariableScope localScope,
      WorldConnection connection ) throws IOException {
    // first, replace everything that needs to be replaced,
    // mostly the aliases.
 
    VariableScope scope;
    if( localScope == null ){
      if( connection != null )
        scope = connection.getWorldVariables();
      else
        scope = MudClient.getRootVariableScope();
    } else
      scope = localScope;
    KObj key = new KObj( scope, connection );
    if( cmd.endsWith( "\\" ) ){
      if( partialCmds.containsKey( key ) )
        partialCmds.get( key ).append( cmd.substring( 0, cmd.length()-1 ));
      else
        partialCmds.put( key,
              new StringBuffer( cmd.substring( 0, cmd.length()-1 ) ));
      return null;
    }
    if( partialCmds.containsKey( key ) ){
      cmd = partialCmds.get( key ).append( cmd ).toString();
    }
    // now check if it is a command
    if( cmd.startsWith("/") ){
      String[] split = cmd.substring(1).split("\\s", 2);
      if( split.length == 0 ) return null;
      String[] args = new String[ split.length - 1 ];
      int i;
      for( i = 0; i < args.length; i++ )
        args[ i ] = split[ i + 1 ];
      Object userVal = scope.getValue( split[ 0 ] );
      if( userVal instanceof ActionSequence )
        return ((ActionSequence)userVal).perform( scope, connection );
      Command c = MudClient.getCommand( split[ 0 ] );
      return (c == null)?null:c.doCommand( args, scope, connection );
    }
    
    // otherwise, just send it on to the connection
    if( connection != null ){
      return connection.send( cmd + "\n" );
    }
    return null;
  }
  // a simple object to combine WorldConnection and VariableScope,
  // either of which can be null, into a single key object
  private static class KObj{
    private VariableScope vs;
    private WorldConnection wc;
    private KObj(VariableScope vs, WorldConnection wc){
      this.vs = vs;
      this.wc = wc;
    }
    // I can't think of a meaningful way to have either of these be equal
    // without actually being the same object.
    public boolean equals( Object o ){
      return ( o instanceof KObj && ((KObj)o).vs == vs && ((KObj)o).wc == wc );
    }
    public int hashCode(){
      return (vs == null ? 0 : vs.hashCode())
            + (wc == null ? 0 : wc.hashCode());
    }
  }
}
