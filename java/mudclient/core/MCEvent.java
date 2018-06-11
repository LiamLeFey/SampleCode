package mudclient.core;

public interface MCEvent{
  // this class represents events  related to the mud client, as
  // opposed to the awt package Event class
  public String getName();
  public String[] getArgs();
  public boolean hasProperty( String key );
  public Object getProperty( String key );

  public Object fire( Object arg );
}
