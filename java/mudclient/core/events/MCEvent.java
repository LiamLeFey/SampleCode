package mudclient.core.events;

import mudclient.core.*;

public interface MCEvent{

  public String getName();
  public String[] getArgs();
  public boolean hasProperty( String propName );
  public Object getProperty( String propName );
  public Object fire( Object arg );
}
