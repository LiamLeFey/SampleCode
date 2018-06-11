package mudclient.core;

import java.io.*;
import java.nio.charset.*;
import java.net.*;

public class WorldConnection{
  private World world;

  private Socket mySocket;
  private OutputStream out;
  private BufferedInputStream in;
  private BufferedReader inReader;
  private OutputStreamWriter outWriter;

  private VariableScope worldVars;

  protected WorldConnection(){}

  public WorldConnection( World world, VariableScope root ) 
    throws UnknownHostException, IOException{
    this( world, root, null );
  }
  public WorldConnection( World world, VariableScope root, Charset cs ) 
    throws UnknownHostException, IOException{
    this.world = world;
    mySocket = new Socket( world.getHost(), world.getPort() );
    out = mySocket.getOutputStream();
    in = new BufferedInputStream( mySocket.getInputStream() );
    if( cs == null ){
      cs = Charset.defaultCharset();
    }
    CharsetDecoder decoder = cs.newDecoder();
    decoder.onUnmappableCharacter( CodingErrorAction.REPORT );
    decoder.onMalformedInput( CodingErrorAction.REPORT );
    inReader = new BufferedReader( 
                  new InputStreamReader( mySocket.getInputStream(), cs ) );
    outWriter = new OutputStreamWriter( out, cs );
    worldVars = new VariableScope( root );
  }
  public WorldConnection( String host, VariableScope root ) 
    throws UnknownHostException, IOException{
    this( host, root, null );
  }
  public WorldConnection( String host, VariableScope root, Charset cs ) 
    throws UnknownHostException, IOException{
    this( host, 23, root, cs );
  }
  public WorldConnection( String host, int port, VariableScope root ) 
    throws UnknownHostException, IOException{
    this( host, port , root, null );
  }
  public WorldConnection( String host, int port, VariableScope root, Charset cs ) 
    throws UnknownHostException, IOException{
    this( new World( host, port ), root, cs );
  }

  public World getWorld(){
    return world;
  }
  public VariableScope getWorldVariables(){
    return worldVars;
  }

  public boolean isClosed(){
    return mySocket.isClosed();
  }
  public boolean isConnected(){
    return mySocket.isConnected();
  }
  public void close() throws IOException {
    mySocket.close();
  }
  public int read( byte[] buf ) throws IOException {
    return in.read( buf );
  }
  public int read( char[] buf ) throws IOException {
    return inReader.read( buf );
  }

  public void send( char[] chars, int off, int len ) throws IOException {
    outWriter.write( chars, off, len );
    outWriter.flush();
  }
  public void send( byte[] bytes, int off, int len ) throws IOException {
    out.write( bytes, off, len );
    out.flush();
  }
  public String send( String s ) throws IOException {
    outWriter.write( s, 0, s.length() );
    outWriter.flush();
    return s;
  }
}
