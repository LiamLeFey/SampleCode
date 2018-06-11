package mudclient.core;

public class DummyConnection extends WorldConnection{
  public DummyConnection(){ }
  public World getWorld(){ return new World( "none", 0 ); }
  public boolean isClosed(){ return true; }
  public boolean isConnected(){ return false; }
  public void close(){}
  public int read( byte[] buf ){ return -1; }
  public int read( char[] buf ){ return -1; }
  public void send( char[] chars, int off, int len ){}
  public void send( byte[] bytes, int off, int len ){}
  public String send( String s ){ return s; }
}
