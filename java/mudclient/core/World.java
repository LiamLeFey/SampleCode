package mudclient.core;

public class World{
  private String host;
  private int port;
  private String name;

  public World( String host ){
    this( host, 23 );
  }
  public World( String host, int port ){
    this( host, host, port );
  }
  public World( String name, String host, int port ){
    this.host = host;
    this.port = port;
    this.name = name;
  }
  public String getName(){
    return name;
  }
  public String getHost(){
    return host;
  }
  public int getPort(){
    return port;
  }
}
