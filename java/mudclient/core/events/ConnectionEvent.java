package mudclient.core.events;

import mudclient.core.*;

public class ConnectionEvent implements MCEvent{

  private WorldConnection wc;

  public ConnectionEvent( WorldConnection wc ){
    this.wc = wc;
  }
  public String getName(){
    return "CONNECT";
  }
  public String[] getArgs(){
    return null;
  }
  public boolean hasProperty( String propName ){
    return "WorldConnection".equals( propName );
  }
  public Object getProperty( String propName ){
    if( "WorldConnection".equals( propName ) )
      return wc;
    return null;
  }
  public Object fire( Object arg ){
    return null;
  }
}
